package app.simplecloud.api.internal;

import app.simplecloud.api.CloudApi;
import app.simplecloud.api.CloudApiOptions;
import app.simplecloud.api.internal.nats.NatsFailoverConnectionManager;
import app.simplecloud.api.internal.nats.PlatformNatsConnection;
import app.simplecloud.api.web.ApiClient;
import com.sun.net.httpserver.HttpServer;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.MessageHandler;
import io.nats.client.Nats;
import okhttp3.ConnectionPool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class SharedCloudApiResourcesTest {

    private static final String NATS_MONITOR_THREAD = "simplecloud-nats-failover-monitor";
    private static final String SUBJECT = "network-1.shared-test";

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
    void natsViewRejectsDirectSubscriptions() {
        try (CloudApiImpl api = (CloudApiImpl) CloudApi.create(options(UnaryOperator.identity()))) {
            Connection connection = api.getNatsConnection();

            assertThrows(UnsupportedOperationException.class, () -> connection.subscribe("network-1.direct"));
        }
    }

    @Test
    void closedInstanceRejectsCallsOnItsNatsConnection() {
        CloudApiImpl closed = (CloudApiImpl) CloudApi.create(options(UnaryOperator.identity()));
        Connection connection = closed.getNatsConnection();
        closed.close();

        byte[] payload = new byte[0];
        assertThrows(IllegalStateException.class, () -> connection.publish("network-1.closed", payload));
    }

    @Test
    void closingOneInstanceLeavesTheSharedHttpClientWorkingForTheOther() throws Exception {
        CloudApi closing = CloudApi.create(options(builder -> builder.component("closing-plugin")));
        try (CloudApi remaining = CloudApi.create(options(builder -> builder.component("remaining-plugin")))) {
            ConnectionPool closingPool = apiClient(closing).getHttpClient().connectionPool();
            ConnectionPool remainingPool = apiClient(remaining).getHttpClient().connectionPool();
            assertSame(closingPool, remainingPool);

            closing.close();

            ExecutorService remainingDispatcher = apiClient(remaining).getHttpClient().dispatcher().executorService();
            assertFalse(remainingDispatcher.isShutdown());
            assertEquals(0, remaining.player().getOnlinePlayerCount().join());
        }
        assertEquals(List.of("remaining-plugin"), requestComponents);
    }

    @Test
    void failedConstructionLeavesNoNatsConnectionAndNoRegisteredConnection() throws Exception {
        Set<Thread> monitorThreadsBefore = natsMonitorThreads();
        CloudApiOptions failingOptions = options(builder -> builder.cache(null));

        assertThrows(NullPointerException.class, () -> CloudApiImpl.createNatsConnectionOwner(failingOptions));

        PlatformNatsConnection.Settings settings = PlatformNatsConnection.Settings.of(failingOptions);
        assertTrue(PlatformNatsConnection.find(settings).isEmpty());
        awaitNoNewNatsMonitorThread(monitorThreadsBefore);
    }

    @Test
    void secondOwnerNeitherReplacesNorUnregistersTheFirst() {
        CloudApiOptions firstOptions = options(UnaryOperator.identity());
        CloudApiOptions secondOptions = options(builder -> builder.natsFailoverReconnectAfter(Duration.ofSeconds(31)));
        PlatformNatsConnection.Settings firstSettings = PlatformNatsConnection.Settings.of(firstOptions);
        PlatformNatsConnection.Settings secondSettings = PlatformNatsConnection.Settings.of(secondOptions);

        try (CloudApiImpl first = CloudApiImpl.createNatsConnectionOwner(firstOptions)) {
            CloudApiImpl second = CloudApiImpl.createNatsConnectionOwner(secondOptions);
            assertTrue(PlatformNatsConnection.find(secondSettings).isEmpty());

            second.close();

            assertTrue(PlatformNatsConnection.find(firstSettings).isPresent());
        }
        assertTrue(PlatformNatsConnection.find(firstSettings).isEmpty());
    }

    // Run the tests below against an isolated broker with NATS_TEST_URL=nats://127.0.0.1:4222.
    @Test
    void ownerSharesItsConnectionOnlyWithInstancesOfEqualSettings() throws Exception {
        String url = natsTestUrl();
        CloudApiOptions ownerOptions = options(builder -> builder.natsUrl(url));
        CloudApiOptions otherDelayOptions = options(builder -> builder.natsUrl(url)
                .natsFailoverReconnectAfter(Duration.ofSeconds(31)));

        try (CloudApiImpl owner = CloudApiImpl.createNatsConnectionOwner(ownerOptions);
             CloudApiImpl sameSettings = (CloudApiImpl) CloudApi.create(ownerOptions);
             CloudApiImpl otherSettings = (CloudApiImpl) CloudApi.create(otherDelayOptions)) {
            int ownerClientId = awaitClientId(owner);

            assertEquals(ownerClientId, awaitClientId(sameSettings));
            assertNotEquals(ownerClientId, awaitClientId(otherSettings));
        }
    }

    @Test
    void instancesWithoutOwnerOpenAndCloseTheirOwnConnections() throws Exception {
        CloudApiOptions brokerOptions = options(builder -> builder.natsUrl(natsTestUrl()));

        try (CloudApiImpl remaining = (CloudApiImpl) CloudApi.create(brokerOptions)) {
            CloudApiImpl closing = (CloudApiImpl) CloudApi.create(brokerOptions);
            assertNotEquals(awaitClientId(remaining), awaitClientId(closing));
            Connection closingConnection = ownNatsConnection(closing);

            closing.close();

            assertEquals(Connection.Status.CLOSED, closingConnection.getStatus());
            assertEquals(Connection.Status.CONNECTED, ownNatsConnection(remaining).getStatus());
        }
    }

    @Test
    void closingAnInstanceStopsOnlyItsOwnSubscriptions() throws Exception {
        String url = natsTestUrl();
        CloudApiOptions brokerOptions = options(builder -> builder.natsUrl(url));
        BlockingQueue<String> ownerMessages = new ArrayBlockingQueue<>(10);
        BlockingQueue<String> closingMessages = new ArrayBlockingQueue<>(10);

        try (Connection publisher = Nats.connect(url);
             CloudApiImpl owner = CloudApiImpl.createNatsConnectionOwner(brokerOptions)) {
            CloudApiImpl closing = (CloudApiImpl) CloudApi.create(brokerOptions);
            awaitClientId(owner);
            subscribe(owner.getNatsConnection(), ownerMessages);
            subscribe(closing.getNatsConnection(), closingMessages);
            owner.getNatsConnection().flush(Duration.ofSeconds(5));

            closing.close();
            publish(publisher, "after-close");

            assertEquals("after-close", ownerMessages.poll(5, TimeUnit.SECONDS));
            owner.getNatsConnection().flush(Duration.ofSeconds(5));
            assertNull(closingMessages.poll(500, TimeUnit.MILLISECONDS));
            assertEquals(Connection.Status.CONNECTED, ownNatsConnection(owner).getStatus());
        }
    }

    @Test
    void drainingAViewLeavesTheSharedConnectionOpen() throws Exception {
        CloudApiOptions brokerOptions = options(builder -> builder.natsUrl(natsTestUrl()));

        try (CloudApiImpl owner = CloudApiImpl.createNatsConnectionOwner(brokerOptions);
             CloudApiImpl draining = (CloudApiImpl) CloudApi.create(brokerOptions)) {
            awaitClientId(owner);
            Connection drainingView = draining.getNatsConnection();

            drainingView.drain(Duration.ofSeconds(1)).get(5, TimeUnit.SECONDS);

            byte[] payload = new byte[0];
            assertThrows(IllegalStateException.class, () -> drainingView.publish(SUBJECT, payload));
            assertEquals(Connection.Status.CONNECTED, owner.getNatsConnection().getStatus());
        }
    }

    @Test
    void closingAViewLeavesTheSharedConnectionOpen() throws Exception {
        CloudApiOptions brokerOptions = options(builder -> builder.natsUrl(natsTestUrl()));

        try (CloudApiImpl owner = CloudApiImpl.createNatsConnectionOwner(brokerOptions);
             CloudApiImpl closing = (CloudApiImpl) CloudApi.create(brokerOptions)) {
            awaitClientId(owner);
            Connection closingView = closing.getNatsConnection();

            closingView.close();

            byte[] payload = new byte[0];
            assertThrows(IllegalStateException.class, () -> closingView.publish(SUBJECT, payload));
            assertEquals(Connection.Status.CONNECTED, owner.getNatsConnection().getStatus());
        }
    }

    @Test
    void closingTheOwnerFirstLeavesLaterClosesQuiet() throws Exception {
        CloudApiOptions brokerOptions = options(builder -> builder.natsUrl(natsTestUrl()));
        PlatformNatsConnection.Settings settings = PlatformNatsConnection.Settings.of(brokerOptions);
        List<String> logsAboveFine = new CopyOnWriteArrayList<>();
        Handler logCollector = new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record.getLevel().intValue() > Level.FINE.intValue()) {
                    logsAboveFine.add(record.getLevel() + " " + record.getMessage());
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        Logger apiLogger = Logger.getLogger("app.simplecloud.api");

        try (CloudApiImpl owner = CloudApiImpl.createNatsConnectionOwner(brokerOptions);
             CloudApiImpl closedLast = (CloudApiImpl) CloudApi.create(brokerOptions)) {
            awaitClientId(owner);
            subscribe(closedLast.getNatsConnection(), new ArrayBlockingQueue<>(10));
            Connection sharedConnection = ownNatsConnection(owner);

            owner.close();

            assertEquals(Connection.Status.CLOSED, sharedConnection.getStatus());
            assertTrue(PlatformNatsConnection.find(settings).isEmpty());
            apiLogger.addHandler(logCollector);
            try {
                assertDoesNotThrow(closedLast::close);
            } finally {
                apiLogger.removeHandler(logCollector);
            }
        }
        assertEquals(List.of(), logsAboveFine);
    }

    private static void subscribe(Connection connection, BlockingQueue<String> received) {
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

    private static Set<Thread> natsMonitorThreads() {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(thread -> thread.getName().equals(NATS_MONITOR_THREAD))
                .collect(Collectors.toSet());
    }

    private static void awaitNoNewNatsMonitorThread(Set<Thread> threadsBefore) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        Set<Thread> newThreads = newNatsMonitorThreads(threadsBefore);
        while (!newThreads.isEmpty() && System.nanoTime() < deadline) {
            Thread.sleep(10);
            newThreads = newNatsMonitorThreads(threadsBefore);
        }
        assertEquals(Set.of(), newThreads);
    }

    private static Set<Thread> newNatsMonitorThreads(Set<Thread> threadsBefore) {
        Set<Thread> threads = natsMonitorThreads();
        threads.removeAll(threadsBefore);
        return threads;
    }

    private static Connection ownNatsConnection(CloudApiImpl api) throws ReflectiveOperationException {
        Field field = CloudApiImpl.class.getDeclaredField("ownNatsConnectionManager");
        field.setAccessible(true);
        NatsFailoverConnectionManager connectionManager = (NatsFailoverConnectionManager) field.get(api);
        return connectionManager.getConnection();
    }

    private static ApiClient apiClient(CloudApi api) throws ReflectiveOperationException {
        Field field = CloudApiImpl.class.getDeclaredField("apiClient");
        field.setAccessible(true);
        return (ApiClient) field.get(api);
    }

    private CloudApiOptions options(UnaryOperator<CloudApiOptions.Builder> customizer) {
        CloudApiOptions.Builder builder = CloudApiOptions.builder()
                .natsUrl("nats://127.0.0.1:1")
                .controllerUrl("http://127.0.0.1:" + controller.getAddress().getPort())
                .networkId("network-1")
                .networkSecret("secret");
        return customizer.apply(builder).build();
    }
}
