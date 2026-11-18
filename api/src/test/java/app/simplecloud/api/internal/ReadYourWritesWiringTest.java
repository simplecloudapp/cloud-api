package app.simplecloud.api.internal;

import app.simplecloud.api.CloudApi;
import app.simplecloud.api.CloudApiOptions;
import app.simplecloud.api.internal.networkstate.MonotonicClock;
import app.simplecloud.api.internal.networkstate.NetworkView;
import app.simplecloud.api.internal.networkstate.NetworkViewMachine;
import app.simplecloud.api.internal.networkstate.NetworkViewShell;
import app.simplecloud.api.internal.networkstate.ReadRoute;
import app.simplecloud.api.internal.networkstate.ViewInput;
import app.simplecloud.api.server.Server;
import build.buf.gen.simplecloud.controller.v2.NetworkState;
import build.buf.gen.simplecloud.controller.v2.StateBatch;
import build.buf.gen.simplecloud.controller.v2.StatePulse;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A write through a {@link CloudApiImpl} reaches the view it shares, so that the next read of what the write
 * changed waits for the copy to hold the write, and goes to the controller if it does not within 3 s.
 */
class ReadYourWritesWiringTest {

    /**
     * {@code fixtureNetworkID} of the controller's fixture test.
     */
    private static final String NETWORK_ID = "0d000000-0000-4000-8000-000000000001";
    private static final String SERVER_LIST_PATH = "/v0/servers";
    private static final String STOPPED_SERVER_ID = "0d000000-0000-4000-8000-0000000000aa";
    private static final String UNREACHABLE_NATS_URL = "nats://127.0.0.1:1";

    private final MockWebServer controller = new MockWebServer();
    private final AtomicInteger serverListRequests = new AtomicInteger();
    private NetworkState snapshot;
    private String serverListBody;

    @BeforeEach
    void start() throws IOException, URISyntaxException {
        Path fixture = fixtureDirectory();
        snapshot = NetworkState.parseFrom(Files.readAllBytes(fixture.resolve("network-state.pb")));
        serverListBody = Files.readString(fixture.resolve("servers.json"));
        controller.setDispatcher(new ControllerDispatcher());
        controller.start();
    }

    @AfterEach
    void stop() throws IOException {
        controller.close();
    }

    @Test
    void aServerReadAfterAServerWriteWaitsForTheCopyToHaveTheWrite() throws Exception {
        try (CloudApiImpl api = (CloudApiImpl) CloudApi.create(options())) {
            NetworkViewShell shell = shellOf(api);
            makeViewLive(shell);

            resultOf(api.server().stopServer(STOPPED_SERVER_ID));
            CompletableFuture<List<Server>> read = api.server().getAllServers();
            boolean waitedForTheCopy = !read.isDone();
            receiveBatchWithTheWrite(shell);
            List<Server> servers = resultOf(read);

            assertTrue(waitedForTheCopy, "the copy lacks the write, so the read waits for it");
            assertEquals(snapshot.getServersCount(), servers.size(), "the copy answers once it has the write");
            assertEquals(0, serverListRequests.get(), "no read went to the controller");
        }
    }

    @Test
    void aServerReadGoesToTheControllerWhenTheCopyLacksTheServerWriteForThreeSeconds() throws Exception {
        try (CloudApiImpl api = (CloudApiImpl) CloudApi.create(options())) {
            makeViewLive(shellOf(api));

            resultOf(api.server().getAllServers());
            int requestsBeforeTheWrite = serverListRequests.get();
            resultOf(api.server().stopServer(STOPPED_SERVER_ID));
            resultOf(api.server().getAllServers());
            int requestsAfterTheWrite = serverListRequests.get();

            assertEquals(0, requestsBeforeTheWrite, "a live view answers the read before the write");
            assertEquals(1, requestsAfterTheWrite, "the copy lacks the write, so the read goes to the controller");
        }
    }

    private CloudApiOptions options() {
        return CloudApiOptions.builder()
                .natsUrl(UNREACHABLE_NATS_URL)
                .controllerUrl("http://" + controller.getHostName() + ":" + controller.getPort())
                .networkId(NETWORK_ID)
                .networkSecret("networkPassword")
                .disableCache()
                .build();
    }

    /**
     * Replaces the shell's view by a live machine on the fixture's snapshot, since no NATS server sends the
     * pulse that would make the shell's own view live. Under the shell's lock, so no tick steps the old view
     * in between.
     */
    private void makeViewLive(NetworkViewShell shell) throws ReflectiveOperationException {
        long now = MonotonicClock.SYSTEM.nowMillis();
        StatePulse pulse = StatePulse.newBuilder()
                .setEpoch(snapshot.getEpoch())
                .setRevision(snapshot.getRevision())
                .setViewsMayAnswer(true)
                .build();
        NetworkView view = NetworkViewMachine.start(index -> 0.0);
        view = view.step(new ViewInput.SubscriptionsActive(), now).view();
        view = view.step(new ViewInput.SnapshotLoaded(snapshot), now).view();
        view = view.step(new ViewInput.PulseReceived(pulse), now).view();
        assertEquals(ReadRoute.FROM_COPY, view.routeRead(now, now));

        Object stepLock = readField(shell, "stepLock");
        Field viewField = NetworkViewShell.class.getDeclaredField("view");
        viewField.setAccessible(true);
        synchronized (stepLock) {
            viewField.set(shell, view);
        }
    }

    /**
     * Waits long enough for a read that waits its full 3 s, and fails instead of hanging the build.
     */
    private static <T> T resultOf(CompletableFuture<T> call)
            throws InterruptedException, ExecutionException, TimeoutException {
        return call.get(5, TimeUnit.SECONDS);
    }

    /**
     * Steps the shell's view as a batch from NATS would, since no NATS server delivers one.
     */
    private void receiveBatchWithTheWrite(NetworkViewShell shell) throws ReflectiveOperationException {
        StateBatch batch = StateBatch.newBuilder()
                .setEpoch(snapshot.getEpoch())
                .setBaseRevision(snapshot.getRevision())
                .setRevision(snapshot.getRevision() + 1)
                .build();
        Method feed = NetworkViewShell.class.getDeclaredMethod("feed", ViewInput.class);
        feed.setAccessible(true);
        feed.invoke(shell, new ViewInput.BatchReceived(batch));
    }

    private static NetworkViewShell shellOf(CloudApiImpl api) throws ReflectiveOperationException {
        SharedRuntime runtime = (SharedRuntime) readField(api, "runtime");
        return (NetworkViewShell) runtime.answeringCopy();
    }

    private static Object readField(Object owner, String name) throws ReflectiveOperationException {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private static Path fixtureDirectory() throws URISyntaxException {
        URL root = Objects.requireNonNull(ReadYourWritesWiringTest.class.getResource("/network-state/properties"));
        return Path.of(root.toURI());
    }

    /**
     * Answers each write with a revision one past the snapshot's, and the server list with the fixture's.
     */
    private final class ControllerDispatcher extends Dispatcher {

        @Override
        public @NotNull MockResponse dispatch(@NotNull RecordedRequest request) {
            boolean serverList = "GET".equals(request.getMethod())
                    && SERVER_LIST_PATH.equals(request.getRequestUrl().encodedPath());
            if (serverList) {
                serverListRequests.incrementAndGet();
                return json(serverListBody);
            }
            if ("GET".equals(request.getMethod())) {
                return new MockResponse().setResponseCode(404);
            }
            return json("{}")
                    .setHeader("X-SC-State-Epoch", Long.toUnsignedString(snapshot.getEpoch()))
                    .setHeader("X-SC-State-Revision", Long.toUnsignedString(snapshot.getRevision() + 1));
        }

        private static MockResponse json(String body) {
            return new MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody(body);
        }
    }
}
