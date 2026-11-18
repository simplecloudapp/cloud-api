package app.simplecloud.api.internal.networkstate.read;

import app.simplecloud.api.internal.networkstate.NetworkCopy;
import app.simplecloud.api.internal.networkstate.ReadKind;
import app.simplecloud.api.web.models.ModelsListServersResponse;
import build.buf.gen.simplecloud.controller.v2.StatePersistentServer;
import build.buf.gen.simplecloud.controller.v2.StateServer;
import build.buf.gen.simplecloud.controller.v2.StateServerGroup;
import build.buf.gen.simplecloud.controller.v2.StateServerMetrics;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.protobuf.ByteString;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Answers {@code GET /v0/servers} from a copy as the controller would at the copy's revision:
 * {@code handleListServers} in {@code internal/web/server_handlers.go} and {@code ListServersWithFilters} in
 * {@code db/queries/servers.sql}, for a caller that may read every server.
 */
public final class CopyServerList implements CopyList<ServerListParameters, ModelsListServersResponse> {

    private static final Set<String> STATES = Set.of(
            "QUEUED", "PREPARING", "STARTING", "AVAILABLE", "INGAME", "STOPPING", "CLEANUP", "STOPPED");
    private static final Set<String> TYPES = Set.of("SERVER", "PROXY");
    private static final Map<String, Comparator<StateServer>> SORT_FIELDS = Map.of(
            "created_at", Comparator.comparingLong(StateServer::getCreatedAtMicros),
            "updated_at", Comparator.comparingLong(StateServer::getUpdatedAtMicros),
            "numerical_id", Comparator.comparingInt(StateServer::getNumericalId),
            "state", Comparator.comparing(StateServer::getState, ByteOrder.TEXT));

    /**
     * Groups first by id and persistent servers last, as Postgres sorts the NULL group id of a persistent
     * server's server after every group id.
     */
    private static final Comparator<StateServer> DEFAULT_ORDER = Comparator
            .comparing(CopyServerList::groupIdOrNull, Comparator.nullsLast(ByteOrder.ID))
            .thenComparingInt(StateServer::getNumericalId)
            .thenComparing(StateServer::getId, ByteOrder.ID);

    private final String networkId;

    /**
     * @param networkId the copy's network, a UUID, which the rows do not carry. It is parsed only when
     *                  answering: an instance whose network id is no UUID has no view, so it never answers.
     */
    public CopyServerList(String networkId) {
        this.networkId = networkId;
    }

    @Override
    public ReadKind kind() {
        return ReadKind.SERVER;
    }

    /**
     * Returns the controller's answer, read as the HTTP client reads it, or empty when only the controller can
     * give it:
     * <ul>
     *   <li>it refuses the parameters with an error;</li>
     *   <li>the request picks servers by id or numerical id, and the copy has none of them: such a server may
     *   be newer than the copy;</li>
     *   <li>a listed server has a metric that is not a finite number, which the controller cannot encode.</li>
     * </ul>
     * A body the HTTP client cannot read throws as it does over HTTP. Every reference in the copy must
     * resolve, as it does in a view's copy.
     */
    @Override
    public Optional<ModelsListServersResponse> answer(NetworkCopy copy, ServerListParameters parameters) {
        Optional<JsonObject> body = responseBody(copy, parameters);
        return body.map(json -> ControllerJson.toModel(json, ModelsListServersResponse.class));
    }

    @Override
    public boolean refusesParameters(ServerListParameters parameters) {
        return Query.parse(parameters).isEmpty();
    }

    /**
     * Returns the body the controller would send, under the conditions of {@link #answer}.
     */
    Optional<JsonObject> responseBody(NetworkCopy copy, ServerListParameters parameters) {
        Optional<Query> parsed = Query.parse(parameters);
        if (parsed.isEmpty()) {
            return Optional.empty();
        }
        Query query = parsed.get();
        List<StateServer> listed = query.select(copy);
        boolean pickedServersMissing = query.picksServers() && listed.isEmpty();
        if (pickedServersMissing || hasUnencodableMetrics(listed, copy)) {
            return Optional.empty();
        }
        ByteString networkIdBytes = uuidBytes(networkId);
        JsonArray servers = new JsonArray();
        for (StateServer server : listed) {
            servers.add(ServerJson.render(server, copy, networkIdBytes));
        }
        JsonObject body = new JsonObject();
        // The handler collects the servers into a nil slice, which Go encodes as null when nothing matched.
        body.add("servers", servers.isEmpty() ? JsonNull.INSTANCE : servers);
        body.addProperty("count", servers.size());
        return Optional.of(body);
    }

    private static ByteString uuidBytes(String uuid) {
        UUID parsed = UUID.fromString(uuid);
        ByteBuffer bytes = ByteBuffer.allocate(16);
        bytes.putLong(parsed.getMostSignificantBits());
        bytes.putLong(parsed.getLeastSignificantBits());
        return ByteString.copyFrom(bytes.array());
    }

    private static boolean hasUnencodableMetrics(List<StateServer> servers, NetworkCopy copy) {
        for (StateServer server : servers) {
            StateServerMetrics metrics = copy.metrics().get(server.getId());
            boolean unencodable = metrics != null
                    && !(Double.isFinite(metrics.getCpuUsage()) && Double.isFinite(metrics.getMemoryUsage()));
            if (unencodable) {
                return true;
            }
        }
        return false;
    }

    private static @Nullable ByteString groupIdOrNull(StateServer server) {
        return server.hasServerGroupId() ? server.getServerGroupId() : null;
    }

    /**
     * A server's group or persistent server, as far as the filters read it. A server has exactly one of the
     * two, so the SQL's {@code sg.name = ANY(...) OR ps.name = ANY(...)} reads the one it has.
     */
    private record Parent(@Nullable String name, @Nullable String type, @Nullable List<String> tags) {

        static Parent of(StateServer server, NetworkCopy copy) {
            ByteString info;
            if (server.hasServerGroupId()) {
                StateServerGroup group = CopyReferences.resolve(copy.serverGroups(), server.getServerGroupId(),
                        "server group");
                info = group.getInfoJson();
            } else {
                StatePersistentServer persistentServer = CopyReferences.resolve(copy.persistentServers(),
                        server.getPersistentServerId(), "persistent server");
                info = persistentServer.getInfoJson();
            }
            JsonObject json = ControllerJson.parseObject(info);
            return new Parent(ControllerJson.text(json, "name"), ControllerJson.text(json, "type"),
                    ControllerJson.texts(json, "tags"));
        }

        static ByteString idOf(StateServer server) {
            return server.hasServerGroupId() ? server.getServerGroupId() : server.getPersistentServerId();
        }
    }

    /**
     * The parameters as {@code parseServerListFilters} in {@code internal/web/list_filters.go} reads them.
     * An empty list does not filter.
     */
    private record Query(
            List<ByteString> serverIds,
            List<ByteString> serverGroupIds,
            List<ByteString> persistentServerIds,
            List<ByteString> serverhostIds,
            List<String> states,
            List<String> types,
            List<String> names,
            List<String> tags,
            List<Integer> numericalIds,
            Comparator<StateServer> order,
            Page page
    ) {

        static Optional<Query> parse(ServerListParameters parameters) {
            Optional<List<ByteString>> serverIds = parseUuids(parameters.serverId());
            Optional<List<ByteString>> serverGroupIds = parseUuids(parameters.serverGroupId());
            Optional<List<ByteString>> persistentServerIds = parseUuids(parameters.persistentServerId());
            Optional<List<ByteString>> serverhostIds = parseUuids(parameters.serverhostId());
            List<String> states = ControllerQueryValues.splitCsv(parameters.state());
            List<String> types = ControllerQueryValues.splitCsv(parameters.type());
            List<String> names = ControllerQueryValues.splitCsv(parameters.name());
            List<String> tags = ControllerQueryValues.splitCsv(parameters.tags());
            Optional<List<Integer>> numericalIds = ControllerQueryValues.parseInt32s(
                    ControllerQueryValues.splitCsv(parameters.numericalId()));
            Optional<Comparator<StateServer>> order = order(parameters.sortBy(), parameters.sortOrder());
            Optional<Page> page = Page.parse(parameters.limit(), parameters.offset());

            boolean idsParse = serverIds.isPresent() && serverGroupIds.isPresent()
                    && persistentServerIds.isPresent() && serverhostIds.isPresent();
            boolean valuesValid = STATES.containsAll(states) && TYPES.containsAll(types);
            boolean textAccepted = names.stream().noneMatch(ControllerQueryValues::isRejectedAsText)
                    && tags.stream().noneMatch(ControllerQueryValues::isRejectedAsText);
            boolean accepted = idsParse && valuesValid && textAccepted
                    && numericalIds.isPresent() && order.isPresent() && page.isPresent();
            if (!accepted) {
                return Optional.empty();
            }
            return Optional.of(new Query(serverIds.get(), serverGroupIds.get(), persistentServerIds.get(),
                    serverhostIds.get(), states, types, names, tags, numericalIds.get(), order.get(), page.get()));
        }

        /**
         * Reads the sorting as {@code parseServerSorting} does: no field keeps the default order, and the
         * direction is {@code asc} unless it says {@code desc}. Rows that tie on the field keep the default
         * order, which the SQL appends to every sorting.
         */
        private static Optional<Comparator<StateServer>> order(@Nullable String sortBy, @Nullable String sortOrder) {
            String field = ControllerQueryValues.trimSpace(Objects.requireNonNullElse(sortBy, ""));
            String direction = ControllerQueryValues.trimSpace(Objects.requireNonNullElse(sortOrder, ""));
            boolean fieldValid = field.isEmpty() || SORT_FIELDS.containsKey(field);
            boolean descending = direction.equals("desc");
            boolean directionValid = direction.isEmpty() || direction.equals("asc") || descending;
            if (!fieldValid || !directionValid) {
                return Optional.empty();
            }
            if (field.isEmpty()) {
                return Optional.of(DEFAULT_ORDER);
            }
            Comparator<StateServer> byField = SORT_FIELDS.get(field);
            Comparator<StateServer> directed = descending ? byField.reversed() : byField;
            return Optional.of(directed.thenComparing(DEFAULT_ORDER));
        }

        private static Optional<List<ByteString>> parseUuids(@Nullable String value) {
            return ControllerQueryValues.parseUuids(ControllerQueryValues.splitCsv(value));
        }

        boolean picksServers() {
            return !serverIds.isEmpty() || !numericalIds.isEmpty();
        }

        List<StateServer> select(NetworkCopy copy) {
            Map<ByteString, Parent> parents = new HashMap<>();
            List<StateServer> matching = new ArrayList<>();
            for (StateServer server : copy.servers().values()) {
                if (matches(server, parents, copy)) {
                    matching.add(server);
                }
            }
            matching.sort(order);
            return page.select(matching);
        }

        /**
         * @param parents the parents read so far, so that each group or persistent server is parsed once
         */
        private boolean matches(StateServer server, Map<ByteString, Parent> parents, NetworkCopy copy) {
            if (!matchesRow(server)) {
                return false;
            }
            boolean filtersByParent = !types.isEmpty() || !tags.isEmpty() || !names.isEmpty();
            if (!filtersByParent) {
                return true;
            }
            Parent parent = parents.computeIfAbsent(Parent.idOf(server), parentId -> Parent.of(server, copy));
            return matchesParent(parent);
        }

        private boolean matchesRow(StateServer server) {
            boolean serverIdMatches = serverIds.isEmpty() || serverIds.contains(server.getId());
            boolean groupIdMatches = serverGroupIds.isEmpty()
                    || (server.hasServerGroupId() && serverGroupIds.contains(server.getServerGroupId()));
            boolean persistentServerIdMatches = persistentServerIds.isEmpty()
                    || (server.hasPersistentServerId() && persistentServerIds.contains(server.getPersistentServerId()));
            boolean serverhostIdMatches = serverhostIds.isEmpty()
                    || (server.hasServerhostId() && serverhostIds.contains(server.getServerhostId()));
            boolean stateMatches = states.isEmpty() || states.contains(server.getState());
            boolean numericalIdMatches = numericalIds.isEmpty() || numericalIds.contains(server.getNumericalId());
            return serverIdMatches && groupIdMatches && persistentServerIdMatches && serverhostIdMatches
                    && stateMatches && numericalIdMatches;
        }

        /**
         * Tags match as Postgres's {@code &&} does: one tag in common is enough.
         */
        private boolean matchesParent(Parent parent) {
            boolean typeMatches = types.isEmpty() || types.contains(parent.type());
            boolean tagsMatch = tags.isEmpty()
                    || (parent.tags() != null && !Collections.disjoint(tags, parent.tags()));
            boolean nameMatches = names.isEmpty() || names.contains(parent.name());
            return typeMatches && tagsMatch && nameMatches;
        }
    }
}
