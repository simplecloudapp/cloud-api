package app.simplecloud.api.internal.nats;

import io.nats.client.Options;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Runs a NATS connection on daemon threads, so that an open connection cannot keep the JVM alive.
 * jnats does not shut down executors it is given, so {@link #shutdown()} does.
 */
final class DaemonNatsThreads implements NatsThreads {

    private static final int JNATS_DEFAULT_SCHEDULER_THREADS = 3;

    private final ExecutorService executor = Executors.newCachedThreadPool(DaemonNatsThreads::newDaemonThread);
    private final ScheduledExecutorService scheduledExecutor =
            Executors.newScheduledThreadPool(JNATS_DEFAULT_SCHEDULER_THREADS, DaemonNatsThreads::newDaemonThread);
    private final ExecutorService connectExecutor =
            Executors.newSingleThreadExecutor(DaemonNatsThreads::newDaemonThread);
    private final ExecutorService callbackExecutor =
            Executors.newSingleThreadExecutor(DaemonNatsThreads::newDaemonThread);

    @Override
    public void applyTo(Options.Builder optionsBuilder) {
        optionsBuilder.executor(executor);
        optionsBuilder.scheduledExecutor(scheduledExecutor);
        optionsBuilder.connectExecutor(connectExecutor);
        optionsBuilder.callbackExecutor(callbackExecutor);
    }

    @Override
    public void shutdown() {
        executor.shutdownNow();
        scheduledExecutor.shutdownNow();
        connectExecutor.shutdownNow();
        callbackExecutor.shutdownNow();
    }

    private static Thread newDaemonThread(Runnable task) {
        Thread thread = new Thread(task, "simplecloud-nats-daemon");
        thread.setDaemon(true);
        return thread;
    }
}
