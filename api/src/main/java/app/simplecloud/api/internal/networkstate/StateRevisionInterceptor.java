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
 * answers reads, a read waits up to 3 s for the copy to reach the write before it goes over HTTP. The
 * controller puts the revision on every successful response to a write of a network-scoped route. A write
 * that may have committed but whose revision is unknown goes to the view as
 * {@link ViewInput.WriteRevisionUnknown}, so a live view reloads: a 2xx without the revision, a 5xx (from the
 * controller or a proxy in front of it), and a call that threw, such as a read timeout or a dropped
 * connection. A 4xx hands over nothing, because the controller committed nothing.
 */
public final class StateRevisionInterceptor implements Interceptor {

    private static final Logger LOGGER = Logger.getLogger(StateRevisionInterceptor.class.getName());
    private static final String EPOCH_HEADER = "X-SC-State-Epoch";
    private static final String REVISION_HEADER = "X-SC-State-Revision";
    private static final String API_VERSION_SEGMENT = "v0";
    private static final int FIRST_SERVER_ERROR = 500;
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
        if (!writesNetworkState(request)) {
            return chain.proceed(request);
        }
        Response response = proceedReportingThrowToView(chain);
        if (response.isSuccessful()) {
            handOverRevision(response);
        } else if (response.code() >= FIRST_SERVER_ERROR) {
            LOGGER.fine("A network state write returned " + response.code());
            shell.writeRevisionUnknown();
        }
        // Any other status, above all a 4xx, means the controller committed nothing.
        return response;
    }

    private Response proceedReportingThrowToView(Chain chain) throws IOException {
        try {
            return chain.proceed(chain.request());
        } catch (IOException e) {
            LOGGER.fine("A network state write threw before its response arrived: " + e);
            shell.writeRevisionUnknown();
            throw e;
        }
    }

    /**
     * Returns whether the request writes a server, group, persistent server or blueprint row, by its method and
     * its route's first path segment after the API version. The player routes write none of these rows, and
     * leaving them out keeps a 5xx on them from reloading the view.
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
            shell.writeRevisionUnknown();
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
            shell.writeRevisionUnknown();
            return;
        }
        shell.writeReturned(parsedEpoch, parsedRevision);
    }
}
