package app.simplecloud.api.internal.networkstate.read;

import app.simplecloud.api.CloudApiException;
import app.simplecloud.api.internal.networkstate.NetworkCopy;
import app.simplecloud.api.internal.persistentserver.PersistentServerImpl;
import app.simplecloud.api.internal.persistentserver.PersistentServerSource;
import app.simplecloud.api.persistentserver.PersistentServer;
import app.simplecloud.api.persistentserver.PersistentServerQuery;
import app.simplecloud.api.web.models.ModelsPersistentServerSummary;
import build.buf.gen.simplecloud.controller.v2.StatePersistentServer;
import build.buf.gen.simplecloud.controller.v2.StateServer;
import com.google.protobuf.ByteString;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * The persistent servers of one copy as API objects, built once, and the persistent server source of that copy.
 * Each one is the controller's {@code summary_json} for it, with the {@code player_count} the copy computes,
 * because it changes with every server.
 *
 * <p>Every answer is complete on return. A query with a limit below 1 throws a {@link CloudApiException} with
 * {@link CloudApiException.Reason#INVALID_REQUEST} from {@link #persistentServers} itself. An id in another
 * form than 8-4-4-4-12 matches nothing.
 */
final class PersistentServerIndex implements PersistentServerSource {

    /**
     * {@code ORDER BY created_at DESC, id}.
     */
    private static final Comparator<ListedPersistentServer> DEFAULT_ORDER = Comparator
            .comparing(ListedPersistentServer::createdAt, Comparator.reverseOrder())
            .thenComparing(ListedPersistentServer::id, ControllerSortOrder.ID);

    private final List<ListedPersistentServer> inDefaultOrder;
    private final List<PersistentServer> allPersistentServers;
    private final Map<ByteString, PersistentServer> byId;
    private final Map<String, PersistentServer> byName;

    PersistentServerIndex(NetworkCopy copy) {
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

    @Override
    public CompletableFuture<@Nullable PersistentServer> persistentServerWithId(String id) {
        ByteString idBytes = ControllerIds.bytes(id);
        PersistentServer persistentServer = idBytes == null ? null : byId.get(idBytes);
        return CompletableFuture.completedFuture(persistentServer);
    }

    @Override
    public CompletableFuture<@Nullable PersistentServer> persistentServerWithName(String name) {
        PersistentServer persistentServer = byName.get(name);
        return CompletableFuture.completedFuture(persistentServer);
    }

    /**
     * Lists the persistent servers that match every filter the query sets, in the default order, up to its
     * limit, and all of them for no query.
     *
     * @throws CloudApiException with {@link CloudApiException.Reason#INVALID_REQUEST} for a limit below 1
     */
    @Override
    public CompletableFuture<List<PersistentServer>> persistentServers(@Nullable PersistentServerQuery query) {
        if (query == null) {
            return CompletableFuture.completedFuture(allPersistentServers);
        }
        List<String> tags = query.getTags();
        Boolean active = query.getActive();
        String serverhostId = query.getServerhostId();
        boolean filtersByTags = tags != null && !tags.isEmpty();
        boolean filtersByServerhost = serverhostId != null && !serverhostId.isBlank();
        Set<String> wantedTags = filtersByTags ? new HashSet<>(tags) : Set.of();
        ByteString wantedServerhost = filtersByServerhost ? ControllerIds.bytes(serverhostId) : null;

        List<PersistentServer> matching = new ArrayList<>();
        for (ListedPersistentServer listed : inDefaultOrder) {
            boolean tagsMatch = !filtersByTags || Tags.haveOneInCommon(wantedTags, listed.tags());
            boolean activeMatches = active == null || active.equals(listed.active());
            boolean serverhostMatches = !filtersByServerhost || listed.isOnServerhost(wantedServerhost);
            if (tagsMatch && activeMatches && serverhostMatches) {
                matching.add(listed.persistentServer());
            }
        }
        List<PersistentServer> limited = QueryLimit.firstOf(matching, query.getLimit());
        return CompletableFuture.completedFuture(List.copyOf(limited));
    }

    /**
     * Adds up the players of each persistent server's counted servers in one pass over the servers, as
     * {@code loadPersistentServerPlayerCountsFromMetrics} in {@code internal/web/persistent_servers.go} does.
     * The controller skips counts of 0 or less; the pulse carries no count below 0, so adding every count gives
     * the same sum.
     */
    private static Map<ByteString, Long> playerCountsByPersistentServer(NetworkCopy copy) {
        Map<ByteString, Long> playerCounts = new HashMap<>();
        for (StateServer server : copy.servers().values()) {
            boolean counted = server.hasPersistentServerId() && PlayerCounts.countedIn(server.getState());
            if (counted) {
                long playerCount = PlayerCounts.of(server, copy);
                playerCounts.merge(server.getPersistentServerId(), playerCount, Long::sum);
            }
        }
        return playerCounts;
    }

    /**
     * A built persistent server with the values its filters and order read, from its summary.
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
            ByteString serverhostId = ControllerIds.bytes(summary.getServerhostId());
            Instant createdAt = Instant.parse(summary.getCreatedAt());
            return new ListedPersistentServer(persistentServer, id, summary.getName(), summary.getTags(),
                    summary.getActive(), serverhostId, createdAt);
        }

        boolean isOnServerhost(@Nullable ByteString wanted) {
            return serverhostId != null && serverhostId.equals(wanted);
        }
    }
}
