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
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Drives one view through a simulated network with faults, and checks the promises of {@link NetworkView} after
 * every step.
 *
 * <ul>
 *   <li>The model database ({@link ModelDatabase}) takes random writes and fails over to a standby that
 *   lost the last few changes, under a new epoch.</li>
 *   <li>The model relay ({@link ModelOwner}) publishes batches and pulses. Owners change; for a moment two of
 *   them relay, a new owner continues from the stored number, a network can have no owner, rounds can fail, a
 *   crash can skip storing the number, and a lost change log entry makes a batch with base = revision.</li>
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
 *   <li>a live view lacks no batch of its epoch published about 30 s before, and is in the database's epoch
 *   from about 30 s after a failover;</li>
 *   <li>a read answered from the copy under 3 s after an own write returned holds that write, unless the
 *   database failed over after the write's commit;</li>
 *   <li>a read waits only while the last own write returned under 3 s ago;</li>
 *   <li>the view never runs two loads at once, or starts them less than 2 s apart.</li>
 * </ul>
 * Liveness: from about 50 s after the faults stop, the view is live and has every batch published 1 s before,
 * and at the end its copy equals the database.
 *
 * <p>The runs never switch answers off, since every pulse allows them, and revisions never reach 2^63, since the
 * counter starts at 0. The example checks in {@link ViewRuleChecks} cover both.
 */
final class ViewSimulation {

    /**
     * Every kind of fault a run injects. Some come one at a time, picked by {@link #SCHEDULED_FAULT_SHARES};
     * the others are drawn for each message, load, revision read or relay round.
     */
    enum Fault {
        CONNECTION_CUT,
        SILENT_CONNECTION_CUT,
        OWNER_HANDOVER,
        NO_OWNER,
        DATABASE_FAILOVER,
        FAILED_RELAY_ROUNDS,
        LOST_CHANGE_LOG_ENTRY,
        LOST_MESSAGE,
        REPEATED_MESSAGE,
        DELAYED_MESSAGE,
        MESSAGE_LOST_BEFORE_INTEREST_SPREAD,
        FAILED_LOAD,
        FAILED_REVISION_READ,
        CRASH_BEFORE_STORE
    }

    private record FaultShare(Fault fault, int percent) {
    }

    /**
     * A random delay of {@code shortest} plus up to {@code spread - 1} ms.
     */
    private record RandomDelay(long shortest, int spread) {

        long draw(Random random) {
            return shortest + random.nextInt(spread);
        }

        long longest() {
            return shortest + spread - 1;
        }
    }

    private static final List<FaultShare> SCHEDULED_FAULT_SHARES = List.of(
            new FaultShare(Fault.CONNECTION_CUT, 25),
            new FaultShare(Fault.OWNER_HANDOVER, 20),
            new FaultShare(Fault.NO_OWNER, 10),
            new FaultShare(Fault.DATABASE_FAILOVER, 12),
            new FaultShare(Fault.FAILED_RELAY_ROUNDS, 10),
            new FaultShare(Fault.LOST_CHANGE_LOG_ENTRY, 8),
            new FaultShare(Fault.SILENT_CONNECTION_CUT, 15));
    private static final double MOST_LOSS_CHANCE = 0.1;
    private static final double MOST_REPEAT_CHANCE = 0.05;
    private static final double MOST_DELAY_CHANCE = 0.05;
    private static final double REVISION_READ_FAILURE_CHANCE = 0.1;
    private static final double MOST_LOAD_FAILURE_CHANCE = 0.4;
    private static final int CRASH_BEFORE_STORE_ONE_IN = 20;
    private static final int OWN_WRITES_IN_TEN = 3;
    private static final int WRITE_BURST_ONE_IN = 5;
    static final int MOST_PLAYERS = 50;

    private static final long TICK_MILLIS = 100;
    private static final long ROUND_INTERVAL_MILLIS = 1_000;
    private static final long FIRST_FLUSH_MILLIS = 10;
    private static final long HEALED_RECONNECT_MILLIS = 50;
    private static final long INTEREST_SPREAD_MILLIS = 100;
    private static final long SPARSE_FAULT_GAP_MILLIS = 20 * 60_000;
    /**
     * Drawn once per dense run: the mean gap between its scheduled faults.
     */
    private static final RandomDelay DENSE_FAULT_GAP = new RandomDelay(2_000, 25_000);
    private static final RandomDelay SNAPSHOT_READ = new RandomDelay(10, 300);
    private static final RandomDelay SNAPSHOT_ANSWER = new RandomDelay(10, 800);
    private static final RandomDelay FAILED_LOAD_ANSWER = new RandomDelay(20, 1_000);
    private static final RandomDelay WRITE_GAP = new RandomDelay(50, 1_400);
    private static final RandomDelay WRITE_GAP_IN_BURST = new RandomDelay(1, 20);
    private static final RandomDelay REVISION_READ_AFTER_COMMIT = new RandomDelay(0, 20);
    private static final RandomDelay WRITE_RESPONSE = new RandomDelay(5, 300);
    private static final RandomDelay FIRST_ROUND = new RandomDelay(0, (int) ROUND_INTERVAL_MILLIS);
    private static final RandomDelay FIRST_PULSE = new RandomDelay(0, (int) ModelOwner.PULSE_INTERVAL_MILLIS);
    private static final RandomDelay ROUND_AFTER_WRITE = new RandomDelay(1, 30);
    private static final RandomDelay STORE_AFTER_ROUND = new RandomDelay(5, 45);
    private static final RandomDelay WIRE_LATENCY = new RandomDelay(1, 40);
    /**
     * Comes on top of the latency, and lets a message overtake the ones sent after it.
     */
    private static final RandomDelay EXTRA_DELAY = new RandomDelay(0, 2_500);
    private static final RandomDelay REPEAT_AFTER_LATENCY = new RandomDelay(0, 3_000);
    private static final RandomDelay RECONNECT_AFTER_CUT = new RandomDelay(200, 15_000);
    private static final RandomDelay SILENT_CUT_NOTICED = new RandomDelay(1_000, 40_000);
    private static final RandomDelay RECONNECT_AFTER_NOTICE = new RandomDelay(200, 5_000);
    private static final RandomDelay FLUSH_AFTER_RECONNECT = new RandomDelay(5, 200);
    private static final RandomDelay OWNER_OVERLAP = new RandomDelay(0, 3_000);
    private static final RandomDelay WITHOUT_OWNER = new RandomDelay(5_000, 35_000);
    private static final RandomDelay FAILING_ROUNDS = new RandomDelay(1_000, 20_000);

    private static final long LONGEST_LOAD_MILLIS = SNAPSHOT_READ.longest() + SNAPSHOT_ANSWER.longest();
    private static final long NEW_OWNER_MARGIN_MILLIS = 7_000;
    /**
     * After the faults stop: the longest back-off and random delay before a reload, the longest load, and two
     * pulse intervals, because a snapshot can be ahead of the relay's last round; then a margin for a new
     * owner's first round.
     */
    private static final long LIVENESS_BOUND_MILLIS = RuleTimes.LONGEST_BACK_OFF + RuleTimes.RANDOM_LOAD_DELAY_BOUND
            + LONGEST_LOAD_MILLIS + 2 * ModelOwner.PULSE_INTERVAL_MILLIS + NEW_OWNER_MARGIN_MILLIS;
    private static final long CHANGE_REACHES_VIEW_MILLIS = 1_000;
    private static final long LAG_MARGIN_MILLIS = 4_500;
    /**
     * How old a batch a live view may lack while faults happen: its last good pulse ages, the next pulse comes,
     * a former owner's pulses can still match the copy while two owners relay, and the wire delays a message as
     * long as it can; then a margin.
     */
    private static final long LIVE_VIEW_LAGS_AT_MOST_MILLIS = RuleTimes.GOOD_PULSE_LIFETIME
            + ModelOwner.PULSE_INTERVAL_MILLIS + OWNER_OVERLAP.longest() + WIRE_LATENCY.longest()
            + EXTRA_DELAY.longest() + LAG_MARGIN_MILLIS;
    private static final long QUIET_END_MILLIS = 90_000;
    private static final long WRITES_STOP_BEFORE_END_MILLIS = 15_000;

    private static final int SHORT_RUNS = 400;
    private static final long FIRST_SHORT_RUN_SEED = 1_000;
    private static final long SHORTEST_FAULTS_MILLIS = 60_000;
    private static final long FAULTS_SPREAD_MILLIS = 120_000;
    /**
     * A prime, so the runs' fault lengths spread over the whole range.
     */
    private static final long FAULTS_LENGTH_STEP_MILLIS = 7_919;
    private static final int LONG_RUNS = 8;
    private static final long FIRST_LONG_RUN_SEED = 9_000;
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
     * One seeded run. Faults happen until {@code faultsEndAt}, then the run goes on without them for 90 s.
     *
     * <p>With dense faults, a scheduled fault comes every few seconds, and the wire loses, repeats and delays
     * messages, and revision reads fail. With sparse faults, a scheduled fault comes about every 20 min, and
     * nothing else fails but loads and the relay's stores. Sparse runs exist for the audits: every reload moves
     * the next audit 20 to 30 min away, so under dense faults an audit hardly ever comes.
     */
    record RunPlan(long seed, long faultsEndAt, boolean sparseFaults) {
    }

    /**
     * What the runs exercised, so a test can show that the faults happen at all.
     */
    record Coverage(int steps, int liveSteps, int loads, Map<Fault, Integer> faults,
                    Set<AuditOutcome> auditOutcomes) {

        Coverage {
            faults = Map.copyOf(faults);
            auditOutcomes = Set.copyOf(auditOutcomes);
        }

        static Coverage none() {
            return new Coverage(0, 0, 0, Map.of(), Set.of());
        }

        int count(Fault fault) {
            return faults.getOrDefault(fault, 0);
        }

        Coverage plus(Coverage other) {
            Map<Fault, Integer> summedFaults = new EnumMap<>(Fault.class);
            summedFaults.putAll(faults);
            other.faults.forEach((fault, count) -> summedFaults.merge(fault, count, Integer::sum));
            Set<AuditOutcome> outcomes = EnumSet.noneOf(AuditOutcome.class);
            outcomes.addAll(auditOutcomes);
            outcomes.addAll(other.auditOutcomes);
            return new Coverage(steps + other.steps, liveSteps + other.liveSteps, loads + other.loads,
                    summedFaults, outcomes);
        }
    }

    static List<RunPlan> shortRuns() {
        List<RunPlan> plans = new ArrayList<>();
        for (int run = 0; run < SHORT_RUNS; run++) {
            long faultsEndAt = SHORTEST_FAULTS_MILLIS + (run * FAULTS_LENGTH_STEP_MILLIS) % FAULTS_SPREAD_MILLIS;
            plans.add(new RunPlan(FIRST_SHORT_RUN_SEED + run, faultsEndAt, false));
        }
        return plans;
    }

    static List<RunPlan> longRuns() {
        List<RunPlan> plans = new ArrayList<>();
        for (int run = 0; run < LONG_RUNS; run++) {
            plans.add(new RunPlan(FIRST_LONG_RUN_SEED + run, LONG_RUN_FAULTS_MILLIS, true));
        }
        return plans;
    }

    static Coverage runAll(ViewStarter starter, List<RunPlan> plans) {
        Coverage total = Coverage.none();
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
    /**
     * Raised when the run heals, so that a reconnect or a noticed cut scheduled before it does nothing.
     */
    private int reconnectGeneration;
    private long interestSpreadsUntil;
    private long roundsFailUntil;
    private long epochChangedAt;
    private boolean loadRunning;
    private Long lastLoadStartedAt;
    private int steps;
    private int liveSteps;
    private int loads;
    private final Map<Fault, Integer> faults = new EnumMap<>(Fault.class);
    private final Set<AuditOutcome> auditOutcomes = EnumSet.noneOf(AuditOutcome.class);

    private ViewSimulation(ViewStarter starter, RunPlan plan) {
        this.plan = plan;
        random = new Random(plan.seed());
        database = new ModelDatabase(random);
        view = starter.start(RandomSequence.seeded(plan.seed()));
        endAt = plan.faultsEndAt() + QUIET_END_MILLIS;
        double faultScale = plan.sparseFaults() ? 0 : 1;
        faultGapMillis = plan.sparseFaults() ? SPARSE_FAULT_GAP_MILLIS : DENSE_FAULT_GAP.draw(random);
        lossChance = random.nextDouble() * MOST_LOSS_CHANCE * faultScale;
        repeatChance = random.nextDouble() * MOST_REPEAT_CHANCE * faultScale;
        delayChance = random.nextDouble() * MOST_DELAY_CHANCE * faultScale;
        revisionReadFailureChance = REVISION_READ_FAILURE_CHANCE * faultScale;
        loadFailureChance = random.nextDouble() * MOST_LOAD_FAILURE_CHANCE;
    }

    private Coverage run() {
        activateNewOwner(false);
        schedule(FIRST_FLUSH_MILLIS, this::confirmSubscriptions);
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
        return new Coverage(steps, liveSteps, loads, faults, auditOutcomes);
    }

    private boolean faultsAllowed() {
        return now < plan.faultsEndAt();
    }

    private void schedule(long delay, Runnable action) {
        events.add(new Scheduled(now + delay, nextOrder, action));
        nextOrder++;
    }

    private void count(Fault fault) {
        faults.merge(fault, 1, Integer::sum);
    }

    private int countOf(Fault fault) {
        return faults.getOrDefault(fault, 0);
    }

    /**
     * A message sent before a cut never arrives, also once the link is back.
     */
    private int connectionCuts() {
        return countOf(Fault.CONNECTION_CUT) + countOf(Fault.SILENT_CONNECTION_CUT);
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
        boolean tooSoon = lastLoadStartedAt != null && now - lastLoadStartedAt < RuleTimes.LOAD_SPACING;
        if (tooSoon) {
            failRun("the view started loads " + (now - lastLoadStartedAt) + " ms apart");
        }
        loadRunning = true;
        lastLoadStartedAt = now;
        loads++;
        boolean fails = faultsAllowed() && random.nextDouble() < loadFailureChance;
        if (fails) {
            count(Fault.FAILED_LOAD);
            schedule(FAILED_LOAD_ANSWER.draw(random), this::answerLoadWithFailure);
            return;
        }
        schedule(SNAPSHOT_READ.draw(random), this::readSnapshot);
    }

    private void answerLoadWithFailure() {
        loadRunning = false;
        step(new ViewInput.SnapshotFailed());
    }

    private void readSnapshot() {
        NetworkState snapshot = snapshotOf(database.current());
        schedule(SNAPSHOT_ANSWER.draw(random), () -> answerLoad(snapshot));
    }

    private void answerLoad(NetworkState snapshot) {
        loadRunning = false;
        step(new ViewInput.SnapshotLoaded(snapshot));
    }

    private NetworkState snapshotOf(ModelDatabase.State state) {
        ModelDatabase.Items items = state.items();
        NetworkState.Builder snapshot = NetworkState.newBuilder()
                .setEpoch(state.epoch())
                .setRevision(state.headRevision())
                .addAllServers(items.servers().values())
                .addAllServerGroups(items.serverGroups().values())
                .addAllPersistentServers(items.persistentServers().values())
                .addAllBlueprints(items.blueprints().values());
        for (StateServer server : items.servers().values()) {
            StateServerMetrics metrics = StateServerMetrics.newBuilder()
                    .setServerId(server.getId())
                    .setPlayerCount(random.nextInt(MOST_PLAYERS))
                    .build();
            snapshot.addMetrics(metrics);
        }
        return snapshot.build();
    }

    // The database and this process's own writes.

    private void write() {
        if (now < endAt - WRITES_STOP_BEFORE_END_MILLIS) {
            int failoversBeforeCommit = countOf(Fault.DATABASE_FAILOVER);
            ModelDatabase.Commit commit = database.commitRandomWrite();
            notifyOwners();
            boolean ownWrite = random.nextInt(10) < OWN_WRITES_IN_TEN;
            if (ownWrite) {
                schedule(REVISION_READ_AFTER_COMMIT.draw(random),
                        () -> readRevisionHeaders(commit, failoversBeforeCommit));
            }
        }
        schedule(nextWriteDelay(), this::write);
    }

    /**
     * Writes come in bursts now and then, so one relay round can cover several revisions.
     */
    private long nextWriteDelay() {
        boolean burst = random.nextInt(WRITE_BURST_ONE_IN) == 0;
        if (burst) {
            return WRITE_GAP_IN_BURST.draw(random);
        }
        return WRITE_GAP.draw(random);
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
            count(Fault.FAILED_REVISION_READ);
            return;
        }
        ViewInput.WriteReturned response =
                new ViewInput.WriteReturned(afterCommit.epoch(), afterCommit.headRevision());
        schedule(WRITE_RESPONSE.draw(random), () -> returnOwnWrite(commit, failoversBeforeCommit, response));
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
        schedule(FIRST_ROUND.draw(random), () -> roundLoop(slot));
        schedule(FIRST_PULSE.draw(random), () -> pulseLoop(slot));
    }

    private void notifyOwners() {
        for (OwnerSlot slot : owners) {
            if (slot.active) {
                schedule(ROUND_AFTER_WRITE.draw(random), () -> runRound(slot));
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
        boolean crashBeforeStore = faultsAllowed() && random.nextInt(CRASH_BEFORE_STORE_ONE_IN) == 0;
        if (crashBeforeStore) {
            count(Fault.CRASH_BEFORE_STORE);
            return;
        }
        schedule(STORE_AFTER_ROUND.draw(random),
                () -> database.storeLastRelayed(batch.getEpoch(), batch.getRevision()));
    }

    private void pulseLoop(OwnerSlot slot) {
        if (!slot.active) {
            return;
        }
        StatePulse pulse = slot.owner.pulse(database.current(), now, random);
        if (pulse != null) {
            publish(slot, new ViewInput.PulseReceived(pulse));
        }
        schedule(ModelOwner.PULSE_INTERVAL_MILLIS, () -> pulseLoop(slot));
    }

    // The wire.

    private void publish(OwnerSlot slot, ViewInput message) {
        boolean linkCarries = link == Link.UP || link == Link.RECONNECTED;
        if (!linkCarries) {
            return;
        }
        if (faultsAllowed() && random.nextDouble() < lossChance) {
            count(Fault.LOST_MESSAGE);
            return;
        }
        int cutsAtSend = connectionCuts();
        long latency = WIRE_LATENCY.draw(random);
        boolean delayed = faultsAllowed() && random.nextDouble() < delayChance;
        if (delayed) {
            count(Fault.DELAYED_MESSAGE);
            long overtakableDelay = latency + EXTRA_DELAY.draw(random);
            schedule(overtakableDelay, () -> deliver(slot, message, cutsAtSend));
        } else {
            long deliverAt = Math.max(slot.lastDeliveryAt, now + latency);
            slot.lastDeliveryAt = deliverAt;
            schedule(deliverAt - now, () -> deliver(slot, message, cutsAtSend));
        }
        boolean repeated = faultsAllowed() && random.nextDouble() < repeatChance;
        if (repeated) {
            count(Fault.REPEATED_MESSAGE);
            schedule(latency + REPEAT_AFTER_LATENCY.draw(random), () -> deliver(slot, message, cutsAtSend));
        }
    }

    private void deliver(OwnerSlot slot, ViewInput message, int cutsAtSend) {
        boolean linkCarries = link == Link.UP || link == Link.RECONNECTED;
        boolean cutInFlight = cutsAtSend != connectionCuts();
        if (!linkCarries || cutInFlight) {
            return;
        }
        boolean interestNotSpread = slot.onOtherNatsServer && now < interestSpreadsUntil && random.nextBoolean();
        if (interestNotSpread) {
            count(Fault.MESSAGE_LOST_BEFORE_INTEREST_SPREAD);
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

    /**
     * Exponential gaps, with {@code faultGapMillis} as their mean.
     */
    private long nextFaultDelay() {
        return 1 + (long) (-faultGapMillis * Math.log(1 - random.nextDouble()));
    }

    private void injectFault() {
        if (!faultsAllowed()) {
            return;
        }
        Fault fault = scheduledFaultFor(random.nextInt(100));
        switch (fault) {
            case CONNECTION_CUT -> cutConnection();
            case OWNER_HANDOVER -> handOverOwnership();
            case NO_OWNER -> leaveWithoutOwner();
            case DATABASE_FAILOVER -> failOverDatabase();
            case FAILED_RELAY_ROUNDS -> failRelayRounds();
            case LOST_CHANGE_LOG_ENTRY -> loseChangeLogEntry();
            case SILENT_CONNECTION_CUT -> cutConnectionSilently();
            default -> throw new IllegalStateException(fault + " has no share of the scheduled faults");
        }
        schedule(nextFaultDelay(), this::injectFault);
    }

    private static Fault scheduledFaultFor(int percentRoll) {
        int sharesUpTo = 0;
        for (FaultShare share : SCHEDULED_FAULT_SHARES) {
            sharesUpTo += share.percent();
            if (percentRoll < sharesUpTo) {
                return share.fault();
            }
        }
        throw new IllegalStateException("the scheduled fault shares add up to " + sharesUpTo + ", not 100");
    }

    private void failOverDatabase() {
        database.failOver();
        epochChangedAt = now;
        count(Fault.DATABASE_FAILOVER);
    }

    private void failRelayRounds() {
        roundsFailUntil = now + FAILING_ROUNDS.draw(random);
        count(Fault.FAILED_RELAY_ROUNDS);
    }

    private void loseChangeLogEntry() {
        boolean lost = database.loseChangeLogEntry();
        if (lost) {
            count(Fault.LOST_CHANGE_LOG_ENTRY);
        }
    }

    private void cutConnection() {
        if (link != Link.UP) {
            return;
        }
        count(Fault.CONNECTION_CUT);
        link = Link.DOWN;
        step(new ViewInput.ConnectionLost());
        scheduleReconnect(RECONNECT_AFTER_CUT.draw(random));
    }

    private void cutConnectionSilently() {
        if (link != Link.UP) {
            return;
        }
        count(Fault.SILENT_CONNECTION_CUT);
        link = Link.SILENTLY_DOWN;
        int generation = reconnectGeneration;
        schedule(SILENT_CUT_NOTICED.draw(random), () -> noticeSilentCut(generation));
    }

    private void noticeSilentCut(int generation) {
        if (generation != reconnectGeneration) {
            return;
        }
        link = Link.DOWN;
        step(new ViewInput.ConnectionLost());
        scheduleReconnect(RECONNECT_AFTER_NOTICE.draw(random));
    }

    private void scheduleReconnect(long delay) {
        int generation = reconnectGeneration;
        schedule(delay, () -> reconnect(generation));
    }

    private void reconnect(int generation) {
        if (generation != reconnectGeneration) {
            return;
        }
        link = Link.RECONNECTED;
        schedule(FLUSH_AFTER_RECONNECT.draw(random), () -> confirmSubscriptionsAfterReconnect(generation));
    }

    private void confirmSubscriptionsAfterReconnect(int generation) {
        if (generation != reconnectGeneration) {
            return;
        }
        confirmSubscriptions();
    }

    private void handOverOwnership() {
        List<OwnerSlot> previous = activeOwners();
        activateNewOwner(random.nextBoolean());
        count(Fault.OWNER_HANDOVER);
        long overlap = OWNER_OVERLAP.draw(random);
        schedule(overlap, () -> deactivate(previous));
    }

    private void leaveWithoutOwner() {
        deactivate(activeOwners());
        count(Fault.NO_OWNER);
        schedule(WITHOUT_OWNER.draw(random), this::activateOwnerIfNone);
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
        reconnectGeneration++;
        if (link == Link.SILENTLY_DOWN) {
            link = Link.DOWN;
            step(new ViewInput.ConnectionLost());
        }
        scheduleReconnect(HEALED_RECONNECT_MILLIS);
    }

    // The promises.

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
            failRun("the live copy is still in epoch " + copy.epoch() + " " + LIVE_VIEW_LAGS_AT_MOST_MILLIS
                    + " ms after the failover");
        }
        long missedRevision = batchesOlderThanLag.highest(now, copy.epoch());
        if (copy.revision() < missedRevision) {
            failRun("the live copy is at revision " + copy.revision() + ", but revision " + missedRevision
                    + " of its epoch was published over " + LIVE_VIEW_LAGS_AT_MOST_MILLIS + " ms ago");
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
                && now - writesReturnedUnderThreeSecondsAgo.peekFirst().returnedAt() >= RuleTimes.OWN_WRITE_WAIT) {
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
        boolean historyCutSinceCommit = countOf(Fault.DATABASE_FAILOVER) != write.failoversBeforeCommit();
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
                && now - lastOwnWriteReturnedAt < RuleTimes.OWN_WRITE_WAIT;
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
                    + " was published over " + CHANGE_REACHES_VIEW_MILLIS + " ms ago");
        }
    }

    private void checkEnd() {
        step(new ViewInput.TimePassed());
        NetworkCopy copy = copyOrFail("the view holds no copy at the end");
        ModelDatabase.State state = database.current();
        boolean current = copy.epoch() == state.epoch()
                && copy.revision() == state.headRevision()
                && state.items().equals(ModelDatabase.Items.of(copy));
        if (!current) {
            failRun("at the end the copy is at revision " + copy.revision() + " of epoch " + copy.epoch()
                    + ", the database at " + state.headRevision() + " of " + state.epoch());
        }
    }

    private NetworkCopy copyOrFail(String message) {
        return view.copy().orElseGet(() -> failRun(message));
    }

    private <T> T failRun(String message) {
        return fail("seed " + plan.seed() + " at " + now + " ms: " + message);
    }
}
