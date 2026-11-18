package app.simplecloud.api.internal.networkstate;

import app.simplecloud.api.internal.nats.NatsFailoverConnectionManager;
import build.buf.gen.simplecloud.controller.v2.NetworkState;
import build.buf.gen.simplecloud.controller.v2.StateBatch;
import build.buf.gen.simplecloud.controller.v2.StatePulse;
import com.google.protobuf.InvalidProtocolBufferException;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Message;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runs one network's {@link NetworkView} in this process, and makes none of its decisions. Each input steps
 * the view on the thread it arrives on, under one lock, except the answers to snapshot loads, which step it
 * on the shell's timer thread. The shell then carries out the effects the view returns. The inputs are the
 * messages on {@code <network>.state.v1.batch} and {@code <network>.state.v1.pulse}, the connection's state,
 * the answers to snapshot loads, this process's writes and a time tick.
 *
 * <p>A read that the view says waits for an own write waits for the copy. After every step, also each time
 * tick, the shell asks the view again for each waiting read, and answers it from the copy or sends it over
 * HTTP once the view says so. The view ends a wait 3 s after the read started, so a read goes over HTTP within
 * the next 100 ms tick after that at the latest.
 *
 * <p>A step that throws is a bug in the view, or a class loader that is going away. The shell logs it and
 * stops, as if closed but with its subscriptions left open: it takes no input and answers no read from then
 * on, so every read goes over HTTP, also a waiting one. A view that missed an input could otherwise answer
 * before an own write.
 */
public final class NetworkViewShell implements NatsFailoverConnectionManager.SubscriptionListener, AnsweringCopy {

    private static final Logger LOGGER = Logger.getLogger(NetworkViewShell.class.getName());

    /**
     * Every step acts on the deadlines that have passed, so time needs its own input only while nothing else
     * arrives. 100 ms keeps each deadline within 0.1 s, small against the view's shortest wait of 2 s, and
     * costs at most ten small steps a second.
     */
    private static final long TIME_PASSED_INTERVAL_MILLIS = 100;
    private static final Duration FLUSH_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration FLUSH_RETRY_DELAY = Duration.ofSeconds(1);

    private record WaitingRead(long startedAt, CompletableFuture<Optional<NetworkCopy>> answer) {
    }

    private final String batchSubject;
    private final String pulseSubject;
    private final Connection connection;
    private final SnapshotLoader snapshots;
    private final MonotonicClock clock;
    /**
     * Sends the time ticks and hands over the answers to snapshot loads.
     */
    private final ScheduledExecutorService timer;
    private final Object stepLock = new Object();
    /**
     * Counts losses and resends of the subscriptions, so that a flush that ends after a later change does
     * not report the subscriptions active.
     */
    private final AtomicLong subscriptionChanges = new AtomicLong();
    private final Map<AuditOutcome, Long> auditCounts = new EnumMap<>(AuditOutcome.class);
    /**
     * Guarded by {@link #stepLock}. Every step settles them before it releases the lock.
     */
    private final List<WaitingRead> waitingReads = new ArrayList<>();
    /**
     * Set under {@link #stepLock}, by {@link #close} or by a step that threw, so no step starts and no read
     * waits after it.
     */
    private volatile boolean stopped;
    private volatile NetworkView view;
    private CompletableFuture<NetworkState> runningLoad;
    private Dispatcher natsDispatcher;

    private NetworkViewShell(
            String networkId,
            Connection connection,
            SnapshotLoader snapshots,
            NetworkView startView,
            MonotonicClock clock
    ) {
        this.batchSubject = networkId + ".state.v1.batch";
        this.pulseSubject = networkId + ".state.v1.pulse";
        this.connection = connection;
        this.snapshots = snapshots;
        this.view = startView;
        this.clock = clock;
        this.timer = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform()
                .name("simplecloud-network-view-" + networkId)
                .daemon(true)
                .factory());
    }

    /**
     * Subscribes to the network's batches and pulses and starts the time ticks. It neither blocks nor loads:
     * the view waits for {@link #subscriptionsSent} and the flush after it. The caller adds the shell as the
     * connection's {@link NatsFailoverConnectionManager.SubscriptionListener} after this returns, so that the
     * listener's first call covers these subscriptions.
     *
     * @param networkId  the prefix of the network's subjects, as in its event subjects
     * @param connection the connection that delivers the messages, also across reconnects
     * @param startView  a view that has seen no input, such as {@link NetworkViewMachine#start}
     */
    public static NetworkViewShell start(
            String networkId,
            Connection connection,
            SnapshotLoader snapshots,
            NetworkView startView,
            MonotonicClock clock
    ) {
        NetworkViewShell shell = new NetworkViewShell(networkId, connection, snapshots, startView, clock);
        shell.subscribe();
        shell.timer.scheduleWithFixedDelay(shell::tick, TIME_PASSED_INTERVAL_MILLIS, TIME_PASSED_INTERVAL_MILLIS,
                TimeUnit.MILLISECONDS);
        return shell;
    }

    private void subscribe() {
        // One dispatcher for both subjects: jnats delivers its messages from one queue, in arrival order.
        natsDispatcher = connection.createDispatcher();
        natsDispatcher.subscribe(batchSubject, this::receiveBatch);
        natsDispatcher.subscribe(pulseSubject, this::receivePulse);
    }

    /**
     * Returns the copy to answer a read from, or empty, so the read goes over HTTP. The future is complete on
     * return unless the read waits for an own write. A waiting read completes on a thread of its own, not under
     * the shell's lock or on a thread that feeds the view, so the caller's continuation may block. Only when
     * that thread cannot start does the read complete on the thread that settles it.
     */
    @Override
    public CompletableFuture<Optional<NetworkCopy>> copyToAnswer() {
        NetworkView current = view;
        // Read after the view, so the time is at or after the time of the step that made it.
        long now = clock.nowMillis();
        ReadRoute route = routeOf(current, now, now);
        return switch (route) {
            case FROM_COPY -> CompletableFuture.completedFuture(current.copy());
            case OVER_HTTP -> CompletableFuture.completedFuture(Optional.empty());
            case WAIT -> waitForOwnWrite(now);
        };
    }

    /**
     * Hands the view a write of this process that returned with its state revision. The view takes it on
     * the caller's thread before this returns, so a read the writer starts next already waits for the write.
     */
    public void writeReturned(long epoch, long revision) {
        feed(new ViewInput.WriteReturned(epoch, revision));
    }

    /**
     * Hands the view a write of this process that may have committed but whose state revision is unknown,
     * before this returns.
     */
    public void writeRevisionUnknown() {
        feed(new ViewInput.WriteRevisionUnknown());
    }

    @Override
    public void connectionLost() {
        subscriptionChanges.incrementAndGet();
        feed(new ViewInput.ConnectionLost());
    }

    @Override
    public void subscriptionsSent() {
        long change = subscriptionChanges.incrementAndGet();
        // flush blocks until the server answers, and this call comes on a NATS or reconnect thread.
        Thread.ofVirtual()
                .name("simplecloud-network-view-flush")
                .start(() -> confirmSubscriptions(change));
    }

    /**
     * Stops the time ticks, cancels a running snapshot load and ends the subscriptions. No step starts after
     * this returns. Call it once.
     */
    public void close() {
        synchronized (stepLock) {
            stopped = true;
            if (runningLoad != null) {
                runningLoad.cancel(false);
            }
            settleWaitingReads();
        }
        timer.shutdownNow();
        // Outside the lock, which the dispatcher's thread takes for every message.
        closeNatsDispatcher();
    }

    private void receiveBatch(Message message) {
        StateBatch batch;
        try {
            batch = StateBatch.parseFrom(message.getData());
        } catch (InvalidProtocolBufferException e) {
            // As good as lost: the view finds the gap by the next batch or pulse.
            LOGGER.log(Level.FINE, "Dropped a network state batch that does not parse", e);
            return;
        }
        feed(new ViewInput.BatchReceived(batch));
    }

    private void receivePulse(Message message) {
        StatePulse pulse;
        try {
            pulse = StatePulse.parseFrom(message.getData());
        } catch (InvalidProtocolBufferException e) {
            // As good as lost: the next pulse replaces it.
            LOGGER.log(Level.FINE, "Dropped a network state pulse that does not parse", e);
            return;
        }
        feed(new ViewInput.PulseReceived(pulse));
    }

    private void tick() {
        // A throw would end the ticks for good; feed catches what a step throws.
        feed(new ViewInput.TimePassed());
    }

    private void confirmSubscriptions(long change) {
        while (!stopped && subscriptionChanges.get() == change) {
            if (flushed()) {
                feedSubscriptionsActiveIfCurrent(change);
                return;
            }
            try {
                Thread.sleep(FLUSH_RETRY_DELAY);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private boolean flushed() {
        try {
            connection.flush(FLUSH_TIMEOUT);
            return true;
        } catch (TimeoutException e) {
            LOGGER.log(Level.FINE, "Flush after subscribing to the network state timed out; trying again", e);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (RuntimeException e) {
            // Thrown while the failover proxy has no connection, or after it closed.
            LOGGER.log(Level.FINE, "Flush after subscribing to the network state failed; trying again", e);
            return false;
        }
    }

    private void feedSubscriptionsActiveIfCurrent(long change) {
        synchronized (stepLock) {
            // Under the lock: a loss counted after this check steps the view after this step, never before.
            if (subscriptionChanges.get() == change) {
                step(new ViewInput.SubscriptionsActive());
            }
        }
    }

    private void feedLoadAnswer(NetworkState snapshot, Throwable failure) {
        if (failure != null) {
            LOGGER.log(Level.FINE, "Loading the network state failed", failure);
            feed(new ViewInput.SnapshotFailed());
            return;
        }
        feed(new ViewInput.SnapshotLoaded(snapshot));
    }

    private void feed(ViewInput input) {
        synchronized (stepLock) {
            step(input);
        }
    }

    private void step(ViewInput input) {
        if (stopped) {
            return;
        }
        try {
            ViewStep step = view.step(input, clock.nowMillis());
            view = step.view();
            for (ViewEffect effect : step.effects()) {
                carryOut(effect);
            }
            settleWaitingReads();
        } catch (Throwable failure) {
            // Also an Error: the threads that feed include the NATS dispatcher's and the writers'.
            stopped = true;
            LOGGER.log(Level.SEVERE, "The network view failed to take " + input
                    + "; it answers no more reads, and they go over HTTP", failure);
            settleWaitingReads();
        }
    }

    private ReadRoute routeOf(NetworkView current, long startedAt, long now) {
        if (stopped) {
            return ReadRoute.OVER_HTTP;
        }
        return current.routeRead(startedAt, now);
    }

    private CompletableFuture<Optional<NetworkCopy>> waitForOwnWrite(long startedAt) {
        WaitingRead read = new WaitingRead(startedAt, new CompletableFuture<>());
        synchronized (stepLock) {
            waitingReads.add(read);
            // A step between this read's look at the view and the lock settled without it, and may have ended
            // its wait.
            settleWaitingReads();
        }
        return read.answer();
    }

    /**
     * Call under {@link #stepLock}.
     */
    private void settleWaitingReads() {
        long now = clock.nowMillis();
        Iterator<WaitingRead> waiting = waitingReads.iterator();
        while (waiting.hasNext()) {
            WaitingRead read = waiting.next();
            ReadRoute route = routeOf(view, read.startedAt(), now);
            switch (route) {
                case FROM_COPY -> {
                    waiting.remove();
                    answerOnOwnThread(read, view.copy());
                }
                case OVER_HTTP -> {
                    waiting.remove();
                    answerOnOwnThread(read, Optional.empty());
                }
                case WAIT -> {
                }
            }
        }
    }

    /**
     * The caller's continuation may block or start HTTP calls, so it must neither hold the lock nor stall the
     * thread that delivers batches or ticks.
     */
    private static void answerOnOwnThread(WaitingRead read, Optional<NetworkCopy> copy) {
        try {
            Thread.ofVirtual()
                    .name("simplecloud-network-view-read")
                    .start(() -> read.answer().complete(copy));
        } catch (RuntimeException | Error startFailed) {
            // The read is out of the list already, and a read never answered hangs its caller for good, while
            // a continuation on this thread only delays the next step.
            read.answer().complete(copy);
        }
    }

    private void carryOut(ViewEffect effect) {
        switch (effect) {
            case ViewEffect.LoadSnapshot load -> startSnapshotLoad();
            case ViewEffect.AuditFinished finished -> countAudit(finished.outcome());
        }
    }

    private void startSnapshotLoad() {
        CompletableFuture<NetworkState> load = snapshots.load();
        runningLoad = load;
        // On the timer, never in this step: a load that fails at once would step the view inside this step.
        load.whenCompleteAsync(this::feedLoadAnswer, timer);
    }

    private void countAudit(AuditOutcome outcome) {
        long count = auditCounts.merge(outcome, 1L, Long::sum);
        switch (outcome) {
            case DIFFERED -> LOGGER.warning("Network view audit: the copy differed from the snapshot at the same"
                    + " revision and was replaced (" + count + " times since the view started)");
            case NOT_REACHED -> LOGGER.warning("Network view audit: the copy did not reach the snapshot's revision"
                    + " in time and was replaced (" + count + " times since the view started)");
            case MATCHED, JUMPED_PAST -> LOGGER.fine("Network view audit: " + outcome + " ("
                    + count + " times since the view started)");
        }
    }

    private void closeNatsDispatcher() {
        try {
            connection.closeDispatcher(natsDispatcher);
        } catch (RuntimeException e) {
            LOGGER.log(Level.FINE, "Failed to close the network view's NATS dispatcher", e);
        }
    }
}
