package app.simplecloud.api.internal.networkstate.read;

import app.simplecloud.api.CloudApiException;
import app.simplecloud.api.group.GroupServerType;
import app.simplecloud.api.internal.networkstate.NetworkCopy;
import app.simplecloud.api.internal.server.ServerImpl;
import app.simplecloud.api.internal.server.ServerSource;
import app.simplecloud.api.server.Server;
import app.simplecloud.api.server.ServerQuery;
import app.simplecloud.api.server.ServerState;
import app.simplecloud.api.web.models.ModelsPersistentServerInfo;
import app.simplecloud.api.web.models.ModelsServerGroupInfo;
import app.simplecloud.api.web.models.ModelsServerSummary;
import build.buf.gen.simplecloud.controller.v2.StateServer;
import com.google.protobuf.ByteString;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

/**
 * The servers of one copy as API objects, built once, and the server source of that copy. It keeps them in the
 * controller's default order, and filters and sorts them as the controller's server list does.
 *
 * <p>Every answer is complete on return. A query with an unknown sort field or sort order throws a
 * {@link CloudApiException} with {@link CloudApiException.Reason#INVALID_REQUEST} from {@link #servers}
 * itself. An id in another form than 8-4-4-4-12 matches nothing.
 */
final class ServerIndex implements ServerSource {

    private static final Map<String, Comparator<ListedServer>> SORT_FIELDS = sortFields();
    private static final Comparator<ListedServer> KEEPS_DEFAULT_ORDER = (first, second) -> 0;

    /**
     * Groups first by id and persistent servers last, as Postgres sorts the NULL group id of a persistent
     * server's server after every group id.
     */
    private static final Comparator<StateServer> DEFAULT_ORDER = Comparator
            .comparing(ServerIndex::groupIdOrNull, Comparator.nullsLast(ControllerSortOrder.ID))
            .thenComparingInt(StateServer::getNumericalId)
            .thenComparing(StateServer::getId, ControllerSortOrder.ID);

    private final List<ListedServer> inDefaultOrder;
    private final List<Server> allServers;
    private final Map<ByteString, Server> byId;

    /**
     * @param networkId the copy's network, a UUID, which the rows do not carry
     * @throws IllegalStateException if a reference of a server does not resolve in the copy, which a view's
     *                               copy rules out
     */
    ServerIndex(NetworkCopy copy, String networkId) {
        ServerSummaries summaries = new ServerSummaries(copy, networkId);
        List<StateServer> rows = new ArrayList<>(copy.servers().values());
        rows.sort(DEFAULT_ORDER);

        List<ListedServer> listed = new ArrayList<>();
        Map<ByteString, Server> servers = new HashMap<>();
        for (StateServer row : rows) {
            ModelsServerSummary summary = summaries.of(row);
            ListedServer server = ListedServer.of(new ServerImpl(summary), row, summary);
            listed.add(server);
            servers.put(row.getId(), server.server());
        }
        this.inDefaultOrder = List.copyOf(listed);
        this.allServers = serversOf(inDefaultOrder);
        this.byId = Map.copyOf(servers);
    }

    @Override
    public CompletableFuture<@Nullable Server> serverWithId(String id) {
        ByteString idBytes = ControllerIds.bytes(id);
        Server server = idBytes == null ? null : byId.get(idBytes);
        return CompletableFuture.completedFuture(server);
    }

    /**
     * Picks the first server in the default order whose parent has the name, as a lookup over HTTP picks the
     * first of the list it filters by name and numerical id.
     */
    @Override
    public CompletableFuture<@Nullable Server> serverWithNumericalId(String groupName, int numericalId) {
        for (ListedServer listed : inDefaultOrder) {
            boolean ofGroup = groupName.equals(listed.parentName());
            boolean withNumericalId = listed.numericalId() == numericalId;
            if (ofGroup && withNumericalId) {
                return CompletableFuture.completedFuture(listed.server());
            }
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Lists the servers whose parent has the name, as the controller filters by a group name.
     */
    @Override
    public CompletableFuture<List<Server>> serversOfGroup(String groupName) {
        List<Server> ofGroup = new ArrayList<>();
        for (ListedServer listed : inDefaultOrder) {
            if (groupName.equals(listed.parentName())) {
                ofGroup.add(listed.server());
            }
        }
        return CompletableFuture.completedFuture(List.copyOf(ofGroup));
    }

    /**
     * Lists the servers that match every filter the query sets, in its order, and all of them for no query.
     *
     * @throws CloudApiException with {@link CloudApiException.Reason#INVALID_REQUEST} for an unknown sort field
     *                           or sort order
     */
    @Override
    public CompletableFuture<List<Server>> servers(@Nullable ServerQuery query) {
        if (query == null) {
            return CompletableFuture.completedFuture(allServers);
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
        List<Server> servers = serversOf(matching);
        return CompletableFuture.completedFuture(servers);
    }

    private static Map<String, Comparator<ListedServer>> sortFields() {
        Map<String, Comparator<ListedServer>> fields = new LinkedHashMap<>();
        fields.put("created_at", Comparator.comparingLong(ListedServer::createdAtMicros));
        fields.put("updated_at", Comparator.comparingLong(ListedServer::updatedAtMicros));
        fields.put("numerical_id", Comparator.comparingInt(ListedServer::numericalId));
        fields.put("state", Comparator.comparing(ListedServer::state, ControllerSortOrder.TEXT));
        return fields;
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
            String knownFields = String.join(", ", SORT_FIELDS.keySet());
            throw invalidRequest("Unknown sort field '" + query.getSortBy() + "', use one of " + knownFields);
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
            ByteString serverhostId = ControllerIds.bytes(query.getServerhostId());
            filters.add(listed -> listed.isOnServerhost(serverhostId));
        }
        if (isSet(query.getPersistentServerId())) {
            ByteString persistentServerId = ControllerIds.bytes(query.getPersistentServerId());
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
            filters.add(listed -> Tags.haveOneInCommon(tags, listed.parentTags()));
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
            ByteString id = ControllerIds.bytes(uuid);
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
    }
}
