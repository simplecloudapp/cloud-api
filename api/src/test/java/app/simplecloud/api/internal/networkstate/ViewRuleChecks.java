package app.simplecloud.api.internal.networkstate;

import build.buf.gen.simplecloud.controller.v2.NetworkState;
import build.buf.gen.simplecloud.controller.v2.StateBatch;
import build.buf.gen.simplecloud.controller.v2.StatePulse;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static app.simplecloud.api.internal.networkstate.StateItems.EPOCH;
import static app.simplecloud.api.internal.networkstate.StateItems.batch;
import static app.simplecloud.api.internal.networkstate.StateItems.group;
import static app.simplecloud.api.internal.networkstate.StateItems.groupOfBlueprint;
import static app.simplecloud.api.internal.networkstate.StateItems.groupServer;
import static app.simplecloud.api.internal.networkstate.StateItems.id;
import static app.simplecloud.api.internal.networkstate.StateItems.metrics;
import static app.simplecloud.api.internal.networkstate.StateItems.network;
import static app.simplecloud.api.internal.networkstate.StateItems.persistentServerInstance;
import static app.simplecloud.api.internal.networkstate.StateItems.pulse;
import static app.simplecloud.api.internal.networkstate.StateItems.serverStateBatch;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One example per rule and edge of {@link NetworkView}. Most start from a view that is live on
 * {@link StateItems#network} at revision 10, at 200 ms, whose only load so far started at 0 ms.
 */
final class ViewRuleChecks {

    private static final long FIRST_AUDIT_MILLIS = 20 * 60_000;
    private static final long LAST_AUDIT_MILLIS = 30 * 60_000;
    private static final long AUDIT_WAIT_LIMIT_MILLIS = LAST_AUDIT_MILLIS + 60_000;
    private static final double HIGHEST_RANDOM_NUMBER = 0.999_999;
    private static final long HIGHEST_SIGNED = Long.MAX_VALUE;
    /**
     * 2^63 as a uint64: above {@link #HIGHEST_SIGNED} unsigned, below every other number signed.
     */
    private static final long LOWEST_PAST_SIGNED = Long.MIN_VALUE;

    private ViewRuleChecks() {
    }

    static List<ViewCheck> all() {
        return List.of(
                new ViewCheck("start: loads only once the subscriptions are active",
                        ViewRuleChecks::loadsOnlyOnceSubscribed),
                new ViewCheck("start: delays the first load by up to 2 s of random delay",
                        ViewRuleChecks::delaysFirstLoadByRandomNumber),
                new ViewCheck("start: live only after the first good pulse",
                        ViewRuleChecks::liveOnlyAfterFirstGoodPulse),
                new ViewCheck("start: a snapshot alone never makes the view live",
                        ViewRuleChecks::snapshotAloneNeverMakesLive),
                new ViewCheck("start: the snapshot gives the copy its metrics",
                        ViewRuleChecks::snapshotGivesCopyItsMetrics),
                new ViewCheck("start: holds batches that arrive during the load",
                        ViewRuleChecks::holdsBatchesArrivingDuringLoad),
                new ViewCheck("start: drops batches during the load that the snapshot holds",
                        ViewRuleChecks::dropsBatchesTheSnapshotHolds),
                new ViewCheck("batch: drops a duplicate", ViewRuleChecks::dropsDuplicateBatch),
                new ViewCheck("batch: deletes items, and an unknown id is no error",
                        ViewRuleChecks::deletesItemsAndIgnoresUnknownIds),
                new ViewCheck("batch: applies an overlapping batch", ViewRuleChecks::appliesOverlappingBatch),
                new ViewCheck("batch: holds a batch ahead of the copy until the gap fills",
                        ViewRuleChecks::holdsBatchUntilGapFills),
                new ViewCheck("batch: reloads after a gap held for 3 s",
                        ViewRuleChecks::reloadsAfterGapHeldThreeSeconds),
                new ViewCheck("batch: reloads after a batch with base = revision above the copy",
                        ViewRuleChecks::reloadsAfterEmptyBatchAboveCopy),
                new ViewCheck("batch: reloads on a batch from another epoch",
                        ViewRuleChecks::reloadsOnBatchFromAnotherEpoch),
                new ViewCheck("batch: compares revisions as unsigned numbers",
                        ViewRuleChecks::comparesRevisionsUnsigned),
                new ViewCheck("batch: a base past 2^63 is ahead of a copy below it",
                        ViewRuleChecks::holdsBaseAboveCopyUnsigned),
                new ViewCheck("batch: a missing batch that arrives at exactly 3 s is on time",
                        ViewRuleChecks::missingBatchAtExactlyThreeSecondsIsOnTime),
                new ViewCheck("batch: a held batch's 3 s count from its arrival during a load",
                        ViewRuleChecks::batchHeldDuringLoadCountsFromArrival),
                new ViewCheck("batch: a held batch's 3 s that ran out during the load reload when it is taken",
                        ViewRuleChecks::heldBatchExpiredDuringLoadReloadsOnTake),
                new ViewCheck("batch: a load longer than 3 s is not abandoned for a waiting batch",
                        ViewRuleChecks::longLoadIsNotAbandonedForWaitingBatch),
                new ViewCheck("batch: the gap's 3 s count from the oldest held batch",
                        ViewRuleChecks::gapCountsFromOldestHeldBatch),
                new ViewCheck("batch: reloads on a batch from a lower epoch",
                        ViewRuleChecks::reloadsOnBatchFromLowerEpoch),
                new ViewCheck("references: a new group and its first server in one batch",
                        ViewRuleChecks::acceptsGroupAndFirstServerInOneBatch),
                new ViewCheck("references: reloads when a server's group is missing",
                        ViewRuleChecks::reloadsWhenGroupMissing),
                new ViewCheck("references: reloads when a server's persistent server is missing",
                        ViewRuleChecks::reloadsWhenPersistentServerMissing),
                new ViewCheck("references: reloads when a group's blueprint is missing",
                        ViewRuleChecks::reloadsWhenBlueprintMissing),
                new ViewCheck("references: reloads when a batch deletes a group a server still uses",
                        ViewRuleChecks::reloadsWhenDeletedGroupStillUsed),
                new ViewCheck("references: a batch with a missing item is not applied",
                        ViewRuleChecks::batchWithMissingItemIsNotApplied),
                new ViewCheck("references: a snapshot with a missing item is not taken",
                        ViewRuleChecks::snapshotWithMissingItemIsNotTaken),
                new ViewCheck("references: a snapshot with a missing item counts as a failed load",
                        ViewRuleChecks::brokenSnapshotCountsAsFailedLoad),
                new ViewCheck("pulse: reloads on a pulse from another epoch",
                        ViewRuleChecks::reloadsOnPulseFromAnotherEpoch),
                new ViewCheck("pulse: reloads on a pulse from a lower epoch",
                        ViewRuleChecks::reloadsOnPulseFromLowerEpoch),
                new ViewCheck("pulse: a pulse past 2^63 is ahead of a copy below it",
                        ViewRuleChecks::pulseAboveCopyUnsigned),
                new ViewCheck("pulse: a pulse past 2^63 is further ahead than one below it",
                        ViewRuleChecks::furtherPulseUnsigned),
                new ViewCheck("pulse: a copy past 2^63 has reached a pulse below it",
                        ViewRuleChecks::copyReachesPulseUnsigned),
                new ViewCheck("pulse: a batch that reaches a pulse ahead at exactly 3 s is on time",
                        ViewRuleChecks::batchReachingPulseAtExactlyThreeSecondsIsOnTime),
                new ViewCheck("pulse: the 3 s behind a pulse count from the first pulse ahead",
                        ViewRuleChecks::behindCountsFromFirstPulseAhead),
                new ViewCheck("pulse: a pulse replaces the metrics also while disconnected",
                        ViewRuleChecks::pulseReplacesMetricsWhileDisconnected),
                new ViewCheck("pulse: a lower revision neither reloads nor counts as good",
                        ViewRuleChecks::lowerPulseNeitherReloadsNorCounts),
                new ViewCheck("pulse: not live 15 s after the last good pulse",
                        ViewRuleChecks::notLiveFifteenSecondsAfterGoodPulse),
                new ViewCheck("pulse: the first pulse after 15 s without a good one reloads",
                        ViewRuleChecks::reloadsOnPulseAfterFifteenSecondsWithoutGoodPulse),
                new ViewCheck("pulse: a pulse ahead of the copy ends live until the copy reaches it",
                        ViewRuleChecks::pulseAheadEndsLiveUntilReached),
                new ViewCheck("pulse: reloads when the copy stays behind a pulse for 3 s",
                        ViewRuleChecks::reloadsWhenBehindPulseThreeSeconds),
                new ViewCheck("pulse: a pulse further ahead keeps the copy behind until it reaches that",
                        ViewRuleChecks::furtherPulseKeepsCopyBehind),
                new ViewCheck("pulse: without a good pulse, the first pulse 15 s after the snapshot reloads",
                        ViewRuleChecks::reloadsOnPulseFifteenSecondsAfterSnapshotWithoutGoodPulse),
                new ViewCheck("pulse: every pulse replaces the metrics", ViewRuleChecks::everyPulseReplacesMetrics),
                new ViewCheck("pulse: views_may_answer false stops answers", ViewRuleChecks::flagFalseStopsAnswers),
                new ViewCheck("connection: not live at once after the loss",
                        ViewRuleChecks::notLiveAtOnceAfterConnectionLoss),
                new ViewCheck("connection: ignores batches until the subscriptions are active again",
                        ViewRuleChecks::ignoresBatchesWhileDisconnected),
                new ViewCheck("connection: reloads after a reconnect", ViewRuleChecks::reloadsAfterReconnect),
                new ViewCheck("connection: a loss drops a wanted reload",
                        ViewRuleChecks::lossDropsWantedReload),
                new ViewCheck("connection: a loss abandons an audit waiting to compare",
                        ViewRuleChecks::lossAbandonsComparingAudit),
                new ViewCheck("connection: a failed answer of an abandoned load is no failed load",
                        ViewRuleChecks::failedAbandonedLoadIsNoFailedLoad),
                new ViewCheck("connection: ignores the answer of a load that ran across the loss",
                        ViewRuleChecks::ignoresLoadRunningAcrossLoss),
                new ViewCheck("reload: loads at least 2 s apart", ViewRuleChecks::loadsTwoSecondsApart),
                new ViewCheck("reload: backs off up to 30 s after failed loads",
                        ViewRuleChecks::backsOffAfterFailedLoads),
                new ViewCheck("reload: a taken snapshot ends the back-off", ViewRuleChecks::takenSnapshotEndsBackOff),
                new ViewCheck("reload: every random delay draws a new number",
                        ViewRuleChecks::everyRandomDelayDrawsNewNumber),
                new ViewCheck("reload: adds 2 s times a random number to every reload",
                        ViewRuleChecks::addsRandomDelayToReloads),
                new ViewCheck("audit: first audit 20 min after the snapshot with the lowest number",
                        ViewRuleChecks::auditsTwentyMinutesAfterSnapshot),
                new ViewCheck("audit: first audit within 30 min with the highest number",
                        ViewRuleChecks::auditsWithinThirtyMinutes),
                new ViewCheck("audit: matches when the copy at S equals the snapshot, metrics left out",
                        ViewRuleChecks::auditMatchesAtSameRevision),
                new ViewCheck("audit: a difference at S takes the snapshot",
                        ViewRuleChecks::auditDifferenceTakesSnapshot),
                new ViewCheck("audit: compares when the copy reaches exactly S",
                        ViewRuleChecks::auditComparesAtExactlyS),
                new ViewCheck("audit: a difference found only at exactly S",
                        ViewRuleChecks::auditFindsDifferenceAtExactlyS),
                new ViewCheck("audit: the copy jumping past S audits again 2 s later",
                        ViewRuleChecks::auditJumpedPastRetriesSoon),
                new ViewCheck("audit: a snapshot behind the copy counts as jumped past",
                        ViewRuleChecks::auditSnapshotBehindCopyJumpedPast),
                new ViewCheck("audit: S not reached within 3 s takes the snapshot",
                        ViewRuleChecks::auditNotReachedTakesSnapshot),
                new ViewCheck("audit: a reload waits for the running audit load",
                        ViewRuleChecks::reloadWaitsForAuditLoad),
                new ViewCheck("audit: reaching S at exactly 3 s is a compare",
                        ViewRuleChecks::auditReachingSAtExactlyThreeSecondsCompares),
                new ViewCheck("audit: the audit's deadline acts before the gap's",
                        ViewRuleChecks::auditDeadlineActsBeforeGapDeadline),
                new ViewCheck("audit: compares revisions past 2^63 unsigned",
                        ViewRuleChecks::auditComparesUnsigned),
                new ViewCheck("audit: no audit starts while the view is not live",
                        ViewRuleChecks::noAuditWhileNotLive),
                new ViewCheck("audit: after a match, the next audit comes 20 min later",
                        ViewRuleChecks::auditsAgainTwentyMinutesAfterMatch),
                new ViewCheck("audit: after a failed audit load, the next audit comes 20 min later",
                        ViewRuleChecks::auditsAgainTwentyMinutesAfterFailedLoad),
                new ViewCheck("audit: a failed audit load does not back off reloads",
                        ViewRuleChecks::failedAuditLoadDoesNotBackOff),
                new ViewCheck("audit: an audit snapshot from another epoch reloads",
                        ViewRuleChecks::auditSnapshotFromAnotherEpochReloads),
                new ViewCheck("own write: a server write holds server, group and persistent server reads",
                        ViewRuleChecks::serverWriteHoldsItsReads),
                new ViewCheck("own write: a group write holds group and server reads",
                        ViewRuleChecks::groupWriteHoldsItsReads),
                new ViewCheck("own write: a persistent server write holds persistent server and server reads",
                        ViewRuleChecks::persistentServerWriteHoldsItsReads),
                new ViewCheck("own write: a blueprint write holds group and server reads",
                        ViewRuleChecks::blueprintWriteHoldsItsReads),
                new ViewCheck("own write: a write in a later epoch holds reads until the copy is in it",
                        ViewRuleChecks::laterEpochWriteHoldsReads),
                new ViewCheck("own write: a write in an earlier epoch holds nothing",
                        ViewRuleChecks::earlierEpochWriteHoldsNothing),
                new ViewCheck("own write: a write the copy already has holds nothing",
                        ViewRuleChecks::writeInCopyHoldsNothing),
                new ViewCheck("own write: compares revisions past 2^63 unsigned",
                        ViewRuleChecks::ownWriteRevisionsUnsigned),
                new ViewCheck("own write: compares epochs past 2^63 unsigned",
                        ViewRuleChecks::ownWriteEpochsUnsigned),
                new ViewCheck("own write: the highest write holds, also when responses come out of order",
                        ViewRuleChecks::highestOwnWriteHolds),
                new ViewCheck("own write: a snapshot in a lower epoch reaches a write handed over before its load",
                        ViewRuleChecks::lowerEpochSnapshotReachesEarlierWrite),
                new ViewCheck("own write: a snapshot misses a write handed over after its load started, "
                        + "also in the same millisecond",
                        ViewRuleChecks::snapshotMissesWriteDuringItsLoad),
                new ViewCheck("own write: a load that starts in the write's step reaches it",
                        ViewRuleChecks::loadStartingInWriteStepReachesWrite),
                new ViewCheck("own write: a differing audit reaches a write handed over before its load",
                        ViewRuleChecks::differingAuditReachesEarlierWrite),
                new ViewCheck("own write: an audit not reached in 3 s reaches a write handed over before its load",
                        ViewRuleChecks::unreachedAuditReachesEarlierWrite),
                new ViewCheck("own write: an audit misses a write handed over after its load started",
                        ViewRuleChecks::auditMissesWriteDuringItsLoad),
                new ViewCheck("own write: an audit that takes no snapshot reaches no write",
                        ViewRuleChecks::untakenAuditReachesNoWrite),
                new ViewCheck("own write: a write after the snapshot makes reads wait again",
                        ViewRuleChecks::writeAfterSnapshotMakesReadsWait),
                new ViewCheck("own write wait: a server write makes server, group and persistent server reads wait",
                        ViewRuleChecks::serverWriteMakesItsReadsWait),
                new ViewCheck("own write wait: a group write makes group and server reads wait",
                        ViewRuleChecks::groupWriteMakesItsReadsWait),
                new ViewCheck("own write wait: a persistent server write makes persistent server and server reads wait",
                        ViewRuleChecks::persistentServerWriteMakesItsReadsWait),
                new ViewCheck("own write wait: a blueprint write makes group and server reads wait",
                        ViewRuleChecks::blueprintWriteMakesItsReadsWait),
                new ViewCheck("own write wait: a read waits until just before 3 s, then goes over HTTP",
                        ViewRuleChecks::readGoesOverHttpAfterThreeSeconds),
                new ViewCheck("own write wait: at exactly 3 s, a copy that has the write answers the read",
                        ViewRuleChecks::copyAnswersAtExactlyThreeSeconds),
                new ViewCheck("own write wait: no read waits while a batch is held",
                        ViewRuleChecks::noWaitWhileBatchHeld),
                new ViewCheck("own write wait: no read waits while the copy is behind a pulse",
                        ViewRuleChecks::noWaitWhileBehindPulse),
                new ViewCheck("own write wait: no read waits once the last good pulse is 15 s old",
                        ViewRuleChecks::noWaitOnceGoodPulseFifteenSecondsOld),
                new ViewCheck("own write wait: no read waits after the connection is lost",
                        ViewRuleChecks::noWaitAfterConnectionLoss),
                new ViewCheck("own write wait: no read waits while a reload is wanted or runs",
                        ViewRuleChecks::noWaitWhileReloading),
                new ViewCheck("own write wait: no read waits while views_may_answer is false",
                        ViewRuleChecks::noWaitWhileAnswersSwitchedOff),
                new ViewCheck("own write of unknown revision: a synced view answers nothing until it reloaded",
                        ViewRuleChecks::writeOfUnknownRevisionReloadsSyncedView),
                new ViewCheck("own write of unknown revision: abandons the running reload's load",
                        ViewRuleChecks::writeOfUnknownRevisionAbandonsRunningReload),
                new ViewCheck("own write of unknown revision: a wanted reload keeps its start",
                        ViewRuleChecks::writeOfUnknownRevisionKeepsWantedReload),
                new ViewCheck("own write of unknown revision: starts no load while disconnected",
                        ViewRuleChecks::writeOfUnknownRevisionWhileDisconnectedStartsNoLoad)
        );
    }

    // Start (rule 1).

    static void loadsOnlyOnceSubscribed(ViewStarter starter) {
        ViewDriver view = ViewDriver.start(starter);

        view.advanceWithoutLoadTo(5_000, "a view must not load before its subscriptions are active");
        view.subscribe();

        view.assertLoadRequested("a view loads at once when its subscriptions are active and the random delay is 0");
    }

    static void delaysFirstLoadByRandomNumber(ViewStarter starter) {
        ViewDriver halfway = ViewDriver.start(starter, 0.5);
        halfway.subscribe();
        long halfwayLoad = halfway.waitForLoad(3_000);

        ViewDriver highest = ViewDriver.start(starter, HIGHEST_RANDOM_NUMBER);
        highest.subscribe();
        long highestLoad = highest.waitForLoad(3_000);

        assertEquals(1_000, halfwayLoad, "a random number of 0.5 delays the load by 1 s");
        assertTrue(highestLoad >= 1_990 && highestLoad < 2_000,
                "the random delay stays below 2 s, but was " + highestLoad + " ms");
    }

    static void liveOnlyAfterFirstGoodPulse(ViewStarter starter) {
        ViewDriver view = ViewDriver.start(starter);
        view.subscribe();
        view.advance(50);
        // Allows answers, so that live shows in the reads.
        view.pulse(pulse(9));
        view.advance(50);
        view.loadSnapshot(network(10));

        assertFalse(view.isLive(), "a snapshot does not count as a pulse");

        view.advance(100);
        view.pulse(pulse(10));

        assertTrue(view.isLive(), "a pulse at the snapshot's revision makes the view live");
    }

    static void snapshotAloneNeverMakesLive(ViewStarter starter) {
        ViewDriver view = ViewDriver.start(starter);
        view.subscribe();
        view.advance(50);
        // Allows answers, so that live shows in the reads.
        view.pulse(pulse(9));
        view.advance(50);
        view.loadSnapshot(network(10));
        view.advance(10_000);
        boolean liveWithoutPulse = view.isLive();
        view.pulse(pulse(9));

        assertFalse(liveWithoutPulse, "a view without a pulse is not live");
        assertFalse(view.isLive(), "a pulse below the copy is not a good pulse");
    }

    static void snapshotGivesCopyItsMetrics(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        view.advance(100);
        view.pulse(pulse(10).toBuilder().addMetrics(metrics(2, 40)).build());
        view.advance(2_000);
        view.loseConnection();
        view.subscribe();
        view.assertLoadRequested("a reconnect means reload");
        view.loadSnapshot(network(12));

        assertEquals(Map.of(id(1), metrics(1, 5)), view.copy().metrics(), "a taken snapshot replaces the metrics");
    }

    static void holdsBatchesArrivingDuringLoad(ViewStarter starter) {
        ViewDriver view = ViewDriver.start(starter);
        view.subscribe();
        view.advance(50);
        view.batch(serverStateBatch(9, 11, "STOPPING"));
        view.advance(50);
        view.loadSnapshot(network(10));

        assertEquals(11, view.copy().revision(), "a batch held during the load applies on top of the snapshot");
        assertEquals("STOPPING", serverState(view, 1));
    }

    static void dropsBatchesTheSnapshotHolds(ViewStarter starter) {
        ViewDriver view = ViewDriver.start(starter);
        view.subscribe();
        view.advance(50);
        view.batch(serverStateBatch(8, 10, "STOPPING"));
        view.advance(50);
        view.loadSnapshot(network(10));
        view.advance(100);
        view.pulse(pulse(10));

        assertEquals(10, view.copy().revision());
        assertEquals("AVAILABLE", serverState(view, 1), "a batch at or below the snapshot's revision is dropped");
        assertTrue(view.isLive());
    }

    // Batch (rule 2).

    static void dropsDuplicateBatch(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        StateBatch batch = serverStateBatch(10, 11, "STOPPING");
        view.batch(batch);
        NetworkCopy afterFirst = view.copy();
        view.advance(100);
        view.batch(batch);

        assertEquals(afterFirst, view.copy(), "a duplicate changes nothing");
        assertTrue(view.isLive());
    }

    static void appliesOverlappingBatch(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        view.batch(serverStateBatch(10, 12, "STARTING"));
        view.advance(100);
        StateBatch overlapping = batch(11, 13)
                .addServers(groupServer(1, 10, "AVAILABLE"))
                .addServers(groupServer(3, 10, "PREPARING"))
                .build();
        view.batch(overlapping);

        assertEquals(13, view.copy().revision(), "a batch with base below the copy and revision above it applies");
        assertEquals("AVAILABLE", serverState(view, 1));
        assertEquals("PREPARING", serverState(view, 3));
        assertTrue(view.isLive());
    }

    static void holdsBatchUntilGapFills(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        StateBatch ahead = batch(12, 13).addServers(groupServer(3, 10, "PREPARING")).build();
        view.batch(ahead);
        boolean liveWithGap = view.isLive();
        long revisionWithGap = view.copy().revision();
        view.advance(2_000);
        view.batch(serverStateBatch(10, 12, "STARTING"));

        assertFalse(liveWithGap, "a view that holds a batch is not live");
        assertEquals(10, revisionWithGap, "a batch ahead of the copy is held, not applied");
        assertEquals(13, view.copy().revision(), "the held batch applies once the gap fills");
        assertEquals("PREPARING", serverState(view, 3));
        assertTrue(view.isLive());
    }

    static void reloadsAfterGapHeldThreeSeconds(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        view.advance(800);
        view.batch(serverStateBatch(12, 13, "STARTING"));
        long heldAt = view.now();

        view.advanceWithoutLoadTo(heldAt + 2_999, "a gap waits 3 s for the missing batch");
        view.advance(1);

        view.assertLoadRequested("a gap held for 3 s means reload");
        assertFalse(view.isLive());
    }

    static void reloadsAfterEmptyBatchAboveCopy(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        view.advance(800);
        view.batch(batch(15, 15).build());
        long heldAt = view.now();

        long loadAt = view.waitForLoad(5_000);

        assertEquals(heldAt + 3_000, loadAt, "a batch with base = revision above the copy is a gap, so it reloads");
    }

    static void reloadsOnBatchFromAnotherEpoch(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        view.advance(2_000);
        view.batch(batch(EPOCH + 1, 2, 3).addServers(groupServer(1, 10, "STOPPING")).build());

        view.assertLoadRequested("a batch from another epoch means reload");
        assertFalse(view.isLive());
        assertEquals("AVAILABLE", serverState(view, 1), "a batch from another epoch is not applied");
    }

    static void comparesRevisionsUnsigned(ViewStarter starter) {
        long highestSigned = Long.MAX_VALUE;
        long nextUnsigned = Long.MIN_VALUE;
        ViewDriver view = ViewDriver.liveAt(starter, network(highestSigned));
        view.batch(serverStateBatch(highestSigned, nextUnsigned, "STOPPING"));
        view.advance(100);
        view.pulse(pulse(nextUnsigned));

        assertEquals(nextUnsigned, view.copy().revision(), "2^63 is above 2^63 - 1 as a uint64");
        assertEquals("STOPPING", serverState(view, 1));
        assertTrue(view.isLive());
    }

    // References (rule 2).

    static void acceptsGroupAndFirstServerInOneBatch(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        StateBatch batch = batch(10, 11)
                .addServers(groupServer(3, 11, "PREPARING"))
                .addServerGroups(group(11))
                .build();
        view.batch(batch);

        assertEquals(11, view.copy().revision());
        assertTrue(view.isLive(), "the references are checked after the whole batch");
        assertFalse(view.requestedLoad());
    }

    static void reloadsWhenGroupMissing(ViewStarter starter) {
        StateBatch batch = batch(10, 11).addServers(groupServer(3, 99, "PREPARING")).build();
        assertBatchReloads(starter, batch, "a server whose group the copy lacks means reload");
    }

    static void reloadsWhenPersistentServerMissing(ViewStarter starter) {
        StateBatch batch = batch(10, 11).addServers(persistentServerInstance(3, 99)).build();
        assertBatchReloads(starter, batch, "a server whose persistent server the copy lacks means reload");
    }

    static void reloadsWhenBlueprintMissing(ViewStarter starter) {
        StateBatch batch = batch(10, 11).addServerGroups(groupOfBlueprint(11, 99)).build();
        assertBatchReloads(starter, batch, "a group whose blueprint the copy lacks means reload");
    }

    static void reloadsWhenDeletedGroupStillUsed(ViewStarter starter) {
        StateBatch batch = batch(10, 11).addDeletedServerGroupIds(id(10)).build();
        assertBatchReloads(starter, batch, "deleting a group that server 1 still names means reload");
    }

    private static void assertBatchReloads(ViewStarter starter, StateBatch batch, String message) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        view.advance(2_000);
        view.batch(batch);

        view.assertLoadRequested(message);
        assertFalse(view.isLive(), message);
    }

    static void brokenSnapshotCountsAsFailedLoad(ViewStarter starter) {
        NetworkState broken = network(EPOCH, 10).addServers(groupServer(3, 99, "PREPARING")).build();
        ViewDriver view = ViewDriver.start(starter);
        view.subscribe();
        view.loadSnapshot(broken);
        long secondLoad = view.waitForLoad(10_000);
        view.loadSnapshot(broken);
        long thirdLoad = view.waitForLoad(10_000);

        assertEquals(2_000, secondLoad, "the first failed load backs off 2 s");
        assertEquals(6_000, thirdLoad, "a second snapshot with a missing item backs off 4 s, as a failed load");
    }

    // Pulse (rule 3).

    static void reloadsOnPulseFromAnotherEpoch(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        view.advance(2_000);
        view.pulse(pulse(EPOCH + 1, 10));

        view.assertLoadRequested("a pulse from another epoch means reload, even at the same revision");
        assertFalse(view.isLive());
    }

    static void lowerPulseNeitherReloadsNorCounts(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        long goodPulseAt = view.now();
        view.advance(5_000);
        view.pulse(pulse(9));
        boolean reloadedOnLowerPulse = view.requestedLoad();
        view.advance(5_000);
        view.pulse(pulse(9));
        view.advanceWithoutLoadTo(goodPulseAt + 14_999, "a lower pulse does not reload");
        boolean liveJustBefore = view.isLive();
        view.advance(1);

        assertFalse(reloadedOnLowerPulse, "a lower pulse does not reload");
        assertTrue(liveJustBefore, "a view is live for 15 s after its last good pulse");
        assertFalse(view.isLive(), "lower pulses do not keep a view live");
    }

    static void notLiveFifteenSecondsAfterGoodPulse(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        long goodPulseAt = view.now();

        boolean liveJustBefore = view.isLiveAt(goodPulseAt + 14_999);
        boolean liveAtFifteenSeconds = view.isLiveAt(goodPulseAt + 15_000);

        assertTrue(liveJustBefore, "a view is live for 15 s after its last good pulse");
        assertFalse(liveAtFifteenSeconds, "time alone ends live 15 s after the last good pulse");
    }

    static void reloadsOnPulseAfterFifteenSecondsWithoutGoodPulse(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        long goodPulseAt = view.now();
        view.advanceWithoutLoadTo(goodPulseAt + 15_000, "silence alone does not reload");
        view.pulse(pulse(10));

        view.assertLoadRequested("the first pulse after 15 s without a good pulse reloads, whatever it says");
        assertFalse(view.isLive());
    }

    static void pulseAheadEndsLiveUntilReached(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        view.advance(100);
        view.pulse(pulse(12));
        boolean liveWhileBehind = view.isLive();
        view.advance(1_000);
        view.batch(serverStateBatch(10, 12, "STOPPING"));

        assertFalse(liveWhileBehind, "a copy behind a pulse is not live");
        assertTrue(view.isLive(), "reaching the pulse's revision makes the view live again");
        view.advanceWithoutLoadTo(view.now() + 5_000, "a reached pulse does not reload");
    }

    static void reloadsWhenBehindPulseThreeSeconds(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        view.advance(800);
        view.pulse(pulse(12));
        long behindSince = view.now();
        view.batch(serverStateBatch(10, 11, "STOPPING"));

        long loadAt = view.waitForLoad(5_000);

        assertEquals(behindSince + 3_000, loadAt, "a copy still behind a pulse after 3 s reloads");
    }

    static void furtherPulseKeepsCopyBehind(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        view.advance(100);
        view.pulse(pulse(12));
        view.advance(1_000);
        view.pulse(pulse(14));
        view.advance(100);
        view.batch(serverStateBatch(10, 12, "STARTING"));
        boolean liveAtFirstPulse = view.isLive();
        view.advance(100);
        view.batch(serverStateBatch(12, 14, "STOPPING"));

        assertFalse(liveAtFirstPulse, "the copy is behind until it reaches the highest revision a pulse showed");
        assertTrue(view.isLive());
    }

    static void reloadsOnPulseFifteenSecondsAfterSnapshotWithoutGoodPulse(ViewStarter starter) {
        ViewDriver view = ViewDriver.start(starter);
        view.subscribe();
        view.advance(100);
        view.loadSnapshot(network(10));
        long tookSnapshotAt = view.now();
        while (view.now() < tookSnapshotAt + 10_000) {
            view.advance(5_000);
            view.pulse(pulse(9));
            assertFalse(view.requestedLoad(), "a lower pulse within 15 s of the snapshot does not reload");
        }
        view.advance(5_000);
        view.pulse(pulse(9));

        view.assertLoadRequested("the first pulse 15 s after the snapshot, without a good pulse since, reloads");
    }

    static void everyPulseReplacesMetrics(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        StatePulse lowerWithMetrics = pulse(9).toBuilder().addMetrics(metrics(2, 40)).build();
        view.advance(100);
        view.pulse(lowerWithMetrics);

        assertEquals(Map.of(id(2), metrics(2, 40)), view.copy().metrics(),
                "a pulse replaces the metrics, also a lower one; server 1 now shows zeros");
    }

    static void flagFalseStopsAnswers(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        boolean answeredAfterTrue = view.mayAnswer(ReadKind.SERVER);
        view.advance(10_000);
        view.pulse(pulse(10).toBuilder().setViewsMayAnswer(false).build());
        Set<ReadKind> answeredAfterFalse = answeredKinds(view);
        view.advance(6_000);
        view.pulse(pulse(9));

        assertTrue(answeredAfterTrue);
        assertEquals(Set.of(), answeredAfterFalse, "views_may_answer false stops every answer");
        assertEquals(EnumSet.allOf(ReadKind.class), answeredKinds(view),
                "the next true pulse allows answers again, and the false one was a good pulse: the flag stops"
                        + " answers, not live");
    }

    // Connection (rule 4).

    static void notLiveAtOnceAfterConnectionLoss(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        view.loseConnection();

        assertFalse(view.isLive(), "a lost connection ends live at once");
    }

    static void ignoresBatchesWhileDisconnected(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        view.loseConnection();
        view.advance(100);
        view.batch(serverStateBatch(10, 11, "STOPPING"));

        assertEquals(10, view.copy().revision(), "batches before the subscriptions are active again are ignored");
    }

    static void reloadsAfterReconnect(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        view.advance(3_000);
        view.loseConnection();
        view.advance(1_000);
        view.subscribe();
        view.assertLoadRequested("a reconnect means reload");
        view.advance(100);
        view.loadSnapshot(network(12));
        boolean liveAfterSnapshot = view.isLive();
        view.advance(100);
        view.pulse(pulse(12));

        assertFalse(liveAfterSnapshot, "after a reload, the view waits for a good pulse again");
        assertTrue(view.isLive());
        assertEquals(12, view.copy().revision());
    }

    static void ignoresLoadRunningAcrossLoss(ViewStarter starter) {
        ViewDriver view = ViewDriver.start(starter);
        view.subscribe();
        view.advance(50);
        view.loseConnection();
        view.advance(50);
        view.subscribe();
        boolean loadedWhileOneRuns = view.requestedLoad();
        view.advance(100);
        view.loadSnapshot(network(10));
        Optional<NetworkCopy> copyAfterIgnoredAnswer = view.copyIfAny();
        long secondLoad = view.waitForLoad(5_000);
        view.loadSnapshot(network(11));

        assertFalse(loadedWhileOneRuns, "a view runs at most one load at a time");
        assertTrue(copyAfterIgnoredAnswer.isEmpty(), "the answer of a load that ran across the loss is ignored");
        assertEquals(2_000, secondLoad, "the new load starts 2 s after the previous one");
        assertEquals(11, view.copy().revision());
    }

    // Reload (rule 6).

    static void loadsTwoSecondsApart(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        view.advance(300);
        view.batch(batch(EPOCH + 1, 2, 3).build());

        long reloadAt = view.waitForLoad(5_000);

        assertEquals(2_000, reloadAt, "a reload starts 2 s after the previous load started");
    }

    static void backsOffAfterFailedLoads(ViewStarter starter) {
        ViewDriver view = ViewDriver.start(starter);
        view.subscribe();
        List<Long> expectedLoads = List.of(2_000L, 6_000L, 14_000L, 30_000L, 60_000L, 90_000L);
        for (long expectedLoad : expectedLoads) {
            view.failLoad();
            long loadAt = view.waitForLoad(40_000);
            assertEquals(expectedLoad, loadAt, "after failed loads the delay doubles from 2 s, up to 30 s");
        }
    }

    static void takenSnapshotEndsBackOff(ViewStarter starter) {
        ViewDriver view = ViewDriver.start(starter);
        view.subscribe();
        for (int failure = 0; failure < 4; failure++) {
            view.failLoad();
            view.waitForLoad(40_000);
        }
        view.loadSnapshot(network(10));
        long tookSnapshotAt = view.now();
        view.advance(1_000);
        view.batch(batch(EPOCH + 1, 2, 3).build());
        long reloadAt = view.waitForLoad(10_000);
        view.failLoad();

        long loadAfterFailure = view.waitForLoad(40_000);

        assertEquals(tookSnapshotAt + 2_000, reloadAt, "after a taken snapshot only the 2 s spacing applies");
        assertEquals(reloadAt + 2_000, loadAfterFailure,
                "the first failure after a taken snapshot backs off 2 s, not 30 s");
    }

    static void addsRandomDelayToReloads(ViewStarter starter) {
        ViewDriver view = ViewDriver.start(starter, 0.5);
        view.subscribe();
        long firstLoad = view.waitForLoad(3_000);
        view.failLoad();

        long secondLoad = view.waitForLoad(10_000);

        assertEquals(1_000, firstLoad);
        assertEquals(firstLoad + 2_000 + 1_000, secondLoad, "the random delay comes on top of the back-off");
    }

    // Audit (rule 7).

    static void auditsTwentyMinutesAfterSnapshot(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));

        long auditAt = view.waitForLoadKeepingLive(AUDIT_WAIT_LIMIT_MILLIS);

        assertEquals(100 + FIRST_AUDIT_MILLIS, auditAt, "the snapshot was taken at 100 ms");
    }

    static void auditsWithinThirtyMinutes(ViewStarter starter) {
        ViewDriver view = ViewDriver.start(starter, HIGHEST_RANDOM_NUMBER);
        view.subscribe();
        view.waitForLoad(3_000);
        view.loadSnapshot(network(10));
        long tookSnapshotAt = view.now();
        view.pulse(pulse(10));

        long auditAt = view.waitForLoadKeepingLive(AUDIT_WAIT_LIMIT_MILLIS);
        long auditDelay = auditAt - tookSnapshotAt;

        assertTrue(auditDelay > LAST_AUDIT_MILLIS - 60_000 && auditDelay <= LAST_AUDIT_MILLIS,
                "the audit comes 20 min + 10 min x the random number later, but came after " + auditDelay + " ms");
    }

    static void auditMatchesAtSameRevision(ViewStarter starter) {
        ViewDriver view = auditLoading(starter);
        NetworkCopy beforeAudit = view.copy();
        NetworkState otherMetrics = network(EPOCH, 10).clearMetrics().addMetrics(metrics(1, 77)).build();
        view.loadSnapshot(otherMetrics);

        assertEquals(Optional.of(AuditOutcome.MATCHED), view.lastAuditOutcome(),
                "metrics are left out of the comparison");
        assertTrue(view.isLive(), "a matching audit keeps the view live");
        assertEquals(beforeAudit, view.copy(), "a matching audit keeps the copy, its metrics too");
    }

    static void auditDifferenceTakesSnapshot(ViewStarter starter) {
        ViewDriver view = auditLoading(starter);
        NetworkState different = network(EPOCH, 10).setServers(0, groupServer(1, 10, "STOPPING")).build();
        view.loadSnapshot(different);
        Optional<AuditOutcome> outcome = view.lastAuditOutcome();
        boolean liveAfterDifference = view.isLive();
        view.advance(100);
        view.pulse(pulse(10));

        assertEquals(Optional.of(AuditOutcome.DIFFERED), outcome);
        assertEquals("STOPPING", serverState(view, 1), "a difference takes the snapshot");
        assertFalse(liveAfterDifference, "a taken snapshot needs a good pulse again");
        assertTrue(view.isLive());
    }

    static void auditComparesAtExactlyS(ViewStarter starter) {
        ViewDriver view = auditLoading(starter);
        NetworkState atTwelve = network(EPOCH, 12).setServers(0, groupServer(1, 10, "STOPPING")).build();
        view.loadSnapshot(atTwelve);
        Optional<AuditOutcome> outcomeOnArrival = view.lastAuditOutcome();
        boolean liveWhileComparing = view.isLive();
        view.advance(100);
        view.batch(serverStateBatch(10, 11, "STARTING"));
        Optional<AuditOutcome> outcomeBelowS = view.lastAuditOutcome();
        view.advance(100);
        view.batch(serverStateBatch(11, 12, "STOPPING"));

        assertEquals(Optional.empty(), outcomeOnArrival, "the audit waits for the copy to reach S");
        assertTrue(liveWhileComparing, "an audit keeps the view live");
        assertEquals(Optional.empty(), outcomeBelowS);
        assertEquals(Optional.of(AuditOutcome.MATCHED), view.lastAuditOutcome());
    }

    static void auditFindsDifferenceAtExactlyS(ViewStarter starter) {
        ViewDriver view = auditLoading(starter);
        NetworkState atEleven = network(EPOCH, 11).setServers(0, groupServer(1, 10, "STOPPING")).build();
        view.loadSnapshot(atEleven);
        view.advance(100);
        view.batch(serverStateBatch(10, 11, "STARTING"));

        assertEquals(Optional.of(AuditOutcome.DIFFERED), view.lastAuditOutcome());
        assertEquals("STOPPING", serverState(view, 1));
        assertEquals(11, view.copy().revision());
    }

    static void auditJumpedPastRetriesSoon(ViewStarter starter) {
        ViewDriver view = auditLoading(starter);
        view.loadSnapshot(network(11));
        view.advance(100);
        view.batch(serverStateBatch(10, 12, "STOPPING"));
        Optional<AuditOutcome> outcome = view.lastAuditOutcome();
        long jumpedAt = view.now();
        view.sendPulseAtCopy();

        long retryAt = view.waitForLoad(5_000);

        assertEquals(Optional.of(AuditOutcome.JUMPED_PAST), outcome);
        assertEquals(jumpedAt + 2_000, retryAt,
                "after jumping past S, the audit runs again 2 s + 8 s x the number later");
    }

    static void auditSnapshotBehindCopyJumpedPast(ViewStarter starter) {
        ViewDriver view = auditLoading(starter);
        view.loadSnapshot(network(9));

        assertEquals(Optional.of(AuditOutcome.JUMPED_PAST), view.lastAuditOutcome());
        assertEquals(10, view.copy().revision(), "a snapshot behind the copy is not taken");
        assertTrue(view.isLive());
    }

    static void auditNotReachedTakesSnapshot(ViewStarter starter) {
        ViewDriver view = auditLoading(starter);
        NetworkState atTwelve = network(EPOCH, 12).setServers(0, groupServer(1, 10, "STOPPING")).build();
        view.loadSnapshot(atTwelve);
        long comparingSince = view.now();
        view.advanceWithoutLoadTo(comparingSince + 2_999, "the audit waits 3 s");
        Optional<AuditOutcome> outcomeJustBefore = view.lastAuditOutcome();
        view.advance(1);
        Optional<AuditOutcome> outcomeAtThreeSeconds = view.lastAuditOutcome();
        boolean liveAfterTaking = view.isLive();

        assertEquals(Optional.empty(), outcomeJustBefore);
        assertEquals(Optional.of(AuditOutcome.NOT_REACHED), outcomeAtThreeSeconds);
        assertEquals(12, view.copy().revision(), "S not reached within 3 s takes the snapshot");
        assertEquals("STOPPING", serverState(view, 1));
        assertFalse(liveAfterTaking, "a taken snapshot needs a good pulse again");
    }

    static void reloadWaitsForAuditLoad(ViewStarter starter) {
        ViewDriver view = auditLoading(starter);
        view.advance(100);
        view.batch(batch(EPOCH + 1, 2, 3).build());
        boolean loadedWhileAuditRuns = view.requestedLoad();
        view.advanceWithoutLoadTo(view.now() + 3_000, "a reload waits for the running audit load");
        view.loadSnapshot(network(10));
        boolean reloadedAfterAuditAnswer = view.requestedLoad();

        assertFalse(loadedWhileAuditRuns);
        assertEquals(Optional.empty(), view.lastAuditOutcome(), "a reload abandons the audit");
        assertTrue(reloadedAfterAuditAnswer, "the reload starts once the audit load has answered");
    }

    static void auditsAgainTwentyMinutesAfterMatch(ViewStarter starter) {
        ViewDriver view = auditLoading(starter);
        view.loadSnapshot(network(10));
        long matchedAt = view.now();

        long nextAuditAt = view.waitForLoadKeepingLive(AUDIT_WAIT_LIMIT_MILLIS);

        assertEquals(matchedAt + FIRST_AUDIT_MILLIS, nextAuditAt,
                "after a match, the next audit is 20 min + 10 min x the number later");
    }

    static void auditsAgainTwentyMinutesAfterFailedLoad(ViewStarter starter) {
        ViewDriver view = auditLoading(starter);
        view.advance(1_000);
        view.failLoad();
        long failedAt = view.now();

        long nextAuditAt = view.waitForLoadKeepingLive(AUDIT_WAIT_LIMIT_MILLIS);

        assertEquals(failedAt + FIRST_AUDIT_MILLIS, nextAuditAt,
                "after a failed audit load, the next audit is 20 min later");
    }

    static void failedAuditLoadDoesNotBackOff(ViewStarter starter) {
        ViewDriver view = auditLoading(starter);
        long auditStartedAt = view.now();
        view.advance(1_000);
        view.failLoad();
        view.advance(100);
        view.batch(batch(EPOCH + 1, 2, 3).build());

        long reloadAt = view.waitForLoad(5_000);

        assertEquals(auditStartedAt + 2_000, reloadAt,
                "only the 2 s spacing applies; a failed reload would delay it to 2 s after the failure");
    }

    static void auditSnapshotFromAnotherEpochReloads(ViewStarter starter) {
        ViewDriver view = auditLoading(starter);
        long auditStartedAt = view.now();
        view.loadSnapshot(network(EPOCH + 1, 3).build());
        Optional<AuditOutcome> outcome = view.lastAuditOutcome();
        boolean liveAfterAnswer = view.isLive();

        long reloadAt = view.waitForLoad(5_000);

        assertEquals(Optional.empty(), outcome, "an audit snapshot from another epoch is no audit outcome");
        assertFalse(liveAfterAnswer, "an audit snapshot from another epoch means reload");
        assertEquals(auditStartedAt + 2_000, reloadAt);
    }

    /**
     * Returns a view live at revision 10 whose first audit load is running.
     */
    private static ViewDriver auditLoading(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        view.waitForLoadKeepingLive(AUDIT_WAIT_LIMIT_MILLIS);
        return view;
    }

    // Own writes (design 6.4).

    static void serverWriteHoldsItsReads(ViewStarter starter) {
        assertOwnWriteHolds(starter, WriteKind.SERVER,
                EnumSet.of(ReadKind.SERVER, ReadKind.SERVER_GROUP, ReadKind.PERSISTENT_SERVER));
    }

    static void groupWriteHoldsItsReads(ViewStarter starter) {
        assertOwnWriteHolds(starter, WriteKind.SERVER_GROUP, EnumSet.of(ReadKind.SERVER_GROUP, ReadKind.SERVER));
    }

    static void persistentServerWriteHoldsItsReads(ViewStarter starter) {
        assertOwnWriteHolds(starter, WriteKind.PERSISTENT_SERVER,
                EnumSet.of(ReadKind.PERSISTENT_SERVER, ReadKind.SERVER));
    }

    static void blueprintWriteHoldsItsReads(ViewStarter starter) {
        assertOwnWriteHolds(starter, WriteKind.BLUEPRINT, EnumSet.of(ReadKind.SERVER_GROUP, ReadKind.SERVER));
    }

    private static void assertOwnWriteHolds(ViewStarter starter, WriteKind written, Set<ReadKind> heldReads) {
        Set<ReadKind> otherReads = EnumSet.complementOf(EnumSet.copyOf(heldReads));
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        view.wrote(written, EPOCH, 12);
        Set<ReadKind> answeredAfterWrite = answeredKinds(view);
        view.advance(100);
        view.batch(serverStateBatch(10, 11, "STARTING"));
        Set<ReadKind> answeredBelowWrite = answeredKinds(view);
        view.advance(100);
        view.batch(serverStateBatch(11, 12, "STOPPING"));

        assertEquals(otherReads, answeredAfterWrite, "a " + written + " write holds exactly these reads: " + heldReads);
        assertEquals(otherReads, answeredBelowWrite, "a copy below the write's revision does not answer them");
        assertEquals(EnumSet.allOf(ReadKind.class), answeredKinds(view),
                "a copy at the write's revision answers again");
    }

    static void laterEpochWriteHoldsReads(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        view.wrote(WriteKind.SERVER, EPOCH + 1, 3);
        boolean answeredAfterWrite = view.mayAnswer(ReadKind.SERVER);
        view.advance(2_000);
        view.pulse(pulse(EPOCH + 1, 3));
        view.assertLoadRequested("a pulse from another epoch means reload");
        view.advance(100);
        view.loadSnapshot(network(EPOCH + 1, 3).build());
        view.advance(100);
        view.pulse(pulse(EPOCH + 1, 3));

        assertFalse(answeredAfterWrite, "a copy in an earlier epoch than the write does not have it");
        assertTrue(view.mayAnswer(ReadKind.SERVER), "a copy in the write's epoch at its revision has it");
    }

    static void earlierEpochWriteHoldsNothing(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(EPOCH + 1, 3).build());
        view.wrote(WriteKind.SERVER, EPOCH, 50);

        assertEquals(EnumSet.allOf(ReadKind.class), answeredKinds(view),
                "a copy in a later epoch than the write answers");
    }

    static void writeInCopyHoldsNothing(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        view.wrote(WriteKind.SERVER, EPOCH, 10);

        assertEquals(EnumSet.allOf(ReadKind.class), answeredKinds(view));
    }

    static void highestOwnWriteHolds(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        view.wrote(WriteKind.SERVER, EPOCH, 14);
        view.wrote(WriteKind.SERVER, EPOCH, 12);
        view.advance(100);
        view.batch(serverStateBatch(10, 12, "STARTING"));
        boolean answeredAtLowerWrite = view.mayAnswer(ReadKind.SERVER);
        view.advance(100);
        view.batch(serverStateBatch(12, 14, "STOPPING"));

        assertFalse(answeredAtLowerWrite, "a later response with a lower revision does not lower the bar");
        assertTrue(view.mayAnswer(ReadKind.SERVER));
    }

    static void lowerEpochSnapshotReachesEarlierWrite(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        view.wrote(WriteKind.SERVER, EPOCH, 12);
        wantReloadIntoLowerEpoch(view);
        view.waitForLoad(3_000);
        takeLowerEpochSnapshot(view);

        assertEquals(EnumSet.allOf(ReadKind.class), answeredKinds(view),
                "a snapshot whose load started after the write holds it, also in a lower epoch");
    }

    static void snapshotMissesWriteDuringItsLoad(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        wantReloadIntoLowerEpoch(view);
        view.waitForLoad(3_000);
        view.wrote(WriteKind.SERVER, EPOCH, 12);
        takeLowerEpochSnapshot(view);

        assertTrue(view.awaitsOwnWrite(ReadKind.SERVER),
                "the write came in a step after the load started, so the snapshot may lack it");
    }

    static void loadStartingInWriteStepReachesWrite(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        wantReloadIntoLowerEpoch(view);
        view.jumpTo(2_000);
        view.wrote(WriteKind.SERVER, EPOCH, 12);
        boolean loadStartedInWriteStep = view.requestedLoad();
        takeLowerEpochSnapshot(view);

        assertTrue(loadStartedInWriteStep, "the reload is due at 2 s, 2 s after the first load");
        assertTrue(view.mayAnswer(ReadKind.SERVER), "the input acts before the load starts, so the load holds it");
    }

    static void differingAuditReachesEarlierWrite(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        view.wrote(WriteKind.SERVER, EPOCH, 12);
        view.waitForLoadKeepingLive(AUDIT_WAIT_LIMIT_MILLIS);
        NetworkState different = network(EPOCH, 10).setServers(0, groupServer(1, 10, "STOPPING")).build();
        view.loadSnapshot(different);
        Optional<AuditOutcome> outcome = view.lastAuditOutcome();
        view.advance(100);
        view.pulse(pulse(10));

        assertEquals(Optional.of(AuditOutcome.DIFFERED), outcome);
        assertTrue(view.mayAnswer(ReadKind.SERVER), "the audit's load started after the write, so its snapshot holds it");
    }

    static void unreachedAuditReachesEarlierWrite(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        view.wrote(WriteKind.SERVER, EPOCH, 12);
        view.waitForLoadKeepingLive(AUDIT_WAIT_LIMIT_MILLIS);
        view.loadSnapshot(network(11));
        view.advance(3_000);
        Optional<AuditOutcome> outcome = view.lastAuditOutcome();
        view.advance(100);
        view.pulse(pulse(11));

        assertEquals(Optional.of(AuditOutcome.NOT_REACHED), outcome);
        assertTrue(view.mayAnswer(ReadKind.SERVER), "the audit's load started after the write, so its snapshot holds it");
    }

    static void auditMissesWriteDuringItsLoad(ViewStarter starter) {
        ViewDriver view = auditLoading(starter);
        view.advance(100);
        view.wrote(WriteKind.SERVER, EPOCH, 12);
        view.advance(100);
        view.loadSnapshot(network(11));
        view.advance(3_000);
        Optional<AuditOutcome> outcome = view.lastAuditOutcome();
        view.advance(100);
        view.pulse(pulse(11));

        assertEquals(Optional.of(AuditOutcome.NOT_REACHED), outcome);
        assertTrue(view.awaitsOwnWrite(ReadKind.SERVER),
                "the audit's load started before the write, so its snapshot may lack it, though it arrived after");
    }

    static void untakenAuditReachesNoWrite(ViewStarter starter) {
        ViewDriver matching = ViewDriver.liveAt(starter, network(10));
        matching.wrote(WriteKind.SERVER, EPOCH, 12);
        matching.waitForLoadKeepingLive(AUDIT_WAIT_LIMIT_MILLIS);
        matching.loadSnapshot(network(10));
        ViewDriver jumping = ViewDriver.liveAt(starter, network(10));
        jumping.wrote(WriteKind.SERVER, EPOCH, 12);
        jumping.waitForLoadKeepingLive(AUDIT_WAIT_LIMIT_MILLIS);
        jumping.loadSnapshot(network(9));

        assertEquals(Optional.of(AuditOutcome.MATCHED), matching.lastAuditOutcome());
        assertTrue(matching.awaitsOwnWrite(ReadKind.SERVER), "a matched audit takes no snapshot");
        assertEquals(Optional.of(AuditOutcome.JUMPED_PAST), jumping.lastAuditOutcome());
        assertTrue(jumping.awaitsOwnWrite(ReadKind.SERVER), "nor does an audit that the copy went past");
    }

    static void writeAfterSnapshotMakesReadsWait(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        view.wrote(WriteKind.SERVER, EPOCH, 12);
        wantReloadIntoLowerEpoch(view);
        view.waitForLoad(3_000);
        takeLowerEpochSnapshot(view);
        boolean answeredAfterSnapshot = view.mayAnswer(ReadKind.SERVER);
        view.wrote(WriteKind.SERVER, EPOCH - 1, 6);
        boolean waitingAfterLaterWrite = view.awaitsOwnWrite(ReadKind.SERVER);
        view.advance(100);
        view.batch(batch(EPOCH - 1, 4, 6).addServers(groupServer(1, 10, "STOPPING")).build());

        assertTrue(answeredAfterSnapshot, "the snapshot holds the write before its load");
        assertTrue(waitingAfterLaterWrite, "a write after the snapshot raises the bar again");
        assertTrue(view.mayAnswer(ReadKind.SERVER), "a copy at the later write's revision answers");
    }

    /**
     * Sends a pulse from a lower epoch at 1.2 s, as after a restore, so a reload is due at 2 s.
     */
    private static void wantReloadIntoLowerEpoch(ViewDriver view) {
        view.advance(1_000);
        view.pulse(pulse(EPOCH - 1, 4));
    }

    /**
     * Answers the running reload with a snapshot of the lower epoch at revision 4, then sends a good pulse.
     */
    private static void takeLowerEpochSnapshot(ViewDriver view) {
        view.advance(100);
        view.loadSnapshot(network(EPOCH - 1, 4).build());
        view.advance(100);
        view.pulse(pulse(EPOCH - 1, 4));
    }

    // Waiting for own writes (Answers).

    static void serverWriteMakesItsReadsWait(ViewStarter starter) {
        assertOwnWriteMakesReadsWait(starter, WriteKind.SERVER,
                EnumSet.of(ReadKind.SERVER, ReadKind.SERVER_GROUP, ReadKind.PERSISTENT_SERVER));
    }

    static void groupWriteMakesItsReadsWait(ViewStarter starter) {
        assertOwnWriteMakesReadsWait(starter, WriteKind.SERVER_GROUP,
                EnumSet.of(ReadKind.SERVER_GROUP, ReadKind.SERVER));
    }

    static void persistentServerWriteMakesItsReadsWait(ViewStarter starter) {
        assertOwnWriteMakesReadsWait(starter, WriteKind.PERSISTENT_SERVER,
                EnumSet.of(ReadKind.PERSISTENT_SERVER, ReadKind.SERVER));
    }

    static void blueprintWriteMakesItsReadsWait(ViewStarter starter) {
        assertOwnWriteMakesReadsWait(starter, WriteKind.BLUEPRINT,
                EnumSet.of(ReadKind.SERVER_GROUP, ReadKind.SERVER));
    }

    private static void assertOwnWriteMakesReadsWait(
            ViewStarter starter,
            WriteKind written,
            Set<ReadKind> waitingReads
    ) {
        Set<ReadKind> otherReads = EnumSet.complementOf(EnumSet.copyOf(waitingReads));
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        view.wrote(written, EPOCH, 12);
        Set<ReadKind> waitingAfterWrite = waitingKinds(view);
        Set<ReadKind> answeredAfterWrite = answeredKinds(view);
        view.advance(100);
        view.batch(serverStateBatch(10, 11, "STARTING"));
        Set<ReadKind> waitingBelowWrite = waitingKinds(view);
        view.advance(100);
        view.batch(serverStateBatch(11, 12, "STOPPING"));

        assertEquals(waitingReads, waitingAfterWrite, "the reads a " + written + " write changes wait for the copy");
        assertEquals(otherReads, answeredAfterWrite, "a read the write does not change is answered");
        assertEquals(waitingReads, waitingBelowWrite, "a copy below the write's revision keeps them waiting");
        assertEquals(Set.of(), waitingKinds(view), "a copy at the write's revision ends the wait");
        assertEquals(EnumSet.allOf(ReadKind.class), answeredKinds(view), "and answers them");
    }

    static void readGoesOverHttpAfterThreeSeconds(ViewStarter starter) {
        ViewDriver view = waitingForServerWrite(starter);
        long startedAt = view.now();

        ReadRoute justBefore = view.routeOfReadStartedAt(ReadKind.SERVER, startedAt, startedAt + 2_999);
        ReadRoute atThreeSeconds = view.routeOfReadStartedAt(ReadKind.SERVER, startedAt, startedAt + 3_000);

        assertEquals(ReadRoute.WAIT, justBefore, "a read waits for the copy for 3 s");
        assertEquals(ReadRoute.OVER_HTTP, atThreeSeconds, "after 3 s the read goes over HTTP");
    }

    static void copyAnswersAtExactlyThreeSeconds(ViewStarter starter) {
        ViewDriver view = waitingForServerWrite(starter);
        long startedAt = view.now();
        view.jumpTo(startedAt + 3_000);
        view.batch(serverStateBatch(10, 12, "STOPPING"));

        assertEquals(ReadRoute.FROM_COPY, view.routeOfReadStartedAt(ReadKind.SERVER, startedAt),
                "a copy that has the write answers, also at the read's deadline");
    }

    static void noWaitWhileBatchHeld(ViewStarter starter) {
        ViewDriver view = waitingForServerWrite(starter);
        view.advance(100);
        view.batch(serverStateBatch(11, 12, "STOPPING"));

        assertFalse(view.awaitsOwnWrite(ReadKind.SERVER), "a view that holds a batch is not live");
    }

    static void noWaitWhileBehindPulse(ViewStarter starter) {
        ViewDriver view = waitingForServerWrite(starter);
        view.advance(100);
        view.pulse(pulse(12));

        assertFalse(view.awaitsOwnWrite(ReadKind.SERVER), "a copy behind a pulse is not live");
    }

    static void noWaitOnceGoodPulseFifteenSecondsOld(ViewStarter starter) {
        ViewDriver view = waitingForServerWrite(starter);
        long goodPulseAt = view.now();

        boolean waitingJustBefore = view.awaitsOwnWriteAt(ReadKind.SERVER, goodPulseAt + 14_999);
        boolean waitingAtFifteenSeconds = view.awaitsOwnWriteAt(ReadKind.SERVER, goodPulseAt + 15_000);

        assertTrue(waitingJustBefore, "a read waits while the view is live");
        assertFalse(waitingAtFifteenSeconds, "time alone ends live, and the wait with it");
    }

    static void noWaitAfterConnectionLoss(ViewStarter starter) {
        ViewDriver view = waitingForServerWrite(starter);
        view.loseConnection();

        assertFalse(view.awaitsOwnWrite(ReadKind.SERVER), "a lost connection ends live at once");
    }

    static void noWaitWhileReloading(ViewStarter starter) {
        ViewDriver view = waitingForServerWrite(starter);
        view.wroteRevisionUnknown();
        boolean waitingWhileReloadWanted = view.awaitsOwnWrite(ReadKind.SERVER);
        view.waitForLoad(3_000);

        assertFalse(waitingWhileReloadWanted, "a view that wants a reload is not live");
        assertFalse(view.awaitsOwnWrite(ReadKind.SERVER), "a view whose reload runs is not live");
    }

    static void noWaitWhileAnswersSwitchedOff(ViewStarter starter) {
        ViewDriver view = waitingForServerWrite(starter);
        view.advance(100);
        view.pulse(pulse(10).toBuilder().setViewsMayAnswer(false).build());
        boolean waitingWhileSwitchedOff = view.awaitsOwnWrite(ReadKind.SERVER);
        view.advance(100);
        view.pulse(pulse(10));

        assertFalse(waitingWhileSwitchedOff, "views_may_answer false sends every read over HTTP");
        assertTrue(view.awaitsOwnWrite(ReadKind.SERVER), "the next true pulse lets the read wait again");
    }

    /**
     * Returns a view live at revision 10 in which a server read waits for an own write at revision 12.
     */
    private static ViewDriver waitingForServerWrite(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        view.wrote(WriteKind.SERVER, EPOCH, 12);
        assertTrue(view.awaitsOwnWrite(ReadKind.SERVER), "a server read in a live view waits for a server write");
        return view;
    }

    static void writeOfUnknownRevisionReloadsSyncedView(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        view.wroteRevisionUnknown();
        Set<ReadKind> answeredAfterWrite = answeredKinds(view);
        long loadAt = view.waitForLoad(3_000);
        view.advance(100);
        view.loadSnapshot(network(12));
        view.advance(100);
        view.pulse(pulse(12));

        assertEquals(Set.of(), answeredAfterWrite, "the copy may lack a write whose revision is unknown");
        assertEquals(2_000, loadAt, "the write means reload, 2 s after the previous load");
        assertEquals(EnumSet.allOf(ReadKind.class), answeredKinds(view),
                "a snapshot loaded after the write and a good pulse answer again");
    }

    static void writeOfUnknownRevisionAbandonsRunningReload(ViewStarter starter) {
        ViewDriver view = ViewDriver.start(starter);
        view.subscribe();
        view.advance(50);
        view.wroteRevisionUnknown();
        view.advance(50);
        view.loadSnapshot(network(10));
        Optional<NetworkCopy> copyAfterAnswer = view.copyIfAny();
        long loadAt = view.waitForLoad(3_000);

        assertTrue(copyAfterAnswer.isEmpty(), "a load that started before the write may lack it");
        assertEquals(2_000, loadAt, "the write means reload, 2 s after the abandoned load");
    }

    static void writeOfUnknownRevisionKeepsWantedReload(ViewStarter starter) {
        ViewDriver view = ViewDriver.start(starter, 0.5);
        view.subscribe();
        view.advance(500);
        view.wroteRevisionUnknown();

        long loadAt = view.waitForLoad(3_000);

        assertEquals(1_000, loadAt, "a wanted reload loads after the write anyway, so the write does not move it");
    }

    static void writeOfUnknownRevisionWhileDisconnectedStartsNoLoad(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        view.advance(3_000);
        view.loseConnection();
        view.wroteRevisionUnknown();

        view.advanceWithoutLoadTo(10_000, "the load after the reconnect comes after the write");
        view.subscribe();

        view.assertLoadRequested("a reconnect means reload");
    }

    static void deletesItemsAndIgnoresUnknownIds(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        StateBatch batch = batch(10, 11)
                .addDeletedServerIds(id(2))
                .addDeletedServerIds(id(98))
                .build();
        view.batch(batch);

        assertEquals(Set.of(id(1)), view.copy().servers().keySet(), "a deleted id removes its server");
        assertEquals(11, view.copy().revision());
        assertTrue(view.isLive(), "deleting an unknown id is no error");
    }

    // Decisions taken during the build, and the edges they settle.

    static void holdsBaseAboveCopyUnsigned(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(HIGHEST_SIGNED));
        view.batch(serverStateBatch(LOWEST_PAST_SIGNED, LOWEST_PAST_SIGNED + 1, "STOPPING"));

        assertEquals(HIGHEST_SIGNED, view.copy().revision(), "a base of 2^63 is above a copy at 2^63 - 1");
        assertFalse(view.isLive(), "the batch is held");
    }

    static void missingBatchAtExactlyThreeSecondsIsOnTime(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        view.advance(800);
        view.batch(serverStateBatch(12, 13, "STOPPING"));
        long heldAt = view.now();
        view.advanceWithoutLoadTo(heldAt + 2_999, "a gap waits 3 s");
        view.jumpTo(heldAt + 3_000);
        view.batch(serverStateBatch(10, 12, "STARTING"));

        assertFalse(view.requestedLoad(), "the input acts before the deadline in the same step");
        assertEquals(13, view.copy().revision());
        assertTrue(view.isLive());
    }

    static void batchHeldDuringLoadCountsFromArrival(ViewStarter starter) {
        ViewDriver view = ViewDriver.start(starter);
        view.subscribe();
        view.advance(100);
        view.batch(serverStateBatch(11, 12, "STOPPING"));
        long heldAt = view.now();
        view.advance(2_000);
        view.loadSnapshot(network(10));

        long reloadAt = view.waitForLoad(10_000);

        assertEquals(heldAt + 3_000, reloadAt, "the 3 s count from the batch's arrival, not from the snapshot");
    }

    static void heldBatchExpiredDuringLoadReloadsOnTake(ViewStarter starter) {
        ViewDriver view = ViewDriver.start(starter);
        view.subscribe();
        view.advance(100);
        view.batch(serverStateBatch(11, 12, "STOPPING"));
        view.advanceWithoutLoadTo(3_500, "a held batch's deadline does not act while the load runs");
        view.loadSnapshot(network(10));

        assertEquals(10, view.copy().revision(), "the load's snapshot is taken");
        view.assertLoadRequested("the held batch waited over 3 s, so the step that takes the snapshot reloads");
    }

    static void longLoadIsNotAbandonedForWaitingBatch(ViewStarter starter) {
        ViewDriver view = ViewDriver.start(starter);
        view.subscribe();
        view.advance(100);
        view.batch(serverStateBatch(9, 11, "STOPPING"));
        view.advanceWithoutLoadTo(5_000, "the running load is not replaced");
        view.loadSnapshot(network(10));

        assertEquals(11, view.copy().revision(), "the snapshot is taken, and the waiting batch applies on it");
        assertEquals("STOPPING", view.copy().servers().get(id(1)).getState());
        assertFalse(view.requestedLoad(), "nothing is missing after the snapshot, so nothing reloads");
    }

    static void gapCountsFromOldestHeldBatch(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        view.advance(800);
        view.batch(serverStateBatch(12, 13, "STOPPING"));
        long firstHeldAt = view.now();
        view.advance(2_000);
        view.batch(serverStateBatch(13, 14, "STARTING"));

        long reloadAt = view.waitForLoad(10_000);

        assertEquals(firstHeldAt + 3_000, reloadAt, "the gap's 3 s count from the oldest held batch");
    }

    static void reloadsOnBatchFromLowerEpoch(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        view.advance(2_000);
        view.batch(batch(EPOCH - 1, 10, 11).addServers(groupServer(1, 10, "STOPPING")).build());

        view.assertLoadRequested("a batch from a lower epoch means reload too");
        assertEquals(10, view.copy().revision(), "a batch from another epoch is not applied");
    }

    static void batchWithMissingItemIsNotApplied(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        NetworkCopy before = view.copy();
        view.advance(2_000);
        StateBatch broken = batch(10, 11)
                .addServers(groupServer(1, 10, "STOPPING"))
                .addServers(groupServer(3, 99, "PREPARING"))
                .build();
        view.batch(broken);

        view.assertLoadRequested("a batch with a missing item means reload");
        assertEquals(before, view.copy(), "the view keeps the last copy whose references resolve");
    }

    static void snapshotWithMissingItemIsNotTaken(ViewStarter starter) {
        NetworkState broken = network(EPOCH, 12).addServers(groupServer(3, 99, "PREPARING")).build();
        ViewDriver fresh = ViewDriver.start(starter);
        fresh.subscribe();
        fresh.loadSnapshot(broken);
        ViewDriver reloading = ViewDriver.liveAt(starter, network(10));
        NetworkCopy before = reloading.copy();
        reloading.advance(3_000);
        reloading.loseConnection();
        reloading.subscribe();
        reloading.assertLoadRequested("a reconnect means reload");
        reloading.loadSnapshot(broken);

        assertTrue(fresh.copyIfAny().isEmpty(), "a first snapshot with a missing item leaves no copy");
        assertEquals(before, reloading.copy(), "the view keeps the last copy whose references resolve");
    }

    static void reloadsOnPulseFromLowerEpoch(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        view.advance(2_000);
        view.pulse(pulse(EPOCH - 1, 10));

        view.assertLoadRequested("a pulse from a lower epoch means reload too");
        assertFalse(view.isLive());
    }

    static void pulseAboveCopyUnsigned(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(HIGHEST_SIGNED));
        view.advance(100);
        view.pulse(pulse(LOWEST_PAST_SIGNED));

        assertFalse(view.isLive(), "a pulse at 2^63 is ahead of a copy at 2^63 - 1");
    }

    static void furtherPulseUnsigned(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(HIGHEST_SIGNED - 1));
        view.advance(100);
        view.pulse(pulse(HIGHEST_SIGNED));
        view.advance(100);
        view.pulse(pulse(LOWEST_PAST_SIGNED));
        view.advance(100);
        view.batch(serverStateBatch(HIGHEST_SIGNED - 1, HIGHEST_SIGNED, "STOPPING"));

        assertFalse(view.isLive(), "a pulse at 2^63 is further ahead than one at 2^63 - 1");
    }

    static void copyReachesPulseUnsigned(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(HIGHEST_SIGNED - 1));
        view.advance(100);
        view.pulse(pulse(HIGHEST_SIGNED));
        view.advance(100);
        view.batch(serverStateBatch(HIGHEST_SIGNED - 1, LOWEST_PAST_SIGNED, "STOPPING"));

        assertTrue(view.isLive(), "a copy at 2^63 has reached a pulse at 2^63 - 1");
    }

    static void batchReachingPulseAtExactlyThreeSecondsIsOnTime(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        view.advance(800);
        view.pulse(pulse(12));
        long aheadSince = view.now();
        view.advanceWithoutLoadTo(aheadSince + 2_999, "the copy may stay behind a pulse for 3 s");
        view.jumpTo(aheadSince + 3_000);
        view.batch(serverStateBatch(10, 12, "STOPPING"));

        assertFalse(view.requestedLoad(), "the batch acts before the 3 s deadline in the same step");
        assertTrue(view.isLive());
    }

    static void behindCountsFromFirstPulseAhead(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        view.advance(800);
        view.pulse(pulse(12));
        long firstAheadAt = view.now();
        view.advance(1_000);
        view.pulse(pulse(14));

        long reloadAt = view.waitForLoad(10_000);

        assertEquals(firstAheadAt + 3_000, reloadAt, "the 3 s count from the first pulse ahead");
    }

    static void pulseReplacesMetricsWhileDisconnected(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        view.advance(2_000);
        view.loseConnection();
        view.pulse(pulse(10).toBuilder().addMetrics(metrics(2, 40)).build());

        assertEquals(Map.of(id(2), metrics(2, 40)), view.copy().metrics(), "a pulse replaces metrics in every state");
    }

    static void lossDropsWantedReload(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        view.advance(300);
        view.batch(batch(EPOCH + 1, 2, 3).build());
        view.advance(100);
        view.loseConnection();
        view.advanceWithoutLoadTo(5_000, "a view starts no load while disconnected");
        view.subscribe();

        view.assertLoadRequested("the reconnect reloads");
    }

    static void lossAbandonsComparingAudit(ViewStarter starter) {
        ViewDriver view = auditLoading(starter);
        view.loadSnapshot(network(12));
        view.loseConnection();
        long lostAt = view.now();
        while (view.now() < lostAt + 5_000) {
            view.advance(100);
            assertEquals(Optional.empty(), view.lastAuditOutcome(), "a lost connection abandons the audit");
        }

        assertEquals(10, view.copy().revision(), "the abandoned audit's snapshot is not taken");
        assertFalse(view.isLive());
    }

    static void failedAbandonedLoadIsNoFailedLoad(ViewStarter starter) {
        ViewDriver view = ViewDriver.start(starter);
        view.subscribe();
        view.advance(50);
        view.loseConnection();
        view.advance(50);
        view.subscribe();
        view.advance(100);
        view.failLoad();
        long secondLoad = view.waitForLoad(10_000);
        view.failLoad();

        long thirdLoad = view.waitForLoad(10_000);

        assertEquals(2_000, secondLoad, "the abandoned load's failure does not back off the new load");
        assertEquals(secondLoad + 2_000, thirdLoad, "the next failure is the first in a row");
    }

    static void everyRandomDelayDrawsNewNumber(ViewStarter starter) {
        RandomSequence zeroFirstThenHalf = index -> index == 0 ? 0.0 : 0.5;
        ViewDriver view = ViewDriver.startWithSequence(starter, zeroFirstThenHalf);
        view.subscribe();
        view.assertLoadRequested("the first number is 0, so the first load starts at once");
        view.failLoad();

        long secondLoad = view.waitForLoad(10_000);

        assertEquals(3_000, secondLoad, "the second delay draws a later number, 0.5: 2 s back-off + 1 s");
    }

    static void auditReachingSAtExactlyThreeSecondsCompares(ViewStarter starter) {
        ViewDriver view = auditLoading(starter);
        view.loadSnapshot(network(11));
        long comparingSince = view.now();
        view.advanceWithoutLoadTo(comparingSince + 2_999, "the audit waits 3 s");
        view.jumpTo(comparingSince + 3_000);
        view.batch(serverStateBatch(10, 11, "AVAILABLE"));

        assertEquals(Optional.of(AuditOutcome.MATCHED), view.lastAuditOutcome(),
                "the batch acts before the audit's deadline in the same step");
    }

    static void auditDeadlineActsBeforeGapDeadline(ViewStarter starter) {
        ViewDriver view = auditLoading(starter);
        view.loadSnapshot(network(12));
        view.batch(serverStateBatch(12, 14, "STOPPING"));
        long bothSince = view.now();
        view.advanceWithoutLoadTo(bothSince + 2_999, "both wait 3 s");
        view.advance(1);

        assertEquals(Optional.of(AuditOutcome.NOT_REACHED), view.lastAuditOutcome(), "the audit's deadline acts first");
        assertEquals(14, view.copy().revision(), "its snapshot at 12 lets the held batch apply");
        assertFalse(view.requestedLoad(), "so the gap is closed and nothing reloads");
    }

    static void auditComparesUnsigned(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(HIGHEST_SIGNED - 1));
        view.waitForLoadKeepingLive(AUDIT_WAIT_LIMIT_MILLIS);
        view.loadSnapshot(network(HIGHEST_SIGNED));
        view.advance(100);
        view.batch(serverStateBatch(HIGHEST_SIGNED - 1, LOWEST_PAST_SIGNED, "STOPPING"));

        assertEquals(Optional.of(AuditOutcome.JUMPED_PAST), view.lastAuditOutcome(),
                "a copy at 2^63 is past an audit snapshot at 2^63 - 1");
    }

    static void noAuditWhileNotLive(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        long auditDueAt = 100 + FIRST_AUDIT_MILLIS;
        view.keepLiveWithoutLoadUntil(auditDueAt - 16_000);
        view.advanceWithoutLoadTo(auditDueAt, "no load before the audit is due");
        boolean liveWhenDue = view.isLive();

        view.advanceWithoutLoadTo(auditDueAt + 10_000, "a view that is not live starts no audit");

        assertFalse(liveWhenDue, "the last good pulse is over 15 s old when the audit is due");
    }

    static void ownWriteRevisionsUnsigned(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(HIGHEST_SIGNED - 1));
        view.wrote(WriteKind.SERVER, EPOCH, HIGHEST_SIGNED);
        view.advance(100);
        view.batch(serverStateBatch(HIGHEST_SIGNED - 1, LOWEST_PAST_SIGNED, "STOPPING"));

        assertTrue(view.mayAnswer(ReadKind.SERVER), "a copy at 2^63 has reached a write at 2^63 - 1");
    }

    static void ownWriteEpochsUnsigned(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(LOWEST_PAST_SIGNED, 3).build());
        view.wrote(WriteKind.SERVER, 1, 50);

        assertTrue(view.mayAnswer(ReadKind.SERVER), "a copy in epoch 2^63 is after a write in epoch 1");
    }

    // Helpers.

    private static String serverState(ViewDriver view, int serverNumber) {
        return view.copy().servers().get(id(serverNumber)).getState();
    }

    private static Set<ReadKind> waitingKinds(ViewDriver view) {
        Set<ReadKind> waiting = EnumSet.noneOf(ReadKind.class);
        for (ReadKind kind : ReadKind.values()) {
            if (view.awaitsOwnWrite(kind)) {
                waiting.add(kind);
            }
        }
        return waiting;
    }

    private static Set<ReadKind> answeredKinds(ViewDriver view) {
        Set<ReadKind> answered = EnumSet.noneOf(ReadKind.class);
        for (ReadKind kind : ReadKind.values()) {
            if (view.mayAnswer(kind)) {
                answered.add(kind);
            }
        }
        return answered;
    }
}
