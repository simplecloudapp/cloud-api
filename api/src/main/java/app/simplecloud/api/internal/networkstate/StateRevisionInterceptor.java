package app.simplecloud.api.internal.networkstate;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Hands a view the state revision of each successful write that a client sends, so that the reads the write
 * can change go over HTTP until the view's copy has reached the write. The controller puts the revision on
 * every successful response to a write of a network-scoped route. A write that fails or times out hands over
 * nothing: its caller gets an error, and the view promises nothing about it.
 */
public final class StateRevisionInterceptor implements Interceptor {

    private static final Logger LOGGER = Logger.getLogger(StateRevisionInterceptor.class.getName());
    private static final String EPOCH_HEADER = "X-SC-State-Epoch";
    private static final String REVISION_HEADER = "X-SC-State-Revision";
    private static final String API_VERSION_SEGMENT = "v0";
    private static final List<String> WRITE_METHODS = List.of("POST", "PUT", "PATCH", "DELETE");

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
        Optional<WriteKind> written = writtenKind(request);
        if (written.isPresent() && response.isSuccessful()) {
            handOver(written.get(), response);
        }
        return response;
    }

    /**
     * Returns the kind of item the request's route writes, by the route's first path segment after the API
     * version, or empty for a read and for a route that writes none of these items. The player routes write
     * no server, group, persistent server or blueprint row.
     */
    static Optional<WriteKind> writtenKind(Request request) {
        if (!WRITE_METHODS.contains(request.method())) {
            return Optional.empty();
        }
        List<String> segments = request.url().pathSegments();
        int versionIndex = segments.indexOf(API_VERSION_SEGMENT);
        if (versionIndex < 0 || versionIndex + 1 >= segments.size()) {
            return Optional.empty();
        }
        String resource = segments.get(versionIndex + 1);
        return switch (resource) {
            case "servers" -> Optional.of(WriteKind.SERVER);
            case "server-groups" -> Optional.of(WriteKind.SERVER_GROUP);
            case "persistent-servers" -> Optional.of(WriteKind.PERSISTENT_SERVER);
            case "blueprints" -> Optional.of(WriteKind.BLUEPRINT);
            default -> Optional.empty();
        };
    }

    private void handOver(WriteKind written, Response response) {
        String epoch = response.header(EPOCH_HEADER);
        String revision = response.header(REVISION_HEADER);
        if (epoch == null || revision == null) {
            // An older controller, or one whose read of the revision failed after the write.
            LOGGER.fine("A " + written + " write returned without the state revision headers");
            shell.writeReturnedWithoutRevision();
            return;
        }
        long parsedEpoch;
        long parsedRevision;
        try {
            parsedEpoch = Long.parseUnsignedLong(epoch);
            parsedRevision = Long.parseUnsignedLong(revision);
        } catch (NumberFormatException e) {
            LOGGER.log(Level.WARNING, "A " + written + " write returned state revision headers that are not"
                    + " unsigned numbers: " + EPOCH_HEADER + "=" + epoch + ", " + REVISION_HEADER + "=" + revision, e);
            shell.writeReturnedWithoutRevision();
            return;
        }
        shell.writeReturned(written, parsedEpoch, parsedRevision);
    }
}
