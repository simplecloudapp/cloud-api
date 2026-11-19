package app.simplecloud.api.internal.networkstate;

/**
 * The clock of the network view's code: a {@link NetworkViewShell} passes it into every step of its view, and the
 * read sources time their warnings by it. Tests replace it, so that they can pass waits of seconds or minutes
 * without waiting.
 */
@FunctionalInterface
public interface MonotonicClock {

    MonotonicClock SYSTEM = () -> System.nanoTime() / 1_000_000;

    /**
     * Returns milliseconds that never go back. Only differences between two values mean something.
     */
    long nowMillis();
}
