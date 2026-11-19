package app.simplecloud.api.internal.networkstate;

import build.buf.gen.simplecloud.controller.v2.NetworkState;
import build.buf.gen.simplecloud.controller.v2.StateBatch;
import build.buf.gen.simplecloud.controller.v2.StatePulse;

import java.util.ArrayList;
import java.util.List;

import static app.simplecloud.api.internal.networkstate.RuleTimes.GAP_WAIT;
import static app.simplecloud.api.internal.networkstate.RuleTimes.OWN_WRITE_WAIT;
import static app.simplecloud.api.internal.networkstate.RuleTimes.SHORTEST_AUDIT_RETRY;

/**
 * Machines that each break one rule of {@link NetworkView}, as subclasses of {@link ReferenceMachine}. The
 * checks must catch every one of them.
 */
final class WrongMachines {

    private WrongMachines() {
    }

    /**
     * @param caughtBy the names of the checks that must fail for this machine
     */
    record WrongMachine(String fault, ReferenceMachine machine, List<String> caughtBy) {

        ViewStarter starter() {
            return machine::start;
        }
    }

    static List<WrongMachine> all() {
        return List.of(
                new WrongMachine("applies a batch whose base is above the copy", new AppliesBatchAheadOfCopy(),
                        List.of("batch: holds a batch ahead of the copy until the gap fills",
                                ViewSimulation.SHORT_RUNS_CHECK)),
                new WrongMachine("ignores the epoch of a batch", new IgnoresBatchEpoch(),
                        List.of("batch: reloads on a batch from another epoch",
                                ViewSimulation.SHORT_RUNS_CHECK)),
                signedAt(ReferenceMachine.Comparison.BATCH_REVISION, "batch: compares revisions as unsigned numbers"),
                signedAt(ReferenceMachine.Comparison.BATCH_BASE,
                        "batch: a base at 2^63 is ahead of a copy at 2^63 - 1"),
                signedAt(ReferenceMachine.Comparison.PULSE_REVISION,
                        "pulse: a pulse at 2^63 is ahead of a copy at 2^63 - 1"),
                signedAt(ReferenceMachine.Comparison.FURTHER_PULSE,
                        "pulse: a pulse at 2^63 is further ahead than one at 2^63 - 1"),
                signedAt(ReferenceMachine.Comparison.REACHED_PULSE,
                        "pulse: a copy at 2^63 has reached a pulse at 2^63 - 1"),
                signedAt(ReferenceMachine.Comparison.OWN_WRITE_REACHED,
                        "own write: compares revisions at 2^63 unsigned"),
                signedAt(ReferenceMachine.Comparison.OWN_WRITE_KEPT,
                        "own write: keeps the higher revision at 2^63 unsigned"),
                signedAt(ReferenceMachine.Comparison.AUDIT_REVISION, "audit: compares revisions at 2^63 unsigned"),
                new WrongMachine("acts on the 3 s deadlines before the step's input", new DeadlinesBeforeInput(),
                        List.of("batch: a missing batch that arrives at exactly 3 s is on time",
                                "pulse: a batch that reaches a pulse ahead at exactly 3 s is on time",
                                "audit: reaching S at exactly 3 s is a compare")),
                new WrongMachine("acts on the behind-pulse deadline before the step's input",
                        new BehindDeadlineBeforeInput(),
                        List.of("pulse: a batch that reaches a pulse ahead at exactly 3 s is on time")),
                new WrongMachine("acts on the gap's deadline before the audit's", new GapDeadlineBeforeAudit(),
                        List.of("audit: the audit's deadline acts before the gap's")),
                new WrongMachine("counts a held batch's 3 s from the taken snapshot", new GapCountsFromSnapshot(),
                        List.of("batch: a held batch's 3 s count from its arrival during a load")),
                new WrongMachine("skips the gap deadline in the step that takes a snapshot",
                        new SkipsGapDeadlineWhenTaking(),
                        List.of("batch: a held batch's 3 s that ran out during the load reload when it is taken")),
                new WrongMachine("acts on the gap deadline while a reload's load runs", new GapDeadlineWhileReloading(),
                        List.of("batch: a load longer than 3 s is not abandoned for a waiting batch")),
                new WrongMachine("counts the gap's 3 s from the newest held batch", new GapCountsFromNewestBatch(),
                        List.of("batch: the gap's 3 s count from the oldest held batch")),
                new WrongMachine("counts the 3 s behind a pulse from the latest pulse ahead",
                        new BehindCountsFromLatestPulse(),
                        List.of("pulse: the 3 s behind a pulse count from the first pulse ahead")),
                new WrongMachine("applies a batch with a missing item", new AppliesBatchWithMissingItem(),
                        List.of("references: a batch with a missing item is not applied")),
                new WrongMachine("takes a snapshot with a missing item", new TakesSnapshotWithMissingItem(),
                        List.of("references: a snapshot with a missing item is not taken")),
                new WrongMachine("drops a batch from a lower epoch", new DropsBatchFromLowerEpoch(),
                        List.of("batch: reloads on a batch from a lower epoch")),
                new WrongMachine("ignores a pulse from a lower epoch", new IgnoresPulseFromLowerEpoch(),
                        List.of("pulse: reloads on a pulse from a lower epoch")),
                new WrongMachine("keeps the failed loads in a row after a taken snapshot",
                        new KeepsFailuresAfterSnapshot(),
                        List.of("reload: a taken snapshot ends the back-off")),
                new WrongMachine("keeps a wanted reload when the connection is lost", new KeepsWantedReloadOnLoss(),
                        List.of("connection: a loss drops a wanted reload")),
                new WrongMachine("keeps an audit waiting to compare when the connection is lost",
                        new KeepsComparingAuditOnLoss(),
                        List.of("connection: a loss abandons an audit waiting to compare")),
                new WrongMachine("counts the failure of an abandoned load", new CountsFailureOfAbandonedLoad(),
                        List.of("connection: a failed answer of an abandoned load is no failed load")),
                new WrongMachine("keeps the batches that arrive while disconnected",
                        new KeepsBatchesWhileDisconnected(),
                        List.of("connection: ignores batches until the subscriptions are active again")),
                new WrongMachine("replaces metrics only while synced", new ReplacesMetricsOnlyWhileSynced(),
                        List.of("pulse: a pulse replaces the metrics also while disconnected")),
                new WrongMachine("starts an audit while the view is not live", new AuditsWhileNotLive(),
                        List.of("audit: no audit starts while the view is not live")),
                new WrongMachine("always draws the first random number", new AlwaysDrawsFirstNumber(),
                        List.of("reload: every random delay draws a new number")),
                new WrongMachine("waits for a missing batch for ever", new WaitsForGapForever(),
                        List.of("batch: reloads after a gap held for 3 s",
                                "batch: reloads 3 s after a batch with base = revision above the copy")),
                new WrongMachine("skips the reference check", new SkipsReferenceCheck(),
                        List.of("references: reloads when a server's group is missing",
                                "references: reloads when a server's persistent server is missing",
                                "references: reloads when a group's blueprint is missing",
                                "references: reloads when a batch deletes a group a server still uses",
                                "references: a snapshot with a missing item counts as a failed load")),
                new WrongMachine("counts the snapshot as a pulse", new CountsSnapshotAsPulse(),
                        List.of("start: live only after the first good pulse",
                                "connection: reloads after a reconnect")),
                new WrongMachine("counts a pulse below the copy as good", new CountsLowerPulseAsGood(),
                        List.of("pulse: a lower revision neither reloads nor counts as good")),
                new WrongMachine("ignores the epoch of a pulse", new IgnoresPulseEpoch(),
                        List.of("pulse: reloads on a pulse from another epoch")),
                new WrongMachine("never lets the last good pulse age", new GoodPulseNeverAges(),
                        List.of("pulse: a lower revision neither reloads nor counts as good",
                                "pulse: not live 15 s after the last good pulse",
                                ViewSimulation.SHORT_RUNS_CHECK)),
                new WrongMachine("stays behind a pulse for ever", new StaysBehindPulseForever(),
                        List.of("pulse: reloads when the copy stays behind a pulse for 3 s")),
                new WrongMachine("ignores views_may_answer", new IgnoresViewsMayAnswer(),
                        List.of("pulse: views_may_answer false stops answers")),
                new WrongMachine("stays live after the connection is lost", new IgnoresConnectionLoss(),
                        List.of("connection: not live at once after the loss",
                                "connection: ignores batches until the subscriptions are active again")),
                new WrongMachine("takes a load that ran across the connection loss",
                        new TakesLoadAcrossConnectionLoss(),
                        List.of("connection: ignores the answer of a load that ran across the loss")),
                new WrongMachine("starts a load less than 2 s after the previous one", new IgnoresLoadSpacing(),
                        List.of("reload: loads at least 2 s apart", ViewSimulation.SHORT_RUNS_CHECK)),
                new WrongMachine("does not back off after failed loads", new IgnoresBackOff(),
                        List.of("reload: backs off up to 30 s after failed loads")),
                new WrongMachine("adds no random delay to a reload", new SkipsRandomLoadDelay(),
                        List.of("start: delays the first load by up to 2 s of random delay",
                                "reload: adds 2 s times a random number to every reload")),
                new WrongMachine("compares the audit snapshot on arrival, while the copy is below S",
                        new ComparesAuditOnArrival(),
                        List.of("audit: compares when the copy reaches exactly S")),
                new WrongMachine("keeps the metrics when it takes a snapshot", new KeepsMetricsOnSnapshot(),
                        List.of("reload: a taken snapshot replaces the copy's metrics")),
                new WrongMachine("keeps the first pulse ahead, not the highest", new KeepsFirstPulseAhead(),
                        List.of("pulse: a pulse further ahead keeps the copy behind until it reaches that")),
                new WrongMachine("reloads after 15 s without a good pulse only if one came since the snapshot",
                        new NeedsGoodPulseBeforeSilenceReload(),
                        List.of("pulse: without a good pulse, the first pulse 15 s after the snapshot reloads")),
                new WrongMachine("audits again 2 s after a match", new AuditsAgainSoonAfterMatch(),
                        List.of("audit: after a match, the next audit comes 20 min later")),
                new WrongMachine("retries a failed audit load at once", new RetriesFailedAuditLoadAtOnce(),
                        List.of("audit: after a failed audit load, the next audit comes 20 min later")),
                new WrongMachine("counts a failed audit load as a failed reload",
                        new CountsFailedAuditLoadAsFailedReload(),
                        List.of("audit: a failed audit load does not back off reloads")),
                new WrongMachine("compares an audit snapshot from another epoch", new ComparesAuditOfAnotherEpoch(),
                        List.of("audit: an audit snapshot from another epoch reloads")),
                new WrongMachine("never lets a read wait for an own write", new NeverWaitsForOwnWrite(),
                        List.of("own write: a read waits while the copy is below the write's revision in its epoch",
                                ViewSimulation.SHORT_RUNS_CHECK)),
                new WrongMachine("counts an own write reached only once the copy is above its revision",
                        new ReachedOnlyAboveRevision(),
                        List.of("own write: the copy answers once it reaches the write's revision, at it or above")),
                new WrongMachine("counts an own write reached only while the copy is at exactly its revision",
                        new ReachedOnlyAtRevision(),
                        List.of("own write: the copy answers once it reaches the write's revision, at it or above")),
                new WrongMachine("lets a read wait that the copy may answer", new WaitsWhenCopyMayAnswer(),
                        List.of("own write: the copy answers once it reaches the write's revision, at it or above")),
                new WrongMachine("lets a read wait for an own write for ever", new WaitsForOwnWriteForEver(),
                        List.of("own write: exactly 3 s after the write the copy answers, though it lacks the write",
                                ViewSimulation.SHORT_RUNS_CHECK)),
                new WrongMachine("still lets a read wait exactly 3 s after the own write", new WaitsAtThreeSeconds(),
                        List.of("own write: exactly 3 s after the write the copy answers, though it lacks the write")),
                new WrongMachine("keeps the last own write's revision, also when it is lower in the same epoch",
                        new KeepsLastRevision(),
                        List.of("own write: a later write restarts the 3 s, and in one epoch the higher revision"
                                + " stays")),
                new WrongMachine("keeps the first own write's time when a later one returns",
                        new KeepsFirstWriteTime(),
                        List.of("own write: a later write restarts the 3 s, and in one epoch the higher revision"
                                + " stays")),
                new WrongMachine("orders the epochs of own writes and keeps the write of the higher one",
                        new KeepsOwnWriteOfHigherEpoch(),
                        List.of("own write: a write from another epoch replaces the kept write, whatever the"
                                + " revisions")),
                new WrongMachine("keeps the higher revision of own writes also across epochs",
                        new KeepsHigherRevisionAcrossEpochs(),
                        List.of("own write: a write from another epoch replaces the kept write, whatever the"
                                + " revisions")),
                new WrongMachine("does not reload on an own write from another epoch than the copy's",
                        new NoReloadOnOwnWriteOfOtherEpoch(),
                        List.of("own write: in a synced view, a write from another epoch than the copy's reloads")),
                new WrongMachine("reloads only on an own write from a higher epoch than the copy's",
                        new ReloadsOnlyOnOwnWriteOfHigherEpoch(),
                        List.of("own write: in a synced view, a write from another epoch than the copy's reloads")),
                new WrongMachine("counts an own write reached at its revision in any epoch", new ReachedInAnyEpoch(),
                        List.of("own write: a copy in another epoch than the write waits only until 3 s after the"
                                + " write")),
                new WrongMachine("lets a read wait beyond 3 s for an own write from another epoch than the copy's",
                        new WaitsForOwnWriteOfOtherEpochForEver(),
                        List.of("own write: a copy in another epoch than the write waits only until 3 s after the"
                                + " write")),
                new WrongMachine("reloads on an own write from another epoch in every phase",
                        new ReloadsOnOwnWriteOfOtherEpochInEveryPhase(),
                        List.of("own write: a write while disconnected, wanting a reload or reloading starts no"
                                + " load")),
                new WrongMachine("lets a read wait for an own write while the view is not live",
                        new WaitsWhileNotLive(),
                        List.of("own write wait: no read waits while a batch is held",
                                "own write wait: no read waits while the copy is behind a pulse",
                                "own write wait: no read waits once the last good pulse is 15 s old",
                                "own write wait: no read waits after the connection is lost",
                                "own write wait: no read waits while a reload is wanted or runs",
                                ViewSimulation.SHORT_RUNS_CHECK)),
                new WrongMachine("lets a read wait for an own write while views_may_answer is false",
                        new WaitsWhileAnswersSwitchedOff(),
                        List.of("own write wait: no read waits while views_may_answer is false"))
        );
    }

    static final class AppliesBatchAheadOfCopy extends ReferenceMachine {
        @Override
        BatchFit fitOf(NetworkCopy copy, StateBatch batch) {
            BatchFit fit = super.fitOf(copy, batch);
            return fit == BatchFit.AHEAD_OF_COPY ? BatchFit.FITS_COPY : fit;
        }
    }

    static final class IgnoresBatchEpoch extends ReferenceMachine {
        @Override
        BatchFit fitOf(NetworkCopy copy, StateBatch batch) {
            StateBatch asIfSameEpoch = batch.toBuilder().setEpoch(copy.epoch()).build();
            return super.fitOf(copy, asIfSameEpoch);
        }
    }

    private static WrongMachine signedAt(ReferenceMachine.Comparison where, String caughtBy) {
        String fault = "compares " + where + " signed";
        return new WrongMachine(fault, new ComparesSignedAt(where), List.of(caughtBy));
    }

    static final class ComparesSignedAt extends ReferenceMachine {
        private final Comparison signedComparison;

        ComparesSignedAt(Comparison signedComparison) {
            this.signedComparison = signedComparison;
        }

        @Override
        int compare(Comparison where, long first, long second) {
            if (where == signedComparison) {
                return Long.compare(first, second);
            }
            return super.compare(where, first, second);
        }
    }

    /**
     * Acts on them again after the input, so loads still start last.
     */
    static final class DeadlinesBeforeInput extends ReferenceMachine {
        @Override
        void runStep(Memory m, ViewInput input, long now, List<ViewEffect> effects) {
            if (m.mode == Mode.SYNCED) {
                actOnSyncedDeadlines(m, now, effects);
            }
            super.runStep(m, input, now, effects);
        }
    }

    static final class BehindDeadlineBeforeInput extends ReferenceMachine {
        @Override
        void runStep(Memory m, ViewInput input, long now, List<ViewEffect> effects) {
            if (m.mode == Mode.SYNCED && behindPulseTooLong(m, now)) {
                wantReload(m, now);
            }
            super.runStep(m, input, now, effects);
        }
    }

    static final class GapDeadlineBeforeAudit extends ReferenceMachine {
        @Override
        void actOnSyncedDeadlines(Memory m, long now, List<ViewEffect> effects) {
            if (heldTooLong(m, now)) {
                wantReload(m, now);
                return;
            }
            super.actOnSyncedDeadlines(m, now, effects);
        }
    }

    static final class GapCountsFromSnapshot extends ReferenceMachine {
        @Override
        void takeSnapshot(Memory m, NetworkState snapshot, long now, List<ViewEffect> effects) {
            List<HeldBatch> restamped = new ArrayList<>();
            for (HeldBatch held : m.heldBatches) {
                restamped.add(new HeldBatch(held.batch(), now));
            }
            m.heldBatches = restamped;
            super.takeSnapshot(m, snapshot, now, effects);
        }
    }

    static final class SkipsGapDeadlineWhenTaking extends ReferenceMachine {
        @Override
        boolean heldTooLong(Memory m, long now) {
            return m.snapshotTakenAt != now && super.heldTooLong(m, now);
        }
    }

    static final class GapDeadlineWhileReloading extends ReferenceMachine {
        @Override
        void actOnDeadlines(Memory m, long now, List<ViewEffect> effects) {
            if (m.mode == Mode.RELOADING && heldTooLong(m, now)) {
                wantReload(m, now);
            }
            super.actOnDeadlines(m, now, effects);
        }
    }

    static final class GapCountsFromNewestBatch extends ReferenceMachine {
        @Override
        boolean heldTooLong(Memory m, long now) {
            if (m.heldBatches.isEmpty()) {
                return false;
            }
            HeldBatch newest = m.heldBatches.getLast();
            return now - newest.arrivedAt() >= GAP_WAIT;
        }
    }

    static final class BehindCountsFromLatestPulse extends ReferenceMachine {
        @Override
        void onPulse(Memory m, StatePulse pulse, long now) {
            BehindPulse before = m.behindPulse;
            super.onPulse(m, pulse, now);
            boolean raised = before != null
                    && m.behindPulse != null
                    && m.behindPulse.highestRevision() != before.highestRevision();
            if (raised) {
                m.behindPulse = new BehindPulse(m.behindPulse.highestRevision(), now);
            }
        }
    }

    static final class AppliesBatchWithMissingItem extends ReferenceMachine {
        @Override
        void applyBatch(Memory m, StateBatch batch, long now, List<ViewEffect> effects) {
            NetworkCopy applied = applied(m.copy, batch);
            if (!referencesResolve(applied)) {
                m.copy = applied;
            }
            super.applyBatch(m, batch, now, effects);
        }
    }

    static final class TakesSnapshotWithMissingItem extends ReferenceMachine {
        @Override
        void takeSnapshot(Memory m, NetworkState snapshot, long now, List<ViewEffect> effects) {
            NetworkCopy taken = copyOf(snapshot);
            if (!referencesResolve(taken)) {
                m.copy = taken;
            }
            super.takeSnapshot(m, snapshot, now, effects);
        }
    }

    static final class DropsBatchFromLowerEpoch extends ReferenceMachine {
        @Override
        BatchFit fitOf(NetworkCopy copy, StateBatch batch) {
            boolean lowerEpoch = Long.compareUnsigned(batch.getEpoch(), copy.epoch()) < 0;
            return lowerEpoch ? BatchFit.ALREADY_IN_COPY : super.fitOf(copy, batch);
        }
    }

    static final class IgnoresPulseFromLowerEpoch extends ReferenceMachine {
        @Override
        PulseFit fitOf(NetworkCopy copy, StatePulse pulse) {
            boolean lowerEpoch = Long.compareUnsigned(pulse.getEpoch(), copy.epoch()) < 0;
            return lowerEpoch ? PulseFit.BEHIND_COPY : super.fitOf(copy, pulse);
        }
    }

    static final class KeepsFailuresAfterSnapshot extends ReferenceMachine {
        @Override
        void takeSnapshot(Memory m, NetworkState snapshot, long now, List<ViewEffect> effects) {
            int failedLoadsInRow = m.failedLoadsInRow;
            long lastLoadFailedAt = m.lastLoadFailedAt;
            super.takeSnapshot(m, snapshot, now, effects);
            if (m.mode == Mode.SYNCED) {
                m.failedLoadsInRow = failedLoadsInRow;
                m.lastLoadFailedAt = lastLoadFailedAt;
            }
        }
    }

    static final class KeepsWantedReloadOnLoss extends ReferenceMachine {
        @Override
        void loseConnection(Memory m) {
            boolean reloadWanted = m.mode == Mode.RELOAD_WANTED;
            super.loseConnection(m);
            if (reloadWanted) {
                m.mode = Mode.RELOAD_WANTED;
            }
        }
    }

    static final class KeepsComparingAuditOnLoss extends ReferenceMachine {
        @Override
        void loseConnection(Memory m) {
            AuditPhase phase = m.auditPhase;
            NetworkState snapshot = m.auditSnapshot;
            super.loseConnection(m);
            if (phase == AuditPhase.COMPARING) {
                m.auditPhase = phase;
                m.auditSnapshot = snapshot;
            }
        }

        @Override
        void actOnDeadlines(Memory m, long now, List<ViewEffect> effects) {
            super.actOnDeadlines(m, now, effects);
            if (m.mode == Mode.DISCONNECTED) {
                takeAuditSnapshotIfNotReached(m, now, effects);
            }
        }
    }

    static final class CountsFailureOfAbandonedLoad extends ReferenceMachine {
        @Override
        void onSnapshotFailed(Memory m, long now) {
            boolean abandoned = m.loadRunning && m.loadAnswerIgnored;
            if (abandoned) {
                m.failedLoadsInRow++;
                m.lastLoadFailedAt = now;
            }
            super.onSnapshotFailed(m, now);
        }
    }

    static final class KeepsBatchesWhileDisconnected extends ReferenceMachine {
        @Override
        void onBatch(Memory m, StateBatch batch, long now, List<ViewEffect> effects) {
            if (m.mode == Mode.DISCONNECTED) {
                m.heldBatches.add(new HeldBatch(batch, now));
                return;
            }
            super.onBatch(m, batch, now, effects);
        }

        /**
         * Keeps them across the reconnect's reload, which drops held batches.
         */
        @Override
        void wantReload(Memory m, long now) {
            boolean reconnect = m.mode == Mode.DISCONNECTED;
            List<HeldBatch> keptAcrossReconnect = reconnect ? List.copyOf(m.heldBatches) : List.of();
            super.wantReload(m, now);
            m.heldBatches.addAll(keptAcrossReconnect);
        }
    }

    static final class ReplacesMetricsOnlyWhileSynced extends ReferenceMachine {
        @Override
        void onPulse(Memory m, StatePulse pulse, long now) {
            NetworkCopy copyBefore = m.copy;
            boolean synced = m.mode == Mode.SYNCED;
            super.onPulse(m, pulse, now);
            if (!synced) {
                m.copy = copyBefore;
            }
        }
    }

    static final class AuditsWhileNotLive extends ReferenceMachine {
        @Override
        boolean auditMayStart(Memory m, long now) {
            return true;
        }
    }

    static final class AlwaysDrawsFirstNumber extends ReferenceMachine {
        @Override
        double drawNumber(Memory m) {
            return m.random.at(0);
        }
    }

    static final class WaitsForGapForever extends ReferenceMachine {
        @Override
        boolean heldTooLong(Memory m, long now) {
            return false;
        }
    }

    static final class SkipsReferenceCheck extends ReferenceMachine {
        @Override
        boolean referencesResolve(NetworkCopy copy) {
            return true;
        }
    }

    static final class CountsSnapshotAsPulse extends ReferenceMachine {
        @Override
        void takeSnapshot(Memory m, NetworkState snapshot, long now, List<ViewEffect> effects) {
            super.takeSnapshot(m, snapshot, now, effects);
            m.goodPulseAt = now;
        }
    }

    static final class CountsLowerPulseAsGood extends ReferenceMachine {
        @Override
        PulseFit fitOf(NetworkCopy copy, StatePulse pulse) {
            PulseFit fit = super.fitOf(copy, pulse);
            return fit == PulseFit.BEHIND_COPY ? PulseFit.MATCHES_COPY : fit;
        }
    }

    static final class IgnoresPulseEpoch extends ReferenceMachine {
        @Override
        PulseFit fitOf(NetworkCopy copy, StatePulse pulse) {
            StatePulse asIfSameEpoch = pulse.toBuilder().setEpoch(copy.epoch()).build();
            return super.fitOf(copy, asIfSameEpoch);
        }
    }

    static final class GoodPulseNeverAges extends ReferenceMachine {
        @Override
        boolean isLive(Memory m, long now) {
            long asIfAtTheGoodPulse = m.goodPulseAt == null ? now : m.goodPulseAt;
            return super.isLive(m, asIfAtTheGoodPulse);
        }
    }

    static final class StaysBehindPulseForever extends ReferenceMachine {
        @Override
        boolean behindPulseTooLong(Memory m, long now) {
            return false;
        }
    }

    static final class IgnoresViewsMayAnswer extends ReferenceMachine {
        @Override
        boolean answersAllowed(Memory m, long now) {
            return isLive(m, now);
        }
    }

    static final class IgnoresConnectionLoss extends ReferenceMachine {
        @Override
        void loseConnection(Memory m) {
        }
    }

    /**
     * The loss leaves the running load counted, and the reconnect takes it as its reload instead of starting
     * one.
     */
    static final class TakesLoadAcrossConnectionLoss extends ReferenceMachine {
        @Override
        void loseConnection(Memory m) {
            boolean ignoredBeforeLoss = m.loadAnswerIgnored;
            super.loseConnection(m);
            m.loadAnswerIgnored = ignoredBeforeLoss;
        }

        @Override
        void wantReload(Memory m, long now) {
            boolean reconnectWhileCountedLoadRuns = m.mode == Mode.DISCONNECTED
                    && m.loadRunning
                    && !m.loadAnswerIgnored;
            if (reconnectWhileCountedLoadRuns) {
                m.mode = Mode.RELOADING;
                return;
            }
            super.wantReload(m, now);
        }
    }

    static final class IgnoresLoadSpacing extends ReferenceMachine {
        @Override
        long earliestSpacedStart(Memory m) {
            return Long.MIN_VALUE;
        }
    }

    static final class IgnoresBackOff extends ReferenceMachine {
        @Override
        long earliestStartAfterBackOff(Memory m) {
            return Long.MIN_VALUE;
        }
    }

    static final class SkipsRandomLoadDelay extends ReferenceMachine {
        /**
         * Still draws the number, so every later delay draws the one it would have drawn.
         */
        @Override
        long randomLoadDelay(Memory m) {
            drawNumber(m);
            return 0;
        }
    }

    static final class ComparesAuditOnArrival extends ReferenceMachine {
        @Override
        void onAuditSnapshot(Memory m, NetworkState snapshot, long now, List<ViewEffect> effects) {
            super.onAuditSnapshot(m, snapshot, now, effects);
            boolean waitsForCopyToReachSnapshot = m.auditPhase == AuditPhase.COMPARING;
            if (waitsForCopyToReachSnapshot) {
                compareAudit(m, now, effects);
            }
        }
    }

    static final class KeepsMetricsOnSnapshot extends ReferenceMachine {
        @Override
        void takeSnapshot(Memory m, NetworkState snapshot, long now, List<ViewEffect> effects) {
            NetworkCopy before = m.copy;
            super.takeSnapshot(m, snapshot, now, effects);
            if (before != null) {
                m.copy = withMetrics(m.copy, List.copyOf(before.metrics().values()));
            }
        }
    }

    static final class KeepsFirstPulseAhead extends ReferenceMachine {
        @Override
        void onPulse(Memory m, StatePulse pulse, long now) {
            BehindPulse first = m.behindPulse;
            super.onPulse(m, pulse, now);
            if (first != null && m.behindPulse != null) {
                m.behindPulse = first;
            }
        }
    }

    static final class NeedsGoodPulseBeforeSilenceReload extends ReferenceMachine {
        @Override
        boolean lacksGoodPulse(Memory m, long now) {
            return m.goodPulseAt != null && super.lacksGoodPulse(m, now);
        }
    }

    static final class AuditsAgainSoonAfterMatch extends ReferenceMachine {
        @Override
        void compareAudit(Memory m, long now, List<ViewEffect> effects) {
            super.compareAudit(m, now, effects);
            boolean matched = effects.getLast().equals(new ViewEffect.AuditFinished(AuditOutcome.MATCHED));
            if (matched) {
                m.auditDueAt = now + SHORTEST_AUDIT_RETRY;
            }
        }
    }

    static final class RetriesFailedAuditLoadAtOnce extends ReferenceMachine {
        @Override
        void onSnapshotFailed(Memory m, long now) {
            long dueBefore = m.auditDueAt;
            boolean auditLoad = m.loadRunning && !m.loadAnswerIgnored && m.auditPhase == AuditPhase.LOADING;
            super.onSnapshotFailed(m, now);
            if (auditLoad) {
                m.auditDueAt = dueBefore;
            }
        }
    }

    static final class CountsFailedAuditLoadAsFailedReload extends ReferenceMachine {
        @Override
        void onSnapshotFailed(Memory m, long now) {
            boolean auditLoad = m.loadRunning && !m.loadAnswerIgnored && m.auditPhase == AuditPhase.LOADING;
            super.onSnapshotFailed(m, now);
            if (auditLoad) {
                m.failedLoadsInRow++;
                m.lastLoadFailedAt = now;
            }
        }
    }

    static final class ComparesAuditOfAnotherEpoch extends ReferenceMachine {
        @Override
        void onAuditSnapshot(Memory m, NetworkState snapshot, long now, List<ViewEffect> effects) {
            NetworkState asIfSameEpoch = snapshot.toBuilder().setEpoch(m.copy.epoch()).build();
            super.onAuditSnapshot(m, asIfSameEpoch, now, effects);
        }
    }

    static final class NeverWaitsForOwnWrite extends ReferenceMachine {
        @Override
        boolean awaitsOwnWrite(Memory m, long now) {
            return false;
        }
    }

    static final class ReachedOnlyAboveRevision extends ReferenceMachine {
        @Override
        boolean copyHasOwnWrite(Memory m) {
            OwnWrite kept = m.keptOwnWrite;
            boolean atRevision = kept != null && m.copy.revision() == kept.revision();
            return !atRevision && super.copyHasOwnWrite(m);
        }
    }

    static final class ReachedOnlyAtRevision extends ReferenceMachine {
        @Override
        boolean copyHasOwnWrite(Memory m) {
            OwnWrite kept = m.keptOwnWrite;
            boolean aboveRevision = kept != null && m.copy.revision() != kept.revision();
            return !aboveRevision && super.copyHasOwnWrite(m);
        }
    }

    static final class WaitsWhenCopyMayAnswer extends ReferenceMachine {
        @Override
        boolean awaitsOwnWrite(Memory m, long now) {
            return m.keptOwnWrite != null && !ownWriteWaitIsOver(m, now);
        }
    }

    static final class WaitsForOwnWriteForEver extends ReferenceMachine {
        @Override
        boolean ownWriteWaitIsOver(Memory m, long now) {
            return false;
        }
    }

    static final class WaitsAtThreeSeconds extends ReferenceMachine {
        @Override
        boolean ownWriteWaitIsOver(Memory m, long now) {
            OwnWrite kept = m.keptOwnWrite;
            return kept == null || now - kept.handedOverAt() > OWN_WRITE_WAIT;
        }
    }

    static final class KeepsLastRevision extends ReferenceMachine {
        @Override
        void keepOwnWrite(Memory m, ViewInput.WriteReturned write, long now) {
            m.keptOwnWrite = new OwnWrite(write.epoch(), write.revision(), now);
        }
    }

    static final class KeepsFirstWriteTime extends ReferenceMachine {
        @Override
        void keepOwnWrite(Memory m, ViewInput.WriteReturned write, long now) {
            long firstHandedOverAt = m.keptOwnWrite == null ? now : m.keptOwnWrite.handedOverAt();
            super.keepOwnWrite(m, write, now);
            OwnWrite kept = m.keptOwnWrite;
            m.keptOwnWrite = new OwnWrite(kept.epoch(), kept.revision(), firstHandedOverAt);
        }
    }

    static final class KeepsOwnWriteOfHigherEpoch extends ReferenceMachine {
        @Override
        void keepOwnWrite(Memory m, ViewInput.WriteReturned write, long now) {
            OwnWrite kept = m.keptOwnWrite;
            boolean lowerEpoch = kept != null && Long.compareUnsigned(write.epoch(), kept.epoch()) < 0;
            if (!lowerEpoch) {
                super.keepOwnWrite(m, write, now);
            }
        }
    }

    static final class KeepsHigherRevisionAcrossEpochs extends ReferenceMachine {
        @Override
        void keepOwnWrite(Memory m, ViewInput.WriteReturned write, long now) {
            OwnWrite kept = m.keptOwnWrite;
            boolean keptIsHigherInOtherEpoch = kept != null
                    && kept.epoch() != write.epoch()
                    && Long.compareUnsigned(kept.revision(), write.revision()) > 0;
            if (keptIsHigherInOtherEpoch) {
                m.keptOwnWrite = new OwnWrite(write.epoch(), kept.revision(), now);
                return;
            }
            super.keepOwnWrite(m, write, now);
        }
    }

    static final class NoReloadOnOwnWriteOfOtherEpoch extends ReferenceMachine {
        @Override
        boolean writeCutsHistory(Memory m, ViewInput.WriteReturned write) {
            return false;
        }
    }

    static final class ReloadsOnlyOnOwnWriteOfHigherEpoch extends ReferenceMachine {
        @Override
        boolean writeCutsHistory(Memory m, ViewInput.WriteReturned write) {
            return super.writeCutsHistory(m, write) && Long.compareUnsigned(write.epoch(), m.copy.epoch()) > 0;
        }
    }

    static final class ReachedInAnyEpoch extends ReferenceMachine {
        @Override
        boolean copyHasOwnWrite(Memory m) {
            OwnWrite kept = m.keptOwnWrite;
            return kept == null || Long.compareUnsigned(m.copy.revision(), kept.revision()) >= 0;
        }
    }

    static final class WaitsForOwnWriteOfOtherEpochForEver extends ReferenceMachine {
        @Override
        boolean ownWriteWaitIsOver(Memory m, long now) {
            OwnWrite kept = m.keptOwnWrite;
            boolean otherEpoch = kept != null && m.copy.epoch() != kept.epoch();
            return !otherEpoch && super.ownWriteWaitIsOver(m, now);
        }
    }

    static final class ReloadsOnOwnWriteOfOtherEpochInEveryPhase extends ReferenceMachine {
        @Override
        boolean writeCutsHistory(Memory m, ViewInput.WriteReturned write) {
            return m.copy == null || write.epoch() != m.copy.epoch();
        }
    }

    static final class WaitsWhileNotLive extends ReferenceMachine {
        @Override
        ReadRoute routeRead(Memory m, long now) {
            boolean waits = m.copy != null && m.viewsMayAnswer && awaitsOwnWrite(m, now);
            return waits ? ReadRoute.WAIT : super.routeRead(m, now);
        }
    }

    static final class WaitsWhileAnswersSwitchedOff extends ReferenceMachine {
        @Override
        ReadRoute routeRead(Memory m, long now) {
            boolean waits = isLive(m, now) && awaitsOwnWrite(m, now);
            return waits ? ReadRoute.WAIT : super.routeRead(m, now);
        }
    }
}
