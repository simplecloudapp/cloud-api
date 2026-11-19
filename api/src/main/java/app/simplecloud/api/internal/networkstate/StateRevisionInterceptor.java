package app.simplecloud.api.internal.networkstate;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Hands a view the state revision of each network state write that a client sends, so that while the view
 * answers reads, a read waits for the copy to reach the write, for up to 3 s after the write. The controller
 * puts the revision on every successful response to a write of a network-scoped route. The interceptor hands
 * a write over only when its response was successful and both headers parse as unsigned numbers. A 4xx
 * committed nothing. A 5xx, a call that threw, and a response with missing or malformed headers hand over
 * nothing either, though the write may have committed: the copy gets it with its batch, and a read right after
 * it may be answered from a copy that lacks it.
 */
public final class StateRevisionInterceptor implements Interceptor {

    private static final Logger LOGGER = Logger.getLogger(StateRevisionInterceptor.class.getName());
    private static final String EPOCH_HEADER = "X-SC-State-Epoch";
    private static final String REVISION_HEADER = "X-SC-State-Revision";
    private static final String API_VERSION_SEGMENT = "v0";
    private static final List<String> WRITE_METHODS = List.of("POST", "PUT", "PATCH", "DELETE");
    private static final List<String> NETWORK_STATE_RESOURCES =
            List.of("servers", "server-groups", "persistent-servers", "blueprints");

    private final NetworkViewShell shell;

    private StateRevisionInterceptor(NetworkViewShell shell) {
        this.shell = shell;
    }

    /**
     * Returns a client that sends its requests through {@code httpClient}'s connection pool and dispatcher,
     * and hands {@code shell} the revision of each of its writes before the write's caller gets the response.
     */
    public static OkHttpClient addTo(OkHttpClient httpClient, NetworkViewShell shell) {
        return httpClient.newBuilder()
                .addInterceptor(new StateRevisionInterceptor(shell))
                .build();
    }

    @Override
    public @NotNull Response intercept(@NotNull Chain chain) throws IOException {
        Request request = chain.request();
        Response response = chain.proceed(request);
        boolean successfulWrite = writesNetworkState(request) && response.isSuccessful();
        if (successfulWrite) {
            handOverRevision(response);
        }
        return response;
    }

    /**
     * Returns whether the request writes a server, group, persistent server or blueprint row, by its method and
     * its route's first path segment after the API version. The player routes write none of these rows, so no
     * read needs to wait for them.
     */
    static boolean writesNetworkState(Request request) {
        if (!WRITE_METHODS.contains(request.method())) {
            return false;
        }
        List<String> segments = request.url().pathSegments();
        int versionIndex = segments.indexOf(API_VERSION_SEGMENT);
        if (versionIndex < 0 || versionIndex + 1 >= segments.size()) {
            return false;
        }
        String resource = segments.get(versionIndex + 1);
        return NETWORK_STATE_RESOURCES.contains(resource);
    }

    private void handOverRevision(Response response) {
        String epoch = response.header(EPOCH_HEADER);
        String revision = response.header(REVISION_HEADER);
        if (epoch == null || revision == null) {
            // An older controller, or one whose read of the revision failed after the write.
            LOGGER.fine("A network state write returned without the state revision headers");
            return;
        }
        long parsedEpoch;
        long parsedRevision;
        try {
            parsedEpoch = Long.parseUnsignedLong(epoch);
            parsedRevision = Long.parseUnsignedLong(revision);
        } catch (NumberFormatException e) {
            LOGGER.log(Level.WARNING, "A network state write returned state revision headers that are not"
                    + " unsigned numbers: " + EPOCH_HEADER + "=" + epoch + ", " + REVISION_HEADER + "=" + revision, e);
            return;
        }
        shell.writeReturned(parsedEpoch, parsedRevision);
    }
}
