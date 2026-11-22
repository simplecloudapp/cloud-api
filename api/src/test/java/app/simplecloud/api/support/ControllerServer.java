package app.simplecloud.api.support;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ControllerServer implements AutoCloseable {

    private final HttpServer server;
    private final Map<String, Deque<Response>> responses = new ConcurrentHashMap<>();
    private final List<Request> requests = new CopyOnWriteArrayList<>();

    private ControllerServer(HttpServer server) {
        this.server = server;
    }

    public static ControllerServer start() {
        try {
            HttpServer httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            ControllerServer controller = new ControllerServer(httpServer);
            httpServer.createContext("/", controller::handle);
            httpServer.start();
            return controller;
        } catch (IOException e) {
            throw new IllegalStateException("Could not start controller server", e);
        }
    }

    public String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public ControllerServer on(String method, String path, String json) {
        return on(method, path, 200, json);
    }

    public ControllerServer on(String method, String path, int status, String json) {
        responses
                .computeIfAbsent(method + " " + path, key -> new ArrayDeque<>())
                .add(new Response(status, json));
        return this;
    }

    public List<Request> requests() {
        return List.copyOf(requests);
    }

    public List<Request> requests(String method, String path) {
        return requests.stream()
                .filter(request ->
                        request.method().equals(method) && request.path().equals(path))
                .toList();
    }

    public Request onlyRequest(String method, String path) {
        List<Request> matching = requests(method, path);
        assertEquals(1, matching.size(), "requests to " + method + " " + path);
        return matching.getFirst();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        requests.add(new Request(
                method, path, parseQuery(exchange.getRequestURI().getRawQuery()), exchange.getRequestHeaders(), body));

        Response response = nextResponse(method + " " + path);
        byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(response.status(), bytes.length == 0 ? -1 : bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private Response nextResponse(String route) {
        Deque<Response> queue = responses.get(route);
        if (queue == null || queue.isEmpty()) {
            return new Response(500, "{\"error\":\"unexpected request " + route + "\"}");
        }
        synchronized (queue) {
            return queue.size() > 1 ? queue.poll() : queue.peek();
        }
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> query = new HashMap<>();
        if (rawQuery == null || rawQuery.isEmpty()) {
            return query;
        }
        for (String pair : rawQuery.split("&")) {
            int separator = pair.indexOf('=');
            String key = separator < 0 ? pair : pair.substring(0, separator);
            String value = separator < 0 ? "" : pair.substring(separator + 1);
            query.put(URLDecoder.decode(key, StandardCharsets.UTF_8), URLDecoder.decode(value, StandardCharsets.UTF_8));
        }
        return query;
    }

    private record Response(int status, String body) {}

    public record Request(String method, String path, Map<String, String> query, Headers headers, String body) {

        public String header(String name) {
            return headers.getFirst(name);
        }

        public String query(String name) {
            return query.get(name);
        }

        public JsonObject json() {
            return JsonParser.parseString(body).getAsJsonObject();
        }
    }
}
