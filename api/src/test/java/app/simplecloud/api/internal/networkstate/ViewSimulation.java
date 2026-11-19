package app.simplecloud.api.internal.networkstate;

import build.buf.gen.simplecloud.controller.v2.NetworkState;
import build.buf.gen.simplecloud.controller.v2.StateBatch;
import build.buf.gen.simplecloud.controller.v2.StatePulse;
import build.buf.gen.simplecloud.controller.v2.StateServer;
import build.buf.gen.simplecloud.controller.v2.StateServerMetrics;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Drives one view through a simulated network with faults, and checks the promise of the design (section
 * 3) after every step.
 *
 * <ul>
 *   <li>The model database ({@link ModelDatabase}) takes random writes and fails over to a standby that
 *   lost the last few changes, under a new epoch.</li>
 *   <li>The model relay ({@link ModelOwner}) publishes batches and pulses as section 4.2 says. Owners
 *   change; for a moment two of them relay, a new owner continues from the stored number, a network can
 *   have no owner, rounds can fail, a crash can skip storing the number, and a lost change log entry makes
 *   a batch with base = revision.</li>
 *   <li>The model wire loses, repeats and delays messages, so one owner's messages can overtake each
 *   other; it cuts the connection, loudly or silently, and right after a reconnect it can lose messages
 *   from other NATS servers. Snapshot loads fail.</li>
 *   <li>The shell hands everything to the view in arrival order, ticks every 100 ms, runs the loads the
 *   view asks for, and hands it the responses of this process's own writes.</li>
 * </ul>
 *
 * <p>Safety, checked after every step:
 * <ul>
 *   <li>a live view's copy equals the database at the copy's epoch and revision, a state that existed;</li>
 *   <li>a live view lacks no batch of its epoch published 30 s before, and is in the database's epoch from
 *   30 s after a failover;</li>
 *   <li>a read answered from the copy under 3 s after an own write returned holds that write, unless the
 *   database failed over after the write's commit;</li>
 *   <li>a read waits only while the last own write returned under 3 s ago;</li>
 *   <li>the view never runs two loads at once, or starts them less than 2 s apart.</li>
 * </ul>
 * Liveness: from 50 s after the faults stop, the view is live and has every batch published 1 s before,
 * and at the end its copy equals the database.
 */
final class ViewSimulation {

    private static final long TICK_MILLIS = 100;
    private static final long PULSE_INTERVAL_MILLIS = 5_000;
    private static final long ROUND_INTERVAL_MILLIS = 1_000;
    private static final long LOAD_SPACING_MILLIS = 2_000;
    /**
     * The longest back-off (30 s) and random delay (2 s), a load, and two pulse intervals, because a
     * snapshot can be ahead of the relay's last round; plus a few seconds for the owner's first round.
     */
    private static final long LIVENESS_BOUND_MILLIS = 50_000;
    private static final long CHANGE_REACHES_VIEW_MILLIS = 1_000;
    /**
     * How old a batch a live view may lack while faults happen: 15 s for its last good pulse to age, 5 s to
     * the next pulse, 3 s in which a former owner's pulses can still match the copy, and the wire's longest
     * delay of 2.5 s; the rest is margin.
     */
    private static final long LIVE_VIEW_LAGS_AT_MOST_MILLIS = 30_000;
    private static final long QUIET_END_MILLIS = 90_000;
    private static final long WRITES_STOP_BEFORE_END_MILLIS = 15_000;
    private static final long INTEREST_SPREAD_MILLIS = 100;
    private static final long OWN_WRITE_WAIT_MILLIS = 3_000;

    private static final int SHORT_RUNS = 400;
    private static final int LONG_RUNS = 8;
    private static final long LONG_RUN_FAULTS_MILLIS = 60 * 60_000;

    static final String SHORT_RUNS_CHECK = "simulator: safety and liveness in " + SHORT_RUNS
            + " seeded runs of 1 to 3 min of faults";
    static final String LONG_RUNS_CHECK = "simulator: safety and liveness in " + LONG_RUNS
            + " seeded runs of 60 min with sparse faults and audits";

    static List<ViewCheck> all() {
        return List.of(
                new ViewCheck(SHORT_RUNS_CHECK, starter -> runAll(starter, shortRuns())),
                new ViewCheck(LONG_RUNS_CHECK, starter -> runAll(starter, longRuns()))
        );
    }

    /**
     * One seeded run. Dense faults test the rules under stress. Sparse faults come about every 20 min, and
     * the wire then loses, repeats and delays nothing, and every revision read after a write succeeds, because
     * every reload moves the next audit 20 to 30 min away.
     */
    record RunPlan(long seed, long faultsEndAt, boolean sparseFaults) {
    }

    /**
     * What the runs exercised, so a test can show that the faults happen at all.
     */
    record Coverage(int steps, int liveSteps, int loads, int failovers, int ownerChanges, int connectionCuts,
                    Set<AuditOutcome> auditOutcomes) {

        Coverage plus(Coverage other) {
            Set<AuditOutcome> outcomes = EnumSet.noneOf(AuditOutcome.class);
            outcomes.addAll(auditOutcomes);
            outcomes.addAll(other.auditOutcomes);
            return new Coverage(steps + other.steps, liveSteps + other.liveSteps, loads + other.loads,
                    failovers + other.failovers, ownerChanges + other.ownerChanges,
                    connectionCuts + other.connectionCuts, outcomes);
        }
    }

    static List<RunPlan> shortRuns() {
        List<RunPlan> plans = new ArrayList<>();
        for (int run = 0; run < SHORT_RUNS; run++) {
            long faultsEndAt = 60_000 + (run * 7_919L) % 120_000;
            plans.add(new RunPlan(1_000 + run, faultsEndAt, false));
        }
        return plans;
    }

    static List<RunPlan> longRuns() {
        List<RunPlan> plans = new ArrayList<>();
        for (int run = 0; run < LONG_RUNS; run++) {
            plans.add(new RunPlan(9_000 + run, LONG_RUN_FAULTS_MILLIS, true));
        }
        return plans;
    }

    static Coverage runAll(ViewStarter starter, List<RunPlan> plans) {
        Coverage total = new Coverage(0, 0, 0, 0, 0, 0, EnumSet.noneOf(AuditOutcome.class));
        for (RunPlan plan : plans) {
            Coverage run = new ViewSimulation(starter, plan).run();
            total = total.plus(run);
        }
        return total;
    }

    private enum Link {
        UP,
        /** Messages are lost, but the client has not noticed yet. */
        SILENTLY_DOWN,
        DOWN,
        /** Reconnected; the flush that confirms the subscriptions has not returned yet. */
        RECONNECTED
    }

    private record Scheduled(long at, long order, Runnable action) {
    }

    /**
     * An owner and the NATS connection it publishes on.
     */
    private static final class OwnerSlot {
        final ModelOwner owner = new ModelOwner();
        final boolean onOtherNatsServer;
        boolean active = true;
        long lastDeliveryAt;

        OwnerSlot(boolean onOtherNatsServer) {
            this.onOtherNatsServer = onOtherNatsServer;
        }
    }

    private record Published(long at, long epoch, long revision) {
    }

    /**
     * The highest revision per epoch among the batches published at least {@code ageMillis} ago.
     */
    private static final class AgedRevisions {
        private final long ageMillis;
        private final Deque<Published> younger = new ArrayDeque<>();
        private final Map<Long, Long> highestByEpoch = new HashMap<>();

        AgedRevisions(long ageMillis) {
            this.ageMillis = ageMillis;
        }

        void add(Published batch) {
            younger.addLast(batch);
        }

        long highest(long now, long epoch) {
            while (!younger.isEmpty() && younger.peekFirst().at() <= now - ageMillis) {
                Published aged = younger.pollFirst();
                highestByEpoch.merge(aged.epoch(), aged.revision(), Math::max);
            }
            return highestByEpoch.getOrDefault(epoch, 0L);
        }
    }

    /**
     * An own write whose response reached the view, with the number of failovers before its commit.
     */
    private record ReturnedWrite(ModelDatabase.Commit commit, int failoversBeforeCommit, long returnedAt) {
    }

    private final RunPlan plan;
    private final Random random;
    private final ModelDatabase database;
    private final PriorityQueue<Scheduled> events = new PriorityQueue<>(
            Comparator.comparingLong(Scheduled::at).thenComparingLong(Scheduled::order));
    private final List<OwnerSlot> owners = new ArrayList<>();
    private final AgedRevisions batchesOlderThanLag = new AgedRevisions(LIVE_VIEW_LAGS_AT_MOST_MILLIS);
    private final AgedRevisions batchesOlderThanReach = new AgedRevisions(CHANGE_REACHES_VIEW_MILLIS);
    private final Deque<ReturnedWrite> writesReturnedUnderThreeSecondsAgo = new ArrayDeque<>();
    private Long lastOwnWriteReturnedAt;
    private final long endAt;
    private final long faultGapMillis;
    private final double lossChance;
    private final double repeatChance;
    private final double delayChance;
    private final double revisionReadFailureChance;
    private final double loadFailureChance;

    private NetworkView view;
    private long now;
    private long nextOrder;
    private Link link = Link.DOWN;
    private int linkCuts;
    private int connectionAttempt;
    private long interestSpreadsUntil;
    private long roundsFailUntil;
    private long epochChangedAt;
    private boolean loadRunning;
    private Long lastLoadStartedAt;
    private int steps;
    private int liveSteps;
    private int loads;
    private int failovers;
    private int ownerChanges;
    private int connectionCuts;
    private final Set<AuditOutcome> auditOutcomes = EnumSet.noneOf(AuditOutcome.class);

    private ViewSimulation(ViewStarter starter, RunPlan plan) {
        this.plan = plan;
        random = new Random(plan.seed());
        database = new ModelDatabase(random);
        view = starter.start(RandomSequence.seeded(plan.seed()));
        endAt = plan.faultsEndAt() + QUIET_END_MILLIS;
        double faultScale = plan.sparseFaults() ? 0 : 1;
        faultGapMillis = plan.sparseFaults() ? 20 * 60_000 : 2_000 + random.nextInt(25_000);
        lossChance = random.nextDouble() * 0.1 * faultScale;
        repeatChance = random.nextDouble() * 0.05 * faultScale;
        delayChance = random.nextDouble() * 0.05 * faultScale;
        revisionReadFailureChance = 0.1 * faultScale;
        loadFailureChance = random.nextDouble() * 0.4;
    }

    private Coverage run() {
        activateNewOwner(false);
        schedule(10, this::confirmSubscriptions);
        schedule(TICK_MILLIS, this::tick);
        schedule(nextWriteDelay(), this::write);
        schedule(nextFaultDelay(), this::injectFault);
        schedule(plan.faultsEndAt(), this::healEverything);
        schedule(endAt, this::checkEnd);
        while (!events.isEmpty()) {
            Scheduled next = events.poll();
            if (next.at() > endAt) {
                break;
            }
            now = next.at();
            next.action().run();
        }
        return new Coverage(steps, liveSteps, loads, failovers, ownerChanges, connectionCuts, auditOutcomes);
    }

    private boolean faultsAllowed() {
        return now < plan.faultsEndAt();
    }

    private void schedule(long delay, Runnable action) {
        events.add(new Scheduled(now + delay, nextOrder, action));
        nextOrder++;
    }

    // The shell.

    private void step(ViewInput input) {
        ViewStep result = view.step(input, now);
        view = result.view();
        steps++;
        for (ViewEffect effect : result.effects()) {
            carryOut(effect);
        }
        checkSafety();
        if (now >= plan.faultsEndAt() + LIVENESS_BOUND_MILLIS) {
            checkLiveAndCurrent();
        }
    }

    private void carryOut(ViewEffect effect) {
        switch (effect) {
            case ViewEffect.LoadSnapshot load -> startLoad();
            case ViewEffect.AuditFinished finished -> auditOutcomes.add(finished.outcome());
        }
    }

    private void tick() {
        step(new ViewInput.TimePassed());
        schedule(TICK_MILLIS, this::tick);
    }

    private void startLoad() {
        if (loadRunning) {
            failRun("the view asked for a load while one was running");
        }
        boolean tooSoon = lastLoadStartedAt != null && now - lastLoadStartedAt < LOAD_SPACING_MILLIS;
        if (tooSoon) {
            failRun("the view started loads " + (now - lastLoadStartedAt) + " ms apart");
        }
        loadRunning = true;
        lastLoadStartedAt = now;
        loads++;
        boolean fails = faultsAllowed() && random.nextDouble() < loadFailureChance;
        if (fails) {
            schedule(20 + random.nextInt(1_000), this::answerLoadWithFailure);
            return;
        }
        schedule(10 + random.nextInt(300), this::readSnapshot);
    }

    private void answerLoadWithFailure() {
        loadRunning = false;
        step(new ViewInput.SnapshotFailed());
    }

    private void readSnapshot() {
        NetworkState snapshot = snapshotOf(database.current());
        schedule(10 + random.nextInt(800), () -> answerLoad(snapshot));
    }

    private void answerLoad(NetworkState snapshot) {
        loadRunning = false;
        step(new ViewInput.SnapshotLoaded(snapshot));
    }

    private NetworkState snapshotOf(ModelDatabase.State state) {
        ModelDatabase.Items items = state.items();
        NetworkState.Builder snapshot = NetworkState.newBuilder()
                .setEpoch(state.epoch())
                .setRevision(state.counter())
                .addAllServers(items.servers().values())
                .addAllServerGroups(items.serverGroups().values())
                .addAllPersistentServers(items.persistentServers().values())
                .addAllBlueprints(items.blueprints().values());
        for (StateServer server : items.servers().values()) {
            StateServerMetrics metrics = StateServerMetrics.newBuilder()
                    .setServerId(server.getId())
                    .setPlayerCount(random.nextInt(50))
                    .build();
            snapshot.addMetrics(metrics);
        }
        return snapshot.build();
    }

    // The database and this process's own writes.

    private void write() {
        if (now < endAt - WRITES_STOP_BEFORE_END_MILLIS) {
            int failoversBeforeCommit = failovers;
            ModelDatabase.Commit commit = database.commitRandomWrite();
            notifyOwners();
            boolean ownWrite = random.nextInt(10) < 3;
            if (ownWrite) {
                schedule(random.nextInt(20), () -> readRevisionHeaders(commit, failoversBeforeCommit));
            }
        }
        schedule(nextWriteDelay(), this::write);
    }

    /**
     * Writes come in bursts now and then, so one relay round can cover several revisions.
     */
    private long nextWriteDelay() {
        boolean burst = random.nextInt(5) == 0;
        if (burst) {
            return 1 + random.nextInt(20);
        }
        return 50 + random.nextInt(1_400);
    }

    /**
     * The controller reads the headers after the commit; the response then takes its own time, so the
     * responses of two writes can arrive in the other order. In dense faults, one read in ten fails, and
     * the response comes without the headers, so the view gets nothing.
     */
    private void readRevisionHeaders(ModelDatabase.Commit commit, int failoversBeforeCommit) {
        ModelDatabase.State afterCommit = database.current();
        boolean readFailed = faultsAllowed() && random.nextDouble() < revisionReadFailureChance;
        if (readFailed) {
            return;
        }
        ViewInput.WriteReturned response = new ViewInput.WriteReturned(afterCommit.epoch(), afterCommit.counter());
        schedule(5 + random.nextInt(300), () -> returnOwnWrite(commit, failoversBeforeCommit, response));
    }

    private void returnOwnWrite(ModelDatabase.Commit commit, int failoversBeforeCommit,
                                ViewInput.WriteReturned response) {
        writesReturnedUnderThreeSecondsAgo.addLast(new ReturnedWrite(commit, failoversBeforeCommit, now));
        lastOwnWriteReturnedAt = now;
        step(response);
    }

    // The relay.

    private void activateNewOwner(boolean onOtherNatsServer) {
        OwnerSlot slot = new OwnerSlot(onOtherNatsServer);
        owners.add(slot);
        schedule(random.nextInt((int) ROUND_INTERVAL_MILLIS), () -> roundLoop(slot));
        schedule(random.nextInt((int) PULSE_INTERVAL_MILLIS), () -> pulseLoop(slot));
    }

    private void notifyOwners() {
        for (OwnerSlot slot : owners) {
            if (slot.active) {
                schedule(1 + random.nextInt(30), () -> runRound(slot));
            }
        }
    }

    private void roundLoop(OwnerSlot slot) {
        if (!slot.active) {
            return;
        }
        runRound(slot);
        schedule(ROUND_INTERVAL_MILLIS, () -> roundLoop(slot));
    }

    private void runRound(OwnerSlot slot) {
        boolean roundFails = !slot.active || now < roundsFailUntil;
        if (roundFails) {
            return;
        }
        ModelDatabase.State read = database.current();
        StateBatch batch = slot.owner.runRound(read, now);
        if (batch == null) {
            return;
        }
        Published published = new Published(now, batch.getEpoch(), batch.getRevision());
        batchesOlderThanLag.add(published);
        batchesOlderThanReach.add(published);
        publish(slot, new ViewInput.BatchReceived(batch));
        boolean crashBeforeStore = faultsAllowed() && random.nextInt(20) == 0;
        if (!crashBeforeStore) {
            schedule(5 + random.nextInt(45), () -> database.storeLastRelayed(batch.getEpoch(), batch.getRevision()));
        }
    }

    private void pulseLoop(OwnerSlot slot) {
        if (!slot.active) {
            return;
        }
        StatePulse pulse = slot.owner.pulse(database.current(), now, random);
        if (pulse != null) {
            publish(slot, new ViewInput.PulseReceived(pulse));
        }
        schedule(PULSE_INTERVAL_MILLIS, () -> pulseLoop(slot));
    }

    // The wire.

    private void publish(OwnerSlot slot, ViewInput message) {
        boolean linkCarries = link == Link.UP || link == Link.RECONNECTED;
        if (!linkCarries) {
            return;
        }
        if (faultsAllowed() && random.nextDouble() < lossChance) {
            return;
        }
        int cutsAtSend = linkCuts;
        long latency = 1 + random.nextInt(40);
        boolean delayed = faultsAllowed() && random.nextDouble() < delayChance;
        if (delayed) {
            long overtakableDelay = latency + random.nextInt(2_500);
            schedule(overtakableDelay, () -> deliver(slot, message, cutsAtSend));
        } else {
            long deliverAt = Math.max(slot.lastDeliveryAt, now + latency);
            slot.lastDeliveryAt = deliverAt;
            schedule(deliverAt - now, () -> deliver(slot, message, cutsAtSend));
        }
        boolean repeated = faultsAllowed() && random.nextDouble() < repeatChance;
        if (repeated) {
            schedule(latency + random.nextInt(3_000), () -> deliver(slot, message, cutsAtSend));
        }
    }

    private void deliver(OwnerSlot slot, ViewInput message, int cutsAtSend) {
        boolean linkCarries = link == Link.UP || link == Link.RECONNECTED;
        boolean cutInFlight = cutsAtSend != linkCuts;
        if (!linkCarries || cutInFlight) {
            return;
        }
        boolean interestNotSpread = slot.onOtherNatsServer && now < interestSpreadsUntil && random.nextBoolean();
        if (interestNotSpread) {
            return;
        }
        step(message);
    }

    private void confirmSubscriptions() {
        link = Link.UP;
        interestSpreadsUntil = now + INTEREST_SPREAD_MILLIS;
        step(new ViewInput.SubscriptionsActive());
    }

    // Faults.

    private long nextFaultDelay() {
        return 1 + (long) (-faultGapMillis * Math.log(1 - random.nextDouble()));
    }

    private void injectFault() {
        if (!faultsAllowed()) {
            return;
        }
        int roll = random.nextInt(100);
        if (roll < 25) {
            cutConnection();
        } else if (roll < 45) {
            handOverOwnership();
        } else if (roll < 55) {
            leaveWithoutOwner();
        } else if (roll < 67) {
            failOverDatabase();
        } else if (roll < 77) {
            failRelayRounds();
        } else if (roll < 85) {
            database.loseChangeLogEntry();
        } else {
            cutConnectionSilently();
        }
        schedule(nextFaultDelay(), this::injectFault);
    }

    private void failOverDatabase() {
        database.failOver();
        epochChangedAt = now;
        failovers++;
    }

    private void failRelayRounds() {
        roundsFailUntil = now + 1_000 + random.nextInt(20_000);
    }

    private void cutConnection() {
        if (link != Link.UP) {
            return;
        }
        connectionCuts++;
        link = Link.DOWN;
        linkCuts++;
        step(new ViewInput.ConnectionLost());
        scheduleReconnect(200 + random.nextInt(15_000));
    }

    private void cutConnectionSilently() {
        if (link != Link.UP) {
            return;
        }
        connectionCuts++;
        link = Link.SILENTLY_DOWN;
        linkCuts++;
        int attempt = connectionAttempt;
        schedule(1_000 + random.nextInt(40_000), () -> noticeSilentCut(attempt));
    }

    private void noticeSilentCut(int attempt) {
        if (attempt != connectionAttempt) {
            return;
        }
        link = Link.DOWN;
        step(new ViewInput.ConnectionLost());
        scheduleReconnect(200 + random.nextInt(5_000));
    }

    private void scheduleReconnect(long delay) {
        int attempt = connectionAttempt;
        schedule(delay, () -> reconnect(attempt));
    }

    private void reconnect(int attempt) {
        if (attempt != connectionAttempt) {
            return;
        }
        link = Link.RECONNECTED;
        schedule(5 + random.nextInt(200), () -> confirmSubscriptionsAfterReconnect(attempt));
    }

    private void confirmSubscriptionsAfterReconnect(int attempt) {
        if (attempt != connectionAttempt) {
            return;
        }
        confirmSubscriptions();
    }

    private void handOverOwnership() {
        List<OwnerSlot> previous = activeOwners();
        activateNewOwner(random.nextBoolean());
        ownerChanges++;
        long overlap = random.nextInt(3_000);
        schedule(overlap, () -> deactivate(previous));
    }

    private void leaveWithoutOwner() {
        deactivate(activeOwners());
        ownerChanges++;
        schedule(5_000 + random.nextInt(35_000), this::activateOwnerIfNone);
    }

    private void activateOwnerIfNone() {
        if (activeOwners().isEmpty()) {
            activateNewOwner(random.nextBoolean());
        }
    }

    private List<OwnerSlot> activeOwners() {
        List<OwnerSlot> active = new ArrayList<>();
        for (OwnerSlot slot : owners) {
            if (slot.active) {
                active.add(slot);
            }
        }
        return active;
    }

    private static void deactivate(List<OwnerSlot> slots) {
        for (OwnerSlot slot : slots) {
            slot.active = false;
        }
    }

    /**
     * Ends every fault at once: the connection comes back, one owner relays, and rounds work.
     */
    private void healEverything() {
        roundsFailUntil = 0;
        List<OwnerSlot> active = activeOwners();
        if (active.isEmpty()) {
            activateNewOwner(false);
        } else {
            deactivate(active.subList(0, active.size() - 1));
        }
        if (link == Link.UP) {
            return;
        }
        connectionAttempt++;
        if (link == Link.SILENTLY_DOWN) {
            link = Link.DOWN;
            step(new ViewInput.ConnectionLost());
        }
        scheduleReconnect(50);
    }

    // The promise.

    private void checkSafety() {
        boolean live = isLive();
        if (live) {
            liveSteps++;
            checkCopyIsRealState();
            checkLiveCopyIsRecent();
        }
        forgetWritesReturnedThreeSecondsAgo();
        checkOwnWritesAreSeen();
        checkWaitEndsThreeSecondsAfterLastWrite();
    }

    /**
     * A read that starts now goes over HTTP exactly while the view is not live or the flag is false, and every
     * pulse here allows answers.
     */
    private boolean isLive() {
        return view.routeRead(now) != ReadRoute.OVER_HTTP;
    }

    private void checkLiveCopyIsRecent() {
        NetworkCopy copy = copyOrFail("a live view holds no copy");
        boolean oldEpoch = copy.epoch() != database.current().epoch();
        if (oldEpoch && now - epochChangedAt >= LIVE_VIEW_LAGS_AT_MOST_MILLIS) {
            failRun("the live copy is still in epoch " + copy.epoch() + " over 30 s after the failover");
        }
        long missedRevision = batchesOlderThanLag.highest(now, copy.epoch());
        if (copy.revision() < missedRevision) {
            failRun("the live copy is at revision " + copy.revision() + ", but revision " + missedRevision
                    + " of its epoch was published over 30 s ago");
        }
    }

    private void checkCopyIsRealState() {
        NetworkCopy copy = copyOrFail("a live view holds no copy");
        ModelDatabase.Items expected = database.itemsAt(copy.epoch(), copy.revision());
        if (expected == null) {
            failRun("the live copy is at revision " + copy.revision() + " of epoch " + copy.epoch()
                    + ", a state that never existed");
        }
        boolean equal = expected.equals(ModelDatabase.Items.of(copy));
        if (!equal) {
            failRun("the live copy differs from the database at revision " + copy.revision() + " of epoch "
                    + copy.epoch());
        }
    }

    private void forgetWritesReturnedThreeSecondsAgo() {
        while (!writesReturnedUnderThreeSecondsAgo.isEmpty()
                && now - writesReturnedUnderThreeSecondsAgo.peekFirst().returnedAt() >= OWN_WRITE_WAIT_MILLIS) {
            writesReturnedUnderThreeSecondsAgo.removeFirst();
        }
    }

    private void checkOwnWritesAreSeen() {
        boolean answeredFromCopy = view.routeRead(now) == ReadRoute.FROM_COPY;
        if (!answeredFromCopy) {
            return;
        }
        NetworkCopy copy = copyOrFail("a view that may answer holds no copy");
        for (ReturnedWrite write : writesReturnedUnderThreeSecondsAgo) {
            checkCopyHoldsWrite(copy, write);
        }
    }

    /**
     * Without a failover since the commit, the commit's epoch is the only one with revisions after it.
     */
    private void checkCopyHoldsWrite(NetworkCopy copy, ReturnedWrite write) {
        boolean historyCutSinceCommit = failovers != write.failoversBeforeCommit();
        if (historyCutSinceCommit) {
            return;
        }
        ModelDatabase.Commit commit = write.commit();
        boolean holdsWrite = copy.epoch() == commit.epoch() && copy.revision() >= commit.revision();
        if (!holdsWrite) {
            failRun("the copy at revision " + copy.revision() + " of epoch " + copy.epoch() + " answers a read "
                    + (now - write.returnedAt()) + " ms after an own write at revision " + commit.revision()
                    + " of epoch " + commit.epoch() + " returned");
        }
    }

    private void checkWaitEndsThreeSecondsAfterLastWrite() {
        boolean waits = view.routeRead(now) == ReadRoute.WAIT;
        if (!waits) {
            return;
        }
        boolean underThreeSecondsAfterWrite = lastOwnWriteReturnedAt != null
                && now - lastOwnWriteReturnedAt < OWN_WRITE_WAIT_MILLIS;
        if (!underThreeSecondsAfterWrite) {
            failRun("a read waits, but the last own write returned at " + lastOwnWriteReturnedAt + " ms");
        }
    }

    private void checkLiveAndCurrent() {
        long sinceFaultsEnded = now - plan.faultsEndAt();
        if (!isLive()) {
            failRun("the view is not live " + sinceFaultsEnded + " ms after the faults stopped");
        }
        NetworkCopy copy = copyOrFail("a live view holds no copy");
        long epoch = database.current().epoch();
        if (copy.epoch() != epoch) {
            failRun("the copy is in epoch " + copy.epoch() + " " + sinceFaultsEnded
                    + " ms after the faults stopped, the database in " + epoch);
        }
        long reachableRevision = batchesOlderThanReach.highest(now, epoch);
        if (copy.revision() < reachableRevision) {
            failRun("the copy is at revision " + copy.revision() + ", but revision " + reachableRevision
                    + " was published over 1 s ago");
        }
    }

    private void checkEnd() {
        step(new ViewInput.TimePassed());
        NetworkCopy copy = copyOrFail("the view holds no copy at the end");
        ModelDatabase.State state = database.current();
        boolean current = copy.epoch() == state.epoch()
                && copy.revision() == state.counter()
                && state.items().equals(ModelDatabase.Items.of(copy));
        if (!current) {
            failRun("at the end the copy is at revision " + copy.revision() + " of epoch " + copy.epoch()
                    + ", the database at " + state.counter() + " of " + state.epoch());
        }
    }

    private NetworkCopy copyOrFail(String message) {
        return view.copy().orElseGet(() -> failRun(message));
    }

    private <T> T failRun(String message) {
        return fail("seed " + plan.seed() + " at " + now + " ms: " + message);
    }
}
