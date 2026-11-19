package app.simplecloud.api.internal;

import app.simplecloud.api.CloudApi;
import app.simplecloud.api.CloudApiOptions;
import build.buf.gen.simplecloud.controller.v2.NetworkState;
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
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * A reconnect of the NATS connection a {@link CloudApiImpl} shares with its view makes the view load a new
 * snapshot, through the production wiring: the connection manager, the shell and the machine.
 */
class ReconnectReloadWiringTest {

    private static final String NETWORK_ID = "0d000000-0000-4000-8000-000000000001";
    private static final String SNAPSHOT_PATH = "/v0/network-state";
    /**
     * Covers jnats' wait of up to 2 s before it connects again, and the view's 2 s between loads plus up to 2 s
     * of random delay, with room for a slow build machine.
     */
    private static final Duration LOAD_WAIT_LIMIT = Duration.ofSeconds(15);
    private static final NetworkState SNAPSHOT = NetworkState.newBuilder()
            .setEpoch(1)
            .setRevision(10)
            .build();

    private final MockWebServer controller = new MockWebServer();
    private final BlockingQueue<RecordedRequest> snapshotLoads = new LinkedBlockingQueue<>();
    private FakeNatsServer nats;

    @BeforeEach
    void start() throws IOException {
        controller.setDispatcher(new SnapshotDispatcher());
        controller.start();
        nats = new FakeNatsServer();
    }

    @AfterEach
    void stop() throws IOException {
        controller.close();
        nats.close();
    }

    @Test
    void aReconnectMakesTheViewLoadASnapshotAgain() throws Exception {
        try (CloudApi api = CloudApi.create(options())) {
            RecordedRequest firstLoad = nextSnapshotLoad();
            nats.dropClient();
            RecordedRequest loadAfterReconnect = nextSnapshotLoad();

            assertNotNull(firstLoad, "the view loads a snapshot once its subscriptions are active");
            assertNotNull(loadAfterReconnect, "the view loads a snapshot again after the reconnect");
        }
    }

    private RecordedRequest nextSnapshotLoad() throws InterruptedException {
        return snapshotLoads.poll(LOAD_WAIT_LIMIT.toMillis(), TimeUnit.MILLISECONDS);
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
     * Answers every snapshot load with the same snapshot and records it; any other request gets a 404.
     */
    private final class SnapshotDispatcher extends Dispatcher {

        @Override
        public @NotNull MockResponse dispatch(@NotNull RecordedRequest request) {
            String path = request.getRequestUrl().encodedPath();
            boolean snapshotLoad = "GET".equals(request.getMethod()) && SNAPSHOT_PATH.equals(path);
            if (!snapshotLoad) {
                return new MockResponse().setResponseCode(404);
            }
            snapshotLoads.add(request);
            Buffer body = new Buffer().write(SNAPSHOT.toByteArray());
            return new MockResponse()
                    .setHeader("Content-Type", "application/x-protobuf")
                    .setBody(body);
        }
    }
}
