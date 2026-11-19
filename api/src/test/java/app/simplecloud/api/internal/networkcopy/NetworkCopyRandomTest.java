package app.simplecloud.api.internal.networkcopy;

import build.buf.gen.simplecloud.controller.v2.NetworkState;
import build.buf.gen.simplecloud.controller.v2.StateBatch;
import build.buf.gen.simplecloud.controller.v2.StatePersistentServer;
import build.buf.gen.simplecloud.controller.v2.StatePulse;
import build.buf.gen.simplecloud.controller.v2.StateServer;
import build.buf.gen.simplecloud.controller.v2.StateServerGroup;
import build.buf.gen.simplecloud.controller.v2.StateServerMetrics;
import com.google.protobuf.ByteString;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * A network changes at random. Its batches reach the copy dropped, doubled or swapped.
 * After a last pulse, the copy must equal a copy built from a fresh snapshot.
 */
class NetworkCopyRandomTest {

    private static final String NETWORK_ID = "0d000000-0000-4000-8000-000000000001";
    private static final int STEPS = 300;

    private MockWebServer controllerHttp;
    private volatile NetworkState servedState = NetworkState.getDefaultInstance();

    @BeforeEach
    void startController() throws IOException {
        controllerHttp = new MockWebServer();
        controllerHttp.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return new MockResponse().setBody(new Buffer().write(servedState.toByteArray()));
            }
        });
        controllerHttp.start();
    }

    @AfterEach
    void stopController() throws IOException {
        controllerHttp.close();
    }

    @ParameterizedTest(name = "seed {0}")
    @ValueSource(longs = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20})
    void copyEqualsAFreshSnapshotAfterTheLastPulse(long seed) {
        Random random = new Random(seed);
        FakeController controller = new FakeController(random);
        String controllerUrl = "http://" + controllerHttp.getHostName() + ":" + controllerHttp.getPort();
        LongSupplier stoppedNanoTime = () -> 0L;
        NetworkCopy copy = new NetworkCopy(new OkHttpClient(), controllerUrl, NETWORK_ID, "", stoppedNanoTime);
        List<StateBatch> heldBatches = new ArrayList<>();

        for (int step = 0; step < STEPS; step++) {
            int roll = random.nextInt(100);
            if (roll < 5) {
                sendPulse(copy, controller);
            } else if (roll < 6) {
                controller.startNewEpoch();
            } else {
                StateBatch batch = controller.change();
                deliverUnreliably(copy, batch, heldBatches, random);
            }
        }
        for (StateBatch held : heldBatches) {
            copy.onBatch(held);
        }
        sendPulse(copy, controller);

        NetworkContents fresh = ReceivedNetworkState.fromSnapshot(controller.snapshot(), NETWORK_ID).toContents();
        NetworkContents copied = copy.contents();
        assertNotNull(copied);
        assertEquals(fresh.servers(null), copied.servers(null));
        assertEquals(fresh.groups(null), copied.groups(null));
        assertEquals(fresh.persistentServers(null), copied.persistentServers(null));
    }

    // The pulse first, so a load on this pulse gets the pulse's metrics.
    private void sendPulse(NetworkCopy copy, FakeController controller) {
        StatePulse pulse = controller.pulse();
        servedState = controller.snapshot();
        copy.onPulse(pulse);
    }

    // A held batch arrives after the next one, so the two are swapped.
    private static void deliverUnreliably(NetworkCopy copy, StateBatch batch, List<StateBatch> heldBatches, Random random) {
        int roll = random.nextInt(10);
        if (roll == 0) {
            return;
        }
        if (roll == 1) {
            heldBatches.add(batch);
            return;
        }
        copy.onBatch(batch);
        if (roll == 2) {
            copy.onBatch(batch);
        }
        for (StateBatch held : heldBatches) {
            copy.onBatch(held);
        }
        heldBatches.clear();
    }

    /**
     * The controller's side: items by id, a revision that counts changes, and one batch per change.
     */
    private static final class FakeController {
        private final Random random;
        private final Map<String, StateServer> servers = new LinkedHashMap<>();
        private final Map<String, StateServerGroup> groups = new LinkedHashMap<>();
        private final Map<String, StatePersistentServer> persistentServers = new LinkedHashMap<>();
        private final Map<String, Integer> playerCounts = new LinkedHashMap<>();
        private long epoch = 1;
        private long revision = 1;

        FakeController(Random random) {
            this.random = random;
        }

        StateBatch change() {
            revision++;
            StateBatch.Builder batch = StateBatch.newBuilder().setEpoch(epoch).setBaseRevision(revision - 1).setRevision(revision);
            int kind = random.nextInt(6);
            switch (kind) {
                case 0 -> batch.addServerGroups(putGroup());
                case 1 -> batch.addPersistentServers(putPersistentServer());
                case 2 -> batch.addDeletedServerGroupIds(uuidBytes(removeOneOf(groups, "0a")));
                case 3 -> batch.addDeletedPersistentServerIds(uuidBytes(removeOneOf(persistentServers, "0c")));
                case 4 -> batch.addDeletedServerIds(uuidBytes(removeOneOf(servers, "00")));
                default -> batch.addServers(putServer());
            }
            return batch.build();
        }

        // Revisions may be handed out again in the new epoch.
        void startNewEpoch() {
            epoch++;
            revision = random.nextInt(5);
        }

        StatePulse pulse() {
            List<StateServerMetrics> metrics = new ArrayList<>();
            for (String serverId : servers.keySet()) {
                int playerCount = random.nextInt(20);
                playerCounts.put(serverId, playerCount);
                metrics.add(StateServerMetrics.newBuilder().setServerId(uuidBytes(serverId)).setPlayerCount(playerCount).build());
            }
            return StatePulse.newBuilder()
                    .setEpoch(epoch)
                    .setRevision(revision)
                    .addAllMetrics(metrics)
                    .setViewsMayAnswer(true)
                    .build();
        }

        NetworkState snapshot() {
            NetworkState.Builder snapshot = NetworkState.newBuilder()
                    .setEpoch(epoch)
                    .setRevision(revision)
                    .addAllServers(servers.values())
                    .addAllServerGroups(groups.values())
                    .addAllPersistentServers(persistentServers.values());
            for (Map.Entry<String, Integer> playerCount : playerCounts.entrySet()) {
                StateServerMetrics metrics = StateServerMetrics.newBuilder()
                        .setServerId(uuidBytes(playerCount.getKey()))
                        .setPlayerCount(playerCount.getValue())
                        .build();
                snapshot.addMetrics(metrics);
            }
            return snapshot.build();
        }

        private StateServerGroup putGroup() {
            String id = pickId("0a");
            String name = "group-" + random.nextInt(100);
            String summary = "{\"server_group_id\":\"" + id + "\",\"name\":\"" + name + "\",\"created_at\":\"2026-11-17T09:00:00Z\"}";
            String info = "{\"id\":\"" + id + "\",\"name\":\"" + name + "\"}";
            StateServerGroup group = StateServerGroup.newBuilder()
                    .setId(uuidBytes(id))
                    .setSummaryJson(ByteString.copyFromUtf8(summary))
                    .setInfoJson(ByteString.copyFromUtf8(info))
                    .build();
            groups.put(id, group);
            return group;
        }

        private StatePersistentServer putPersistentServer() {
            String id = pickId("0c");
            String name = "persistent-" + random.nextInt(100);
            String summary = "{\"persistent_server_id\":\"" + id + "\",\"name\":\"" + name + "\",\"created_at\":\"2026-11-17T09:00:00Z\"}";
            String info = "{\"id\":\"" + id + "\",\"name\":\"" + name + "\"}";
            StatePersistentServer persistentServer = StatePersistentServer.newBuilder()
                    .setId(uuidBytes(id))
                    .setSummaryJson(ByteString.copyFromUtf8(summary))
                    .setInfoJson(ByteString.copyFromUtf8(info))
                    .build();
            persistentServers.put(id, persistentServer);
            return persistentServer;
        }

        private StateServer putServer() {
            String id = pickId("00");
            boolean inGroup = random.nextBoolean();
            String baseId = pickId(inGroup ? "0a" : "0c");
            List<String> states = List.of("STARTING", "AVAILABLE", "INGAME", "STOPPING");
            StateServer.Builder server = StateServer.newBuilder()
                    .setId(uuidBytes(id))
                    .setNumericalId(1 + random.nextInt(5))
                    .setState(states.get(random.nextInt(states.size())))
                    .setPropertiesJson(ByteString.copyFromUtf8("{\"n\":" + random.nextInt(100) + "}"));
            if (inGroup) {
                server.setServerGroupId(uuidBytes(baseId));
            } else {
                server.setPersistentServerId(uuidBytes(baseId));
            }
            StateServer built = server.build();
            servers.put(id, built);
            return built;
        }

        // Few ids, so changes often hit an item that exists.
        private String pickId(String prefix) {
            int number = random.nextInt(8);
            return prefix + "000000-0000-4000-8000-00000000000" + number;
        }

        private String removeOneOf(Map<String, ?> items, String prefix) {
            String id = pickId(prefix);
            items.remove(id);
            playerCounts.remove(id);
            return id;
        }
    }

    private static ByteString uuidBytes(String id) {
        UUID uuid = UUID.fromString(id);
        ByteBuffer bytes = ByteBuffer.allocate(16);
        bytes.putLong(uuid.getMostSignificantBits());
        bytes.putLong(uuid.getLeastSignificantBits());
        return ByteString.copyFrom(bytes.array());
    }
}
