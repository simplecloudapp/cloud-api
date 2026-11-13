package app.simplecloud.api.internal;

import app.simplecloud.api.CloudApi;
import app.simplecloud.api.CloudApiOptions;
import app.simplecloud.api.server.Server;
import com.sun.net.httpserver.HttpServer;
import io.nats.client.Connection;
import io.nats.client.Nats;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class SharedCloudApiResourcesTest {

    private final AtomicInteger serverRequests = new AtomicInteger();
    private HttpServer controller;

    @BeforeEach
    void startController() throws Exception {
        controller = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        controller.createContext("/v0/servers", exchange -> {
            serverRequests.incrementAndGet();
            try {
                // Keeps the request in flight while the other instance asks for the same data.
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] body = "{\"servers\":[]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        controller.setExecutor(Executors.newCachedThreadPool());
        controller.start();
    }

    @AfterEach
    void stopController() {
        controller.stop(0);
    }

    @Test
    void instancesWithTheSameSettingsSendOneRequestForTheSameRead() {
        try (CloudApi first = CloudApi.create(options(builder -> builder.component("first-plugin")));
             CloudApi second = CloudApi.create(options(builder -> builder.component("second-plugin")))) {
            assertSame(first.cache(), second.cache());

            CompletableFuture<List<Server>> firstRead = first.server().getServersByGroup("lobby");
            CompletableFuture<List<Server>> secondRead = second.server().getServersByGroup("lobby");

            assertEquals(List.of(), firstRead.join());
            assertEquals(List.of(), secondRead.join());
            assertEquals(1, serverRequests.get());
        }
    }

    @Test
    void dedicatedHttpClientNeverSharesRequests() {
        try (CloudApi first = CloudApi.create(options(UnaryOperator.identity()));
             CloudApi second = CloudApi.create(options(CloudApiOptions.Builder::dedicatedHttpClient))) {
            assertNotSame(first.cache(), second.cache());

            CompletableFuture<List<Server>> firstRead = first.server().getServersByGroup("lobby");
            CompletableFuture<List<Server>> secondRead = second.server().getServersByGroup("lobby");

            firstRead.join();
            secondRead.join();
            assertEquals(2, serverRequests.get());
        }
    }

    @Test
    void closingOneInstanceKeepsTheSharedResourcesOfTheOtherOpen() {
        CloudApi second = CloudApi.create(options(UnaryOperator.identity()));
        try {
            CloudApi.create(options(UnaryOperator.identity())).close();

            assertEquals(List.of(), second.server().getServersByGroup("lobby").join());
            assertEquals(1, serverRequests.get());
        } finally {
            second.close();
        }
    }

    // Run against an isolated broker with NATS_TEST_URL=nats://127.0.0.1:4222.
    @Test
    void instancesShareOneNatsConnectionAndKeepTheirOwnSubscriptions() throws Exception {
        String url = System.getenv("NATS_TEST_URL");
        assumeTrue(url != null && !url.isBlank(), "set NATS_TEST_URL to run broker integration tests");

        String subject = "network-1.shared-test";
        BlockingQueue<String> firstMessages = new ArrayBlockingQueue<>(10);
        BlockingQueue<String> secondMessages = new ArrayBlockingQueue<>(10);
        UnaryOperator<CloudApiOptions.Builder> nats = builder -> builder.natsUrl(url);

        try (Connection publisher = Nats.connect(url);
             CloudApi dedicated = CloudApi.create(options(builder -> nats.apply(builder).dedicatedNatsConnection()));
             CloudApi second = CloudApi.create(options(nats));
             CloudApiImpl first = (CloudApiImpl) CloudApi.create(options(nats))) {
            Connection firstConnection = first.getNatsConnection();
            Connection secondConnection = ((CloudApiImpl) second).getNatsConnection();
            Connection dedicatedConnection = ((CloudApiImpl) dedicated).getNatsConnection();
            awaitConnected(firstConnection);
            awaitConnected(dedicatedConnection);

            assertEquals(firstConnection.getServerInfo().getClientId(), secondConnection.getServerInfo().getClientId());
            assertNotEquals(firstConnection.getServerInfo().getClientId(), dedicatedConnection.getServerInfo().getClientId());

            firstConnection.createDispatcher(message -> firstMessages.add(new String(message.getData(), StandardCharsets.UTF_8)))
                    .subscribe(subject);
            secondConnection.createDispatcher(message -> secondMessages.add(new String(message.getData(), StandardCharsets.UTF_8)))
                    .subscribe(subject);
            firstConnection.flush(Duration.ofSeconds(5));

            publisher.publish(subject, "before".getBytes(StandardCharsets.UTF_8));
            assertEquals("before", firstMessages.poll(5, TimeUnit.SECONDS));
            assertEquals("before", secondMessages.poll(5, TimeUnit.SECONDS));

            first.close();
            assertEquals(Connection.Status.CONNECTED, secondConnection.getStatus());

            publisher.publish(subject, "after".getBytes(StandardCharsets.UTF_8));
            assertEquals("after", secondMessages.poll(5, TimeUnit.SECONDS));
            secondConnection.flush(Duration.ofSeconds(5));
            assertNull(firstMessages.poll(500, TimeUnit.MILLISECONDS));
        }
    }

    private CloudApiOptions options(UnaryOperator<CloudApiOptions.Builder> customizer) {
        CloudApiOptions.Builder builder = CloudApiOptions.builder()
                .natsUrl("nats://127.0.0.1:1")
                .controllerUrl("http://127.0.0.1:" + controller.getAddress().getPort())
                .networkId("network-1")
                .networkSecret("secret");
        return customizer.apply(builder).build();
    }

    private static void awaitConnected(Connection connection) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (connection.getStatus() != Connection.Status.CONNECTED && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertEquals(Connection.Status.CONNECTED, connection.getStatus());
    }
}
