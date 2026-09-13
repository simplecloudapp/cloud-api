package app.simplecloud.api.internal.web;

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
        PlayerApiImpl untaggedBefore = new PlayerApiImpl(options(null), null);
        PlayerApiImpl essentials = new PlayerApiImpl(options("proxy-essentials"), null);
        PlayerApiImpl anotherPlugin = new PlayerApiImpl(options("another-plugin"), null);
        PlayerApiImpl untaggedAfter = new PlayerApiImpl(options(null), null);
        PlayerApiImpl blank = new PlayerApiImpl(options("  "), null);

        for (PlayerApiImpl api : List.of(untaggedBefore, essentials, anotherPlugin, untaggedAfter, blank, essentials)) {
            assertEquals(0, api.getOnlinePlayerCount().join());
        }

        assertEquals(Arrays.asList(null, "proxy-essentials", "another-plugin", null, null, "proxy-essentials"), components);
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
                .networkId("network")
                .networkSecret("secret")
                .component(component)
                .build();
    }
}
