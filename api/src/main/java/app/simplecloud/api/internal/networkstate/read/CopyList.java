package app.simplecloud.api.internal.networkstate.read;

import app.simplecloud.api.internal.networkstate.NetworkCopy;
import app.simplecloud.api.internal.networkstate.ReadKind;

import java.util.Optional;

/**
 * One controller list route as a copy answers it, with the response the HTTP client reads for the same
 * parameters.
 *
 * @param <P> the route's parameters
 * @param <R> the generated response model
 */
public interface CopyList<P, R> {

    /**
     * Returns the kind of read the view routes for this list.
     */
    ReadKind kind();

    /**
     * Returns whether the controller refuses these parameters with an error, so that no copy answers them.
     */
    boolean refuses(P parameters);

    /**
     * Returns the controller's answer, read as the HTTP client reads it, or empty when only the controller can
     * give it. A body the HTTP client cannot read throws as it does over HTTP.
     */
    Optional<R> answer(NetworkCopy copy, P parameters);
}
