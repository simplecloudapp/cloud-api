package app.simplecloud.api.internal.networkstate;

import app.simplecloud.api.web.ApiClient;
import app.simplecloud.api.web.ApiException;
import build.buf.gen.simplecloud.controller.v2.NetworkState;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Loads the network's whole state with {@code GET /v0/network-state}, through an {@link ApiClient}, so the
 * request uses the client's connection pool, credential, headers and timeouts.
 */
public final class SnapshotLoader {

    private static final String PATH = "/v0/network-state";
    private static final String PROTOBUF = "application/x-protobuf";
    private static final String[] AUTH_NAMES = {"NetworkCredential"};

    private final ApiClient apiClient;
    private final String controllerUrl;
    private final String networkId;

    public SnapshotLoader(ApiClient apiClient, String controllerUrl, String networkId) {
        this.apiClient = apiClient;
        this.controllerUrl = controllerUrl;
        this.networkId = networkId;
    }

    /**
     * Starts a load and returns at once. The result fails on a request that cannot be built, an HTTP error, a
     * timeout, a body of another type than protobuf, and a body that does not parse. Cancelling the result
     * cancels the request.
     */
    public CompletableFuture<NetworkState> load() {
        Call call;
        try {
            call = newCall();
        } catch (ApiException | RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
        CompletableFuture<NetworkState> loaded = new CompletableFuture<>();
        loaded.whenComplete((snapshot, error) -> cancelCallIfCancelled(loaded, call));
        call.enqueue(new SnapshotCallback(loaded));
        return loaded;
    }

    private Call newCall() throws ApiException {
        // Mutable: the client adds the credential and its default headers to these.
        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", PROTOBUF);
        headers.put("X-Network-ID", networkId);
        return apiClient.buildCall(controllerUrl, PATH, "GET", new ArrayList<>(), new ArrayList<>(), null, headers,
                new HashMap<>(), new HashMap<>(), AUTH_NAMES, null);
    }

    private static void cancelCallIfCancelled(CompletableFuture<NetworkState> loaded, Call call) {
        if (loaded.isCancelled()) {
            call.cancel();
        }
    }

    private static NetworkState readSnapshot(Response response) throws IOException {
        if (!response.isSuccessful()) {
            throw new IOException("GET " + PATH + " answered " + response.code());
        }
        ResponseBody body = response.body();
        MediaType contentType = body.contentType();
        boolean protobuf = contentType != null && PROTOBUF.equals(contentType.type() + "/" + contentType.subtype());
        // A proxy or an older controller can answer 200 with a page; protobuf would read most bytes as
        // unknown fields instead of failing.
        if (!protobuf) {
            throw new IOException("GET " + PATH + " answered with content type " + contentType);
        }
        return NetworkState.parseFrom(body.bytes());
    }

    private record SnapshotCallback(CompletableFuture<NetworkState> loaded) implements Callback {

        @Override
        public void onResponse(@NotNull Call call, @NotNull Response response) {
            try (response) {
                loaded.complete(readSnapshot(response));
            } catch (IOException | RuntimeException e) {
                loaded.completeExceptionally(e);
            }
        }

        @Override
        public void onFailure(@NotNull Call call, @NotNull IOException e) {
            loaded.completeExceptionally(e);
        }
    }
}
