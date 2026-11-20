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
 * A network changes at random. Its batches reach the synchronizer dropped, doubled or swapped.
 * After a last pulse, the synchronizer's contents must equal contents built from a fresh snapshot.
 */
class NetworkCopySynchronizerRandomTest {

    private static final String NETWORK_ID = "0d000000-0000-4000-8000-000000000001";
    private static final int STEPS = 300;

    private MockWebServer controllerHttp;
    private volatile NetworkState servedSnapshot = NetworkState.getDefaultInstance();

    @BeforeEach
    void startController() throws IOException {
        controllerHttp = new MockWebServer();
        controllerHttp.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                Buffer snapshotBody = new Buffer().write(servedSnapshot.toByteArray());
                return new MockResponse().setBody(snapshotBody);
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
        LongSupplier stoppedNanoClock = () -> 0L;
        NetworkCopySynchronizer synchronizer = new NetworkCopySynchronizer(new OkHttpClient(), controllerUrl, NETWORK_ID, "", stoppedNanoClock);
        List<StateBatch> heldBatches = new ArrayList<>();

        for (int step = 0; step < STEPS; step++) {
            int roll = random.nextInt(100);
            if (roll < 5) {
                sendPulse(synchronizer, controller);
            } else if (roll < 6) {
                controller.startNewEpoch();
            } else {
                StateBatch batch = controller.changeAtRandom();
                deliverUnreliably(synchronizer, batch, heldBatches, random);
            }
        }
        for (StateBatch held : heldBatches) {
            synchronizer.handleBatch(held);
        }
        sendPulse(synchronizer, controller);

        NetworkContents freshContents = NetworkCopy.createFromSnapshot(controller.buildSnapshot(), NETWORK_ID).buildContents();
        NetworkContents copiedContents = synchronizer.currentContents();
        assertNotNull(copiedContents);
        assertEquals(freshContents.listServerSummaries(null), copiedContents.listServerSummaries(null));
        assertEquals(freshContents.listGroupSummaries(null), copiedContents.listGroupSummaries(null));
        assertEquals(freshContents.listPersistentServerSummaries(null), copiedContents.listPersistentServerSummaries(null));
    }

    // The pulse first, so a load on this pulse gets the pulse's metrics.
    private void sendPulse(NetworkCopySynchronizer synchronizer, FakeController controller) {
        StatePulse pulse = controller.buildPulse();
        servedSnapshot = controller.buildSnapshot();
        synchronizer.handlePulse(pulse);
    }

    // A held batch arrives after the next one, so the two are swapped.
    private static void deliverUnreliably(NetworkCopySynchronizer synchronizer, StateBatch batch, List<StateBatch> heldBatches, Random random) {
        int roll = random.nextInt(10);
        if (roll == 0) {
            return;
        }
        if (roll == 1) {
            heldBatches.add(batch);
            return;
        }
        synchronizer.handleBatch(batch);
        if (roll == 2) {
            synchronizer.handleBatch(batch);
        }
        for (StateBatch held : heldBatches) {
            synchronizer.handleBatch(held);
        }
        heldBatches.clear();
    }

    /**
     * The controller's side: items by id, a revision that counts changes, and one batch per change.
     */
    private static final class FakeController {
        private final Random random;
        private final Map<String, StateServer> stateServersById = new LinkedHashMap<>();
        private final Map<String, StateServerGroup> stateServerGroupsById = new LinkedHashMap<>();
        private final Map<String, StatePersistentServer> statePersistentServersById = new LinkedHashMap<>();
        private final Map<String, Integer> playerCountsByServerId = new LinkedHashMap<>();
        private long epoch = 1;
        private long revision = 1;

        FakeController(Random random) {
            this.random = random;
        }

        StateBatch changeAtRandom() {
            revision++;
            StateBatch.Builder batch = StateBatch.newBuilder()
                    .setEpoch(epoch)
                    .setBaseRevision(revision - 1)
                    .setRevision(revision);
            int kind = random.nextInt(6);
            switch (kind) {
                case 0 -> batch.addServerGroups(putRandomGroup());
                case 1 -> batch.addPersistentServers(putRandomPersistentServer());
                case 2 -> batch.addDeletedServerGroupIds(toUuidBytes(removeOneOf(stateServerGroupsById, "0a")));
                case 3 -> batch.addDeletedPersistentServerIds(toUuidBytes(removeOneOf(statePersistentServersById, "0c")));
                case 4 -> batch.addDeletedServerIds(toUuidBytes(removeOneOf(stateServersById, "00")));
                default -> batch.addServers(putRandomServer());
            }
            return batch.build();
        }

        // Revisions may be handed out again in the new epoch.
        void startNewEpoch() {
            epoch++;
            revision = random.nextInt(5);
        }

        StatePulse buildPulse() {
            List<StateServerMetrics> metrics = new ArrayList<>();
            for (String serverId : stateServersById.keySet()) {
                int playerCount = random.nextInt(20);
                playerCountsByServerId.put(serverId, playerCount);
                StateServerMetrics serverMetrics = StateServerMetrics.newBuilder()
                        .setServerId(toUuidBytes(serverId))
                        .setPlayerCount(playerCount)
                        .build();
                metrics.add(serverMetrics);
            }
            return StatePulse.newBuilder()
                    .setEpoch(epoch)
                    .setRevision(revision)
                    .addAllMetrics(metrics)
                    .setViewsMayAnswer(true)
                    .build();
        }

        NetworkState buildSnapshot() {
            NetworkState.Builder snapshot = NetworkState.newBuilder()
                    .setEpoch(epoch)
                    .setRevision(revision)
                    .addAllServers(stateServersById.values())
                    .addAllServerGroups(stateServerGroupsById.values())
                    .addAllPersistentServers(statePersistentServersById.values());
            for (Map.Entry<String, Integer> playerCountEntry : playerCountsByServerId.entrySet()) {
                StateServerMetrics metrics = StateServerMetrics.newBuilder()
                        .setServerId(toUuidBytes(playerCountEntry.getKey()))
                        .setPlayerCount(playerCountEntry.getValue())
                        .build();
                snapshot.addMetrics(metrics);
            }
            return snapshot.build();
        }

        private StateServerGroup putRandomGroup() {
            String id = pickId("0a");
            String name = "group-" + random.nextInt(100);
            String summaryJson = "{\"server_group_id\":\"" + id + "\",\"name\":\"" + name + "\",\"created_at\":\"2026-11-17T09:00:00Z\"}";
            String infoJson = "{\"id\":\"" + id + "\",\"name\":\"" + name + "\"}";
            StateServerGroup stateServerGroup = StateServerGroup.newBuilder()
                    .setId(toUuidBytes(id))
                    .setSummaryJson(ByteString.copyFromUtf8(summaryJson))
                    .setInfoJson(ByteString.copyFromUtf8(infoJson))
                    .build();
            stateServerGroupsById.put(id, stateServerGroup);
            return stateServerGroup;
        }

        private StatePersistentServer putRandomPersistentServer() {
            String id = pickId("0c");
            String name = "persistent-" + random.nextInt(100);
            String summaryJson = "{\"persistent_server_id\":\"" + id + "\",\"name\":\"" + name + "\",\"created_at\":\"2026-11-17T09:00:00Z\"}";
            String infoJson = "{\"id\":\"" + id + "\",\"name\":\"" + name + "\"}";
            StatePersistentServer statePersistentServer = StatePersistentServer.newBuilder()
                    .setId(toUuidBytes(id))
                    .setSummaryJson(ByteString.copyFromUtf8(summaryJson))
                    .setInfoJson(ByteString.copyFromUtf8(infoJson))
                    .build();
            statePersistentServersById.put(id, statePersistentServer);
            return statePersistentServer;
        }

        private StateServer putRandomServer() {
            String id = pickId("00");
            boolean inGroup = random.nextBoolean();
            String groupOrPersistentServerId = pickId(inGroup ? "0a" : "0c");
            List<String> states = List.of("STARTING", "AVAILABLE", "INGAME", "STOPPING");
            StateServer.Builder stateServerBuilder = StateServer.newBuilder()
                    .setId(toUuidBytes(id))
                    .setNumericalId(1 + random.nextInt(5))
                    .setState(states.get(random.nextInt(states.size())))
                    .setPropertiesJson(ByteString.copyFromUtf8("{\"n\":" + random.nextInt(100) + "}"));
            if (inGroup) {
                stateServerBuilder.setServerGroupId(toUuidBytes(groupOrPersistentServerId));
            } else {
                stateServerBuilder.setPersistentServerId(toUuidBytes(groupOrPersistentServerId));
            }
            StateServer stateServer = stateServerBuilder.build();
            stateServersById.put(id, stateServer);
            return stateServer;
        }

        // Few ids, so changes often hit an item that exists.
        private String pickId(String prefix) {
            int number = random.nextInt(8);
            return prefix + "000000-0000-4000-8000-00000000000" + number;
        }

        private String removeOneOf(Map<String, ?> itemsById, String prefix) {
            String id = pickId(prefix);
            itemsById.remove(id);
            playerCountsByServerId.remove(id);
            return id;
        }
    }

    private static ByteString toUuidBytes(String id) {
        UUID uuid = UUID.fromString(id);
        ByteBuffer bytes = ByteBuffer.allocate(16);
        bytes.putLong(uuid.getMostSignificantBits());
        bytes.putLong(uuid.getLeastSignificantBits());
        return ByteString.copyFrom(bytes.array());
    }
}
