package app.simplecloud.api.internal.blueprint;

import app.simplecloud.api.CloudApiOptions;
import app.simplecloud.api.blueprint.CreateBlueprintRequest;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ManifestServerUrlResolverTest {

    @Test
    void resolve_returnsDownloadLinkForMinecraftVersion() throws Exception {
        try (ManifestHttpServer server = new ManifestHttpServer("""
                [
                  {
                    "name": "paper",
                    "downloadLinks": [
                      { "version": "1.21.11", "link": "https://example.com/paper-1.21.11.jar" }
                    ]
                  }
                ]
                """)) {
            ManifestServerUrlResolver resolver = new ManifestServerUrlResolver(
                    CloudApiOptions.builder()
                            .serverVersionManifestUrl(server.url())
                            .build()
            );

            String resolvedUrl = resolver.resolve(
                    CreateBlueprintRequest.builder()
                            .serverSoftware("paper")
                            .minecraftVersion("1.21.11")
                            .build()
            );

            assertEquals("https://example.com/paper-1.21.11.jar", resolvedUrl);
        }
    }

    @Test
    void resolve_returnsNullWhenVersionIsMissingFromManifest() throws Exception {
        try (ManifestHttpServer server = new ManifestHttpServer("""
                [
                  {
                    "name": "paper",
                    "downloadLinks": [
                      { "version": "1.21.10", "link": "https://example.com/paper-1.21.10.jar" }
                    ]
                  }
                ]
                """)) {
            ManifestServerUrlResolver resolver = new ManifestServerUrlResolver(
                    CloudApiOptions.builder()
                            .serverVersionManifestUrl(server.url())
                            .build()
            );

            String resolvedUrl = resolver.resolve(
                    CreateBlueprintRequest.builder()
                            .serverSoftware("paper")
                            .minecraftVersion("1.21.11")
                            .build()
            );

            assertNull(resolvedUrl);
        }
    }

    private static final class ManifestHttpServer implements AutoCloseable {
        private final HttpServer server;

        private ManifestHttpServer(String body) throws Exception {
            this.server = HttpServer.create(new InetSocketAddress(0), 0);
            this.server.createContext("/server_versions.json", exchange -> {
                byte[] responseBody = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, responseBody.length);
                try (OutputStream outputStream = exchange.getResponseBody()) {
                    outputStream.write(responseBody);
                }
            });
            this.server.start();
        }

        private String url() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/server_versions.json";
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
