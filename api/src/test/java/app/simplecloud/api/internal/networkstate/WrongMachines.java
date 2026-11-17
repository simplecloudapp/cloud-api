package app.simplecloud.api.internal.networkstate;

import build.buf.gen.simplecloud.controller.v2.NetworkState;
import build.buf.gen.simplecloud.controller.v2.StateBatch;
import build.buf.gen.simplecloud.controller.v2.StatePulse;

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
                new WrongMachine("compares revisions as signed numbers",
                        random -> new ComparesRevisionsSigned().start(random),
                        List.of("batch: compares revisions as unsigned numbers")),
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
                                "own write: a blueprint write holds blueprint, group and server reads",
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

    static final class ComparesRevisionsSigned extends ReferenceMachine {
        @Override
        BatchFit fitOf(NetworkCopy copy, StateBatch batch) {
            if (batch.getEpoch() != copy.epoch()) {
                return BatchFit.OTHER_EPOCH;
            }
            if (batch.getRevision() <= copy.revision()) {
                return BatchFit.ALREADY_IN_COPY;
            }
            if (batch.getBaseRevision() <= copy.revision()) {
                return BatchFit.FITS_COPY;
            }
            return BatchFit.AHEAD_OF_COPY;
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
        boolean mayAnswer(Memory m, ItemKind kind, long now) {
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
            for (ItemKind read : readsChangedBy(write.written())) {
                m.ownWritesByRead.put(read, written);
            }
        }
    }

    static final class AnswersBeforeOwnWrite extends ReferenceMachine {
        @Override
        void onWriteReturned(Memory m, ViewInput.WriteReturned write) {
        }
    }
}
