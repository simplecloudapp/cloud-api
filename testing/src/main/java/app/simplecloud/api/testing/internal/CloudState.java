package app.simplecloud.api.testing.internal;

import app.simplecloud.api.CloudApiException;
import app.simplecloud.api.group.CreateGroupRequest;
import app.simplecloud.api.group.GroupQuery;
import app.simplecloud.api.group.GroupStartQueue;
import app.simplecloud.api.group.GroupStartQueueEntry;
import app.simplecloud.api.group.GroupStartQueueItemStatus;
import app.simplecloud.api.group.ScalingConfig;
import app.simplecloud.api.group.ScalingMode;
import app.simplecloud.api.group.UpdateGroupRequest;
import app.simplecloud.api.server.Server;
import app.simplecloud.api.server.ServerQuery;
import app.simplecloud.api.server.ServerState;
import app.simplecloud.api.server.UpdateServerRequest;
import app.simplecloud.api.testing.internal.event.EventBus;
import app.simplecloud.api.testing.internal.event.SimulatedEvents.GroupCreated;
import app.simplecloud.api.testing.internal.event.SimulatedEvents.GroupDeleted;
import app.simplecloud.api.testing.internal.event.SimulatedEvents.GroupUpdated;
import app.simplecloud.api.testing.internal.event.SimulatedEvents.ServerStarted;
import app.simplecloud.api.testing.internal.event.SimulatedEvents.ServerStateChanged;
import app.simplecloud.api.testing.internal.event.SimulatedEvents.ServerStopped;
import app.simplecloud.api.testing.internal.event.SimulatedEvents.ServerUpdated;
import app.simplecloud.api.testing.internal.group.GroupData;
import app.simplecloud.api.testing.internal.group.QueuedStart;
import app.simplecloud.api.testing.internal.group.TestGroupImpl;
import app.simplecloud.api.testing.internal.server.ServerData;
import app.simplecloud.api.testing.internal.server.TestServerImpl;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public final class CloudState {

    private static final String SERVERHOST_ID = "test-serverhost";
    private static final String SERVER_IP = "127.0.0.1";
    private static final int FIRST_PORT = 25566;
    private static final int DEFAULT_MIN_MEMORY = 512;
    private static final int DEFAULT_MAX_MEMORY = 1024;
    private static final int DEFAULT_MAX_PLAYERS = 20;
    private static final int MAX_SCALE_STEPS = 100;

    private static final Instant START_TIME = Instant.parse("2026-01-01T00:00:00Z");

    private final Object lock = new Object();
    private final String networkId;
    private final EventBus bus = new EventBus();
    private final Map<String, GroupData> groups = new LinkedHashMap<>();
    private final Map<String, ServerData> servers = new LinkedHashMap<>();
    private final List<QueuedStart> startQueue = new ArrayList<>();
    private final TestNetworkImpl network = new TestNetworkImpl();
    private Instant now = START_TIME;
    private boolean autoStart = true;
    private boolean scaling;
    private ServerState startState = ServerState.AVAILABLE;
    private int nextPort = FIRST_PORT;

    public CloudState(String networkId) {
        this.networkId = Objects.requireNonNull(networkId, "networkId");
    }

    public String networkId() {
        return networkId;
    }

    public EventBus bus() {
        return bus;
    }

    public TestNetworkImpl network() {
        return network;
    }

    public Instant now() {
        return read(() -> now);
    }

    public String timestamp() {
        return now().toString();
    }

    public void advanceTime(Duration duration) {
        Objects.requireNonNull(duration, "duration");
        if (duration.isNegative()) {
            throw new IllegalArgumentException("duration must be >= 0");
        }
        synchronized (lock) {
            now = now.plus(duration);
        }
    }

    public <T> T read(Supplier<T> action) {
        synchronized (lock) {
            return action.get();
        }
    }

    private <T> T mutate(Function<List<Object>, T> action) {
        List<Object> events = new ArrayList<>();
        T result;
        synchronized (lock) {
            result = action.apply(events);
            if (scaling) {
                scaleLocked(events);
            }
        }
        events.forEach(bus::publish);
        return result;
    }

    public TestGroupImpl handle(GroupData data) {
        synchronized (lock) {
            if (data.handle == null) {
                data.handle = new TestGroupImpl(this, data);
            }
            return data.handle;
        }
    }

    public TestServerImpl handle(ServerData data) {
        synchronized (lock) {
            if (data.handle == null) {
                data.handle = new TestServerImpl(this, data, handle(data.group));
            }
            return data.handle;
        }
    }

    public GroupData createGroup(CreateGroupRequest request) {
        return mutate(events -> {
            String name = request.getName();
            if (name == null || name.isBlank()) {
                throw invalid("Group name must not be blank");
            }
            if (groupByName(name).isPresent()) {
                throw conflict("Group '" + name + "' already exists");
            }
            String now = timestamp();
            GroupData group = new GroupData(UUID.randomUUID().toString(), name, now);
            if (request.getType() != null) {
                group.type = request.getType();
            }
            if (request.getActive() != null) {
                group.active = request.getActive();
            }
            group.minMemory = request.getMinMemory();
            group.maxMemory = request.getMaxMemory();
            group.maxPlayers = request.getMaxPlayers();
            group.priority = request.getPriority();
            group.deployment = request.getDeployment();
            group.scaling = request.getScaling();
            group.source = request.getSource();
            group.workflows = request.getWorkflows();
            if (request.getProperties() != null) {
                group.properties.putAll(request.getProperties());
            }
            if (request.getTags() != null) {
                group.tags.addAll(request.getTags());
            }
            groups.put(group.id, group);
            events.add(new GroupCreated(networkId, now, group.snapshot()));
            return group;
        });
    }

    public GroupData updateGroup(String id, UpdateGroupRequest request) {
        return modifyGroup(id, group -> {
            String name = request.getName();
            if (name != null && !name.equals(group.name)) {
                if (name.isBlank()) {
                    throw invalid("Group name must not be blank");
                }
                if (groupByName(name).isPresent()) {
                    throw conflict("Group '" + name + "' already exists");
                }
                group.name = name;
            }
            if (request.getType() != null) {
                group.type = request.getType();
            }
            if (request.getMinMemory() != null) {
                group.minMemory = request.getMinMemory();
            }
            if (request.getMaxMemory() != null) {
                group.maxMemory = request.getMaxMemory();
            }
            if (request.getMaxPlayers() != null) {
                group.maxPlayers = request.getMaxPlayers();
            }
            if (request.getActive() != null) {
                group.active = request.getActive();
            }
            if (request.getPriority() != null) {
                group.priority = request.getPriority();
            }
            if (request.getDeployment() != null) {
                group.deployment = request.getDeployment();
            }
            if (request.getScaling() != null) {
                group.scaling = request.getScaling();
            }
            if (request.getSource() != null) {
                group.source = request.getSource();
            }
            if (request.getWorkflows() != null) {
                group.workflows = request.getWorkflows();
            }
            if (request.getProperties() != null) {
                group.properties.clear();
                group.properties.putAll(request.getProperties());
            }
            if (request.getTags() != null) {
                group.tags.clear();
                group.tags.addAll(request.getTags());
            }
        });
    }

    public Map<String, Object> updateGroupProperties(String id, Map<String, Object> properties) {
        GroupData group = modifyGroup(id, data -> data.properties.putAll(properties));
        return read(() -> new LinkedHashMap<>(group.properties));
    }

    public Map<String, Object> deleteGroupProperties(String id, Collection<String> keys) {
        GroupData group = modifyGroup(id, data -> keys.forEach(data.properties::remove));
        return read(() -> new LinkedHashMap<>(group.properties));
    }

    private GroupData modifyGroup(String id, Consumer<GroupData> change) {
        return mutate(events -> {
            GroupData group = requireGroup(id);
            change.accept(group);
            group.updatedAt = timestamp();
            events.add(new GroupUpdated(networkId, group.updatedAt, group.snapshot()));
            return group;
        });
    }

    public void deleteGroup(String id) {
        mutate(events -> {
            GroupData group = requireGroup(id);
            for (ServerData server : serversOf(group)) {
                stopLocked(server, false, null, "group deleted", events);
            }
            startQueue.removeIf(start -> start.group == group);
            groups.remove(group.id);
            group.deleted = true;
            events.add(new GroupDeleted(networkId, timestamp(), group.id, group.name));
            return null;
        });
    }

    public Optional<GroupData> groupById(String id) {
        return Optional.ofNullable(groups.get(id));
    }

    public Optional<GroupData> groupByName(String name) {
        return groups.values().stream()
                .filter(group -> group.name.equals(name))
                .findFirst();
    }

    public List<GroupData> groups(@Nullable GroupQuery query) {
        List<GroupData> result = groups.values().stream()
                .filter(group -> matches(group, query))
                .sorted(Comparator.comparing(group -> group.name))
                .toList();
        if (query != null && query.getLimit() != null && query.getLimit() >= 0 && query.getLimit() < result.size()) {
            return result.subList(0, query.getLimit());
        }
        return result;
    }

    private static boolean matches(GroupData group, @Nullable GroupQuery query) {
        if (query == null) {
            return true;
        }
        if (query.getType() != null && query.getType() != group.type) {
            return false;
        }
        return query.getTag() == null || group.tags.contains(query.getTag());
    }

    public ServerData startServer(String groupId) {
        return mutate(events -> startLocked(requireGroup(groupId), events));
    }

    private ServerData startLocked(GroupData group, List<Object> events) {
        String now = timestamp();
        ServerData server = new ServerData(UUID.randomUUID().toString(), networkId, group, nextNumericalId(group), now);
        server.serverhostId = SERVERHOST_ID;
        server.ip = SERVER_IP;
        server.port = nextPort++;
        server.minMemory = group.minMemory == null ? DEFAULT_MIN_MEMORY : group.minMemory;
        server.maxMemory = group.maxMemory == null ? DEFAULT_MAX_MEMORY : group.maxMemory;
        server.maxPlayers = group.maxPlayers == null ? DEFAULT_MAX_PLAYERS : group.maxPlayers;
        server.state = startState;
        servers.put(server.id, server);
        events.add(new ServerStarted(networkId, now, server.snapshot()));
        return server;
    }

    private int nextNumericalId(GroupData group) {
        Set<Integer> used = serversOf(group).stream()
                .map(server -> server.numericalId)
                .collect(Collectors.toSet());
        int numericalId = 1;
        while (used.contains(numericalId)) {
            numericalId++;
        }
        return numericalId;
    }

    public ServerData modifyServer(String id, Consumer<ServerData> change) {
        return mutate(events -> {
            ServerData server = requireServer(id);
            ServerState oldState = server.state;
            change.accept(server);
            server.updatedAt = timestamp();
            Server snapshot = server.snapshot();
            if (oldState != server.state) {
                events.add(new ServerStateChanged(networkId, server.updatedAt, snapshot, oldState, server.state));
            }
            events.add(new ServerUpdated(networkId, server.updatedAt, snapshot));
            return server;
        });
    }

    public ServerData updateServer(String id, UpdateServerRequest request) {
        return modifyServer(id, server -> {
            if (request.getState() != null) {
                server.state = request.getState();
            }
            if (request.getProperties() != null) {
                server.properties.putAll(request.getProperties());
            }
            if (request.getMinMemory() != null) {
                server.minMemory = request.getMinMemory();
            }
            if (request.getMaxMemory() != null) {
                server.maxMemory = request.getMaxMemory();
            }
            if (request.getMaxPlayers() != null) {
                server.maxPlayers = request.getMaxPlayers();
            }
        });
    }

    public Map<String, Object> updateServerProperties(String id, Map<String, Object> properties) {
        ServerData server = modifyServer(id, data -> data.properties.putAll(properties));
        return read(() -> new LinkedHashMap<>(server.properties));
    }

    public Map<String, Object> deleteServerProperties(String id, Collection<String> keys) {
        ServerData server = modifyServer(id, data -> keys.forEach(data.properties::remove));
        return read(() -> new LinkedHashMap<>(server.properties));
    }

    public void stopServer(String id, boolean crashed, @Nullable Integer exitCode, @Nullable String reason) {
        mutate(events -> {
            stopLocked(requireServer(id), crashed, exitCode, reason, events);
            return null;
        });
    }

    private void stopLocked(ServerData server, boolean crashed, @Nullable Integer exitCode, @Nullable String reason,
                            List<Object> events) {
        String now = timestamp();
        ServerState oldState = server.state;
        server.state = ServerState.STOPPED;
        server.updatedAt = now;
        server.running = false;
        servers.remove(server.id);
        Server snapshot = server.snapshot();
        if (oldState != ServerState.STOPPED) {
            events.add(new ServerStateChanged(networkId, now, snapshot, oldState, ServerState.STOPPED));
        }
        events.add(new ServerStopped(networkId, now, snapshot, crashed, exitCode, reason));
    }

    public Optional<ServerData> serverById(String id) {
        return Optional.ofNullable(servers.get(id));
    }

    public Optional<ServerData> serverByName(String name) {
        return servers.values().stream()
                .filter(server -> server.name().equals(name))
                .findFirst();
    }

    public Optional<ServerData> serverByNumericalId(String groupName, int numericalId) {
        return servers.values().stream()
                .filter(server -> server.group.name.equals(groupName) && server.numericalId == numericalId)
                .findFirst();
    }

    public List<ServerData> serversOf(GroupData group) {
        return servers.values().stream()
                .filter(server -> server.group == group)
                .sorted(Comparator.comparingInt(server -> server.numericalId))
                .toList();
    }

    public List<ServerData> servers(@Nullable ServerQuery query) {
        return servers.values().stream()
                .filter(server -> matches(server, query))
                .sorted(comparator(query))
                .toList();
    }

    private static Comparator<ServerData> comparator(@Nullable ServerQuery query) {
        Comparator<ServerData> natural = Comparator.<ServerData, String>comparing(server -> server.group.name)
                .thenComparingInt(server -> server.numericalId);
        if (query == null) {
            return natural;
        }
        Comparator<ServerData> comparator = natural;
        String sortBy = query.getSortBy();
        if (sortBy != null && !sortBy.isBlank()) {
            Comparator<ServerData> field = switch (sortBy.toLowerCase(Locale.ROOT)) {
                case "created_at" -> Comparator.comparing((ServerData server) -> Instant.parse(server.createdAt));
                case "updated_at" -> Comparator.comparing((ServerData server) -> Instant.parse(server.updatedAt));
                case "numerical_id" -> Comparator.comparingInt((ServerData server) -> server.numericalId);
                case "state" -> Comparator.comparing((ServerData server) -> server.state);
                default -> throw invalid("Unsupported sort field '" + sortBy + "'");
            };
            comparator = field.thenComparing(natural);
        }
        String sortOrder = query.getSortOrder();
        if (sortOrder == null || sortOrder.isBlank() || sortOrder.equalsIgnoreCase("asc")) {
            return comparator;
        }
        if (sortOrder.equalsIgnoreCase("desc")) {
            return comparator.reversed();
        }
        throw invalid("Unsupported sort order '" + sortOrder + "'");
    }

    private static boolean matches(ServerData server, @Nullable ServerQuery query) {
        if (query == null) {
            return true;
        }
        GroupData group = server.group;
        if (query.getServerGroupIds() != null && !query.getServerGroupIds().contains(group.id)) {
            return false;
        }
        if (query.getServerGroupNames() != null && !query.getServerGroupNames().contains(group.name)) {
            return false;
        }
        if (query.getServerGroupTypes() != null && !query.getServerGroupTypes().contains(group.type)) {
            return false;
        }
        if (query.getServerGroupTags() != null && query.getServerGroupTags().stream().noneMatch(group.tags::contains)) {
            return false;
        }
        if (query.getStates() != null && !query.getStates().contains(server.state)) {
            return false;
        }
        if (query.getNumericalIds() != null && !query.getNumericalIds().contains(server.numericalId)) {
            return false;
        }
        if (query.getServerhostId() != null && !query.getServerhostId().equals(server.serverhostId)) {
            return false;
        }
        return query.getPersistentServerId() == null;
    }

    public boolean isAutoStart() {
        return read(() -> autoStart);
    }

    public void autoStart(boolean autoStart) {
        synchronized (lock) {
            this.autoStart = autoStart;
        }
    }

    public ServerState startState() {
        return read(() -> startState);
    }

    public void startState(ServerState startState) {
        Objects.requireNonNull(startState, "startState");
        switch (startState) {
            case QUEUED, PREPARING, STARTING, AVAILABLE, INGAME -> {
            }
            default -> throw new IllegalArgumentException("Servers cannot start in state " + startState);
        }
        synchronized (lock) {
            this.startState = startState;
        }
    }

    public boolean isScaling() {
        return read(() -> scaling);
    }

    public void scaling(boolean scaling) {
        mutate(events -> {
            this.scaling = scaling;
            return null;
        });
    }

    public List<ServerData> scale() {
        return mutate(events -> scaleLocked(events));
    }

    private List<ServerData> scaleLocked(List<Object> events) {
        List<ServerData> started = new ArrayList<>();
        for (GroupData group : List.copyOf(groups.values())) {
            ScalingConfig config = group.scaling;
            if (config == null || Boolean.FALSE.equals(group.active) || config.getScalingMode() == ScalingMode.MANUAL) {
                continue;
            }
            int attempts = 0;
            while (needsServer(group, config)) {
                if (++attempts > MAX_SCALE_STEPS) {
                    throw new IllegalStateException("Scaling of group '" + group.name + "' did not settle after "
                            + MAX_SCALE_STEPS + " servers, check its scaling config");
                }
                started.add(startLocked(group, events));
            }
        }
        return started;
    }

    private boolean needsServer(GroupData group, ScalingConfig config) {
        List<ServerData> running = serversOf(group);
        if (config.getMaxServers() > 0 && running.size() >= config.getMaxServers()) {
            return false;
        }
        if (running.size() < config.getMinServers()) {
            return true;
        }
        if (running.isEmpty()) {
            return false;
        }
        if (config.getScalingMode() == ScalingMode.PLAYERS) {
            double threshold = config.getPlayerThreshold() > 1 ? config.getPlayerThreshold() / 100 : config.getPlayerThreshold();
            return config.hasPlayerThreshold() && running.stream().allMatch(server -> load(server) >= threshold);
        }
        return config.getAvailableSlots() > 0 && freeSlots(running) < config.getAvailableSlots();
    }

    private static double load(ServerData server) {
        return server.maxPlayers <= 0 ? 1 : (double) server.playerCount / server.maxPlayers;
    }

    private static int freeSlots(List<ServerData> servers) {
        return servers.stream()
                .filter(server -> switch (server.state) {
                    case INGAME, STOPPING, CLEANUP, STOPPED -> false;
                    default -> true;
                })
                .mapToInt(server -> Math.max(0, server.maxPlayers - server.playerCount))
                .sum();
    }

    public Optional<ServerData> requestServerStart(String groupId) {
        return mutate(events -> {
            GroupData group = requireGroup(groupId);
            if (autoStart) {
                return Optional.of(startLocked(group, events));
            }
            startQueue.add(new QueuedStart(UUID.randomUUID().toString(), group, timestamp()));
            return Optional.empty();
        });
    }

    public void clearStartQueue(String groupId) {
        mutate(events -> {
            GroupData group = requireGroup(groupId);
            startQueue.removeIf(start -> start.group == group);
            return null;
        });
    }

    public List<ServerData> processStartQueue() {
        return mutate(events -> {
            List<ServerData> started = new ArrayList<>();
            for (QueuedStart start : List.copyOf(startQueue)) {
                if (start.status != GroupStartQueueItemStatus.PENDING) {
                    continue;
                }
                startQueue.remove(start);
                started.add(startLocked(start.group, events));
            }
            return started;
        });
    }

    public void failQueuedStarts(String groupName, String reason) {
        mutate(events -> {
            GroupData group = groupByName(groupName).orElseThrow(() -> notFound("Group '" + groupName + "' not found"));
            for (QueuedStart start : startQueue) {
                if (start.group == group && start.status == GroupStartQueueItemStatus.PENDING) {
                    start.status = GroupStartQueueItemStatus.FAILED;
                    start.failureReason = reason;
                }
            }
            return null;
        });
    }

    public GroupStartQueue startQueue() {
        return read(() -> {
            Map<GroupData, List<QueuedStart>> byGroup = startQueue.stream()
                    .collect(Collectors.groupingBy(start -> start.group, LinkedHashMap::new, Collectors.toList()));
            List<GroupStartQueueEntry> entries = new ArrayList<>();
            int failed = 0;
            int queued = 0;
            for (Map.Entry<GroupData, List<QueuedStart>> entry : byGroup.entrySet()) {
                List<QueuedStart> starts = entry.getValue();
                int groupFailed = count(starts, GroupStartQueueItemStatus.FAILED);
                int groupQueued = count(starts, GroupStartQueueItemStatus.PENDING);
                failed += groupFailed;
                queued += groupQueued;
                entries.add(new GroupStartQueueEntry(
                        groupFailed,
                        groupQueued,
                        entry.getKey().id,
                        entry.getKey().name,
                        starts.stream().map(QueuedStart::toItem).toList(),
                        starts.size()
                ));
            }
            return new GroupStartQueue(entries.size(), failed, entries, queued, startQueue.size());
        });
    }

    private static int count(List<QueuedStart> starts, GroupStartQueueItemStatus status) {
        return (int) starts.stream().filter(start -> start.status == status).count();
    }

    private GroupData requireGroup(String id) {
        return groupById(id).orElseThrow(() -> notFound("Group with ID '" + id + "' not found"));
    }

    private ServerData requireServer(String id) {
        return serverById(id).orElseThrow(() -> notFound("Server with ID '" + id + "' not found"));
    }

    public static CloudApiException notFound(String message) {
        return new CloudApiException(CloudApiException.Reason.NOT_FOUND, 404, message, null);
    }

    private static CloudApiException conflict(String message) {
        return new CloudApiException(CloudApiException.Reason.CONFLICT, 409, message, null);
    }

    private static CloudApiException invalid(String message) {
        return new CloudApiException(CloudApiException.Reason.INVALID_REQUEST, 400, message, null);
    }
}
