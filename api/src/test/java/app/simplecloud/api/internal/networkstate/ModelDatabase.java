package app.simplecloud.api.internal.networkstate;

import build.buf.gen.simplecloud.controller.v2.StateBlueprint;
import build.buf.gen.simplecloud.controller.v2.StatePersistentServer;
import build.buf.gen.simplecloud.controller.v2.StateServer;
import build.buf.gen.simplecloud.controller.v2.StateServerGroup;
import com.google.protobuf.ByteString;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

/**
 * The simulator's Postgres for one network (design 4.1): the items, the revision counter, the change log,
 * the last relayed number and the epoch. Writes keep the references whole, as foreign keys would. Every
 * state of the items is kept per epoch and revision, so a check can ask what the network looked like.
 */
final class ModelDatabase {

    private static final int MAX_SERVERS = 14;
    private static final int MAX_GROUPS = 4;
    private static final int MAX_PERSISTENT_SERVERS = 3;
    private static final int MAX_BLUEPRINTS = 3;
    private static final int STATES_A_FAILOVER_CAN_LOSE = 4;
    private static final List<String> SERVER_STATES = List.of("PREPARING", "STARTING", "AVAILABLE", "STOPPING");

    record EntityKey(ItemKind kind, ByteString id) {
    }

    record Items(
            Map<ByteString, StateServer> servers,
            Map<ByteString, StateServerGroup> serverGroups,
            Map<ByteString, StatePersistentServer> persistentServers,
            Map<ByteString, StateBlueprint> blueprints
    ) {

        Items {
            servers = Map.copyOf(servers);
            serverGroups = Map.copyOf(serverGroups);
            persistentServers = Map.copyOf(persistentServers);
            blueprints = Map.copyOf(blueprints);
        }

        static Items of(NetworkCopy copy) {
            return new Items(copy.servers(), copy.serverGroups(), copy.persistentServers(), copy.blueprints());
        }
    }

    /**
     * One state of the database. {@code changeLog} maps a revision to the entities its transaction changed.
     */
    record State(long epoch, long counter, long lastRelayed, Items items, NavigableMap<Long, Set<EntityKey>> changeLog) {

        State {
            changeLog = Collections.unmodifiableNavigableMap(new TreeMap<>(changeLog));
        }
    }

    /**
     * A committed write. {@code route} is the kind of item the HTTP route that made it writes.
     */
    record Commit(long epoch, long revision, ItemKind route) {
    }

    private final Random random;
    private State current;
    private final Deque<State> statesAFailoverCanLose = new ArrayDeque<>();
    private final Map<Long, NavigableMap<Long, Items>> itemsByEpoch = new HashMap<>();

    ModelDatabase(Random random) {
        this.random = random;
        Items empty = new Items(Map.of(), Map.of(), Map.of(), Map.of());
        long firstEpoch = 1;
        current = new State(firstEpoch, 0, 0, empty, new TreeMap<>());
        NavigableMap<Long, Items> firstHistory = new TreeMap<>();
        firstHistory.put(0L, empty);
        itemsByEpoch.put(firstEpoch, firstHistory);
    }

    State current() {
        return current;
    }

    /**
     * Returns the items at {@code revision} of {@code epoch}, or null if that state never existed.
     */
    Items itemsAt(long epoch, long revision) {
        NavigableMap<Long, Items> history = itemsByEpoch.get(epoch);
        if (history == null) {
            return null;
        }
        return history.get(revision);
    }

    Commit commitRandomWrite() {
        WriteBuilder write = new WriteBuilder(current.items());
        ItemKind route = write.makeRandomChange();
        long revision = current.counter() + 1;
        NavigableMap<Long, Set<EntityKey>> changeLog = new TreeMap<>(current.changeLog());
        changeLog.put(revision, Set.copyOf(write.changed));
        Items items = write.result();
        moveTo(new State(current.epoch(), revision, current.lastRelayed(), items, changeLog));
        itemsByEpoch.get(current.epoch()).put(revision, items);
        return new Commit(current.epoch(), revision, route);
    }

    /**
     * Stores the last relayed number after a relay round of {@code roundEpoch} published up to
     * {@code revision}, and deletes the change log up to it. The number only goes up, and a round of an
     * earlier epoch stores nothing: its revisions may not exist in this one.
     */
    void storeLastRelayed(long roundEpoch, long revision) {
        boolean sameEpoch = roundEpoch == current.epoch();
        boolean higher = revision > current.lastRelayed();
        if (!sameEpoch || !higher) {
            return;
        }
        NavigableMap<Long, Set<EntityKey>> changeLog = new TreeMap<>(current.changeLog().tailMap(revision, false));
        moveTo(new State(current.epoch(), current.counter(), revision, current.items(), changeLog));
    }

    /**
     * Promotes a standby that missed the last few changes, so the counter may go back and its numbers are
     * handed out again, under a new epoch.
     */
    void failOver() {
        List<State> promotable = new ArrayList<>();
        promotable.add(current);
        promotable.addAll(statesAFailoverCanLose);
        State promoted = promotable.get(random.nextInt(promotable.size()));
        long newEpoch = current.epoch() + 1;
        NavigableMap<Long, Items> sharedHistory = itemsByEpoch.get(promoted.epoch()).headMap(promoted.counter(), true);
        itemsByEpoch.put(newEpoch, new TreeMap<>(sharedHistory));
        statesAFailoverCanLose.clear();
        moveTo(new State(newEpoch, promoted.counter(), promoted.lastRelayed(), promoted.items(), promoted.changeLog()));
    }

    /**
     * Deletes one unrelayed change log entry, as a bug would, so the relay finds fewer numbers than it needs.
     */
    void loseChangeLogEntry() {
        List<Long> unrelayed = new ArrayList<>(current.changeLog().keySet());
        if (unrelayed.isEmpty()) {
            return;
        }
        long lostRevision = unrelayed.get(random.nextInt(unrelayed.size()));
        NavigableMap<Long, Set<EntityKey>> changeLog = new TreeMap<>(current.changeLog());
        changeLog.remove(lostRevision);
        moveTo(new State(current.epoch(), current.counter(), current.lastRelayed(), current.items(), changeLog));
    }

    private void moveTo(State next) {
        statesAFailoverCanLose.addFirst(current);
        while (statesAFailoverCanLose.size() > STATES_A_FAILOVER_CAN_LOSE) {
            statesAFailoverCanLose.removeLast();
        }
        current = next;
    }

    /**
     * Builds one random transaction on top of the current items.
     */
    private final class WriteBuilder {
        private final Map<ByteString, StateServer> servers;
        private final Map<ByteString, StateServerGroup> groups;
        private final Map<ByteString, StatePersistentServer> persistentServers;
        private final Map<ByteString, StateBlueprint> blueprints;
        private final Set<EntityKey> changed = new HashSet<>();

        WriteBuilder(Items items) {
            servers = new HashMap<>(items.servers());
            groups = new HashMap<>(items.serverGroups());
            persistentServers = new HashMap<>(items.persistentServers());
            blueprints = new HashMap<>(items.blueprints());
        }

        Items result() {
            return new Items(servers, groups, persistentServers, blueprints);
        }

        ItemKind makeRandomChange() {
            int roll = random.nextInt(100);
            if (roll < 55) {
                return changeServer();
            }
            if (roll < 75) {
                return changeGroup();
            }
            if (roll < 88) {
                return changePersistentServer();
            }
            return changeBlueprint();
        }

        private ItemKind changeServer() {
            boolean parentExists = !groups.isEmpty() || !persistentServers.isEmpty();
            if (!parentExists) {
                return createGroupWithFirstServer();
            }
            int roll = random.nextInt(100);
            if (servers.isEmpty() || (roll < 25 && servers.size() < MAX_SERVERS)) {
                putServer(newServer(randomParent()));
            } else if (roll < 85) {
                StateServer server = pick(servers);
                putServer(changedServer(server));
            } else {
                deleteServer(pick(servers).getId());
            }
            return ItemKind.SERVER;
        }

        private ItemKind changeGroup() {
            int roll = random.nextInt(100);
            if (groups.isEmpty() || (roll < 30 && groups.size() < MAX_GROUPS)) {
                return createGroupWithFirstServer();
            }
            StateServerGroup group = pick(groups);
            if (roll < 85) {
                putGroup(newGroup(group.getId()));
            } else {
                deleteGroupWithItsServers(group.getId());
            }
            return ItemKind.SERVER_GROUP;
        }

        private ItemKind createGroupWithFirstServer() {
            StateServerGroup group = newGroup(randomId());
            putGroup(group);
            putServer(newServer(group.getId()));
            return ItemKind.SERVER_GROUP;
        }

        private ItemKind changePersistentServer() {
            int roll = random.nextInt(100);
            if (persistentServers.isEmpty() || (roll < 30 && persistentServers.size() < MAX_PERSISTENT_SERVERS)) {
                StatePersistentServer persistentServer = newPersistentServer(randomId());
                putPersistentServer(persistentServer);
                putServer(newPersistentServerInstance(persistentServer.getId()));
            } else if (roll < 85) {
                putPersistentServer(newPersistentServer(pick(persistentServers).getId()));
            } else {
                deletePersistentServerWithItsServers(pick(persistentServers).getId());
            }
            return ItemKind.PERSISTENT_SERVER;
        }

        private ItemKind changeBlueprint() {
            int roll = random.nextInt(100);
            if (blueprints.isEmpty() || (roll < 35 && blueprints.size() < MAX_BLUEPRINTS)) {
                putBlueprint(newBlueprint(randomId()));
                return ItemKind.BLUEPRINT;
            }
            StateBlueprint blueprint = pick(blueprints);
            if (roll < 85 || isUsed(blueprint.getId())) {
                putBlueprint(newBlueprint(blueprint.getId()));
            } else {
                blueprints.remove(blueprint.getId());
                changed.add(new EntityKey(ItemKind.BLUEPRINT, blueprint.getId()));
            }
            return ItemKind.BLUEPRINT;
        }

        private boolean isUsed(ByteString blueprintId) {
            for (StateServerGroup group : groups.values()) {
                if (group.hasBlueprintId() && group.getBlueprintId().equals(blueprintId)) {
                    return true;
                }
            }
            return false;
        }

        private void deleteGroupWithItsServers(ByteString groupId) {
            for (StateServer server : List.copyOf(servers.values())) {
                if (server.hasServerGroupId() && server.getServerGroupId().equals(groupId)) {
                    deleteServer(server.getId());
                }
            }
            groups.remove(groupId);
            changed.add(new EntityKey(ItemKind.SERVER_GROUP, groupId));
        }

        private void deletePersistentServerWithItsServers(ByteString persistentServerId) {
            for (StateServer server : List.copyOf(servers.values())) {
                if (server.hasPersistentServerId() && server.getPersistentServerId().equals(persistentServerId)) {
                    deleteServer(server.getId());
                }
            }
            persistentServers.remove(persistentServerId);
            changed.add(new EntityKey(ItemKind.PERSISTENT_SERVER, persistentServerId));
        }

        private void putServer(StateServer server) {
            servers.put(server.getId(), server);
            changed.add(new EntityKey(ItemKind.SERVER, server.getId()));
        }

        private void deleteServer(ByteString serverId) {
            servers.remove(serverId);
            changed.add(new EntityKey(ItemKind.SERVER, serverId));
        }

        private void putGroup(StateServerGroup group) {
            groups.put(group.getId(), group);
            changed.add(new EntityKey(ItemKind.SERVER_GROUP, group.getId()));
        }

        private void putPersistentServer(StatePersistentServer persistentServer) {
            persistentServers.put(persistentServer.getId(), persistentServer);
            changed.add(new EntityKey(ItemKind.PERSISTENT_SERVER, persistentServer.getId()));
        }

        private void putBlueprint(StateBlueprint blueprint) {
            blueprints.put(blueprint.getId(), blueprint);
            changed.add(new EntityKey(ItemKind.BLUEPRINT, blueprint.getId()));
        }

        /**
         * Returns the id of a random group or persistent server; a server of a persistent server is that
         * persistent server's instance.
         */
        private ByteString randomParent() {
            boolean useGroup = persistentServers.isEmpty() || (!groups.isEmpty() && random.nextInt(4) != 0);
            if (useGroup) {
                return pick(groups).getId();
            }
            return pick(persistentServers).getId();
        }

        private StateServer newServer(ByteString parentId) {
            if (persistentServers.containsKey(parentId)) {
                return newPersistentServerInstance(parentId);
            }
            return randomServerFields(randomId()).setServerGroupId(parentId).build();
        }

        private StateServer newPersistentServerInstance(ByteString persistentServerId) {
            return randomServerFields(randomId()).setPersistentServerId(persistentServerId).build();
        }

        private StateServer changedServer(StateServer server) {
            StateServer.Builder changedFields = randomServerFields(server.getId());
            if (server.hasServerGroupId()) {
                changedFields.setServerGroupId(server.getServerGroupId());
            }
            if (server.hasPersistentServerId()) {
                changedFields.setPersistentServerId(server.getPersistentServerId());
            }
            return changedFields.setNumericalId(server.getNumericalId()).build();
        }

        private StateServer.Builder randomServerFields(ByteString id) {
            String state = SERVER_STATES.get(random.nextInt(SERVER_STATES.size()));
            String properties = "{\"note\":" + random.nextInt(1_000) + "}";
            return StateServer.newBuilder()
                    .setId(id)
                    .setNumericalId(1 + random.nextInt(9))
                    .setState(state)
                    .setPort(30_000 + random.nextInt(1_000))
                    .setMaxPlayers(100)
                    .setPropertiesJson(ByteString.copyFrom(properties, StandardCharsets.UTF_8));
        }

        private StateServerGroup newGroup(ByteString id) {
            String info = "{\"version\":" + random.nextInt(1_000_000) + "}";
            StateServerGroup.Builder group = StateServerGroup.newBuilder()
                    .setId(id)
                    .setInfoJson(ByteString.copyFrom(info, StandardCharsets.UTF_8))
                    .setSummaryJson(ByteString.copyFrom(info, StandardCharsets.UTF_8));
            boolean withBlueprint = !blueprints.isEmpty() && random.nextBoolean();
            if (withBlueprint) {
                group.setBlueprintId(pick(blueprints).getId());
            }
            return group.build();
        }

        private StatePersistentServer newPersistentServer(ByteString id) {
            String info = "{\"version\":" + random.nextInt(1_000_000) + "}";
            return StatePersistentServer.newBuilder()
                    .setId(id)
                    .setInfoJson(ByteString.copyFrom(info, StandardCharsets.UTF_8))
                    .setSummaryJson(ByteString.copyFrom(info, StandardCharsets.UTF_8))
                    .build();
        }

        private StateBlueprint newBlueprint(ByteString id) {
            String info = "{\"version\":" + random.nextInt(1_000_000) + "}";
            return StateBlueprint.newBuilder()
                    .setId(id)
                    .setInfoJson(ByteString.copyFrom(info, StandardCharsets.UTF_8))
                    .build();
        }

        private <T> T pick(Map<ByteString, T> items) {
            List<ByteString> ids = new ArrayList<>(items.keySet());
            ids.sort(ByteString.unsignedLexicographicalComparator());
            ByteString id = ids.get(random.nextInt(ids.size()));
            return items.get(id);
        }

        private ByteString randomId() {
            byte[] bytes = new byte[16];
            random.nextBytes(bytes);
            return ByteString.copyFrom(bytes);
        }
    }
}
