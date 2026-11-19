package app.simplecloud.api.internal.networkstate.read;

import app.simplecloud.api.CloudApiException;
import app.simplecloud.api.group.GroupServerType;
import app.simplecloud.api.internal.networkstate.NetworkCopy;
import app.simplecloud.api.internal.server.ServerImpl;
import app.simplecloud.api.server.Server;
import app.simplecloud.api.server.ServerQuery;
import app.simplecloud.api.server.ServerState;
import app.simplecloud.api.web.models.ModelsBlueprintInfo;
import app.simplecloud.api.web.models.ModelsPersistentServerInfo;
import app.simplecloud.api.web.models.ModelsServerGroupInfo;
import app.simplecloud.api.web.models.ModelsServerSummary;
import app.simplecloud.api.web.models.ModelsSourceInfo;
import build.buf.gen.simplecloud.controller.v2.StateBlueprint;
import build.buf.gen.simplecloud.controller.v2.StatePersistentServer;
import build.buf.gen.simplecloud.controller.v2.StateServer;
import build.buf.gen.simplecloud.controller.v2.StateServerGroup;
import build.buf.gen.simplecloud.controller.v2.StateServerMetrics;
import com.google.protobuf.ByteString;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * A copy's servers as the controller's server list shows them ({@code buildServerSummary} in
 * {@code internal/web/server_mapper.go}), built once, and the server questions of a {@link ReadableCopy}.
 * Each group, persistent server and blueprint is read from its JSON once, however many servers it has.
 */
final class CopyServers {

    private static final Map<String, Comparator<ListedServer>> SORT_FIELDS = Map.of(
            "created_at", Comparator.comparingLong(ListedServer::createdAtMicros),
            "updated_at", Comparator.comparingLong(ListedServer::updatedAtMicros),
            "numerical_id", Comparator.comparingInt(ListedServer::numericalId),
            "state", Comparator.comparing(ListedServer::state, ByteOrder.TEXT));
    private static final String SORT_FIELD_NAMES = "created_at, updated_at, numerical_id or state";
    private static final Comparator<ListedServer> KEEPS_DEFAULT_ORDER = (first, second) -> 0;

    /**
     * Groups first by id and persistent servers last, as Postgres sorts the NULL group id of a persistent
     * server's server after every group id.
     */
    private static final Comparator<StateServer> DEFAULT_ORDER = Comparator
            .comparing(CopyServers::groupIdOrNull, Comparator.nullsLast(ByteOrder.ID))
            .thenComparingInt(StateServer::getNumericalId)
            .thenComparing(StateServer::getId, ByteOrder.ID);

    private final List<ListedServer> inDefaultOrder;
    private final List<Server> allServers;
    private final Map<ByteString, Server> byId;

    /**
     * @param networkId the copy's network, a UUID, which the rows do not carry
     * @throws IllegalStateException if a reference of a server does not resolve in the copy, which a view's
     *                               copy rules out
     */
    CopyServers(NetworkCopy copy, String networkId) {
        String networkIdText = UUID.fromString(networkId).toString();
        Map<ByteString, ModelsServerGroupInfo> groups = groupInfos(copy);
        Map<ByteString, ModelsPersistentServerInfo> persistentServers = persistentServerInfos(copy);
        List<StateServer> rows = new ArrayList<>(copy.servers().values());
        rows.sort(DEFAULT_ORDER);

        List<ListedServer> listed = new ArrayList<>();
        Map<ByteString, Server> servers = new HashMap<>();
        for (StateServer row : rows) {
            ModelsServerSummary summary = summaryOf(row, networkIdText, groups, persistentServers, copy);
            ListedServer server = ListedServer.of(new ServerImpl(summary), row, summary);
            listed.add(server);
            servers.put(row.getId(), server.server());
        }
        this.inDefaultOrder = List.copyOf(listed);
        this.allServers = serversOf(inDefaultOrder);
        this.byId = Map.copyOf(servers);
    }

    /**
     * Returns the servers that match every filter the query sets, in its order.
     *
     * @throws CloudApiException with {@link CloudApiException.Reason#INVALID_REQUEST} for an unknown sort field
     *                           or sort order
     */
    List<Server> servers(@Nullable ServerQuery query) {
        if (query == null) {
            return allServers;
        }
        Comparator<ListedServer> order = orderOf(query);
        Predicate<ListedServer> matches = filterOf(query);
        List<ListedServer> matching = new ArrayList<>();
        for (ListedServer listed : inDefaultOrder) {
            if (matches.test(listed)) {
                matching.add(listed);
            }
        }
        // A stable sort, so servers that tie on the field keep the default order, as the SQL appends it.
        matching.sort(order);
        return serversOf(matching);
    }

    @Nullable Server serverWithId(String id) {
        ByteString idBytes = ControllerJson.idBytes(id);
        return idBytes == null ? null : byId.get(idBytes);
    }

    private static List<Server> serversOf(List<ListedServer> listed) {
        List<Server> servers = new ArrayList<>();
        for (ListedServer server : listed) {
            servers.add(server.server());
        }
        return List.copyOf(servers);
    }

    /**
     * Reads the sorting as {@code parseServerSorting} does: no field keeps the default order, and the
     * direction is ascending unless it says {@code desc}.
     */
    private static Comparator<ListedServer> orderOf(ServerQuery query) {
        String field = trimmed(query.getSortBy());
        String direction = trimmed(query.getSortOrder());
        boolean descending = direction.equals("desc");
        boolean directionKnown = direction.isEmpty() || direction.equals("asc") || descending;
        if (!directionKnown) {
            throw invalidRequest("Unknown sort order '" + query.getSortOrder() + "', use asc or desc");
        }
        if (field.isEmpty()) {
            return KEEPS_DEFAULT_ORDER;
        }
        Comparator<ListedServer> byField = SORT_FIELDS.get(field);
        if (byField == null) {
            throw invalidRequest("Unknown sort field '" + query.getSortBy() + "', use " + SORT_FIELD_NAMES);
        }
        return descending ? byField.reversed() : byField;
    }

    private static Predicate<ListedServer> filterOf(ServerQuery query) {
        List<Predicate<ListedServer>> filters = new ArrayList<>();
        if (isSet(query.getServerGroupIds())) {
            Set<ByteString> groupIds = idsOf(query.getServerGroupIds());
            filters.add(listed -> listed.isInGroupOf(groupIds));
        }
        if (isSet(query.getStates())) {
            Set<String> states = namesOf(query.getStates());
            filters.add(listed -> listed.hasStateOf(states));
        }
        if (isSet(query.getServerhostId())) {
            ByteString serverhostId = ControllerJson.idBytes(query.getServerhostId());
            filters.add(listed -> listed.isOnServerhost(serverhostId));
        }
        if (isSet(query.getPersistentServerId())) {
            ByteString persistentServerId = ControllerJson.idBytes(query.getPersistentServerId());
            filters.add(listed -> listed.isOfPersistentServer(persistentServerId));
        }
        if (isSet(query.getNumericalIds())) {
            Set<Integer> numericalIds = new HashSet<>(query.getNumericalIds());
            filters.add(listed -> numericalIds.contains(listed.numericalId()));
        }
        if (isSet(query.getServerGroupTypes())) {
            Set<String> types = typeNamesOf(query.getServerGroupTypes());
            filters.add(listed -> types.contains(listed.parentType()));
        }
        if (isSet(query.getServerGroupNames())) {
            Set<String> names = new HashSet<>(query.getServerGroupNames());
            filters.add(listed -> names.contains(listed.parentName()));
        }
        if (isSet(query.getServerGroupTags())) {
            Set<String> tags = new HashSet<>(query.getServerGroupTags());
            filters.add(listed -> listed.parentHasTagOf(tags));
        }
        return listed -> matchesAll(listed, filters);
    }

    private static boolean matchesAll(ListedServer listed, List<Predicate<ListedServer>> filters) {
        for (Predicate<ListedServer> filter : filters) {
            if (!filter.test(listed)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isSet(@Nullable Collection<?> values) {
        return values != null && !values.isEmpty();
    }

    private static boolean isSet(@Nullable String value) {
        return value != null && !value.isBlank();
    }

    private static String trimmed(@Nullable String value) {
        return value == null ? "" : value.strip();
    }

    private static Set<ByteString> idsOf(List<String> uuids) {
        Set<ByteString> ids = new HashSet<>();
        for (String uuid : uuids) {
            ByteString id = ControllerJson.idBytes(uuid);
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }

    private static Set<String> namesOf(List<ServerState> states) {
        Set<String> names = new HashSet<>();
        for (ServerState state : states) {
            names.add(state.name());
        }
        return names;
    }

    private static Set<String> typeNamesOf(List<GroupServerType> types) {
        Set<String> names = new HashSet<>();
        for (GroupServerType type : types) {
            names.add(type.name());
        }
        return names;
    }

    private static CloudApiException invalidRequest(String message) {
        return new CloudApiException(CloudApiException.Reason.INVALID_REQUEST, message);
    }

    /**
     * Sets the members that come from the row and leaves a NULL column unset, as Go's {@code omitempty}
     * leaves out its member.
     */
    private static ModelsServerSummary summaryOf(
            StateServer row,
            String networkId,
            Map<ByteString, ModelsServerGroupInfo> groups,
            Map<ByteString, ModelsPersistentServerInfo> persistentServers,
            NetworkCopy copy
    ) {
        ModelsServerSummary summary = new ModelsServerSummary();
        summary.setServerId(ControllerJson.uuid(row.getId()));
        summary.setNetworkId(networkId);
        if (row.hasServerGroupId()) {
            summary.setServerGroupId(ControllerJson.uuid(row.getServerGroupId()));
            summary.setServerGroup(resolve(groups, row.getServerGroupId(), "server group"));
        }
        if (row.hasPersistentServerId()) {
            summary.setPersistentServerId(ControllerJson.uuid(row.getPersistentServerId()));
            summary.setPersistentServer(resolve(persistentServers, row.getPersistentServerId(), "persistent server"));
        }
        if (row.hasServerhostId()) {
            summary.setServerhostId(ControllerJson.uuid(row.getServerhostId()));
        }
        summary.setNumericalId(row.getNumericalId());
        if (row.hasIp()) {
            summary.setIp(row.getIp());
        }
        if (row.hasPort()) {
            summary.setPort(Math.toIntExact(row.getPort()));
        }
        summary.setMinMemory(Math.toIntExact(row.getMinMemory()));
        summary.setMaxMemory(Math.toIntExact(row.getMaxMemory()));
        summary.setMaxPlayers(Math.toIntExact(row.getMaxPlayers()));
        summary.setState(ModelsServerSummary.StateEnum.fromValue(row.getState()));
        summary.setProperties(ControllerJson.toProperties(row.getPropertiesJson()));
        summary.setCreatedAt(ControllerJson.timestamp(ControllerJson.fromMicros(row.getCreatedAtMicros())));
        summary.setUpdatedAt(ControllerJson.timestamp(ControllerJson.fromMicros(row.getUpdatedAtMicros())));
        setMetrics(summary, row, copy);
        return summary;
    }

    /**
     * Sets zeros and an empty last activity for a server the pulse left out, as the controller does for stale
     * metrics.
     */
    private static void setMetrics(ModelsServerSummary summary, StateServer row, NetworkCopy copy) {
        StateServerMetrics metrics = copy.metrics().getOrDefault(row.getId(), StateServerMetrics.getDefaultInstance());
        summary.setPlayerCount(Math.toIntExact(OnlinePlayers.reportedBy(row, copy)));
        summary.setCpuUsage(finiteOrNull(metrics.getCpuUsage()));
        summary.setMemoryUsage(finiteOrNull(metrics.getMemoryUsage()));
        String lastActivity = metrics.hasLastActivityNanos()
                ? ControllerJson.timestamp(ControllerJson.fromNanos(metrics.getLastActivityNanos()))
                : "";
        summary.setLastActivity(lastActivity);
    }

    private static @Nullable BigDecimal finiteOrNull(double metric) {
        return Double.isFinite(metric) ? BigDecimal.valueOf(metric) : null;
    }

    private static Map<ByteString, ModelsServerGroupInfo> groupInfos(NetworkCopy copy) {
        Map<ByteString, ModelsBlueprintInfo> blueprints = new HashMap<>();
        Map<ByteString, ModelsServerGroupInfo> infos = new HashMap<>();
        for (StateServerGroup group : copy.serverGroups().values()) {
            infos.put(group.getId(), groupInfo(group, blueprints, copy));
        }
        return infos;
    }

    /**
     * Joins the group's blueprint into its info, where the controller's join puts it: into {@code source},
     * whenever the group names a blueprint, also for an image source.
     *
     * @param blueprints the blueprints read so far, so that each is read once
     */
    private static ModelsServerGroupInfo groupInfo(
            StateServerGroup group,
            Map<ByteString, ModelsBlueprintInfo> blueprints,
            NetworkCopy copy
    ) {
        ModelsServerGroupInfo info = ControllerJson.toModel(group.getInfoJson(), ModelsServerGroupInfo.class);
        if (!group.hasBlueprintId()) {
            return info;
        }
        ModelsSourceInfo source = info.getSource();
        if (source == null) {
            throw new IllegalStateException("Server group " + ControllerJson.uuid(group.getId()) + " has no source");
        }
        ModelsBlueprintInfo blueprint = blueprints.computeIfAbsent(group.getBlueprintId(),
                blueprintId -> blueprintInfo(blueprintId, copy));
        source.setBlueprint(blueprint);
        return info;
    }

    private static ModelsBlueprintInfo blueprintInfo(ByteString blueprintId, NetworkCopy copy) {
        StateBlueprint blueprint = resolve(copy.blueprints(), blueprintId, "blueprint");
        return ControllerJson.toModel(blueprint.getInfoJson(), ModelsBlueprintInfo.class);
    }

    private static Map<ByteString, ModelsPersistentServerInfo> persistentServerInfos(NetworkCopy copy) {
        Map<ByteString, ModelsPersistentServerInfo> infos = new HashMap<>();
        for (StatePersistentServer persistentServer : copy.persistentServers().values()) {
            ModelsPersistentServerInfo info = ControllerJson.toModel(persistentServer.getInfoJson(),
                    ModelsPersistentServerInfo.class);
            infos.put(persistentServer.getId(), info);
        }
        return infos;
    }

    /**
     * Follows a reference between items of a copy. A view checks after every batch and snapshot that each one
     * resolves, so a missing item here is a bug in the view, not a state of the network.
     */
    private static <T> T resolve(Map<ByteString, T> items, ByteString id, String kind) {
        T item = items.get(id);
        if (item == null) {
            throw new IllegalStateException("The copy has no " + kind + " " + ControllerJson.uuid(id));
        }
        return item;
    }

    private static @Nullable ByteString groupIdOrNull(StateServer server) {
        return server.hasServerGroupId() ? server.getServerGroupId() : null;
    }

    /**
     * A built server with the values its filters and sort fields read: the row's own, and its parent's,
     * which is its group, or its persistent server when it has no group. A server without either has no
     * parent values, so a filter by parent leaves it out, as the SQL's outer joins do.
     */
    private record ListedServer(
            Server server,
            StateServer row,
            @Nullable String parentName,
            @Nullable String parentType,
            @Nullable List<String> parentTags
    ) {

        static ListedServer of(Server server, StateServer row, ModelsServerSummary summary) {
            ModelsServerGroupInfo group = summary.getServerGroup();
            if (group != null) {
                return new ListedServer(server, row, group.getName(), group.getType(), group.getTags());
            }
            ModelsPersistentServerInfo persistentServer = summary.getPersistentServer();
            if (persistentServer != null) {
                return new ListedServer(server, row, persistentServer.getName(), persistentServer.getType(),
                        persistentServer.getTags());
            }
            return new ListedServer(server, row, null, null, null);
        }

        long createdAtMicros() {
            return row.getCreatedAtMicros();
        }

        long updatedAtMicros() {
            return row.getUpdatedAtMicros();
        }

        int numericalId() {
            return row.getNumericalId();
        }

        String state() {
            return row.getState();
        }

        boolean isInGroupOf(Set<ByteString> groupIds) {
            return row.hasServerGroupId() && groupIds.contains(row.getServerGroupId());
        }

        boolean hasStateOf(Set<String> states) {
            return states.contains(state());
        }

        boolean isOnServerhost(@Nullable ByteString serverhostId) {
            return row.hasServerhostId() && row.getServerhostId().equals(serverhostId);
        }

        boolean isOfPersistentServer(@Nullable ByteString persistentServerId) {
            return row.hasPersistentServerId() && row.getPersistentServerId().equals(persistentServerId);
        }

        /**
         * Tags match as Postgres's {@code &&} does: one tag in common is enough.
         */
        boolean parentHasTagOf(Set<String> tags) {
            return parentTags != null && !Collections.disjoint(tags, parentTags);
        }
    }
}
