package app.simplecloud.api.internal;

import app.simplecloud.api.CloudApi;
import app.simplecloud.api.CloudApiOptions;
import app.simplecloud.api.internal.networkstate.MonotonicClock;
import app.simplecloud.api.internal.networkstate.NetworkCopy;
import app.simplecloud.api.internal.networkstate.NetworkViewShell;
import app.simplecloud.api.server.Server;
import app.simplecloud.api.server.ServerApi;
import build.buf.gen.simplecloud.controller.v2.NetworkState;
import build.buf.gen.simplecloud.controller.v2.StateBatch;
import build.buf.gen.simplecloud.controller.v2.StatePulse;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A write through a {@link CloudApiImpl} reaches the view it shares, so that every read after it waits for the
 * copy to hold the write, for up to 3 s after the write. The view is the production one, fed as in production:
 * its subscriptions, pulses and batches come over NATS, and its snapshot over HTTP.
 */
class ReadYourWritesWiringTest {

    /**
     * {@code fixtureNetworkID} of the controller's fixture test.
     */
    private static final String NETWORK_ID = "0d000000-0000-4000-8000-000000000001";
    private static final String PULSE_SUBJECT = NETWORK_ID + ".state.v1.pulse";
    private static final String BATCH_SUBJECT = NETWORK_ID + ".state.v1.batch";
    private static final String SERVER_LIST_PATH = "/v0/servers";
    private static final String SNAPSHOT_PATH = "/v0/network-state";
    private static final String STOPPED_SERVER_ID = "0d000000-0000-4000-8000-0000000000aa";
    private static final Duration LIVE_WAIT_LIMIT = Duration.ofSeconds(10);
    private static final long OWN_WRITE_HOLD_MILLIS = 3_000;

    private final MockWebServer controller = new MockWebServer();
    private final AtomicInteger serverListRequests = new AtomicInteger();
    private FakeNatsServer nats;
    private NetworkState snapshot;
    private String serverListBody;

    @BeforeEach
    void start() throws IOException, URISyntaxException {
        Path fixture = fixtureDirectory();
        snapshot = NetworkState.parseFrom(Files.readAllBytes(fixture.resolve("network-state.pb")));
        serverListBody = Files.readString(fixture.resolve("servers.json"));
        controller.setDispatcher(new ControllerDispatcher());
        controller.start();
        nats = new FakeNatsServer();
    }

    @AfterEach
    void stop() throws IOException {
        controller.close();
        nats.close();
    }

    @Test
    void aServerReadAfterAServerWriteWaitsUntilTheCopyHasTheWrite() throws Exception {
        try (CloudApiImpl api = (CloudApiImpl) CloudApi.create(options())) {
            ServerApi servers = api.server();
            awaitLiveView(shellOf(api));

            resultOf(servers.stopServer(STOPPED_SERVER_ID));
            CompletableFuture<List<Server>> read = servers.getAllServers();
            boolean waitedForTheCopy = !read.isDone();
            deliverBatchWithTheWrite();
            List<Server> listed = resultOf(read);

            assertTrue(waitedForTheCopy, "the copy lacks the write, so the read waits for it");
            assertEquals(snapshot.getServersCount(), listed.size(), "the copy answers once it has the write");
            assertEquals(0, serverListRequests.get(), "no read went to the controller");
        }
    }

    @Test
    void aServerReadIsAnsweredFromTheCopyThreeSecondsAfterTheServerWriteThoughTheCopyLacksIt() throws Exception {
        try (CloudApiImpl api = (CloudApiImpl) CloudApi.create(options())) {
            ServerApi servers = api.server();
            awaitLiveView(shellOf(api));

            long writeStartedAt = MonotonicClock.SYSTEM.nowMillis();
            resultOf(servers.stopServer(STOPPED_SERVER_ID));
            List<Server> listed = resultOf(servers.getAllServers());
            long answeredAt = MonotonicClock.SYSTEM.nowMillis();
            long waitedMillis = answeredAt - writeStartedAt;

            assertTrue(waitedMillis >= OWN_WRITE_HOLD_MILLIS, "the read waits until 3 s after the write");
            assertEquals(snapshot.getServersCount(), listed.size(), "then the copy answers, though it lacks the write");
            assertEquals(0, serverListRequests.get(), "no read went to the controller");
        }
    }

    private CloudApiOptions options() {
        return CloudApiOptions.builder()
                .natsUrl(nats.url())
                .controllerUrl("http://" + controller.getHostName() + ":" + controller.getPort())
                .networkId(NETWORK_ID)
                .networkSecret("networkPassword")
                .disableCache()
                .build();
    }

    /**
     * Sends the snapshot's pulse until the view answers from its copy: a pulse that comes before the snapshot
     * is loaded does not make the view live.
     */
    private void awaitLiveView(NetworkViewShell shell) throws IOException, InterruptedException {
        long deadline = System.nanoTime() + LIVE_WAIT_LIMIT.toNanos();
        StatePulse pulse = StatePulse.newBuilder()
                .setEpoch(snapshot.getEpoch())
                .setRevision(snapshot.getRevision())
                .setViewsMayAnswer(true)
                .build();
        while (!answersFromItsCopy(shell)) {
            assertTrue(System.nanoTime() < deadline, "the view went live on the fixture's snapshot");
            deliverWhenSubscribed(PULSE_SUBJECT, pulse.toByteArray());
            Thread.sleep(20);
        }
    }

    private static boolean answersFromItsCopy(NetworkViewShell shell) {
        Optional<NetworkCopy> copy = shell.copyToAnswer().join();
        return copy.isPresent();
    }

    private void deliverBatchWithTheWrite() throws IOException {
        StateBatch batch = StateBatch.newBuilder()
                .setEpoch(snapshot.getEpoch())
                .setBaseRevision(snapshot.getRevision())
                .setRevision(snapshot.getRevision() + 1)
                .build();
        nats.deliver(BATCH_SUBJECT, batch.toByteArray());
    }

    private void deliverWhenSubscribed(String subject, byte[] payload) throws IOException {
        if (nats.hasSubscription(subject)) {
            nats.deliver(subject, payload);
        }
    }

    /**
     * Waits long enough for a read that waits the full 3 s after a write, and fails instead of hanging the build.
     */
    private static <T> T resultOf(CompletableFuture<T> call)
            throws InterruptedException, ExecutionException, TimeoutException {
        return call.get(5, TimeUnit.SECONDS);
    }

    private static NetworkViewShell shellOf(CloudApiImpl api) {
        SharedNetworkView networkView = api.runtime().networkView();
        assertNotNull(networkView, "the fixture's network and credential allow a view");
        return networkView.shell();
    }

    private static Path fixtureDirectory() throws URISyntaxException {
        URL root = Objects.requireNonNull(ReadYourWritesWiringTest.class.getResource("/network-state/properties"));
        return Path.of(root.toURI());
    }

    /**
     * Answers the snapshot with the fixture's, each write with a revision one past the snapshot's, and the server
     * list with the fixture's.
     */
    private final class ControllerDispatcher extends Dispatcher {

        @Override
        public @NotNull MockResponse dispatch(@NotNull RecordedRequest request) {
            String path = request.getRequestUrl().encodedPath();
            boolean read = "GET".equals(request.getMethod());
            if (read && SNAPSHOT_PATH.equals(path)) {
                return snapshotResponse();
            }
            if (read && SERVER_LIST_PATH.equals(path)) {
                serverListRequests.incrementAndGet();
                return json(serverListBody);
            }
            if (read) {
                return new MockResponse().setResponseCode(404);
            }
            return writeResponse();
        }

        private MockResponse snapshotResponse() {
            Buffer body = new Buffer().write(snapshot.toByteArray());
            return new MockResponse()
                    .setHeader("Content-Type", "application/x-protobuf")
                    .setBody(body);
        }

        private MockResponse writeResponse() {
            String epoch = Long.toUnsignedString(snapshot.getEpoch());
            String revision = Long.toUnsignedString(snapshot.getRevision() + 1);
            return json("{}")
                    .setHeader("X-SC-State-Epoch", epoch)
                    .setHeader("X-SC-State-Revision", revision);
        }

        private static MockResponse json(String body) {
            return new MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody(body);
        }
    }
}
