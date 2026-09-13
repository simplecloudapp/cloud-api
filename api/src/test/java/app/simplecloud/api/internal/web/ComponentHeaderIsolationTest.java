package app.simplecloud.api.internal.web;

import app.simplecloud.api.CloudApi;
import app.simplecloud.api.CloudApiOptions;
import app.simplecloud.api.internal.cache.NoOpQueryCache;
import app.simplecloud.api.internal.group.GroupApiImpl;
import app.simplecloud.api.internal.persistentserver.PersistentServerApiImpl;
import app.simplecloud.api.internal.player.PlayerApiImpl;
import app.simplecloud.api.internal.server.ServerApiImpl;
import app.simplecloud.api.web.ApiClient;
import app.simplecloud.api.web.Configuration;
import app.simplecloud.api.web.apis.PlayersApi;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class ComponentHeaderIsolationTest {
    private final List<String> components = new CopyOnWriteArrayList<>();
    private HttpServer server;
    private ApiClient previousDefaultClient;

    @BeforeEach
    void startServer() throws Exception {
        previousDefaultClient = Configuration.getDefaultApiClient();
        Configuration.setDefaultApiClient(new ApiClient());
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v0/players/online/count", exchange -> {
            components.add(exchange.getRequestHeaders().getFirst("X-SC-Component"));
            byte[] body = "{\"count\":0}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
        Configuration.setDefaultApiClient(previousDefaultClient);
    }

    @Test
    void requestsKeepTheirOwnComponentRegardlessOfConstructionOrder() {
        try (CloudApi untaggedBefore = CloudApi.create(options(null));
             CloudApi essentials = CloudApi.create(options("proxy-essentials"));
             CloudApi anotherPlugin = CloudApi.create(options("another-plugin"));
             CloudApi untaggedAfter = CloudApi.create(options(null));
             CloudApi blank = CloudApi.create(options("  "))) {
            for (CloudApi api : List.of(untaggedBefore, essentials, anotherPlugin, untaggedAfter, blank, essentials)) {
                assertEquals(0, api.player().getOnlinePlayerCount().join());
            }
        }

        assertEquals(Arrays.asList(null, "proxy-essentials", "another-plugin", null, null, "proxy-essentials"), components);
    }

    @Test
    void createSharesOneHttpClientAcrossAllApisButNotBetweenInstances() throws Exception {
        try (CloudApi first = CloudApi.create(options("proxy-essentials"));
             CloudApi second = CloudApi.create(options("another-plugin"))) {
            ApiClient firstClient = httpClient(first.player(), "playersApi");
            ApiClient secondClient = httpClient(second.player(), "playersApi");
            assertNotSame(firstClient, secondClient);
            assertNotSame(firstClient.getHttpClient(), secondClient.getHttpClient());
            assertNotSame(Configuration.getDefaultApiClient(), firstClient);
            assertNotSame(Configuration.getDefaultApiClient(), secondClient);

            for (CloudApi api : List.of(first, second)) {
                ApiClient client = httpClient(api.player(), "playersApi");
                assertSame(client, httpClient(api.server(), "serversApi"));
                assertSame(client, httpClient(api.group(), "serverGroupsApi"));
                assertSame(client, httpClient(api.persistentServer(), "persistentServersApi"));
                assertSame(client, httpClient(api.group(), "inlineBlueprintSupport", "blueprintsApi"));
                assertSame(client, httpClient(api.persistentServer(), "inlineBlueprintSupport", "blueprintsApi"));
            }
        }
    }

    @Test
    void constructingAnyFacadeDoesNotTagOtherInstancesOrTheGeneratedDefaultClient() throws Exception {
        PlayerApiImpl untagged = new PlayerApiImpl(options(null), null);
        var defaultPlayers = new PlayersApi();
        defaultPlayers.setCustomBaseUrl(options(null).getControllerUrl());
        List<Consumer<CloudApiOptions>> constructors = List.of(
                options -> new ServerApiImpl(options, new NoOpQueryCache()),
                options -> new GroupApiImpl(options, new NoOpQueryCache()),
                options -> new PersistentServerApiImpl(options, new NoOpQueryCache()),
                options -> new PlayerApiImpl(options, null)
        );

        for (Consumer<CloudApiOptions> constructor : constructors) {
            constructor.accept(options("proxy-essentials"));
            untagged.getOnlinePlayerCount().join();
            defaultPlayers.v0PlayersOnlineCountGet("network", "secret");
        }

        assertEquals(Collections.nCopies(constructors.size() * 2, null), components);
    }

    private CloudApiOptions options(String component) {
        return CloudApiOptions.builder()
                .controllerUrl("http://127.0.0.1:" + server.getAddress().getPort())
                .natsUrl("nats://127.0.0.1:1")
                .disableCache()
                .networkId("network")
                .networkSecret("secret")
                .component(component)
                .build();
    }

    private static ApiClient httpClient(Object owner, String... fieldPath) throws Exception {
        Object value = owner;
        for (String fieldName : fieldPath) {
            var field = value.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            value = field.get(value);
        }
        return (ApiClient) value.getClass().getMethod("getApiClient").invoke(value);
    }
}
