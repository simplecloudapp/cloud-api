package app.simplecloud.api.internal.networkstate;

import build.buf.gen.simplecloud.controller.v2.NetworkState;
import build.buf.gen.simplecloud.controller.v2.StateBatch;
import build.buf.gen.simplecloud.controller.v2.StatePulse;

import java.util.ArrayList;
import java.util.List;

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
    record WrongMachine(String brokenRule, ViewStarter starter, List<String> caughtBy) {
    }

    static List<WrongMachine> all() {
        return List.of(
                new WrongMachine("applies a batch whose base is above the copy",
                        random -> new AppliesBatchAheadOfCopy().start(random),
                        List.of("batch: holds a batch ahead of the copy until the gap fills",
                                ViewSimulation.SHORT_RUNS_CHECK)),
                new WrongMachine("applies a batch from another epoch",
                        random -> new AppliesBatchFromAnotherEpoch().start(random),
                        List.of("batch: reloads on a batch from another epoch",
                                ViewSimulation.SHORT_RUNS_CHECK)),
                signedAt(ReferenceMachine.Comparison.BATCH_REVISION, "batch: compares revisions as unsigned numbers"),
                signedAt(ReferenceMachine.Comparison.BATCH_BASE, "batch: a base past 2^63 is ahead of a copy below it"),
                signedAt(ReferenceMachine.Comparison.PULSE_REVISION,
                        "pulse: a pulse past 2^63 is ahead of a copy below it"),
                signedAt(ReferenceMachine.Comparison.FURTHER_PULSE,
                        "pulse: a pulse past 2^63 is further ahead than one below it"),
                signedAt(ReferenceMachine.Comparison.REACHED_PULSE,
                        "pulse: a copy past 2^63 has reached a pulse below it"),
                signedAt(ReferenceMachine.Comparison.OWN_WRITE_EPOCH, "own write: compares epochs past 2^63 unsigned"),
                signedAt(ReferenceMachine.Comparison.OWN_WRITE_REVISION,
                        "own write: compares revisions past 2^63 unsigned"),
                signedAt(ReferenceMachine.Comparison.AUDIT_REVISION, "audit: compares revisions past 2^63 unsigned"),
                new WrongMachine("acts on deadlines before the step's input",
                        random -> new DeadlinesBeforeInput().start(random),
                        List.of("batch: a missing batch that arrives at exactly 3 s is on time",
                                "audit: reaching S at exactly 3 s is a compare")),
                new WrongMachine("acts on the gap's deadline before the audit's",
                        random -> new GapDeadlineBeforeAudit().start(random),
                        List.of("audit: the audit's deadline acts before the gap's")),
                new WrongMachine("counts a held batch's 3 s from the taken snapshot",
                        random -> new GapCountsFromSnapshot().start(random),
                        List.of("batch: a held batch's 3 s count from its arrival during a load")),
                new WrongMachine("acts on the behind-pulse deadline before the step's input",
                        random -> new BehindDeadlineBeforeInput().start(random),
                        List.of("pulse: a batch that reaches a pulse ahead at exactly 3 s is on time")),
                new WrongMachine("skips the gap deadline in the step that takes a snapshot",
                        random -> new SkipsGapDeadlineWhenTaking().start(random),
                        List.of("batch: a held batch's 3 s that ran out during the load reload when it is taken")),
                new WrongMachine("acts on the gap deadline while a reload's load runs",
                        random -> new GapDeadlineWhileReloading().start(random),
                        List.of("batch: a load longer than 3 s is not abandoned for a waiting batch")),
                new WrongMachine("counts the gap's 3 s from the newest held batch",
                        random -> new GapCountsFromNewestBatch().start(random),
                        List.of("batch: the gap's 3 s count from the oldest held batch")),
                new WrongMachine("counts the 3 s behind a pulse from the latest pulse ahead",
                        random -> new BehindCountsFromLatestPulse().start(random),
                        List.of("pulse: the 3 s behind a pulse count from the first pulse ahead")),
                new WrongMachine("applies a batch with a missing item",
                        random -> new AppliesBatchWithMissingItem().start(random),
                        List.of("references: a batch with a missing item is not applied")),
                new WrongMachine("takes a snapshot with a missing item",
                        random -> new TakesSnapshotWithMissingItem().start(random),
                        List.of("references: a snapshot with a missing item is not taken")),
                new WrongMachine("drops a batch from a lower epoch",
                        random -> new DropsBatchFromLowerEpoch().start(random),
                        List.of("batch: reloads on a batch from a lower epoch")),
                new WrongMachine("ignores a pulse from a lower epoch",
                        random -> new IgnoresPulseFromLowerEpoch().start(random),
                        List.of("pulse: reloads on a pulse from a lower epoch")),
                new WrongMachine("keeps the failed loads in a row after a taken snapshot",
                        random -> new KeepsFailuresAfterSnapshot().start(random),
                        List.of("reload: a taken snapshot ends the back-off")),
                new WrongMachine("keeps a wanted reload when the connection is lost",
                        random -> new KeepsWantedReloadOnLoss().start(random),
                        List.of("connection: a loss drops a wanted reload")),
                new WrongMachine("keeps an audit waiting to compare when the connection is lost",
                        random -> new KeepsComparingAuditOnLoss().start(random),
                        List.of("connection: a loss abandons an audit waiting to compare")),
                new WrongMachine("counts the failure of an abandoned load",
                        random -> new CountsFailureOfAbandonedLoad().start(random),
                        List.of("connection: a failed answer of an abandoned load is no failed load")),
                new WrongMachine("replaces metrics only while synced",
                        random -> new ReplacesMetricsOnlyWhileSynced().start(random),
                        List.of("pulse: a pulse replaces the metrics also while disconnected")),
                new WrongMachine("starts an audit while the view is not live",
                        random -> new AuditsWhileNotLive().start(random),
                        List.of("audit: no audit starts while the view is not live")),
                new WrongMachine("always draws the first random number",
                        random -> new AlwaysDrawsFirstNumber().start(random),
                        List.of("reload: every random delay draws a new number")),
                new WrongMachine("waits for a missing batch for ever",
                        random -> new WaitsForGapForever().start(random),
                        List.of("batch: reloads after a gap held for 3 s",
                                "batch: reloads after a batch with base = revision above the copy")),
                new WrongMachine("skips the reference check",
                        random -> new SkipsReferenceCheck().start(random),
                        List.of("references: reloads when a server's group is missing",
                                "references: reloads when a server's persistent server is missing",
                                "references: reloads when a group's blueprint is missing",
                                "references: reloads when a batch deletes a group a server still uses",
                                "references: a snapshot with a missing item counts as a failed load")),
                new WrongMachine("counts the snapshot as a pulse",
                        random -> new CountsSnapshotAsPulse().start(random),
                        List.of("start: live only after the first good pulse",
                                "start: a snapshot alone never makes the view live",
                                "connection: reloads after a reconnect")),
                new WrongMachine("counts a pulse below the copy as good",
                        random -> new CountsLowerPulseAsGood().start(random),
                        List.of("start: a snapshot alone never makes the view live",
                                "pulse: a lower revision neither reloads nor counts as good")),
                new WrongMachine("ignores the epoch of a pulse",
                        random -> new IgnoresPulseEpoch().start(random),
                        List.of("pulse: reloads on a pulse from another epoch",
                                "own write: a write in a later epoch holds reads until the copy is in it")),
                new WrongMachine("stays live without a good pulse",
                        random -> new StaysLiveWithoutGoodPulse().start(random),
                        List.of("pulse: a lower revision neither reloads nor counts as good",
                                "pulse: not live 15 s after the last good pulse",
                                ViewSimulation.SHORT_RUNS_CHECK)),
                new WrongMachine("stays behind a pulse for ever",
                        random -> new StaysBehindPulseForever().start(random),
                        List.of("pulse: reloads when the copy stays behind a pulse for 3 s")),
                new WrongMachine("ignores views_may_answer",
                        random -> new IgnoresViewsMayAnswer().start(random),
                        List.of("pulse: views_may_answer false stops answers")),
                new WrongMachine("stays live after the connection is lost",
                        random -> new IgnoresConnectionLoss().start(random),
                        List.of("connection: not live at once after the loss",
                                "connection: ignores batches until the subscriptions are active again")),
                new WrongMachine("takes a load that ran across the connection loss",
                        random -> new TakesLoadAcrossConnectionLoss().start(random),
                        List.of("connection: ignores the answer of a load that ran across the loss")),
                new WrongMachine("reloads without spacing, back-off or random delay",
                        random -> new ReloadsAtOnce().start(random),
                        List.of("reload: loads at least 2 s apart",
                                "reload: backs off up to 30 s after failed loads",
                                ViewSimulation.SHORT_RUNS_CHECK)),
                new WrongMachine("compares the audit snapshot on arrival",
                        random -> new ComparesAuditOnArrival().start(random),
                        List.of("audit: compares when the copy reaches exactly S")),
                new WrongMachine("keeps the metrics when it takes a snapshot",
                        random -> new KeepsMetricsOnSnapshot().start(random),
                        List.of("start: the snapshot gives the copy its metrics")),
                new WrongMachine("keeps the first pulse ahead, not the highest",
                        random -> new KeepsFirstPulseAhead().start(random),
                        List.of("pulse: a pulse further ahead keeps the copy behind until it reaches that")),
                new WrongMachine("reloads after 15 s without a good pulse only if one came since the snapshot",
                        random -> new NeedsGoodPulseBeforeSilenceReload().start(random),
                        List.of("pulse: without a good pulse, the first pulse 15 s after the snapshot reloads")),
                new WrongMachine("audits again 2 s after a match",
                        random -> new AuditsAgainSoonAfterMatch().start(random),
                        List.of("audit: after a match, the next audit comes 20 min later")),
                new WrongMachine("retries a failed audit load at once",
                        random -> new RetriesFailedAuditLoadAtOnce().start(random),
                        List.of("audit: after a failed audit load, the next audit comes 20 min later")),
                new WrongMachine("counts a failed audit load as a failed reload",
                        random -> new CountsFailedAuditLoadAsFailedReload().start(random),
                        List.of("audit: a failed audit load does not back off reloads")),
                new WrongMachine("compares an audit snapshot from another epoch",
                        random -> new ComparesAuditOfAnotherEpoch().start(random),
                        List.of("audit: an audit snapshot from another epoch reloads")),
                new WrongMachine("keeps only the last own write",
                        random -> new KeepsLastOwnWrite().start(random),
                        List.of("own write: the highest write holds, also when responses come out of order")),
                new WrongMachine("answers before the copy has the own write",
                        random -> new AnswersBeforeOwnWrite().start(random),
                        List.of("own write: a server write holds server, group and persistent server reads",
                                "own write: a group write holds group and server reads",
                                "own write: a persistent server write holds persistent server and server reads",
                                "own write: a blueprint write holds group and server reads",
                                ViewSimulation.SHORT_RUNS_CHECK)),
                new WrongMachine("ignores an own write of unknown revision",
                        random -> new IgnoresWriteOfUnknownRevision().start(random),
                        List.of("own write of unknown revision: a synced view answers nothing until it reloaded",
                                "own write of unknown revision: abandons the running reload's load",
                                ViewSimulation.SHORT_RUNS_CHECK))
        );
    }

    static final class AppliesBatchAheadOfCopy extends ReferenceMachine {
        @Override
        BatchFit fitOf(NetworkCopy copy, StateBatch batch) {
            BatchFit fit = super.fitOf(copy, batch);
            return fit == BatchFit.AHEAD_OF_COPY ? BatchFit.FITS_COPY : fit;
        }
    }

    static final class AppliesBatchFromAnotherEpoch extends ReferenceMachine {
        @Override
        BatchFit fitOf(NetworkCopy copy, StateBatch batch) {
            StateBatch asIfSameEpoch = batch.toBuilder().setEpoch(copy.epoch()).build();
            return super.fitOf(copy, asIfSameEpoch);
        }
    }

    private static WrongMachine signedAt(ReferenceMachine.Comparison where, String caughtBy) {
        String brokenRule = "compares " + where + " signed";
        return new WrongMachine(brokenRule, random -> new ComparesSignedAt(where).start(random), List.of(caughtBy));
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

    static final class DeadlinesBeforeInput extends ReferenceMachine {
        @Override
        void runStep(Memory m, ViewInput input, long now, List<ViewEffect> effects) {
            actOnDeadlines(m, now, effects);
            handle(m, input, now, effects);
        }
    }

    static final class GapDeadlineBeforeAudit extends ReferenceMachine {
        @Override
        void actOnSyncedDeadlines(Memory m, long now, List<ViewEffect> effects) {
            if (heldTooLong(m, now) || behindPulseTooLong(m, now)) {
                wantReload(m, now);
                return;
            }
            takeAuditSnapshotIfNotReached(m, now, effects);
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

    static final class BehindDeadlineBeforeInput extends ReferenceMachine {
        @Override
        void runStep(Memory m, ViewInput input, long now, List<ViewEffect> effects) {
            if (m.mode == Mode.SYNCED && behindPulseTooLong(m, now)) {
                wantReload(m, now);
            }
            super.runStep(m, input, now, effects);
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
            HeldBatch newest = m.heldBatches.get(m.heldBatches.size() - 1);
            return now - newest.arrivedAt() >= GAP_WAIT_MILLIS;
        }
    }

    static final class BehindCountsFromLatestPulse extends ReferenceMachine {
        @Override
        void onPulse(Memory m, StatePulse pulse, long now) {
            BehindPulse before = m.behindPulse;
            super.onPulse(m, pulse, now);
            boolean raised = before != null && m.behindPulse != null && m.behindPulse.revision() != before.revision();
            if (raised) {
                m.behindPulse = new BehindPulse(m.behindPulse.revision(), now);
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
            if (m.mode != Mode.RELOAD_WANTED) {
                super.loseConnection(m);
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

    static final class StaysLiveWithoutGoodPulse extends ReferenceMachine {
        @Override
        boolean isLive(Memory m, long now) {
            return super.isLive(m, m.goodPulseAt == null ? now : m.goodPulseAt);
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
        boolean mayAnswer(Memory m, ReadKind kind, long now) {
            return isLive(m, now) && reachedOwnWrites(m, kind);
        }
    }

    static final class IgnoresConnectionLoss extends ReferenceMachine {
        @Override
        void loseConnection(Memory m) {
        }
    }

    static final class TakesLoadAcrossConnectionLoss extends ReferenceMachine {
        @Override
        void onSnapshotLoaded(Memory m, NetworkState snapshot, long now, List<ViewEffect> effects) {
            boolean answerToIgnore = m.loadRunning && m.loadAnswerIgnored;
            if (answerToIgnore) {
                m.loadAnswerIgnored = false;
                m.mode = Mode.RELOADING;
            }
            super.onSnapshotLoaded(m, snapshot, now, effects);
        }
    }

    static final class ReloadsAtOnce extends ReferenceMachine {
        @Override
        long reloadStartTime(Memory m, long now) {
            return now;
        }
    }

    static final class ComparesAuditOnArrival extends ReferenceMachine {
        @Override
        void onAuditSnapshot(Memory m, NetworkState snapshot, long now, List<ViewEffect> effects) {
            m.auditSnapshot = snapshot;
            compareAudit(m, now, effects);
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
            if (m.mode == Mode.SYNCED && m.auditPhase == AuditPhase.NONE) {
                m.auditDueAt = now + AUDIT_RETRY_MIN_MILLIS;
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

    static final class KeepsLastOwnWrite extends ReferenceMachine {
        @Override
        void onWriteReturned(Memory m, ViewInput.WriteReturned write) {
            Position written = new Position(write.epoch(), write.revision());
            for (ReadKind read : readsChangedBy(write.written())) {
                m.ownWritesByRead.put(read, written);
            }
        }
    }

    static final class AnswersBeforeOwnWrite extends ReferenceMachine {
        @Override
        void onWriteReturned(Memory m, ViewInput.WriteReturned write) {
        }
    }

    static final class IgnoresWriteOfUnknownRevision extends ReferenceMachine {
        @Override
        void onWriteRevisionUnknown(Memory m, long now) {
        }
    }
}
