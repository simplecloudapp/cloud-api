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
import java.util.function.Function;

/**
 * The production machine of {@link NetworkView}.
 *
 * <p>{@link #step} builds the next view from this one and changes only the new view before it returns it, so
 * a view that has been returned never changes. Every method that changes a field is private and runs only
 * inside that step.
 */
public final class NetworkViewMachine implements NetworkView {

    private static final long LOAD_SPACING_MILLIS = 2_000;
    private static final long FIRST_BACK_OFF_MILLIS = 2_000;
    private static final long LONGEST_BACK_OFF_MILLIS = 30_000;
    private static final long LONGEST_RANDOM_LOAD_DELAY_MILLIS = 2_000;
    private static final long GAP_WAIT_MILLIS = 3_000;
    private static final long BEHIND_PULSE_WAIT_MILLIS = 3_000;
    private static final long GOOD_PULSE_LIFETIME_MILLIS = 15_000;
    private static final long AUDIT_REACH_WAIT_MILLIS = 3_000;
    private static final long SHORTEST_AUDIT_INTERVAL_MILLIS = 20 * 60_000;
    private static final long LONGEST_RANDOM_AUDIT_DELAY_MILLIS = 10 * 60_000;
    private static final long SHORTEST_AUDIT_RETRY_MILLIS = 2_000;
    private static final long LONGEST_RANDOM_AUDIT_RETRY_DELAY_MILLIS = 8_000;

    private enum RunningLoad {
        NONE,
        RELOAD,
        AUDIT,
        /** A load that is no longer wanted. The view waits for its answer and ignores it. */
        ABANDONED
    }

    private enum BatchFit {
        OTHER_EPOCH,
        IN_COPY,
        FITS,
        AHEAD_OF_COPY
    }

    private enum PulseFit {
        OTHER_EPOCH,
        AHEAD_OF_COPY,
        AT_COPY,
        BEHIND_COPY
    }

    private record HeldBatch(StateBatch batch, long receivedAt) {
    }

    private record BehindPulse(long highestRevision, long since) {
    }

    private record PendingAudit(NetworkCopy snapshot, long receivedAt) {
    }

    private record Position(long epoch, long revision) {

        boolean isAfter(Position other) {
            int epochOrder = Long.compareUnsigned(epoch, other.epoch);
            if (epochOrder != 0) {
                return epochOrder > 0;
            }
            return Long.compareUnsigned(revision, other.revision) > 0;
        }

        boolean isReachedBy(NetworkCopy copy) {
            Position copyPosition = new Position(copy.epoch(), copy.revision());
            return !isAfter(copyPosition);
        }

        static Position later(Position first, Position second) {
            return second.isAfter(first) ? second : first;
        }
    }

    private final RandomSequence random;
    private long numbersDrawn;
    private NetworkCopy copy;
    private boolean subscriptionsActive;
    private boolean viewsMayAnswer;
    private RunningLoad runningLoad;
    private long nextLoadAllowedAt;
    private int failedLoadsInRow;
    private long lastFailedLoadAt;
    private Long wantedReloadStartsAt;
    private final List<HeldBatch> heldBatches;
    private long snapshotTakenAt;
    private Long lastGoodPulseAt;
    private BehindPulse behindPulse;
    private long nextAuditAt;
    private PendingAudit pendingAudit;
    private final Map<ReadKind, Position> highestOwnWriteByRead;

    private NetworkViewMachine(RandomSequence random) {
        this.random = random;
        runningLoad = RunningLoad.NONE;
        nextLoadAllowedAt = Long.MIN_VALUE;
        heldBatches = new ArrayList<>();
        highestOwnWriteByRead = new EnumMap<>(ReadKind.class);
    }

    private NetworkViewMachine(NetworkViewMachine previous) {
        random = previous.random;
        numbersDrawn = previous.numbersDrawn;
        copy = previous.copy;
        subscriptionsActive = previous.subscriptionsActive;
        viewsMayAnswer = previous.viewsMayAnswer;
        runningLoad = previous.runningLoad;
        nextLoadAllowedAt = previous.nextLoadAllowedAt;
        failedLoadsInRow = previous.failedLoadsInRow;
        lastFailedLoadAt = previous.lastFailedLoadAt;
        wantedReloadStartsAt = previous.wantedReloadStartsAt;
        heldBatches = new ArrayList<>(previous.heldBatches);
        snapshotTakenAt = previous.snapshotTakenAt;
        lastGoodPulseAt = previous.lastGoodPulseAt;
        behindPulse = previous.behindPulse;
        nextAuditAt = previous.nextAuditAt;
        pendingAudit = previous.pendingAudit;
        highestOwnWriteByRead = new EnumMap<>(previous.highestOwnWriteByRead);
    }

    /**
     * Returns a view with no copy and no connection, which waits for {@link ViewInput.SubscriptionsActive}.
     */
    public static NetworkView start(RandomSequence random) {
        return new NetworkViewMachine(random);
    }

    @Override
    public ViewStep step(ViewInput input, long nowMillis) {
        NetworkViewMachine next = new NetworkViewMachine(this);
        List<ViewEffect> effects = new ArrayList<>();
        // A deadline that passed before the input acts as if a tick had come just before it. The load starts
        // last, because the input can make one due.
        next.actOnDeadlines(nowMillis, effects);
        next.handle(input, nowMillis, effects);
        next.startDueLoad(nowMillis, effects);
        return new ViewStep(next, effects);
    }

    @Override
    public boolean isLive(long nowMillis) {
        boolean goodPulseIsRecent = lastGoodPulseAt != null
                && nowMillis - lastGoodPulseAt < GOOD_PULSE_LIFETIME_MILLIS;
        return isSynced() && heldBatches.isEmpty() && behindPulse == null && goodPulseIsRecent;
    }

    @Override
    public boolean mayAnswer(ReadKind kind, long nowMillis) {
        if (!isLive(nowMillis) || !viewsMayAnswer) {
            return false;
        }
        Position highestOwnWrite = highestOwnWriteByRead.get(kind);
        return highestOwnWrite == null || highestOwnWrite.isReachedBy(copy);
    }

    @Override
    public Optional<NetworkCopy> copy() {
        return Optional.ofNullable(copy);
    }

    private boolean isSynced() {
        boolean reloadWantedOrRunning = wantedReloadStartsAt != null || runningLoad == RunningLoad.RELOAD;
        return copy != null && subscriptionsActive && !reloadWantedOrRunning;
    }

    private void handle(ViewInput input, long now, List<ViewEffect> effects) {
        switch (input) {
            case ViewInput.SubscriptionsActive active -> receiveSubscriptionsActive(now);
            case ViewInput.ConnectionLost lost -> receiveConnectionLoss();
            case ViewInput.SnapshotLoaded loaded -> receiveSnapshot(loaded.snapshot(), now, effects);
            case ViewInput.SnapshotFailed failed -> receiveLoadFailure(now);
            case ViewInput.BatchReceived received -> receiveBatch(received.batch(), now, effects);
            case ViewInput.PulseReceived received -> receivePulse(received.pulse(), now);
            case ViewInput.WriteReturned returned -> recordOwnWrite(returned);
            case ViewInput.TimePassed passed -> {
            }
        }
    }

    // Deadlines and load starts (rules 6 and 7).

    private void actOnDeadlines(long now, List<ViewEffect> effects) {
        if (!isSynced()) {
            return;
        }
        // The audit goes first: the snapshot it takes can close a gap without another load.
        takeAuditSnapshotIfNotReached(now, effects);
        reloadIfCopyWaitedTooLong(now);
    }

    private void takeAuditSnapshotIfNotReached(long now, List<ViewEffect> effects) {
        boolean notReached = pendingAudit != null && now >= pendingAudit.receivedAt() + AUDIT_REACH_WAIT_MILLIS;
        if (notReached) {
            finishAudit(AuditOutcome.NOT_REACHED, now, effects);
        }
    }

    private void reloadIfCopyWaitedTooLong(long now) {
        boolean gapHeldTooLong = !heldBatches.isEmpty()
                && now >= heldBatches.getFirst().receivedAt() + GAP_WAIT_MILLIS;
        boolean behindPulseTooLong = behindPulse != null && now >= behindPulse.since() + BEHIND_PULSE_WAIT_MILLIS;
        if (gapHeldTooLong || behindPulseTooLong) {
            wantReload(now);
        }
    }

    private void startDueLoad(long now, List<ViewEffect> effects) {
        if (runningLoad != RunningLoad.NONE) {
            return;
        }
        boolean reloadDue = wantedReloadStartsAt != null && now >= wantedReloadStartsAt;
        if (reloadDue) {
            wantedReloadStartsAt = null;
            startLoad(RunningLoad.RELOAD, now, effects);
            return;
        }
        boolean auditDue = pendingAudit == null && now >= nextAuditAt && now >= nextLoadAllowedAt && isLive(now);
        if (auditDue) {
            startLoad(RunningLoad.AUDIT, now, effects);
        }
    }

    private void startLoad(RunningLoad load, long now, List<ViewEffect> effects) {
        runningLoad = load;
        nextLoadAllowedAt = now + LOAD_SPACING_MILLIS;
        effects.add(new ViewEffect.LoadSnapshot());
    }

    private void wantReload(long now) {
        abandonPendingWork();
        wantedReloadStartsAt = earliestLoadStart(now) + drawDelayUpTo(LONGEST_RANDOM_LOAD_DELAY_MILLIS);
    }

    private void abandonPendingWork() {
        heldBatches.clear();
        behindPulse = null;
        pendingAudit = null;
        if (runningLoad != RunningLoad.NONE) {
            runningLoad = RunningLoad.ABANDONED;
        }
    }

    private long earliestLoadStart(long now) {
        long start = Math.max(now, nextLoadAllowedAt);
        if (failedLoadsInRow > 0) {
            start = Math.max(start, lastFailedLoadAt + backOffAfter(failedLoadsInRow));
        }
        return start;
    }

    private static long backOffAfter(int failedLoads) {
        long backOff = FIRST_BACK_OFF_MILLIS;
        for (int failed = 1; failed < failedLoads && backOff < LONGEST_BACK_OFF_MILLIS; failed++) {
            backOff *= 2;
        }
        return Math.min(backOff, LONGEST_BACK_OFF_MILLIS);
    }

    private long drawDelayUpTo(long longestDelayMillis) {
        double number = random.at(numbersDrawn);
        numbersDrawn++;
        return (long) (longestDelayMillis * number);
    }

    private long drawAuditInterval() {
        return SHORTEST_AUDIT_INTERVAL_MILLIS + drawDelayUpTo(LONGEST_RANDOM_AUDIT_DELAY_MILLIS);
    }

    private long drawAuditRetryDelay() {
        return SHORTEST_AUDIT_RETRY_MILLIS + drawDelayUpTo(LONGEST_RANDOM_AUDIT_RETRY_DELAY_MILLIS);
    }

    private void countFailedLoad(long now) {
        failedLoadsInRow++;
        lastFailedLoadAt = now;
    }

    // Connection (rule 4).

    private void receiveSubscriptionsActive(long now) {
        subscriptionsActive = true;
        wantReload(now);
    }

    private void receiveConnectionLoss() {
        subscriptionsActive = false;
        wantedReloadStartsAt = null;
        abandonPendingWork();
    }

    // Load answers (rules 6 and 7).

    private void receiveSnapshot(NetworkState snapshot, long now, List<ViewEffect> effects) {
        switch (endRunningLoad()) {
            case RELOAD -> takeSnapshotOrReload(copyOf(snapshot), now, effects);
            case AUDIT -> startComparing(copyOf(snapshot), now, effects);
            case ABANDONED, NONE -> {
            }
        }
    }

    private void receiveLoadFailure(long now) {
        switch (endRunningLoad()) {
            case RELOAD -> {
                countFailedLoad(now);
                wantReload(now);
            }
            case AUDIT -> nextAuditAt = now + drawAuditInterval();
            case ABANDONED, NONE -> {
            }
        }
    }

    private RunningLoad endRunningLoad() {
        RunningLoad answered = runningLoad;
        runningLoad = RunningLoad.NONE;
        return answered;
    }

    private void takeSnapshotOrReload(NetworkCopy snapshot, long now, List<ViewEffect> effects) {
        if (!referencesResolve(snapshot)) {
            countFailedLoad(now);
            wantReload(now);
            return;
        }
        copy = snapshot;
        failedLoadsInRow = 0;
        snapshotTakenAt = now;
        lastGoodPulseAt = null;
        behindPulse = null;
        nextAuditAt = now + drawAuditInterval();
        applyHeldBatches(now, effects);
    }

    // Batches (rule 2).

    private void receiveBatch(StateBatch batch, long now, List<ViewEffect> effects) {
        HeldBatch received = new HeldBatch(batch, now);
        if (runningLoad == RunningLoad.RELOAD) {
            heldBatches.add(received);
            return;
        }
        if (isSynced()) {
            heldBatches.add(received);
            applyHeldBatches(now, effects);
        }
    }

    /**
     * Goes through the held batches oldest first until only batches ahead of the copy are left. Each applied
     * batch starts again from the oldest, as it may close the gap in front of an older one.
     */
    private void applyHeldBatches(long now, List<ViewEffect> effects) {
        int index = 0;
        while (isSynced() && index < heldBatches.size()) {
            StateBatch batch = heldBatches.get(index).batch();
            switch (fitOf(batch)) {
                case AHEAD_OF_COPY -> index++;
                case IN_COPY -> heldBatches.remove(index);
                case OTHER_EPOCH -> wantReload(now);
                case FITS -> {
                    heldBatches.remove(index);
                    applyBatchOrReload(batch, now, effects);
                    index = 0;
                }
            }
        }
    }

    private BatchFit fitOf(StateBatch batch) {
        if (batch.getEpoch() != copy.epoch()) {
            return BatchFit.OTHER_EPOCH;
        }
        if (Long.compareUnsigned(batch.getRevision(), copy.revision()) <= 0) {
            return BatchFit.IN_COPY;
        }
        if (Long.compareUnsigned(batch.getBaseRevision(), copy.revision()) > 0) {
            return BatchFit.AHEAD_OF_COPY;
        }
        return BatchFit.FITS;
    }

    private void applyBatchOrReload(StateBatch batch, long now, List<ViewEffect> effects) {
        NetworkCopy applied = withBatch(copy, batch);
        if (!referencesResolve(applied)) {
            wantReload(now);
            return;
        }
        copy = applied;
        boolean reachedBehindPulse = behindPulse != null
                && Long.compareUnsigned(copy.revision(), behindPulse.highestRevision()) >= 0;
        if (reachedBehindPulse) {
            behindPulse = null;
        }
        continueAudit(now, effects);
    }

    // Pulses (rule 3).

    private void receivePulse(StatePulse pulse, long now) {
        viewsMayAnswer = pulse.getViewsMayAnswer();
        if (copy != null) {
            copy = withMetrics(copy, pulse.getMetricsList());
        }
        if (!isSynced()) {
            return;
        }
        if (isTooLongWithoutGoodPulse(now)) {
            wantReload(now);
            return;
        }
        switch (fitOf(pulse)) {
            case OTHER_EPOCH -> wantReload(now);
            case AHEAD_OF_COPY -> markBehind(pulse.getRevision(), now);
            case AT_COPY -> lastGoodPulseAt = now;
            case BEHIND_COPY -> {
            }
        }
    }

    private boolean isTooLongWithoutGoodPulse(long now) {
        long lastConfirmedAt = lastGoodPulseAt != null ? lastGoodPulseAt : snapshotTakenAt;
        return now - lastConfirmedAt >= GOOD_PULSE_LIFETIME_MILLIS;
    }

    private PulseFit fitOf(StatePulse pulse) {
        if (pulse.getEpoch() != copy.epoch()) {
            return PulseFit.OTHER_EPOCH;
        }
        int order = Long.compareUnsigned(pulse.getRevision(), copy.revision());
        if (order > 0) {
            return PulseFit.AHEAD_OF_COPY;
        }
        if (order == 0) {
            return PulseFit.AT_COPY;
        }
        return PulseFit.BEHIND_COPY;
    }

    private void markBehind(long pulseRevision, long now) {
        if (behindPulse == null) {
            behindPulse = new BehindPulse(pulseRevision, now);
            return;
        }
        if (Long.compareUnsigned(pulseRevision, behindPulse.highestRevision()) > 0) {
            behindPulse = new BehindPulse(pulseRevision, behindPulse.since());
        }
    }

    // Audit (rule 7).

    private void startComparing(NetworkCopy snapshot, long now, List<ViewEffect> effects) {
        if (snapshot.epoch() != copy.epoch()) {
            wantReload(now);
            return;
        }
        pendingAudit = new PendingAudit(snapshot, now);
        continueAudit(now, effects);
    }

    /**
     * Compares once the copy is at exactly the audit snapshot's revision, and gives up once it is past it.
     */
    private void continueAudit(long now, List<ViewEffect> effects) {
        if (pendingAudit == null) {
            return;
        }
        NetworkCopy snapshot = pendingAudit.snapshot();
        int order = Long.compareUnsigned(copy.revision(), snapshot.revision());
        if (order > 0) {
            finishAudit(AuditOutcome.JUMPED_PAST, now, effects);
            return;
        }
        if (order == 0) {
            AuditOutcome outcome = sameItems(copy, snapshot) ? AuditOutcome.MATCHED : AuditOutcome.DIFFERED;
            finishAudit(outcome, now, effects);
        }
    }

    private void finishAudit(AuditOutcome outcome, long now, List<ViewEffect> effects) {
        NetworkCopy snapshot = pendingAudit.snapshot();
        pendingAudit = null;
        effects.add(new ViewEffect.AuditFinished(outcome));
        switch (outcome) {
            case MATCHED -> nextAuditAt = now + drawAuditInterval();
            case JUMPED_PAST -> nextAuditAt = now + drawAuditRetryDelay();
            case DIFFERED, NOT_REACHED -> takeSnapshotOrReload(snapshot, now, effects);
        }
    }

    // Own writes (Answers).

    private void recordOwnWrite(ViewInput.WriteReturned write) {
        Position written = new Position(write.epoch(), write.revision());
        for (ReadKind read : readsChangedBy(write.written())) {
            highestOwnWriteByRead.merge(read, written, Position::later);
        }
    }

    private static Set<ReadKind> readsChangedBy(WriteKind written) {
        return switch (written) {
            case SERVER -> EnumSet.of(ReadKind.SERVER, ReadKind.SERVER_GROUP, ReadKind.PERSISTENT_SERVER);
            case SERVER_GROUP, BLUEPRINT -> EnumSet.of(ReadKind.SERVER_GROUP, ReadKind.SERVER);
            case PERSISTENT_SERVER -> EnumSet.of(ReadKind.PERSISTENT_SERVER, ReadKind.SERVER);
        };
    }

    // The copy's items.

    private static NetworkCopy copyOf(NetworkState snapshot) {
        return new NetworkCopy(
                snapshot.getEpoch(),
                snapshot.getRevision(),
                byId(snapshot.getServersList(), StateServer::getId),
                byId(snapshot.getServerGroupsList(), StateServerGroup::getId),
                byId(snapshot.getPersistentServersList(), StatePersistentServer::getId),
                byId(snapshot.getBlueprintsList(), StateBlueprint::getId),
                byId(snapshot.getMetricsList(), StateServerMetrics::getServerId)
        );
    }

    private static NetworkCopy withBatch(NetworkCopy copy, StateBatch batch) {
        return new NetworkCopy(
                copy.epoch(),
                batch.getRevision(),
                replaced(copy.servers(), batch.getServersList(), batch.getDeletedServerIdsList(),
                        StateServer::getId),
                replaced(copy.serverGroups(), batch.getServerGroupsList(), batch.getDeletedServerGroupIdsList(),
                        StateServerGroup::getId),
                replaced(copy.persistentServers(), batch.getPersistentServersList(),
                        batch.getDeletedPersistentServerIdsList(), StatePersistentServer::getId),
                replaced(copy.blueprints(), batch.getBlueprintsList(), batch.getDeletedBlueprintIdsList(),
                        StateBlueprint::getId),
                copy.metrics()
        );
    }

    private static NetworkCopy withMetrics(NetworkCopy copy, List<StateServerMetrics> metrics) {
        return new NetworkCopy(copy.epoch(), copy.revision(), copy.servers(), copy.serverGroups(),
                copy.persistentServers(), copy.blueprints(), byId(metrics, StateServerMetrics::getServerId));
    }

    private static <T> Map<ByteString, T> byId(List<T> items, Function<T, ByteString> idOf) {
        Map<ByteString, T> byId = new HashMap<>();
        for (T item : items) {
            byId.put(idOf.apply(item), item);
        }
        return byId;
    }

    private static <T> Map<ByteString, T> replaced(Map<ByteString, T> current, List<T> changed,
                                                   List<ByteString> deletedIds, Function<T, ByteString> idOf) {
        Map<ByteString, T> replaced = new HashMap<>(current);
        replaced.putAll(byId(changed, idOf));
        for (ByteString deletedId : deletedIds) {
            replaced.remove(deletedId);
        }
        return replaced;
    }

    private static boolean referencesResolve(NetworkCopy copy) {
        for (StateServer server : copy.servers().values()) {
            boolean groupMissing = server.hasServerGroupId()
                    && !copy.serverGroups().containsKey(server.getServerGroupId());
            boolean persistentServerMissing = server.hasPersistentServerId()
                    && !copy.persistentServers().containsKey(server.getPersistentServerId());
            if (groupMissing || persistentServerMissing) {
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

    private static boolean sameItems(NetworkCopy first, NetworkCopy second) {
        return first.servers().equals(second.servers())
                && first.serverGroups().equals(second.serverGroups())
                && first.persistentServers().equals(second.persistentServers())
                && first.blueprints().equals(second.blueprints());
    }
}
