package app.simplecloud.api.internal.networkstate.read;

import app.simplecloud.api.internal.networkstate.NetworkCopy;
import build.buf.gen.simplecloud.controller.v2.NetworkState;

/**
 * Copies as a view holds them after taking a snapshot.
 */
final class Copies {

    private Copies() {
    }

    static NetworkCopy of(NetworkState snapshot) {
        return NetworkCopy.of(snapshot);
    }
}
