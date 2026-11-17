package app.simplecloud.api.internal;

import app.simplecloud.api.CloudApi;
import app.simplecloud.api.CloudApiOptions;
import app.simplecloud.api.internal.nats.NatsFailoverConnectionManager;
import app.simplecloud.api.web.ApiClient;
import com.sun.net.httpserver.HttpServer;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.MessageHandler;
import io.nats.client.Nats;
import io.nats.client.Subscription;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class SharedCloudApiResourcesTest {

    private static final String SUBJECT = "network-1.shared-test";

    private static final AtomicInteger TEST_NETWORKS = new AtomicInteger();

    // Each test gets its own network, so an instance a failed test leaves open shares nothing with the next test.
    private final String networkId = "network-" + TEST_NETWORKS.incrementAndGet();
    private final List<String> requestComponents = new CopyOnWriteArrayList<>();
    private HttpServer controller;

    @BeforeEach
    void startController() throws Exception {
        controller = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        controller.createContext("/v0/players/online/count", exchange -> {
            requestComponents.add(exchange.getRequestHeaders().getFirst("X-SC-Component"));
            byte[] body = "{\"count\":0}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        controller.start();
    }

    @AfterEach
    void stopController() {
        controller.stop(0);
    }

    @Test
    void instancesWithEqualNatsSettingsShareOneConnectionAndOthersGetTheirOwn() throws Exception {
        CloudApiOptions sharedOptions = options(UnaryOperator.identity());
        CloudApiOptions otherDelayOptions = options(builder -> builder.natsFailoverReconnectAfter(Duration.ofSeconds(31)));

        try (CloudApiImpl first = (CloudApiImpl) CloudApi.create(sharedOptions);
             CloudApiImpl second = (CloudApiImpl) CloudApi.create(sharedOptions);
             CloudApiImpl otherSettings = (CloudApiImpl) CloudApi.create(otherDelayOptions)) {
            assertSame(natsConnectionManager(first), natsConnectionManager(second));
            assertNotSame(natsConnectionManager(first), natsConnectionManager(otherSettings));
        }
    }

    @Test
    void theSharedNatsConnectionStaysOpenUntilTheLastInstanceCloses() throws Exception {
        CloudApiOptions sharedOptions = options(UnaryOperator.identity());
        CloudApiImpl platformPlugin = (CloudApiImpl) CloudApi.create(sharedOptions);
        CloudApiImpl otherPlugin = (CloudApiImpl) CloudApi.create(sharedOptions);
        NatsFailoverConnectionManager sharedManager = natsConnectionManager(platformPlugin);

        platformPlugin.close();
        CloudApiImpl reenabledPlatformPlugin = (CloudApiImpl) CloudApi.create(sharedOptions);
        assertSame(sharedManager, natsConnectionManager(reenabledPlatformPlugin));
        assertFalse(isShutDown(sharedManager));

        otherPlugin.close();
        assertFalse(isShutDown(sharedManager));
        reenabledPlatformPlugin.close();
        assertTrue(isShutDown(sharedManager));
        try (CloudApiImpl afterRestart = (CloudApiImpl) CloudApi.create(sharedOptions)) {
            assertNotSame(sharedManager, natsConnectionManager(afterRestart));
        }
    }

    @Test
    void closingAnInstanceClosesOnlyItsOwnEventDispatchers() throws Exception {
        CloudApiOptions sharedOptions = options(UnaryOperator.identity());

        try (CloudApiImpl remaining = (CloudApiImpl) CloudApi.create(sharedOptions)) {
            NatsFailoverConnectionManager sharedManager = natsConnectionManager(remaining);
            int remainingDispatchers = dispatcherCount(sharedManager);
            CloudApiImpl closing = (CloudApiImpl) CloudApi.create(sharedOptions);
            assertTrue(dispatcherCount(sharedManager) > remainingDispatchers);

            closing.close();

            assertEquals(remainingDispatchers, dispatcherCount(sharedManager));
        }
    }

    @Test
    void closedInstanceReportsItsNatsConnectionClosedAndRejectsPublishes() {
        CloudApiImpl closed = (CloudApiImpl) CloudApi.create(options(UnaryOperator.identity()));
        Connection connection = closed.getNatsConnection();

        closed.close();

        byte[] payload = new byte[0];
        assertEquals(Connection.Status.CLOSED, connection.getStatus());
        assertThrows(IllegalStateException.class, () -> connection.publish("network-1.closed", payload));
    }

    @Test
    void closingOneInstanceLeavesTheSharedHttpClientWorkingForTheOther() throws Exception {
        CloudApi closing = CloudApi.create(options(builder -> builder.component("closing-plugin")));
        try (CloudApi remaining = CloudApi.create(options(builder -> builder.component("remaining-plugin")))) {
            ConnectionPool closingPool = httpClient(closing).connectionPool();
            ConnectionPool remainingPool = httpClient(remaining).connectionPool();
            assertSame(closingPool, remainingPool);

            closing.close();

            ExecutorService remainingDispatcher = httpClient(remaining).dispatcher().executorService();
            assertFalse(remainingDispatcher.isShutdown());
            assertEquals(0, remaining.player().getOnlinePlayerCount().join());
        }
        assertEquals(List.of("remaining-plugin"), requestComponents);
    }

    @Test
    void theLastCloseShutsDownTheSharedHttpClient() throws Exception {
        CloudApi first = CloudApi.create(options(UnaryOperator.identity()));
        CloudApi last = CloudApi.create(options(UnaryOperator.identity()));
        ExecutorService sharedDispatcher = httpClient(first).dispatcher().executorService();

        first.close();
        assertFalse(sharedDispatcher.isShutdown());

        last.close();
        assertTrue(sharedDispatcher.isShutdown());
    }

    @Test
    void failedConstructionClosesItsHandlesOnTheSharedConnections() throws Exception {
        CloudApiImpl open = (CloudApiImpl) CloudApi.create(options(UnaryOperator.identity()));
        NatsFailoverConnectionManager natsManager = natsConnectionManager(open);
        ExecutorService httpDispatcher = httpClient(open).dispatcher().executorService();
        CloudApiOptions failingOptions = options(builder -> builder.cache(null));

        assertThrows(NullPointerException.class, () -> CloudApi.create(failingOptions));
        open.close();

        assertTrue(httpDispatcher.isShutdown());
        try (CloudApiImpl next = (CloudApiImpl) CloudApi.create(options(UnaryOperator.identity()))) {
            assertNotSame(natsManager, natsConnectionManager(next));
        }
    }

    // Run the tests below against an isolated broker with NATS_TEST_URL=nats://127.0.0.1:4222.
    @Test
    void instancesWithEqualNatsSettingsUseOneBrokerConnection() throws Exception {
        CloudApiOptions brokerOptions = options(builder -> builder.natsUrl(natsTestUrl()));

        try (CloudApiImpl first = (CloudApiImpl) CloudApi.create(brokerOptions);
             CloudApiImpl second = (CloudApiImpl) CloudApi.create(brokerOptions)) {
            assertEquals(awaitClientId(first), awaitClientId(second));
        }
    }

    @Test
    void closingAnInstanceStopsOnlyItsOwnSubscriptions() throws Exception {
        String url = natsTestUrl();
        CloudApiOptions brokerOptions = options(builder -> builder.natsUrl(url));
        BlockingQueue<String> remainingMessages = new ArrayBlockingQueue<>(10);
        BlockingQueue<String> closingMessages = new ArrayBlockingQueue<>(10);

        try (Connection publisher = Nats.connect(url);
             CloudApiImpl remaining = (CloudApiImpl) CloudApi.create(brokerOptions)) {
            CloudApiImpl closing = (CloudApiImpl) CloudApi.create(brokerOptions);
            awaitClientId(remaining);
            subscribeThroughDispatcher(remaining.getNatsConnection(), remainingMessages);
            subscribeThroughDispatcher(closing.getNatsConnection(), closingMessages);
            Subscription closingDirectSubscription = closing.getNatsConnection().subscribe(SUBJECT);
            remaining.getNatsConnection().flush(Duration.ofSeconds(5));

            closing.close();
            publish(publisher, "after-close");

            assertEquals("after-close", remainingMessages.poll(5, TimeUnit.SECONDS));
            remaining.getNatsConnection().flush(Duration.ofSeconds(5));
            assertNull(closingMessages.poll(500, TimeUnit.MILLISECONDS));
            assertFalse(closingDirectSubscription.isActive());
            assertEquals(Connection.Status.CONNECTED, remaining.getNatsConnection().getStatus());
        }
    }

    @Test
    void theLastCloseClosesTheBrokerConnection() throws Exception {
        CloudApiOptions brokerOptions = options(builder -> builder.natsUrl(natsTestUrl()));
        CloudApiImpl first = (CloudApiImpl) CloudApi.create(brokerOptions);
        CloudApiImpl last = (CloudApiImpl) CloudApi.create(brokerOptions);
        awaitClientId(first);
        Connection sharedConnection = natsConnectionManager(first).getConnection();

        first.close();
        assertEquals(Connection.Status.CONNECTED, sharedConnection.getStatus());

        last.close();
        assertEquals(Connection.Status.CLOSED, sharedConnection.getStatus());
    }

    private static void subscribeThroughDispatcher(Connection connection, BlockingQueue<String> received) {
        MessageHandler handler = message -> received.add(new String(message.getData(), StandardCharsets.UTF_8));
        Dispatcher dispatcher = connection.createDispatcher(handler);
        dispatcher.subscribe(SUBJECT);
    }

    private static void publish(Connection publisher, String message) {
        publisher.publish(SUBJECT, message.getBytes(StandardCharsets.UTF_8));
    }

    private static String natsTestUrl() {
        String url = System.getenv("NATS_TEST_URL");
        assumeTrue(url != null && !url.isBlank(), "set NATS_TEST_URL to run broker integration tests");
        return url;
    }

    private static int awaitClientId(CloudApiImpl api) throws InterruptedException {
        Connection connection = api.getNatsConnection();
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (connection.getStatus() != Connection.Status.CONNECTED && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertEquals(Connection.Status.CONNECTED, connection.getStatus());
        return connection.getServerInfo().getClientId();
    }

    private static NatsFailoverConnectionManager natsConnectionManager(CloudApiImpl api) throws ReflectiveOperationException {
        Field field = CloudApiImpl.class.getDeclaredField("natsConnectionManagerHandle");
        field.setAccessible(true);
        SharedResources.Handle<?> handle = (SharedResources.Handle<?>) field.get(api);
        return (NatsFailoverConnectionManager) handle.resource();
    }

    private static boolean isShutDown(NatsFailoverConnectionManager manager) throws ReflectiveOperationException {
        Field field = NatsFailoverConnectionManager.class.getDeclaredField("monitorExecutor");
        field.setAccessible(true);
        ExecutorService monitorExecutor = (ExecutorService) field.get(manager);
        return monitorExecutor.isShutdown();
    }

    private static int dispatcherCount(NatsFailoverConnectionManager manager) throws ReflectiveOperationException {
        Field field = NatsFailoverConnectionManager.class.getDeclaredField("dispatchers");
        field.setAccessible(true);
        List<?> dispatchers = (List<?>) field.get(manager);
        return dispatchers.size();
    }

    private static OkHttpClient httpClient(CloudApi api) throws ReflectiveOperationException {
        Field field = CloudApiImpl.class.getDeclaredField("apiClient");
        field.setAccessible(true);
        ApiClient apiClient = (ApiClient) field.get(api);
        return apiClient.getHttpClient();
    }

    private CloudApiOptions options(UnaryOperator<CloudApiOptions.Builder> customizer) {
        CloudApiOptions.Builder builder = CloudApiOptions.builder()
                .natsUrl("nats://127.0.0.1:1")
                .controllerUrl("http://127.0.0.1:" + controller.getAddress().getPort())
                .networkId(networkId)
                .networkSecret("secret");
        return customizer.apply(builder).build();
    }
}
