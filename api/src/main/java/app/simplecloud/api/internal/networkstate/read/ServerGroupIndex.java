package app.simplecloud.api.internal.networkstate.read;

import app.simplecloud.api.CloudApiException;
import app.simplecloud.api.group.Group;
import app.simplecloud.api.group.GroupQuery;
import app.simplecloud.api.group.GroupServerType;
import app.simplecloud.api.internal.group.GroupImpl;
import app.simplecloud.api.internal.group.ServerGroupSource;
import app.simplecloud.api.internal.networkstate.NetworkCopy;
import app.simplecloud.api.web.models.ModelsServerGroupStats;
import app.simplecloud.api.web.models.ModelsServerGroupSummary;
import build.buf.gen.simplecloud.controller.v2.StateServer;
import build.buf.gen.simplecloud.controller.v2.StateServerGroup;
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
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;

/**
 * The groups of one copy as API objects, built once, and the group source of that copy. Each group is the
 * controller's {@code summary_json} for it, with the {@code stats} the copy computes, because they change with
 * every server.
 *
 * <p>Every answer is complete on return. A query with a limit below 1 throws a {@link CloudApiException} with
 * {@link CloudApiException.Reason#INVALID_REQUEST} from {@link #serverGroups} itself. An id in another form
 * than 8-4-4-4-12 matches nothing.
 */
final class ServerGroupIndex implements ServerGroupSource {

    /**
     * {@code ORDER BY created_at DESC, id}.
     */
    private static final Comparator<ListedGroup> DEFAULT_ORDER = Comparator
            .comparing(ListedGroup::createdAt, Comparator.reverseOrder())
            .thenComparing(ListedGroup::id, ControllerSortOrder.ID);

    private final List<ListedGroup> inDefaultOrder;
    private final List<Group> allGroups;
    private final Map<ByteString, Group> byId;
    private final Map<String, Group> byName;

    ServerGroupIndex(NetworkCopy copy) {
        Map<ByteString, GroupStats> stats = statsByGroup(copy);
        List<ListedGroup> listed = new ArrayList<>();
        for (StateServerGroup group : copy.serverGroups().values()) {
            ModelsServerGroupSummary summary = ControllerJson.toModel(group.getSummaryJson(),
                    ModelsServerGroupSummary.class);
            GroupStats groupStats = stats.getOrDefault(group.getId(), new GroupStats());
            summary.setStats(groupStats.toModel());
            listed.add(ListedGroup.of(new GroupImpl(summary), group.getId(), summary));
        }
        listed.sort(DEFAULT_ORDER);

        List<Group> groups = new ArrayList<>();
        Map<ByteString, Group> groupsById = new HashMap<>();
        Map<String, Group> groupsByName = new HashMap<>();
        for (ListedGroup group : listed) {
            groups.add(group.group());
            groupsById.put(group.id(), group.group());
            // The first in the default order, as a lookup by name over HTTP takes the first it lists.
            groupsByName.putIfAbsent(group.name(), group.group());
        }
        this.inDefaultOrder = List.copyOf(listed);
        this.allGroups = List.copyOf(groups);
        this.byId = groupsById;
        this.byName = groupsByName;
    }

    @Override
    public CompletableFuture<@Nullable Group> serverGroupWithId(String id) {
        ByteString idBytes = ControllerIds.bytes(id);
        Group group = idBytes == null ? null : byId.get(idBytes);
        return CompletableFuture.completedFuture(group);
    }

    @Override
    public CompletableFuture<@Nullable Group> serverGroupWithName(String name) {
        Group group = byName.get(name);
        return CompletableFuture.completedFuture(group);
    }

    /**
     * Lists the groups that match the query's type and tags, in the default order, up to its limit, and all of
     * them for no query. The query's sorting is not read, because the controller is not asked for it either.
     *
     * @throws CloudApiException with {@link CloudApiException.Reason#INVALID_REQUEST} for a limit below 1
     */
    @Override
    public CompletableFuture<List<Group>> serverGroups(@Nullable GroupQuery query) {
        if (query == null) {
            return CompletableFuture.completedFuture(allGroups);
        }
        GroupServerType type = query.getType();
        Set<String> tags = tagsOf(query.getTag());
        List<Group> matching = new ArrayList<>();
        for (ListedGroup listed : inDefaultOrder) {
            boolean typeMatches = type == null || listed.hasType(type);
            boolean tagsMatch = tags.isEmpty() || Tags.haveOneInCommon(tags, listed.tags());
            if (typeMatches && tagsMatch) {
                matching.add(listed.group());
            }
        }
        List<Group> limited = QueryLimit.firstOf(matching, query.getLimit());
        return CompletableFuture.completedFuture(List.copyOf(limited));
    }

    /**
     * Reads the tags as the controller reads the comma-separated {@code tags} parameter that carries them.
     */
    private static Set<String> tagsOf(@Nullable String commaSeparated) {
        Set<String> tags = new HashSet<>();
        if (commaSeparated == null) {
            return tags;
        }
        for (String part : commaSeparated.split(",")) {
            String tag = part.strip();
            if (!tag.isEmpty()) {
                tags.add(tag);
            }
        }
        return tags;
    }

    /**
     * Counts in one pass over the servers, so that building the groups does not scan every server once per
     * group.
     */
    private static Map<ByteString, GroupStats> statsByGroup(NetworkCopy copy) {
        Map<ByteString, GroupStats> stats = new HashMap<>();
        for (StateServer server : copy.servers().values()) {
            if (server.hasServerGroupId()) {
                GroupStats groupStats = stats.computeIfAbsent(server.getServerGroupId(), groupId -> new GroupStats());
                groupStats.add(server, copy);
            }
        }
        return stats;
    }

    /**
     * The stats as {@code loadServerGroupStats} in {@code internal/web/server_groups.go} computes them: every
     * server of the group by state, and the players of its counted servers. A group without servers has an
     * empty {@code servers_by_state}.
     */
    private static final class GroupStats {

        /**
         * By key bytes, the order in which Go's encoder writes a map, which the HTTP client's map keeps.
         */
        private final Map<String, Integer> serversByState = new TreeMap<>(ControllerSortOrder.TEXT);
        private int totalServers;
        private long onlinePlayers;

        void add(StateServer server, NetworkCopy copy) {
            totalServers++;
            serversByState.merge(server.getState(), 1, Integer::sum);
            if (PlayerCounts.countedIn(server.getState())) {
                onlinePlayers += PlayerCounts.of(server, copy);
            }
        }

        ModelsServerGroupStats toModel() {
            ModelsServerGroupStats stats = new ModelsServerGroupStats();
            stats.setTotalServers(totalServers);
            stats.setServersByState(serversByState);
            stats.setOnlinePlayers(Math.toIntExact(onlinePlayers));
            return stats;
        }
    }

    /**
     * A built group with the values its filters and order read, from its summary.
     */
    private record ListedGroup(
            Group group,
            ByteString id,
            @Nullable String name,
            @Nullable String type,
            @Nullable List<String> tags,
            Instant createdAt
    ) {

        static ListedGroup of(Group group, ByteString id, ModelsServerGroupSummary summary) {
            Instant createdAt = Instant.parse(summary.getCreatedAt());
            return new ListedGroup(group, id, summary.getName(), summary.getType(), summary.getTags(), createdAt);
        }

        boolean hasType(GroupServerType wanted) {
            return wanted.name().equals(type);
        }
    }
}
