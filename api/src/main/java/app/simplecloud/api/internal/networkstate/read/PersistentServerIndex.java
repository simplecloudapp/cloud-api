package app.simplecloud.api.internal.networkstate.read;

import app.simplecloud.api.CloudApiException;
import app.simplecloud.api.internal.networkstate.NetworkCopy;
import app.simplecloud.api.internal.persistentserver.PersistentServerImpl;
import app.simplecloud.api.persistentserver.PersistentServer;
import app.simplecloud.api.persistentserver.PersistentServerQuery;
import app.simplecloud.api.web.models.ModelsPersistentServerSummary;
import build.buf.gen.simplecloud.controller.v2.StatePersistentServer;
import build.buf.gen.simplecloud.controller.v2.StateServer;
import com.google.protobuf.ByteString;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A copy's persistent servers as the controller's persistent server list shows them: each one's
 * {@code summary_json} with the {@code player_count} the copy computes, because it changes with every server.
 * Built once, they answer the persistent server questions of a {@link ReadableCopy}.
 */
final class CopyPersistentServers {

    /**
     * {@code ORDER BY created_at DESC, id}.
     */
    private static final Comparator<ListedPersistentServer> DEFAULT_ORDER = Comparator
            .comparing(ListedPersistentServer::createdAt, Comparator.reverseOrder())
            .thenComparing(ListedPersistentServer::id, ByteOrder.ID);

    private final List<ListedPersistentServer> inDefaultOrder;
    private final List<PersistentServer> allPersistentServers;
    private final Map<ByteString, PersistentServer> byId;
    private final Map<String, PersistentServer> byName;

    CopyPersistentServers(NetworkCopy copy) {
        Map<ByteString, Long> playerCounts = playerCountsByPersistentServer(copy);
        List<ListedPersistentServer> listed = new ArrayList<>();
        for (StatePersistentServer persistentServer : copy.persistentServers().values()) {
            ModelsPersistentServerSummary summary = ControllerJson.toModel(persistentServer.getSummaryJson(),
                    ModelsPersistentServerSummary.class);
            long playerCount = playerCounts.getOrDefault(persistentServer.getId(), 0L);
            summary.setPlayerCount(Math.toIntExact(playerCount));
            PersistentServer built = new PersistentServerImpl(summary);
            listed.add(ListedPersistentServer.of(built, persistentServer.getId(), summary));
        }
        listed.sort(DEFAULT_ORDER);

        List<PersistentServer> persistentServers = new ArrayList<>();
        Map<ByteString, PersistentServer> persistentServersById = new HashMap<>();
        Map<String, PersistentServer> persistentServersByName = new HashMap<>();
        for (ListedPersistentServer persistentServer : listed) {
            persistentServers.add(persistentServer.persistentServer());
            persistentServersById.put(persistentServer.id(), persistentServer.persistentServer());
            // The first in the default order, as a lookup by name over HTTP takes the first it lists.
            persistentServersByName.putIfAbsent(persistentServer.name(), persistentServer.persistentServer());
        }
        this.inDefaultOrder = List.copyOf(listed);
        this.allPersistentServers = List.copyOf(persistentServers);
        this.byId = persistentServersById;
        this.byName = persistentServersByName;
    }

    /**
     * Returns the persistent servers that match every filter the query sets, in the default order, up to its
     * limit.
     *
     * @throws CloudApiException with {@link CloudApiException.Reason#INVALID_REQUEST} for a limit below 1
     */
    List<PersistentServer> persistentServers(@Nullable PersistentServerQuery query) {
        if (query == null) {
            return allPersistentServers;
        }
        List<String> tags = query.getTags();
        Boolean active = query.getActive();
        String serverhostId = query.getServerhostId();
        boolean filtersByTags = tags != null && !tags.isEmpty();
        boolean filtersByServerhost = serverhostId != null && !serverhostId.isBlank();
        Set<String> wantedTags = filtersByTags ? new HashSet<>(tags) : Set.of();
        ByteString wantedServerhost = filtersByServerhost ? ControllerJson.idBytes(serverhostId) : null;

        List<PersistentServer> matching = new ArrayList<>();
        for (ListedPersistentServer listed : inDefaultOrder) {
            boolean tagsMatch = !filtersByTags || listed.hasTagOf(wantedTags);
            boolean activeMatches = active == null || active.equals(listed.active());
            boolean serverhostMatches = !filtersByServerhost || listed.isOnServerhost(wantedServerhost);
            if (tagsMatch && activeMatches && serverhostMatches) {
                matching.add(listed.persistentServer());
            }
        }
        return List.copyOf(QueryLimit.firstOf(matching, query.getLimit()));
    }

    @Nullable PersistentServer persistentServerWithId(String id) {
        ByteString idBytes = ControllerJson.idBytes(id);
        return idBytes == null ? null : byId.get(idBytes);
    }

    @Nullable PersistentServer persistentServerWithName(String name) {
        return byName.get(name);
    }

    /**
     * Adds up the players of each persistent server's counted servers in one pass over the servers, as
     * {@code loadPersistentServerPlayerCountsFromMetrics} in {@code internal/web/persistent_servers.go} does.
     * It skips counts of 0 or less; the pulse carries no count below 0, so every count adds as it is.
     */
    private static Map<ByteString, Long> playerCountsByPersistentServer(NetworkCopy copy) {
        Map<ByteString, Long> playerCounts = new HashMap<>();
        for (StateServer server : copy.servers().values()) {
            boolean counted = server.hasPersistentServerId() && OnlinePlayers.areCounted(server.getState());
            if (counted) {
                playerCounts.merge(server.getPersistentServerId(), OnlinePlayers.reportedBy(server, copy), Long::sum);
            }
        }
        return playerCounts;
    }

    /**
     * A built persistent server with the values its filters and order read, from its summary.
     *
     * @param serverhostId null for a persistent server without a serverhost
     */
    private record ListedPersistentServer(
            PersistentServer persistentServer,
            ByteString id,
            @Nullable String name,
            @Nullable List<String> tags,
            @Nullable Boolean active,
            @Nullable ByteString serverhostId,
            Instant createdAt
    ) {

        static ListedPersistentServer of(
                PersistentServer persistentServer,
                ByteString id,
                ModelsPersistentServerSummary summary
        ) {
            ByteString serverhostId = ControllerJson.idBytes(summary.getServerhostId());
            Instant createdAt = Instant.parse(summary.getCreatedAt());
            return new ListedPersistentServer(persistentServer, id, summary.getName(), summary.getTags(),
                    summary.getActive(), serverhostId, createdAt);
        }

        boolean isOnServerhost(@Nullable ByteString wanted) {
            return serverhostId != null && serverhostId.equals(wanted);
        }

        /**
         * Tags match as Postgres's {@code &&} does: one tag in common is enough.
         */
        boolean hasTagOf(Set<String> wanted) {
            return tags != null && !Collections.disjoint(wanted, tags);
        }
    }
}
