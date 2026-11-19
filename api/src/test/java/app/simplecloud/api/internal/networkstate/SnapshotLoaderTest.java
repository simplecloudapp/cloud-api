package app.simplecloud.api.internal.networkstate;

import app.simplecloud.api.CloudApiOptions;
import app.simplecloud.api.internal.web.ApiClients;
import app.simplecloud.api.web.ApiClient;
import build.buf.gen.simplecloud.controller.v2.NetworkState;
import okhttp3.Call;
import okhttp3.EventListener;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import okio.Buffer;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnapshotLoaderTest {

    private static final String NETWORK_ID = "0d000000-0000-4000-8000-000000000001";
    private static final Duration WAIT_LIMIT = Duration.ofSeconds(5);
    private static final NetworkState SNAPSHOT = NetworkState.newBuilder()
            .setEpoch(1)
            .setRevision(10)
            .build();

    private final MockWebServer controller = new MockWebServer();
    private final CountDownLatch callCanceled = new CountDownLatch(1);

    @BeforeEach
    void start() throws IOException {
        controller.start();
    }

    @AfterEach
    void stop() throws IOException {
        controller.close();
    }

    @Test
    void anAnswerThatIsNotSuccessfulFailsTheLoad() {
        controller.enqueue(new MockResponse().setResponseCode(503));

        Throwable failure = failureOf(loader().load());

        assertInstanceOf(IOException.class, failure);
        assertTrue(failure.getMessage().contains("503"), failure.getMessage());
    }

    @Test
    void aSuccessfulAnswerThatIsNotProtobufFailsTheLoad() {
        controller.enqueue(answerWithSnapshot("text/html"));

        Throwable failure = failureOf(loader().load());

        assertInstanceOf(IOException.class, failure);
        assertTrue(failure.getMessage().contains("text/html"), failure.getMessage());
    }

    @Test
    void cancellingTheLoadCancelsTheRequest() throws Exception {
        controller.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
        CompletableFuture<NetworkState> load = loader().load();
        RecordedRequest sent = controller.takeRequest(WAIT_LIMIT.toMillis(), TimeUnit.MILLISECONDS);

        load.cancel(true);
        boolean canceled = callCanceled.await(WAIT_LIMIT.toMillis(), TimeUnit.MILLISECONDS);

        assertNotNull(sent, "the request reached the controller before the load was cancelled");
        assertTrue(canceled, "cancelling the load cancels the HTTP call");
    }

    private SnapshotLoader loader() {
        String controllerUrl = "http://" + controller.getHostName() + ":" + controller.getPort();
        CloudApiOptions options = CloudApiOptions.builder()
                .controllerUrl(controllerUrl)
                .networkId(NETWORK_ID)
                .networkSecret("secret")
                .build();
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .eventListener(new CancelRecorder())
                .build();
        ApiClient apiClient = ApiClients.create(options, httpClient);
        return new SnapshotLoader(apiClient, controllerUrl, NETWORK_ID);
    }

    private static MockResponse answerWithSnapshot(String contentType) {
        Buffer body = new Buffer().write(SNAPSHOT.toByteArray());
        return new MockResponse()
                .setHeader("Content-Type", contentType)
                .setBody(body);
    }

    private static Throwable failureOf(CompletableFuture<NetworkState> load) {
        ExecutionException failed = assertThrows(ExecutionException.class,
                () -> load.get(WAIT_LIMIT.toMillis(), TimeUnit.MILLISECONDS));
        return failed.getCause();
    }

    private final class CancelRecorder extends EventListener {

        @Override
        public void canceled(@NotNull Call call) {
            callCanceled.countDown();
        }
    }
}
