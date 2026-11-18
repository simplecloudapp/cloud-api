package app.simplecloud.api.internal.networkstate;

/**
 * The clock a {@link NetworkViewShell} passes into every step of its view. Tests replace it, so that they
 * can pass the view's waits of seconds without waiting.
 */
@FunctionalInterface
public interface MonotonicClock {

    MonotonicClock SYSTEM = () -> System.nanoTime() / 1_000_000;

    /**
     * Returns milliseconds that never go back. Only differences between two values mean something.
     */
    long nowMillis();
}
