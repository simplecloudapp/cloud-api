package app.simplecloud.api.internal.networkstate;

import java.util.Optional;

/**
 * The production machine of {@link NetworkView}. It is a stub until its implementation lands. The checks it
 * must pass are its specification; they run against a reference machine in the tests until then.
 */
public final class NetworkViewMachine implements NetworkView {

    private NetworkViewMachine() {
    }

    /**
     * Returns a view with no copy and no connection, which waits for {@link ViewInput.SubscriptionsActive}.
     */
    public static NetworkView start(RandomSequence random) {
        throw new UnsupportedOperationException("NetworkViewMachine is not implemented yet");
    }

    @Override
    public ViewStep step(ViewInput input, long nowMillis) {
        throw new UnsupportedOperationException("NetworkViewMachine is not implemented yet");
    }

    @Override
    public boolean isLive(long nowMillis) {
        throw new UnsupportedOperationException("NetworkViewMachine is not implemented yet");
    }

    @Override
    public boolean mayAnswer(ItemKind kind, long nowMillis) {
        throw new UnsupportedOperationException("NetworkViewMachine is not implemented yet");
    }

    @Override
    public Optional<NetworkCopy> copy() {
        throw new UnsupportedOperationException("NetworkViewMachine is not implemented yet");
    }
}
