package app.simplecloud.api.internal.networkstate;

import build.buf.gen.simplecloud.controller.v2.NetworkState;
import build.buf.gen.simplecloud.controller.v2.StateBatch;
import build.buf.gen.simplecloud.controller.v2.StateBlueprint;
import build.buf.gen.simplecloud.controller.v2.StatePersistentServer;
import build.buf.gen.simplecloud.controller.v2.StatePulse;
import build.buf.gen.simplecloud.controller.v2.StateServer;
import build.buf.gen.simplecloud.controller.v2.StateServerGroup;
import build.buf.gen.simplecloud.controller.v2.StateServerMetrics;
import com.google.protobuf.ByteString;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The plainest machine that follows the rules of {@link NetworkView}, written to be obviously right, not
 * fast. The checks are validated against it: they pass here, and each variant in {@link WrongMachines}
 * overrides one of its rules and must fail a check. Each rule is a method of its own for that reason.
 */
class ReferenceMachine {

    static final long GAP_WAIT_MILLIS = 3_000;
    static final long BEHIND_PULSE_WAIT_MILLIS = 3_000;
    static final long GOOD_PULSE_MAX_AGE_MILLIS = 15_000;
    static final long LOAD_SPACING_MILLIS = 2_000;
    static final long FIRST_BACK_OFF_MILLIS = 2_000;
    static final long MAX_BACK_OFF_MILLIS = 30_000;
    static final long RELOAD_JITTER_MILLIS = 2_000;
    static final long AUDIT_INTERVAL_MIN_MILLIS = 20 * 60_000;
    static final long AUDIT_INTERVAL_SPREAD_MILLIS = 10 * 60_000;
    static final long AUDIT_RETRY_MIN_MILLIS = 2_000;
    static final long AUDIT_RETRY_SPREAD_MILLIS = 8_000;
    static final long AUDIT_REACH_WAIT_MILLIS = 3_000;

    NetworkView start(RandomSequence random) {
        return new View(this, new Memory(random));
    }

    enum Mode {
        DISCONNECTED,
        RELOAD_WANTED,
        RELOADING,
        /** Holds a copy, the connection is up, and no reload is wanted. */
        SYNCED
    }

    enum AuditPhase {
        NONE,
        LOADING,
        /** Holds the audit snapshot and waits for the copy to reach its revision. */
        COMPARING
    }

    enum BatchFit {
        OTHER_EPOCH,
        ALREADY_IN_COPY,
        FITS_COPY,
        AHEAD_OF_COPY
    }

    enum PulseFit {
        OTHER_EPOCH,
        AHEAD_OF_COPY,
        MATCHES_COPY,
        BEHIND_COPY
    }

    record HeldBatch(StateBatch batch, long arrivedAt) {
    }

    /**
     * The highest revision that pulses showed above the copy, and when the first of them arrived.
     */
    record BehindPulse(long revision, long since) {
    }

    record Position(long epoch, long revision) {

        static Position of(NetworkCopy copy) {
            return new Position(copy.epoch(), copy.revision());
        }
    }

    /**
     * Every place where the machine orders two uint64 values, so a wrong machine can compare one of them
     * signed.
     */
    enum Comparison {
        BATCH_REVISION,
        BATCH_BASE,
        PULSE_REVISION,
        FURTHER_PULSE,
        REACHED_PULSE,
        OWN_WRITE_EPOCH,
        OWN_WRITE_REVISION,
        AUDIT_REVISION
    }

    /**
     * What a view remembers. One step changes a fresh copy of it while it builds the next view; a view never
     * changes its own memory.
     */
    static final class Memory {
        final RandomSequence random;
        long numbersDrawn;
        Mode mode = Mode.DISCONNECTED;
        NetworkCopy copy;
        boolean loadRunning;
        boolean loadAnswerIgnored;
        long reloadStartsAt;
        Long lastLoadStartedAt;
        int failedLoadsInRow;
        long lastLoadFailedAt;
        List<HeldBatch> heldBatches = new ArrayList<>();
        BehindPulse behindPulse;
        Long goodPulseAt;
        long snapshotTakenAt;
        boolean viewsMayAnswer;
        AuditPhase auditPhase = AuditPhase.NONE;
        long auditDueAt;
        NetworkState auditSnapshot;
        long auditComparingSince;
        Map<ReadKind, Position> ownWritesByRead = new EnumMap<>(ReadKind.class);

        Memory(RandomSequence random) {
            this.random = random;
        }

        Memory(Memory source) {
            random = source.random;
            numbersDrawn = source.numbersDrawn;
            mode = source.mode;
            copy = source.copy;
            loadRunning = source.loadRunning;
            loadAnswerIgnored = source.loadAnswerIgnored;
            reloadStartsAt = source.reloadStartsAt;
            lastLoadStartedAt = source.lastLoadStartedAt;
            failedLoadsInRow = source.failedLoadsInRow;
            lastLoadFailedAt = source.lastLoadFailedAt;
            heldBatches = new ArrayList<>(source.heldBatches);
            behindPulse = source.behindPulse;
            goodPulseAt = source.goodPulseAt;
            snapshotTakenAt = source.snapshotTakenAt;
            viewsMayAnswer = source.viewsMayAnswer;
            auditPhase = source.auditPhase;
            auditDueAt = source.auditDueAt;
            auditSnapshot = source.auditSnapshot;
            auditComparingSince = source.auditComparingSince;
            ownWritesByRead = new EnumMap<>(source.ownWritesByRead);
        }

    }

    static final class View implements NetworkView {
        private final ReferenceMachine machine;
        private final Memory memory;

        View(ReferenceMachine machine, Memory memory) {
            this.machine = machine;
            this.memory = memory;
        }

        @Override
        public ViewStep step(ViewInput input, long nowMillis) {
            Memory next = new Memory(memory);
            List<ViewEffect> effects = new ArrayList<>();
            machine.runStep(next, input, nowMillis, effects);
            return new ViewStep(new View(machine, next), effects);
        }

        @Override
        public boolean isLive(long nowMillis) {
            return machine.isLive(memory, nowMillis);
        }

        @Override
        public boolean mayAnswer(ReadKind kind, long nowMillis) {
            return machine.mayAnswer(memory, kind, nowMillis);
        }

        @Override
        public boolean awaitsOwnWrite(ReadKind kind, long nowMillis) {
            return machine.awaitsOwnWrite(memory, kind, nowMillis);
        }

        @Override
        public Optional<NetworkCopy> copy() {
            return Optional.ofNullable(memory.copy);
        }
    }

    int compare(Comparison where, long first, long second) {
        return Long.compareUnsigned(first, second);
    }

    double drawNumber(Memory m) {
        double number = m.random.at(m.numbersDrawn);
        m.numbersDrawn++;
        return number;
    }

    /**
     * The input acts first and the deadlines after it, so an input that arrives exactly at a deadline is on
     * time.
     */
    void runStep(Memory m, ViewInput input, long now, List<ViewEffect> effects) {
        handle(m, input, now, effects);
        actOnDeadlines(m, now, effects);
    }

    void handle(Memory m, ViewInput input, long now, List<ViewEffect> effects) {
        switch (input) {
            case ViewInput.SubscriptionsActive active -> wantReload(m, now);
            case ViewInput.ConnectionLost lost -> loseConnection(m);
            case ViewInput.SnapshotLoaded loaded -> onSnapshotLoaded(m, loaded.snapshot(), now, effects);
            case ViewInput.SnapshotFailed failed -> onSnapshotFailed(m, now);
            case ViewInput.BatchReceived received -> onBatch(m, received.batch(), now, effects);
            case ViewInput.PulseReceived received -> onPulse(m, received.pulse(), now);
            case ViewInput.WriteReturned write -> onWriteReturned(m, write);
            case ViewInput.WriteRevisionUnknown write -> onWriteRevisionUnknown(m, now);
            case ViewInput.TimePassed passed -> {
            }
        }
    }

    void loseConnection(Memory m) {
        ignoreRunningLoad(m);
        m.mode = Mode.DISCONNECTED;
        dropPendingWork(m);
    }

    void wantReload(Memory m, long now) {
        if (m.mode == Mode.RELOAD_WANTED) {
            return;
        }
        ignoreRunningLoad(m);
        m.mode = Mode.RELOAD_WANTED;
        dropPendingWork(m);
        m.reloadStartsAt = reloadStartTime(m, now);
    }

    long reloadStartTime(Memory m, long now) {
        long earliest = now;
        if (m.lastLoadStartedAt != null) {
            earliest = Math.max(earliest, m.lastLoadStartedAt + LOAD_SPACING_MILLIS);
        }
        if (m.failedLoadsInRow > 0) {
            earliest = Math.max(earliest, m.lastLoadFailedAt + backOff(m.failedLoadsInRow));
        }
        long jitter = (long) (RELOAD_JITTER_MILLIS * drawNumber(m));
        return earliest + jitter;
    }

    static long backOff(int failedLoadsInRow) {
        long delay = FIRST_BACK_OFF_MILLIS;
        for (int failure = 1; failure < failedLoadsInRow && delay < MAX_BACK_OFF_MILLIS; failure++) {
            delay *= 2;
        }
        return Math.min(MAX_BACK_OFF_MILLIS, delay);
    }

    private static void ignoreRunningLoad(Memory m) {
        if (m.loadRunning) {
            m.loadAnswerIgnored = true;
        }
    }

    private static void dropPendingWork(Memory m) {
        m.heldBatches.clear();
        m.behindPulse = null;
        m.auditPhase = AuditPhase.NONE;
        m.auditSnapshot = null;
    }

    void onSnapshotLoaded(Memory m, NetworkState snapshot, long now, List<ViewEffect> effects) {
        if (!takeLoadAnswer(m)) {
            return;
        }
        if (m.mode == Mode.RELOADING) {
            takeSnapshot(m, snapshot, now, effects);
            return;
        }
        if (m.auditPhase == AuditPhase.LOADING) {
            onAuditSnapshot(m, snapshot, now, effects);
        }
    }

    void onSnapshotFailed(Memory m, long now) {
        if (!takeLoadAnswer(m)) {
            return;
        }
        if (m.mode == Mode.RELOADING) {
            countFailedLoad(m, now);
            return;
        }
        if (m.auditPhase == AuditPhase.LOADING) {
            m.auditPhase = AuditPhase.NONE;
            m.auditDueAt = now + auditInterval(m);
        }
    }

    /**
     * Ends the running load and returns whether its answer counts.
     */
    private static boolean takeLoadAnswer(Memory m) {
        if (!m.loadRunning) {
            return false;
        }
        m.loadRunning = false;
        boolean ignored = m.loadAnswerIgnored;
        m.loadAnswerIgnored = false;
        return !ignored;
    }

    void countFailedLoad(Memory m, long now) {
        m.failedLoadsInRow++;
        m.lastLoadFailedAt = now;
        wantReload(m, now);
    }

    void takeSnapshot(Memory m, NetworkState snapshot, long now, List<ViewEffect> effects) {
        NetworkCopy taken = copyOf(snapshot);
        if (!referencesResolve(taken)) {
            countFailedLoad(m, now);
            return;
        }
        m.copy = taken;
        m.mode = Mode.SYNCED;
        m.snapshotTakenAt = now;
        m.goodPulseAt = null;
        m.behindPulse = null;
        m.auditPhase = AuditPhase.NONE;
        m.auditSnapshot = null;
        m.auditDueAt = now + auditInterval(m);
        m.failedLoadsInRow = 0;
        settleHeldBatches(m, now, effects);
    }

    void onBatch(Memory m, StateBatch batch, long now, List<ViewEffect> effects) {
        switch (m.mode) {
            case DISCONNECTED, RELOAD_WANTED -> {
            }
            case RELOADING -> m.heldBatches.add(new HeldBatch(batch, now));
            case SYNCED -> {
                m.heldBatches.add(new HeldBatch(batch, now));
                settleHeldBatches(m, now, effects);
            }
        }
    }

    /**
     * Runs rule 2 over the held batches, oldest first, until every batch left is ahead of the copy.
     */
    void settleHeldBatches(Memory m, long now, List<ViewEffect> effects) {
        HeldBatch next = firstSettleable(m);
        while (m.mode == Mode.SYNCED && next != null) {
            m.heldBatches.remove(next);
            settleBatch(m, next.batch(), now, effects);
            next = firstSettleable(m);
        }
    }

    private HeldBatch firstSettleable(Memory m) {
        for (HeldBatch held : m.heldBatches) {
            if (fitOf(m.copy, held.batch()) != BatchFit.AHEAD_OF_COPY) {
                return held;
            }
        }
        return null;
    }

    private void settleBatch(Memory m, StateBatch batch, long now, List<ViewEffect> effects) {
        switch (fitOf(m.copy, batch)) {
            case OTHER_EPOCH -> wantReload(m, now);
            case ALREADY_IN_COPY -> {
            }
            case FITS_COPY -> applyBatch(m, batch, now, effects);
            case AHEAD_OF_COPY -> throw new IllegalStateException("a batch ahead of the copy stays held");
        }
    }

    BatchFit fitOf(NetworkCopy copy, StateBatch batch) {
        if (batch.getEpoch() != copy.epoch()) {
            return BatchFit.OTHER_EPOCH;
        }
        if (compare(Comparison.BATCH_REVISION, batch.getRevision(), copy.revision()) <= 0) {
            return BatchFit.ALREADY_IN_COPY;
        }
        if (compare(Comparison.BATCH_BASE, batch.getBaseRevision(), copy.revision()) <= 0) {
            return BatchFit.FITS_COPY;
        }
        return BatchFit.AHEAD_OF_COPY;
    }

    void applyBatch(Memory m, StateBatch batch, long now, List<ViewEffect> effects) {
        NetworkCopy applied = applied(m.copy, batch);
        if (!referencesResolve(applied)) {
            wantReload(m, now);
            return;
        }
        m.copy = applied;
        boolean reachedBehindPulse = m.behindPulse != null
                && compare(Comparison.REACHED_PULSE, m.copy.revision(), m.behindPulse.revision()) >= 0;
        if (reachedBehindPulse) {
            m.behindPulse = null;
        }
        if (m.auditPhase == AuditPhase.COMPARING) {
            continueAudit(m, now, effects);
        }
    }

    boolean referencesResolve(NetworkCopy copy) {
        for (StateServer server : copy.servers().values()) {
            if (!serverReferencesResolve(copy, server)) {
                return false;
            }
        }
        for (StateServerGroup group : copy.serverGroups().values()) {
            boolean blueprintMissing = group.hasBlueprintId() && !copy.blueprints().containsKey(group.getBlueprintId());
            if (blueprintMissing) {
                return false;
            }
        }
        return true;
    }

    private static boolean serverReferencesResolve(NetworkCopy copy, StateServer server) {
        boolean groupMissing = server.hasServerGroupId()
                && !copy.serverGroups().containsKey(server.getServerGroupId());
        boolean persistentServerMissing = server.hasPersistentServerId()
                && !copy.persistentServers().containsKey(server.getPersistentServerId());
        return !groupMissing && !persistentServerMissing;
    }

    void onPulse(Memory m, StatePulse pulse, long now) {
        m.viewsMayAnswer = pulse.getViewsMayAnswer();
        if (m.copy != null) {
            m.copy = withMetrics(m.copy, pulse.getMetricsList());
        }
        if (m.mode != Mode.SYNCED) {
            return;
        }
        if (lacksGoodPulse(m, now)) {
            wantReload(m, now);
            return;
        }
        switch (fitOf(m.copy, pulse)) {
            case OTHER_EPOCH -> wantReload(m, now);
            case AHEAD_OF_COPY -> markBehindPulse(m, pulse, now);
            case MATCHES_COPY -> m.goodPulseAt = now;
            case BEHIND_COPY -> {
            }
        }
    }

    boolean lacksGoodPulse(Memory m, long now) {
        long lastProof = m.goodPulseAt != null ? m.goodPulseAt : m.snapshotTakenAt;
        return now - lastProof >= GOOD_PULSE_MAX_AGE_MILLIS;
    }

    PulseFit fitOf(NetworkCopy copy, StatePulse pulse) {
        if (pulse.getEpoch() != copy.epoch()) {
            return PulseFit.OTHER_EPOCH;
        }
        int order = compare(Comparison.PULSE_REVISION, pulse.getRevision(), copy.revision());
        if (order > 0) {
            return PulseFit.AHEAD_OF_COPY;
        }
        if (order == 0) {
            return PulseFit.MATCHES_COPY;
        }
        return PulseFit.BEHIND_COPY;
    }

    private void markBehindPulse(Memory m, StatePulse pulse, long now) {
        if (m.behindPulse == null) {
            m.behindPulse = new BehindPulse(pulse.getRevision(), now);
            return;
        }
        boolean furtherAhead = compare(Comparison.FURTHER_PULSE, pulse.getRevision(), m.behindPulse.revision()) > 0;
        if (furtherAhead) {
            m.behindPulse = new BehindPulse(pulse.getRevision(), m.behindPulse.since());
        }
    }

    void onWriteReturned(Memory m, ViewInput.WriteReturned write) {
        Position written = new Position(write.epoch(), write.revision());
        for (ReadKind read : readsChangedBy(write.written())) {
            m.ownWritesByRead.merge(read, written, this::later);
        }
    }

    /**
     * A snapshot is read after the write's request arrived, so it holds the write.
     */
    void onWriteRevisionUnknown(Memory m, long now) {
        switch (m.mode) {
            case SYNCED, RELOADING -> wantReload(m, now);
            case RELOAD_WANTED, DISCONNECTED -> {
            }
        }
    }

    static Set<ReadKind> readsChangedBy(WriteKind written) {
        return switch (written) {
            case SERVER -> EnumSet.of(ReadKind.SERVER, ReadKind.SERVER_GROUP, ReadKind.PERSISTENT_SERVER);
            case SERVER_GROUP -> EnumSet.of(ReadKind.SERVER_GROUP, ReadKind.SERVER);
            case PERSISTENT_SERVER -> EnumSet.of(ReadKind.PERSISTENT_SERVER, ReadKind.SERVER);
            case BLUEPRINT -> EnumSet.of(ReadKind.SERVER_GROUP, ReadKind.SERVER);
        };
    }

    void actOnDeadlines(Memory m, long now, List<ViewEffect> effects) {
        if (m.mode == Mode.SYNCED) {
            actOnSyncedDeadlines(m, now, effects);
        }
        startReloadIfDue(m, now, effects);
        startAuditIfDue(m, now, effects);
    }

    /**
     * The audit's deadline comes first: taking its snapshot can settle the held batches.
     */
    void actOnSyncedDeadlines(Memory m, long now, List<ViewEffect> effects) {
        takeAuditSnapshotIfNotReached(m, now, effects);
        boolean stillSynced = m.mode == Mode.SYNCED;
        if (stillSynced && (heldTooLong(m, now) || behindPulseTooLong(m, now))) {
            wantReload(m, now);
        }
    }

    void takeAuditSnapshotIfNotReached(Memory m, long now, List<ViewEffect> effects) {
        boolean auditWaitedTooLong = m.auditPhase == AuditPhase.COMPARING
                && now - m.auditComparingSince >= AUDIT_REACH_WAIT_MILLIS;
        if (auditWaitedTooLong) {
            effects.add(new ViewEffect.AuditFinished(AuditOutcome.NOT_REACHED));
            takeSnapshot(m, m.auditSnapshot, now, effects);
        }
    }

    boolean heldTooLong(Memory m, long now) {
        for (HeldBatch held : m.heldBatches) {
            if (now - held.arrivedAt() >= GAP_WAIT_MILLIS) {
                return true;
            }
        }
        return false;
    }

    boolean behindPulseTooLong(Memory m, long now) {
        return m.behindPulse != null && now - m.behindPulse.since() >= BEHIND_PULSE_WAIT_MILLIS;
    }

    private void startReloadIfDue(Memory m, long now, List<ViewEffect> effects) {
        boolean due = m.mode == Mode.RELOAD_WANTED && !m.loadRunning && now >= m.reloadStartsAt;
        if (due) {
            startLoad(m, now, effects);
            m.mode = Mode.RELOADING;
        }
    }

    private void startAuditIfDue(Memory m, long now, List<ViewEffect> effects) {
        boolean spaced = m.lastLoadStartedAt == null || now >= m.lastLoadStartedAt + LOAD_SPACING_MILLIS;
        boolean due = m.mode == Mode.SYNCED
                && m.auditPhase == AuditPhase.NONE
                && !m.loadRunning
                && now >= m.auditDueAt
                && spaced
                && auditMayStart(m, now);
        if (due) {
            startLoad(m, now, effects);
            m.auditPhase = AuditPhase.LOADING;
        }
    }

    boolean auditMayStart(Memory m, long now) {
        return isLive(m, now);
    }

    private static void startLoad(Memory m, long now, List<ViewEffect> effects) {
        effects.add(new ViewEffect.LoadSnapshot());
        m.loadRunning = true;
        m.loadAnswerIgnored = false;
        m.lastLoadStartedAt = now;
    }

    void onAuditSnapshot(Memory m, NetworkState snapshot, long now, List<ViewEffect> effects) {
        if (snapshot.getEpoch() != m.copy.epoch()) {
            wantReload(m, now);
            return;
        }
        m.auditSnapshot = snapshot;
        m.auditPhase = AuditPhase.COMPARING;
        m.auditComparingSince = now;
        continueAudit(m, now, effects);
    }

    void continueAudit(Memory m, long now, List<ViewEffect> effects) {
        int order = compare(Comparison.AUDIT_REVISION, m.copy.revision(), m.auditSnapshot.getRevision());
        if (order == 0) {
            compareAudit(m, now, effects);
        } else if (order > 0) {
            auditAgainSoon(m, now, effects);
        }
    }

    void compareAudit(Memory m, long now, List<ViewEffect> effects) {
        NetworkState snapshot = m.auditSnapshot;
        m.auditPhase = AuditPhase.NONE;
        m.auditSnapshot = null;
        if (sameItems(m.copy, copyOf(snapshot))) {
            effects.add(new ViewEffect.AuditFinished(AuditOutcome.MATCHED));
            m.auditDueAt = now + auditInterval(m);
            return;
        }
        effects.add(new ViewEffect.AuditFinished(AuditOutcome.DIFFERED));
        takeSnapshot(m, snapshot, now, effects);
    }

    private void auditAgainSoon(Memory m, long now, List<ViewEffect> effects) {
        effects.add(new ViewEffect.AuditFinished(AuditOutcome.JUMPED_PAST));
        m.auditPhase = AuditPhase.NONE;
        m.auditSnapshot = null;
        long retryDelay = AUDIT_RETRY_MIN_MILLIS + (long) (AUDIT_RETRY_SPREAD_MILLIS * drawNumber(m));
        m.auditDueAt = now + retryDelay;
    }

    long auditInterval(Memory m) {
        return AUDIT_INTERVAL_MIN_MILLIS + (long) (AUDIT_INTERVAL_SPREAD_MILLIS * drawNumber(m));
    }

    boolean isLive(Memory m, long now) {
        return m.mode == Mode.SYNCED
                && m.heldBatches.isEmpty()
                && m.behindPulse == null
                && m.goodPulseAt != null
                && now - m.goodPulseAt < GOOD_PULSE_MAX_AGE_MILLIS;
    }

    boolean mayAnswer(Memory m, ReadKind kind, long now) {
        return isLive(m, now) && m.viewsMayAnswer && reachedOwnWrites(m, kind);
    }

    boolean awaitsOwnWrite(Memory m, ReadKind kind, long now) {
        boolean answersAllowed = isLive(m, now) && m.viewsMayAnswer;
        return answersAllowed && !reachedOwnWrites(m, kind);
    }

    boolean reachedOwnWrites(Memory m, ReadKind read) {
        Position ownWrite = m.ownWritesByRead.get(read);
        return ownWrite == null || isAtOrAfter(Position.of(m.copy), ownWrite);
    }

    boolean isAtOrAfter(Position position, Position other) {
        int epochOrder = compare(Comparison.OWN_WRITE_EPOCH, position.epoch(), other.epoch());
        if (epochOrder != 0) {
            return epochOrder > 0;
        }
        return compare(Comparison.OWN_WRITE_REVISION, position.revision(), other.revision()) >= 0;
    }

    Position later(Position first, Position second) {
        return isAtOrAfter(first, second) ? first : second;
    }

    static NetworkCopy copyOf(NetworkState snapshot) {
        Map<ByteString, StateServer> servers = new HashMap<>();
        for (StateServer server : snapshot.getServersList()) {
            servers.put(server.getId(), server);
        }
        Map<ByteString, StateServerGroup> groups = new HashMap<>();
        for (StateServerGroup group : snapshot.getServerGroupsList()) {
            groups.put(group.getId(), group);
        }
        Map<ByteString, StatePersistentServer> persistentServers = new HashMap<>();
        for (StatePersistentServer persistentServer : snapshot.getPersistentServersList()) {
            persistentServers.put(persistentServer.getId(), persistentServer);
        }
        Map<ByteString, StateBlueprint> blueprints = new HashMap<>();
        for (StateBlueprint blueprint : snapshot.getBlueprintsList()) {
            blueprints.put(blueprint.getId(), blueprint);
        }
        Map<ByteString, StateServerMetrics> metrics = metricsById(snapshot.getMetricsList());
        return new NetworkCopy(snapshot.getEpoch(), snapshot.getRevision(), servers, groups, persistentServers,
                blueprints, metrics);
    }

    static NetworkCopy applied(NetworkCopy copy, StateBatch batch) {
        Map<ByteString, StateServer> servers = new HashMap<>(copy.servers());
        for (StateServer server : batch.getServersList()) {
            servers.put(server.getId(), server);
        }
        for (ByteString id : batch.getDeletedServerIdsList()) {
            servers.remove(id);
        }
        Map<ByteString, StateServerGroup> groups = new HashMap<>(copy.serverGroups());
        for (StateServerGroup group : batch.getServerGroupsList()) {
            groups.put(group.getId(), group);
        }
        for (ByteString id : batch.getDeletedServerGroupIdsList()) {
            groups.remove(id);
        }
        Map<ByteString, StatePersistentServer> persistentServers = new HashMap<>(copy.persistentServers());
        for (StatePersistentServer persistentServer : batch.getPersistentServersList()) {
            persistentServers.put(persistentServer.getId(), persistentServer);
        }
        for (ByteString id : batch.getDeletedPersistentServerIdsList()) {
            persistentServers.remove(id);
        }
        Map<ByteString, StateBlueprint> blueprints = new HashMap<>(copy.blueprints());
        for (StateBlueprint blueprint : batch.getBlueprintsList()) {
            blueprints.put(blueprint.getId(), blueprint);
        }
        for (ByteString id : batch.getDeletedBlueprintIdsList()) {
            blueprints.remove(id);
        }
        return new NetworkCopy(copy.epoch(), batch.getRevision(), servers, groups, persistentServers, blueprints,
                copy.metrics());
    }

    static NetworkCopy withMetrics(NetworkCopy copy, List<StateServerMetrics> metrics) {
        return new NetworkCopy(copy.epoch(), copy.revision(), copy.servers(), copy.serverGroups(),
                copy.persistentServers(), copy.blueprints(), metricsById(metrics));
    }

    private static Map<ByteString, StateServerMetrics> metricsById(List<StateServerMetrics> metrics) {
        Map<ByteString, StateServerMetrics> byId = new HashMap<>();
        for (StateServerMetrics serverMetrics : metrics) {
            byId.put(serverMetrics.getServerId(), serverMetrics);
        }
        return byId;
    }

    static boolean sameItems(NetworkCopy first, NetworkCopy second) {
        return first.servers().equals(second.servers())
                && first.serverGroups().equals(second.serverGroups())
                && first.persistentServers().equals(second.persistentServers())
                && first.blueprints().equals(second.blueprints());
    }
}
