package app.simplecloud.api.internal.networkstate;

import build.buf.gen.simplecloud.controller.v2.NetworkState;
import build.buf.gen.simplecloud.controller.v2.StateBatch;
import build.buf.gen.simplecloud.controller.v2.StatePulse;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

import static app.simplecloud.api.internal.networkstate.RuleTimes.AUDIT_INTERVAL_SPREAD;
import static app.simplecloud.api.internal.networkstate.RuleTimes.AUDIT_REACH_WAIT;
import static app.simplecloud.api.internal.networkstate.RuleTimes.AUDIT_RETRY_SPREAD;
import static app.simplecloud.api.internal.networkstate.RuleTimes.FIRST_BACK_OFF;
import static app.simplecloud.api.internal.networkstate.RuleTimes.GAP_WAIT;
import static app.simplecloud.api.internal.networkstate.RuleTimes.GOOD_PULSE_LIFETIME;
import static app.simplecloud.api.internal.networkstate.RuleTimes.LOAD_SPACING;
import static app.simplecloud.api.internal.networkstate.RuleTimes.LONGEST_BACK_OFF;
import static app.simplecloud.api.internal.networkstate.RuleTimes.OWN_WRITE_WAIT;
import static app.simplecloud.api.internal.networkstate.RuleTimes.PULSE_AHEAD_WAIT;
import static app.simplecloud.api.internal.networkstate.RuleTimes.RANDOM_LOAD_DELAY_BOUND;
import static app.simplecloud.api.internal.networkstate.RuleTimes.SHORTEST_AUDIT_INTERVAL;
import static app.simplecloud.api.internal.networkstate.RuleTimes.SHORTEST_AUDIT_RETRY;
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

    private static final long LONGEST_AUDIT_INTERVAL = SHORTEST_AUDIT_INTERVAL + AUDIT_INTERVAL_SPREAD;
    /**
     * A wanted reload starts within this, whatever the spacing and the back-off.
     */
    private static final long RELOAD_WAIT_LIMIT = LONGEST_BACK_OFF + RANDOM_LOAD_DELAY_BOUND;
    private static final long AUDIT_WAIT_LIMIT = LONGEST_AUDIT_INTERVAL + RELOAD_WAIT_LIMIT;
    private static final long AUDIT_RETRY_WAIT_LIMIT = SHORTEST_AUDIT_RETRY + AUDIT_RETRY_SPREAD;
    private static final double HIGHEST_RANDOM_NUMBER = 0.999_999;
    private static final long HIGHEST_SIGNED = Long.MAX_VALUE;
    private static final long TWO_TO_THE_63 = Long.MIN_VALUE;

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
                new ViewCheck("batch: reloads 3 s after a batch with base = revision above the copy",
                        ViewRuleChecks::reloadsThreeSecondsAfterBaseEqualsRevisionAboveCopy),
                new ViewCheck("batch: reloads on a batch from another epoch",
                        ViewRuleChecks::reloadsOnBatchFromAnotherEpoch),
                new ViewCheck("batch: reloads on a batch from a lower epoch",
                        ViewRuleChecks::reloadsOnBatchFromLowerEpoch),
                new ViewCheck("batch: compares revisions as unsigned numbers",
                        ViewRuleChecks::comparesRevisionsUnsigned),
                new ViewCheck("batch: a base at 2^63 is ahead of a copy at 2^63 - 1",
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
                new ViewCheck("pulse: a pulse at 2^63 is ahead of a copy at 2^63 - 1",
                        ViewRuleChecks::pulseAboveCopyUnsigned),
                new ViewCheck("pulse: a pulse at 2^63 is further ahead than one at 2^63 - 1",
                        ViewRuleChecks::furtherPulseUnsigned),
                new ViewCheck("pulse: a copy at 2^63 has reached a pulse at 2^63 - 1",
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
                new ViewCheck("connection: reloads after a reconnect", ViewRuleChecks::reloadsAfterReconnect),
                new ViewCheck("connection: a batch held before the loss is not applied after the reconnect's reload",
                        ViewRuleChecks::batchHeldBeforeLossDoesNotSurviveReload),
                new ViewCheck("connection: a batch that arrives during the loss is not applied after the reconnect's"
                        + " reload", ViewRuleChecks::batchDuringLossDoesNotSurviveReload),
                new ViewCheck("connection: a reload wanted before the loss starts no load while disconnected",
                        ViewRuleChecks::reloadWantedBeforeLossStartsNoLoad),
                new ViewCheck("connection: a held batch's 3 s want no reload while disconnected",
                        ViewRuleChecks::heldBatchWantsNoReloadWhileDisconnected),
                new ViewCheck("connection: no audit finishes while disconnected",
                        ViewRuleChecks::noAuditFinishesWhileDisconnected),
                new ViewCheck("connection: a failed answer of an abandoned load is no failed load",
                        ViewRuleChecks::failedAbandonedLoadIsNoFailedLoad),
                new ViewCheck("connection: ignores the answer of a load that ran across the loss",
                        ViewRuleChecks::ignoresLoadRunningAcrossLoss),
                new ViewCheck("reload: a taken snapshot replaces the copy's metrics",
                        ViewRuleChecks::takenSnapshotReplacesMetrics),
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
                new ViewCheck("audit: the highest number puts the first audit 1 ms before 30 min",
                        ViewRuleChecks::highestNumberAuditsJustBeforeThirtyMinutes),
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
                new ViewCheck("audit: compares revisions at 2^63 unsigned",
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
                new ViewCheck("own write: a read waits while the copy is below the write's revision in its epoch",
                        ViewRuleChecks::readWaitsWhileCopyBelowOwnWrite),
                new ViewCheck("own write: the copy answers once it reaches the write's revision, at it or above",
                        ViewRuleChecks::copyAnswersOnceAtOrAboveOwnWrite),
                new ViewCheck("own write: compares revisions at 2^63 unsigned",
                        ViewRuleChecks::ownWriteRevisionsUnsigned),
                new ViewCheck("own write: exactly 3 s after the write the copy answers, though it lacks the write",
                        ViewRuleChecks::copyAnswersThreeSecondsAfterOwnWrite),
                new ViewCheck("own write: a later write restarts the 3 s, and in one epoch the higher revision stays",
                        ViewRuleChecks::laterWriteRestartsWaitAndKeepsHigherRevision),
                new ViewCheck("own write: keeps the higher revision at 2^63 unsigned",
                        ViewRuleChecks::keptOwnWriteRevisionsUnsigned),
                new ViewCheck("own write: a write from another epoch replaces the kept write, whatever the revisions",
                        ViewRuleChecks::otherEpochWriteReplacesKeptWrite),
                new ViewCheck("own write: in a synced view, a write from another epoch than the copy's reloads",
                        ViewRuleChecks::otherEpochWriteReloadsSyncedView),
                new ViewCheck("own write: a copy in another epoch than the write waits only until 3 s after the write",
                        ViewRuleChecks::copyInOtherEpochWaitsOnlyThreeSeconds),
                new ViewCheck("own write: a write while disconnected, wanting a reload or reloading starts no load",
                        ViewRuleChecks::writeOutsideSyncedViewStartsNoLoad),
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
                        ViewRuleChecks::noWaitWhileAnswersSwitchedOff)
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
        OptionalLong halfwayLoad = halfway.loadWithin(RELOAD_WAIT_LIMIT);
        ViewDriver highest = ViewDriver.start(starter, HIGHEST_RANDOM_NUMBER);
        highest.subscribe();
        OptionalLong highestLoad = highest.loadWithin(RELOAD_WAIT_LIMIT);
        long halfTheBound = RANDOM_LOAD_DELAY_BOUND / 2;
        long justBelowTheBound = RANDOM_LOAD_DELAY_BOUND - 1;

        assertEquals(OptionalLong.of(halfTheBound), halfwayLoad, "a random number of 0.5 delays the load by 1 s");
        assertEquals(OptionalLong.of(justBelowTheBound), highestLoad,
                "the random delay is cut to whole milliseconds and stays below 2 s");
    }

    static void liveOnlyAfterFirstGoodPulse(ViewStarter starter) {
        ViewDriver view = ViewDriver.start(starter);
        view.subscribe();
        view.advance(50);
        // Allows answers, so that live shows in the reads.
        view.pulse(pulse(9));
        view.advance(50);
        view.loadSnapshot(network(10));

        assertFalse(view.readsStayOffHttp(), "a snapshot does not count as a pulse");

        view.advance(100);
        view.pulse(pulse(10));

        assertTrue(view.readsStayOffHttp(), "a pulse at the snapshot's revision makes the view live");
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
        assertTrue(view.readsStayOffHttp());
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
        assertTrue(view.readsStayOffHttp());
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
        assertTrue(view.readsStayOffHttp(), "deleting an unknown id is no error");
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
        assertTrue(view.readsStayOffHttp());
    }

    static void holdsBatchUntilGapFills(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        StateBatch ahead = batch(12, 13).addServers(groupServer(3, 10, "PREPARING")).build();
        view.batch(ahead);
        boolean liveWithGap = view.readsStayOffHttp();
        long revisionWithGap = view.copy().revision();
        view.advance(GAP_WAIT - 1_000);
        view.batch(serverStateBatch(10, 12, "STARTING"));

        assertFalse(liveWithGap, "a view that holds a batch is not live");
        assertEquals(10, revisionWithGap, "a batch ahead of the copy is held, not applied");
        assertEquals(13, view.copy().revision(), "the held batch applies once the gap fills");
        assertEquals("PREPARING", serverState(view, 3));
        assertTrue(view.readsStayOffHttp());
    }

    static void reloadsAfterGapHeldThreeSeconds(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        view.advance(800);
        view.batch(serverStateBatch(12, 13, "STARTING"));
        long heldAt = view.now();

        view.advanceWithoutLoadTo(heldAt + GAP_WAIT - 1, "a gap waits 3 s for the missing batch");
        view.advance(1);

        view.assertLoadRequested("a gap held for 3 s means reload");
        assertFalse(view.readsStayOffHttp());
    }

    static void reloadsThreeSecondsAfterBaseEqualsRevisionAboveCopy(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        view.advance(800);
        view.batch(batch(15, 15).build());
        long heldAt = view.now();

        OptionalLong loadAt = view.loadWithin(RELOAD_WAIT_LIMIT);

        assertEquals(OptionalLong.of(heldAt + GAP_WAIT), loadAt,
                "a batch with base = revision above the copy is a gap, so it reloads after 3 s");
    }

    static void reloadsOnBatchFromAnotherEpoch(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        view.advance(LOAD_SPACING);
        view.batch(batch(EPOCH + 1, 2, 3).addServers(groupServer(1, 10, "STOPPING")).build());

        view.assertLoadRequested("a batch from another epoch means reload");
        assertFalse(view.readsStayOffHttp());
        assertEquals("AVAILABLE", serverState(view, 1), "a batch from another epoch is not applied");
    }

    static void reloadsOnBatchFromLowerEpoch(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        view.advance(LOAD_SPACING);
        view.batch(batch(EPOCH - 1, 10, 11).addServers(groupServer(1, 10, "STOPPING")).build());

        view.assertLoadRequested("a batch from a lower epoch means reload too");
        assertEquals(10, view.copy().revision(), "a batch from another epoch is not applied");
    }

    static void comparesRevisionsUnsigned(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(HIGHEST_SIGNED));
        view.batch(serverStateBatch(HIGHEST_SIGNED, TWO_TO_THE_63, "STOPPING"));
        view.advance(100);
        view.pulse(pulse(TWO_TO_THE_63));

        assertEquals(TWO_TO_THE_63, view.copy().revision(), "2^63 is above 2^63 - 1 as a uint64");
        assertEquals("STOPPING", serverState(view, 1));
        assertTrue(view.readsStayOffHttp());
    }

    static void holdsBaseAboveCopyUnsigned(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(HIGHEST_SIGNED));
        view.batch(serverStateBatch(TWO_TO_THE_63, TWO_TO_THE_63 + 1, "STOPPING"));

        assertEquals(HIGHEST_SIGNED, view.copy().revision(), "a base of 2^63 is above a copy at 2^63 - 1");
        assertFalse(view.readsStayOffHttp(), "the batch is held");
    }

    static void missingBatchAtExactlyThreeSecondsIsOnTime(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        view.advance(800);
        view.batch(serverStateBatch(12, 13, "STOPPING"));
        long heldAt = view.now();
        view.advanceWithoutLoadTo(heldAt + GAP_WAIT - 1, "a gap waits 3 s");
        view.jumpTo(heldAt + GAP_WAIT);
        view.batch(serverStateBatch(10, 12, "STARTING"));

        assertFalse(view.requestedLoad(), "the input acts before the deadline in the same step");
        assertEquals(13, view.copy().revision());
        assertTrue(view.readsStayOffHttp());
    }

    static void batchHeldDuringLoadCountsFromArrival(ViewStarter starter) {
        ViewDriver view = ViewDriver.start(starter);
        view.subscribe();
        view.advance(100);
        view.batch(serverStateBatch(11, 12, "STOPPING"));
        long heldAt = view.now();
        view.advance(GAP_WAIT - 1_000);
        view.loadSnapshot(network(10));

        OptionalLong reloadAt = view.loadWithin(RELOAD_WAIT_LIMIT);

        assertEquals(OptionalLong.of(heldAt + GAP_WAIT), reloadAt,
                "the 3 s count from the batch's arrival, not from the snapshot");
    }

    static void heldBatchExpiredDuringLoadReloadsOnTake(ViewStarter starter) {
        ViewDriver view = ViewDriver.start(starter);
        view.subscribe();
        view.advance(100);
        view.batch(serverStateBatch(11, 12, "STOPPING"));
        long heldAt = view.now();
        view.advanceWithoutLoadTo(heldAt + GAP_WAIT + 500, "a held batch's deadline does not act while the load runs");
        view.loadSnapshot(network(10));

        assertEquals(10, view.copy().revision(), "the load's snapshot is taken");
        view.assertLoadRequested("the held batch waited over 3 s, so the step that takes the snapshot reloads");
    }

    static void longLoadIsNotAbandonedForWaitingBatch(ViewStarter starter) {
        ViewDriver view = ViewDriver.start(starter);
        view.subscribe();
        view.advance(100);
        view.batch(serverStateBatch(9, 11, "STOPPING"));
        long heldAt = view.now();
        view.advanceWithoutLoadTo(heldAt + 2 * GAP_WAIT, "the running load is not replaced");
        view.loadSnapshot(network(10));

        assertEquals(11, view.copy().revision(), "the snapshot is taken, and the waiting batch applies on it");
        assertEquals("STOPPING", serverState(view, 1));
        assertFalse(view.requestedLoad(), "nothing is missing after the snapshot, so nothing reloads");
    }

    static void gapCountsFromOldestHeldBatch(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        view.advance(800);
        view.batch(serverStateBatch(12, 13, "STOPPING"));
        long firstHeldAt = view.now();
        view.advance(GAP_WAIT - 1_000);
        view.batch(serverStateBatch(13, 14, "STARTING"));

        OptionalLong reloadAt = view.loadWithin(RELOAD_WAIT_LIMIT);

        assertEquals(OptionalLong.of(firstHeldAt + GAP_WAIT), reloadAt,
                "the gap's 3 s count from the oldest held batch");
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
        assertTrue(view.readsStayOffHttp(), "the references are checked after the whole batch");
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
        view.advance(LOAD_SPACING);
        view.batch(batch);

        view.assertLoadRequested(message);
        assertFalse(view.readsStayOffHttp(), message);
    }

    static void batchWithMissingItemIsNotApplied(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        NetworkCopy before = view.copy();
        view.advance(LOAD_SPACING);
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
        reloading.advance(LOAD_SPACING);
        reloading.loseConnection();
        reloading.subscribe();
        reloading.requireLoadRequested("a reconnect");
        reloading.loadSnapshot(broken);

        assertTrue(fresh.copyIfAny().isEmpty(), "a first snapshot with a missing item leaves no copy");
        assertEquals(before, reloading.copy(), "the view keeps the last copy whose references resolve");
    }

    static void brokenSnapshotCountsAsFailedLoad(ViewStarter starter) {
        NetworkState broken = network(EPOCH, 10).addServers(groupServer(3, 99, "PREPARING")).build();
        ViewDriver view = ViewDriver.start(starter);
        view.subscribe();
        view.loadSnapshot(broken);
        OptionalLong secondLoad = view.loadWithin(RELOAD_WAIT_LIMIT);
        view.loadSnapshot(broken);
        OptionalLong thirdLoad = view.loadWithin(RELOAD_WAIT_LIMIT);

        assertEquals(OptionalLong.of(FIRST_BACK_OFF), secondLoad, "the first failed load backs off 2 s");
        assertEquals(OptionalLong.of(FIRST_BACK_OFF + 2 * FIRST_BACK_OFF), thirdLoad,
                "a second snapshot with a missing item backs off 4 s, as a failed load");
    }

    // Pulse (rule 3).

    static void reloadsOnPulseFromAnotherEpoch(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        view.advance(LOAD_SPACING);
        view.pulse(pulse(EPOCH + 1, 10));

        view.assertLoadRequested("a pulse from another epoch means reload, even at the same revision");
        assertFalse(view.readsStayOffHttp());
    }

    static void reloadsOnPulseFromLowerEpoch(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        view.advance(LOAD_SPACING);
        view.pulse(pulse(EPOCH - 1, 10));

        view.assertLoadRequested("a pulse from a lower epoch means reload too");
        assertFalse(view.readsStayOffHttp());
    }

    static void pulseAboveCopyUnsigned(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(HIGHEST_SIGNED));
        view.advance(100);
        view.pulse(pulse(TWO_TO_THE_63));

        assertFalse(view.readsStayOffHttp(), "a pulse at 2^63 is ahead of a copy at 2^63 - 1");
    }

    static void furtherPulseUnsigned(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(HIGHEST_SIGNED - 1));
        view.advance(100);
        view.pulse(pulse(HIGHEST_SIGNED));
        view.advance(100);
        view.pulse(pulse(TWO_TO_THE_63));
        view.advance(100);
        view.batch(serverStateBatch(HIGHEST_SIGNED - 1, HIGHEST_SIGNED, "STOPPING"));

        assertFalse(view.readsStayOffHttp(), "a pulse at 2^63 is further ahead than one at 2^63 - 1");
    }

    static void copyReachesPulseUnsigned(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(HIGHEST_SIGNED - 1));
        view.advance(100);
        view.pulse(pulse(HIGHEST_SIGNED));
        view.advance(100);
        view.batch(serverStateBatch(HIGHEST_SIGNED - 1, TWO_TO_THE_63, "STOPPING"));

        assertTrue(view.readsStayOffHttp(), "a copy at 2^63 has reached a pulse at 2^63 - 1");
    }

    static void batchReachingPulseAtExactlyThreeSecondsIsOnTime(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        view.advance(800);
        view.pulse(pulse(12));
        long aheadSince = view.now();
        view.advanceWithoutLoadTo(aheadSince + PULSE_AHEAD_WAIT - 1, "the copy may stay behind a pulse for 3 s");
        view.jumpTo(aheadSince + PULSE_AHEAD_WAIT);
        view.batch(serverStateBatch(10, 12, "STOPPING"));

        assertFalse(view.requestedLoad(), "the batch acts before the 3 s deadline in the same step");
        assertTrue(view.readsStayOffHttp());
    }

    static void behindCountsFromFirstPulseAhead(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        view.advance(800);
        view.pulse(pulse(12));
        long firstAheadAt = view.now();
        view.advance(1_000);
        view.pulse(pulse(14));

        OptionalLong reloadAt = view.loadWithin(RELOAD_WAIT_LIMIT);

        assertEquals(OptionalLong.of(firstAheadAt + PULSE_AHEAD_WAIT), reloadAt,
                "the 3 s count from the first pulse ahead");
    }

    static void pulseReplacesMetricsWhileDisconnected(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        view.advance(2_000);
        view.loseConnection();
        view.pulse(pulse(10).toBuilder().addMetrics(metrics(2, 40)).build());

        assertEquals(Map.of(id(2), metrics(2, 40)), view.copy().metricsByServerId(),
                "a pulse replaces metrics in every state");
    }

    static void lowerPulseNeitherReloadsNorCounts(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        long goodPulseAt = view.now();
        view.advance(5_000);
        view.pulse(pulse(9));
        boolean reloadedOnLowerPulse = view.requestedLoad();
        view.advance(5_000);
        view.pulse(pulse(9));
        view.advanceWithoutLoadTo(goodPulseAt + GOOD_PULSE_LIFETIME - 1, "a lower pulse does not reload");
        boolean liveJustBefore = view.readsStayOffHttp();
        view.advance(1);

        assertFalse(reloadedOnLowerPulse, "a lower pulse does not reload");
        assertTrue(liveJustBefore, "a view is live for 15 s after its last good pulse");
        assertFalse(view.readsStayOffHttp(), "lower pulses do not keep a view live");
    }

    static void notLiveFifteenSecondsAfterGoodPulse(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        long goodPulseAt = view.now();

        boolean liveJustBefore = view.readsStayOffHttpAt(goodPulseAt + GOOD_PULSE_LIFETIME - 1);
        boolean liveAtFifteenSeconds = view.readsStayOffHttpAt(goodPulseAt + GOOD_PULSE_LIFETIME);

        assertTrue(liveJustBefore, "a view is live for 15 s after its last good pulse");
        assertFalse(liveAtFifteenSeconds, "time alone ends live 15 s after the last good pulse");
    }

    static void reloadsOnPulseAfterFifteenSecondsWithoutGoodPulse(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        long goodPulseAt = view.now();
        view.advanceWithoutLoadTo(goodPulseAt + GOOD_PULSE_LIFETIME, "silence alone does not reload");
        view.pulse(pulse(10));

        view.assertLoadRequested("the first pulse after 15 s without a good pulse reloads, whatever it says");
        assertFalse(view.readsStayOffHttp());
    }

    static void pulseAheadEndsLiveUntilReached(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        view.advance(100);
        view.pulse(pulse(12));
        boolean liveWhileBehind = view.readsStayOffHttp();
        view.advance(1_000);
        view.batch(serverStateBatch(10, 12, "STOPPING"));

        assertFalse(liveWhileBehind, "a copy behind a pulse is not live");
        assertTrue(view.readsStayOffHttp(), "reaching the pulse's revision makes the view live again");
        view.advanceWithoutLoadTo(view.now() + 2 * PULSE_AHEAD_WAIT, "a reached pulse does not reload");
    }

    static void reloadsWhenBehindPulseThreeSeconds(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        view.advance(800);
        view.pulse(pulse(12));
        long behindSince = view.now();
        view.batch(serverStateBatch(10, 11, "STOPPING"));

        OptionalLong loadAt = view.loadWithin(RELOAD_WAIT_LIMIT);

        assertEquals(OptionalLong.of(behindSince + PULSE_AHEAD_WAIT), loadAt,
                "a copy still behind a pulse after 3 s reloads");
    }

    static void furtherPulseKeepsCopyBehind(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        view.advance(100);
        view.pulse(pulse(12));
        view.advance(1_000);
        view.pulse(pulse(14));
        view.advance(100);
        view.batch(serverStateBatch(10, 12, "STARTING"));
        boolean liveAtFirstPulse = view.readsStayOffHttp();
        view.advance(100);
        view.batch(serverStateBatch(12, 14, "STOPPING"));

        assertFalse(liveAtFirstPulse, "the copy is behind until it reaches the highest revision a pulse showed");
        assertTrue(view.readsStayOffHttp());
    }

    static void reloadsOnPulseFifteenSecondsAfterSnapshotWithoutGoodPulse(ViewStarter starter) {
        ViewDriver view = ViewDriver.start(starter);
        view.subscribe();
        view.advance(100);
        view.loadSnapshot(network(10));
        long tookSnapshotAt = view.now();
        long lastPulseBeforeFifteenSeconds = tookSnapshotAt + GOOD_PULSE_LIFETIME - ModelOwner.PULSE_INTERVAL_MILLIS;
        while (view.now() < lastPulseBeforeFifteenSeconds) {
            view.advance(ModelOwner.PULSE_INTERVAL_MILLIS);
            view.pulse(pulse(9));
            assertFalse(view.requestedLoad(), "a lower pulse within 15 s of the snapshot does not reload");
        }
        view.advance(ModelOwner.PULSE_INTERVAL_MILLIS);
        view.pulse(pulse(9));

        view.assertLoadRequested("the first pulse 15 s after the snapshot, without a good pulse since, reloads");
    }

    static void everyPulseReplacesMetrics(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        StatePulse lowerWithMetrics = pulse(9).toBuilder().addMetrics(metrics(2, 40)).build();
        view.advance(100);
        view.pulse(lowerWithMetrics);

        assertEquals(Map.of(id(2), metrics(2, 40)), view.copy().metricsByServerId(),
                "a pulse replaces the metrics, also a lower one; server 1 now shows zeros");
    }

    static void flagFalseStopsAnswers(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        long firstGoodPulseAt = view.now();
        boolean answeredAfterTrue = view.mayAnswer();
        view.advance(10_000);
        view.pulse(pulse(10).toBuilder().setViewsMayAnswer(false).build());
        boolean answeredAfterFalse = view.mayAnswer();
        view.jumpTo(firstGoodPulseAt + GOOD_PULSE_LIFETIME + 1_000);
        view.pulse(pulse(9));

        assertTrue(answeredAfterTrue);
        assertFalse(answeredAfterFalse, "views_may_answer false stops every answer");
        assertTrue(view.mayAnswer(), "the next true pulse allows answers again, and the false one was a good pulse:"
                + " the flag stops answers, not live");
    }

    // Connection (rule 4).

    static void notLiveAtOnceAfterConnectionLoss(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        view.loseConnection();

        assertFalse(view.readsStayOffHttp(), "a lost connection ends live at once");
    }

    static void batchHeldBeforeLossDoesNotSurviveReload(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        view.advance(100);
        view.batch(serverStateBatch(11, 12, "STOPPING"));
        view.loseConnection();
        view.advance(100);
        view.subscribe();
        view.waitForLoad(RELOAD_WAIT_LIMIT);
        // At the held batch's base, so a batch the view kept would apply on it.
        view.loadSnapshot(network(11));

        assertEquals(11, view.copy().revision(), "the reconnect's reload drops the held batch");
        assertEquals("AVAILABLE", serverState(view, 1));
    }

    static void batchDuringLossDoesNotSurviveReload(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        view.loseConnection();
        view.advance(100);
        view.batch(serverStateBatch(10, 11, "STOPPING"));
        view.advance(100);
        view.subscribe();
        view.waitForLoad(RELOAD_WAIT_LIMIT);
        // From before the batch, so a batch the view kept would apply on it.
        view.loadSnapshot(network(10));

        assertEquals(10, view.copy().revision(), "the reconnect's reload drops a batch from the loss");
        assertEquals("AVAILABLE", serverState(view, 1));
    }

    static void reloadsAfterReconnect(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        view.advance(LOAD_SPACING);
        view.loseConnection();
        view.advance(1_000);
        view.subscribe();
        view.assertLoadRequested("a reconnect means reload");
        view.advance(100);
        view.loadSnapshot(network(12));
        boolean liveAfterSnapshot = view.readsStayOffHttp();
        view.advance(100);
        view.pulse(pulse(12));

        assertFalse(liveAfterSnapshot, "after a reload, the view waits for a good pulse again");
        assertTrue(view.readsStayOffHttp());
        assertEquals(12, view.copy().revision());
    }

    static void reloadWantedBeforeLossStartsNoLoad(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        view.advance(300);
        view.batch(batch(EPOCH + 1, 2, 3).build());
        long wantedReloadWouldStartAt = LOAD_SPACING;
        view.advance(100);
        view.loseConnection();
        view.advanceWithoutLoadTo(wantedReloadWouldStartAt + 3_000, "a view starts no load while disconnected");
        view.subscribe();

        view.assertLoadRequested("the reconnect reloads");
    }

    static void heldBatchWantsNoReloadWhileDisconnected(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        view.advance(100);
        view.batch(serverStateBatch(11, 12, "STOPPING"));
        long heldAt = view.now();
        view.loseConnection();

        view.advanceWithoutLoadTo(heldAt + GAP_WAIT + RELOAD_WAIT_LIMIT, "a view starts no load while disconnected");
    }

    static void noAuditFinishesWhileDisconnected(ViewStarter starter) {
        ViewDriver view = auditLoading(starter);
        view.loadSnapshot(network(12));
        int auditsBeforeLoss = view.auditsFinished();
        view.loseConnection();
        long lostAt = view.now();
        view.advanceWithoutLoadTo(lostAt + 2 * AUDIT_REACH_WAIT, "a view starts no load while disconnected");

        assertEquals(auditsBeforeLoss, view.auditsFinished(), "the audit's 3 s end no audit while disconnected");
        assertEquals(10, view.copy().revision(), "the audit's snapshot is not taken while disconnected");
        assertFalse(view.readsStayOffHttp());
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
        OptionalLong secondLoad = view.loadWithin(RELOAD_WAIT_LIMIT);
        view.failLoad();

        OptionalLong thirdLoad = view.loadWithin(RELOAD_WAIT_LIMIT);

        assertEquals(OptionalLong.of(LOAD_SPACING), secondLoad,
                "the abandoned load's failure does not back off the new load");
        assertEquals(OptionalLong.of(LOAD_SPACING + FIRST_BACK_OFF), thirdLoad,
                "the next failure is the first in a row");
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
        OptionalLong secondLoad = view.loadWithin(RELOAD_WAIT_LIMIT);
        view.loadSnapshot(network(11));

        assertFalse(loadedWhileOneRuns, "a view runs at most one load at a time");
        assertTrue(copyAfterIgnoredAnswer.isEmpty(), "the answer of a load that ran across the loss is ignored");
        assertEquals(OptionalLong.of(LOAD_SPACING), secondLoad, "the new load starts 2 s after the previous one");
        assertEquals(11, view.copy().revision());
    }

    // Reload (rule 6).

    static void takenSnapshotReplacesMetrics(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        view.advance(100);
        view.pulse(pulse(10).toBuilder().addMetrics(metrics(2, 40)).build());
        view.advance(LOAD_SPACING);
        view.loseConnection();
        view.subscribe();
        view.requireLoadRequested("a reconnect");
        view.loadSnapshot(network(12));

        assertEquals(Map.of(id(1), metrics(1, 5)), view.copy().metricsByServerId(),
                "a taken snapshot replaces the metrics");
    }

    static void loadsTwoSecondsApart(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        view.advance(300);
        view.batch(batch(EPOCH + 1, 2, 3).build());

        OptionalLong reloadAt = view.loadWithin(RELOAD_WAIT_LIMIT);

        assertEquals(OptionalLong.of(LOAD_SPACING), reloadAt, "a reload starts 2 s after the previous load started");
    }

    static void backsOffAfterFailedLoads(ViewStarter starter) {
        ViewDriver view = ViewDriver.start(starter);
        view.subscribe();
        List<Long> expectedBackOffs = List.of(FIRST_BACK_OFF, 2 * FIRST_BACK_OFF, 4 * FIRST_BACK_OFF,
                8 * FIRST_BACK_OFF, LONGEST_BACK_OFF, LONGEST_BACK_OFF);
        for (long expectedBackOff : expectedBackOffs) {
            view.failLoad();
            long failedAt = view.now();
            OptionalLong loadAt = view.loadWithin(RELOAD_WAIT_LIMIT);
            assertEquals(OptionalLong.of(failedAt + expectedBackOff), loadAt,
                    "after failed loads the delay doubles from 2 s, up to 30 s");
        }
    }

    static void takenSnapshotEndsBackOff(ViewStarter starter) {
        ViewDriver view = ViewDriver.start(starter);
        view.subscribe();
        for (int failure = 0; failure < 4; failure++) {
            view.failLoad();
            view.waitForLoad(RELOAD_WAIT_LIMIT);
        }
        view.loadSnapshot(network(10));
        long tookSnapshotAt = view.now();
        view.advance(1_000);
        view.batch(batch(EPOCH + 1, 2, 3).build());
        OptionalLong reloadAt = view.loadWithin(RELOAD_WAIT_LIMIT);
        view.failLoad();

        OptionalLong loadAfterFailure = view.loadWithin(RELOAD_WAIT_LIMIT);

        long reloadAfterSpacing = tookSnapshotAt + LOAD_SPACING;
        assertEquals(OptionalLong.of(reloadAfterSpacing), reloadAt,
                "after a taken snapshot only the 2 s spacing applies");
        assertEquals(OptionalLong.of(reloadAfterSpacing + FIRST_BACK_OFF), loadAfterFailure,
                "the first failure after a taken snapshot backs off 2 s, not 30 s");
    }

    static void everyRandomDelayDrawsNewNumber(ViewStarter starter) {
        RandomSequence zeroFirstThenHalf = index -> index == 0 ? 0.0 : 0.5;
        ViewDriver view = ViewDriver.startWithSequence(starter, zeroFirstThenHalf);
        view.subscribe();
        view.assertLoadRequested("the first number is 0, so the first load starts at once");
        view.failLoad();

        OptionalLong secondLoad = view.loadWithin(RELOAD_WAIT_LIMIT);

        assertEquals(OptionalLong.of(FIRST_BACK_OFF + RANDOM_LOAD_DELAY_BOUND / 2), secondLoad,
                "the second delay draws a later number, 0.5: 2 s back-off + 1 s");
    }

    static void addsRandomDelayToReloads(ViewStarter starter) {
        ViewDriver view = ViewDriver.start(starter, 0.5);
        view.subscribe();
        OptionalLong firstLoad = view.loadWithin(RELOAD_WAIT_LIMIT);
        view.failLoad();

        OptionalLong secondLoad = view.loadWithin(RELOAD_WAIT_LIMIT);

        long halfTheBound = RANDOM_LOAD_DELAY_BOUND / 2;
        assertEquals(OptionalLong.of(halfTheBound), firstLoad);
        assertEquals(OptionalLong.of(halfTheBound + FIRST_BACK_OFF + halfTheBound), secondLoad,
                "the random delay comes on top of the back-off");
    }

    // Audit (rule 7).

    static void auditsTwentyMinutesAfterSnapshot(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));

        OptionalLong auditAt = view.loadWithinKeepingLive(AUDIT_WAIT_LIMIT);

        assertEquals(OptionalLong.of(100 + SHORTEST_AUDIT_INTERVAL), auditAt, "the snapshot was taken at 100 ms");
    }

    static void highestNumberAuditsJustBeforeThirtyMinutes(ViewStarter starter) {
        ViewDriver view = ViewDriver.start(starter, HIGHEST_RANDOM_NUMBER);
        view.subscribe();
        view.waitForLoad(RELOAD_WAIT_LIMIT);
        view.loadSnapshot(network(10));
        long tookSnapshotAt = view.now();
        view.pulse(pulse(10));
        long dueAt = tookSnapshotAt + LONGEST_AUDIT_INTERVAL - 1;

        view.keepLiveWithoutLoadUntil(dueAt - 1_000);
        OptionalLong auditAt = view.loadWithin(2_000);

        assertEquals(OptionalLong.of(dueAt), auditAt,
                "the audit comes 20 min + 10 min x the random number later, cut to whole milliseconds");
    }

    static void auditMatchesAtSameRevision(ViewStarter starter) {
        ViewDriver view = auditLoading(starter);
        NetworkCopy beforeAudit = view.copy();
        NetworkState otherMetrics = network(EPOCH, 10).clearMetrics().addMetrics(metrics(1, 77)).build();
        view.loadSnapshot(otherMetrics);

        assertEquals(Optional.of(AuditOutcome.MATCHED), view.lastAuditOutcome(),
                "metrics are left out of the comparison");
        assertTrue(view.readsStayOffHttp(), "a matching audit keeps the view live");
        assertEquals(beforeAudit, view.copy(), "a matching audit keeps the copy, its metrics too");
    }

    static void auditDifferenceTakesSnapshot(ViewStarter starter) {
        ViewDriver view = auditLoading(starter);
        NetworkState different = network(EPOCH, 10).setServers(0, groupServer(1, 10, "STOPPING")).build();
        view.loadSnapshot(different);
        Optional<AuditOutcome> outcome = view.lastAuditOutcome();
        boolean liveAfterDifference = view.readsStayOffHttp();
        view.advance(100);
        view.pulse(pulse(10));

        assertEquals(Optional.of(AuditOutcome.DIFFERED), outcome);
        assertEquals("STOPPING", serverState(view, 1), "a difference takes the snapshot");
        assertFalse(liveAfterDifference, "a taken snapshot needs a good pulse again");
        assertTrue(view.readsStayOffHttp());
    }

    static void auditComparesAtExactlyS(ViewStarter starter) {
        ViewDriver view = auditLoading(starter);
        NetworkState atTwelve = network(EPOCH, 12).setServers(0, groupServer(1, 10, "STOPPING")).build();
        view.loadSnapshot(atTwelve);
        Optional<AuditOutcome> outcomeOnArrival = view.lastAuditOutcome();
        boolean liveWhileComparing = view.readsStayOffHttp();
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

        OptionalLong retryAt = view.loadWithin(AUDIT_RETRY_WAIT_LIMIT);

        assertEquals(Optional.of(AuditOutcome.JUMPED_PAST), outcome);
        assertEquals(OptionalLong.of(jumpedAt + SHORTEST_AUDIT_RETRY), retryAt,
                "after jumping past S, the audit runs again 2 s + 8 s x the number later");
    }

    static void auditSnapshotBehindCopyJumpedPast(ViewStarter starter) {
        ViewDriver view = auditLoading(starter);
        view.loadSnapshot(network(9));

        assertEquals(Optional.of(AuditOutcome.JUMPED_PAST), view.lastAuditOutcome());
        assertEquals(10, view.copy().revision(), "a snapshot behind the copy is not taken");
        assertTrue(view.readsStayOffHttp());
    }

    static void auditNotReachedTakesSnapshot(ViewStarter starter) {
        ViewDriver view = auditLoading(starter);
        NetworkState atTwelve = network(EPOCH, 12).setServers(0, groupServer(1, 10, "STOPPING")).build();
        view.loadSnapshot(atTwelve);
        long comparingSince = view.now();
        view.advanceWithoutLoadTo(comparingSince + AUDIT_REACH_WAIT - 1, "the audit waits 3 s");
        Optional<AuditOutcome> outcomeJustBefore = view.lastAuditOutcome();
        view.advance(1);
        Optional<AuditOutcome> outcomeAtThreeSeconds = view.lastAuditOutcome();
        boolean liveAfterTaking = view.readsStayOffHttp();

        assertEquals(Optional.empty(), outcomeJustBefore);
        assertEquals(Optional.of(AuditOutcome.NOT_REACHED), outcomeAtThreeSeconds);
        assertEquals(12, view.copy().revision(), "S not reached within 3 s takes the snapshot");
        assertEquals("STOPPING", serverState(view, 1));
        assertFalse(liveAfterTaking, "a taken snapshot needs a good pulse again");
    }

    static void reloadWaitsForAuditLoad(ViewStarter starter) {
        ViewDriver view = auditLoading(starter);
        int auditsBeforeReload = view.auditsFinished();
        view.advance(100);
        view.batch(batch(EPOCH + 1, 2, 3).build());
        boolean loadedWhileAuditRuns = view.requestedLoad();
        view.advanceWithoutLoadTo(view.now() + LOAD_SPACING + 1_000, "a reload waits for the running audit load");
        view.loadSnapshot(network(10));
        boolean reloadedAfterAuditAnswer = view.requestedLoad();

        assertFalse(loadedWhileAuditRuns);
        assertEquals(auditsBeforeReload, view.auditsFinished(), "a reload abandons the audit");
        assertTrue(reloadedAfterAuditAnswer, "the reload starts once the audit load has answered");
    }

    static void auditReachingSAtExactlyThreeSecondsCompares(ViewStarter starter) {
        ViewDriver view = auditLoading(starter);
        view.loadSnapshot(network(11));
        long comparingSince = view.now();
        view.advanceWithoutLoadTo(comparingSince + AUDIT_REACH_WAIT - 1, "the audit waits 3 s");
        view.jumpTo(comparingSince + AUDIT_REACH_WAIT);
        view.batch(serverStateBatch(10, 11, "AVAILABLE"));

        assertEquals(Optional.of(AuditOutcome.MATCHED), view.lastAuditOutcome(),
                "the batch acts before the audit's deadline in the same step");
    }

    static void auditDeadlineActsBeforeGapDeadline(ViewStarter starter) {
        ViewDriver view = auditLoading(starter);
        view.loadSnapshot(network(12));
        view.batch(serverStateBatch(12, 14, "STOPPING"));
        long bothSince = view.now();
        view.advanceWithoutLoadTo(bothSince + AUDIT_REACH_WAIT - 1, "both wait 3 s");
        view.advance(1);

        assertEquals(Optional.of(AuditOutcome.NOT_REACHED), view.lastAuditOutcome(), "the audit's deadline acts first");
        assertEquals(14, view.copy().revision(), "its snapshot at 12 lets the held batch apply");
        assertFalse(view.requestedLoad(), "so the gap is closed and nothing reloads");
    }

    static void auditComparesUnsigned(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(HIGHEST_SIGNED - 1));
        view.waitForLoadKeepingLive(AUDIT_WAIT_LIMIT);
        view.loadSnapshot(network(HIGHEST_SIGNED));
        view.advance(100);
        view.batch(serverStateBatch(HIGHEST_SIGNED - 1, TWO_TO_THE_63, "STOPPING"));

        assertEquals(Optional.of(AuditOutcome.JUMPED_PAST), view.lastAuditOutcome(),
                "a copy at 2^63 is past an audit snapshot at 2^63 - 1");
    }

    static void noAuditWhileNotLive(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        long auditDueAt = 100 + SHORTEST_AUDIT_INTERVAL;
        view.keepLiveWithoutLoadUntil(auditDueAt - GOOD_PULSE_LIFETIME - 1_000);
        view.advanceWithoutLoadTo(auditDueAt, "no load before the audit is due");
        boolean liveWhenDue = view.readsStayOffHttp();

        view.advanceWithoutLoadTo(auditDueAt + 10_000, "a view that is not live starts no audit");

        assertFalse(liveWhenDue, "the last good pulse is over 15 s old when the audit is due");
    }

    static void auditsAgainTwentyMinutesAfterMatch(ViewStarter starter) {
        ViewDriver view = auditLoading(starter);
        view.loadSnapshot(network(10));
        long matchedAt = view.now();

        OptionalLong nextAuditAt = view.loadWithinKeepingLive(AUDIT_WAIT_LIMIT);

        assertEquals(OptionalLong.of(matchedAt + SHORTEST_AUDIT_INTERVAL), nextAuditAt,
                "after a match, the next audit is 20 min + 10 min x the number later");
    }

    static void auditsAgainTwentyMinutesAfterFailedLoad(ViewStarter starter) {
        ViewDriver view = auditLoading(starter);
        view.advance(1_000);
        view.failLoad();
        long failedAt = view.now();

        OptionalLong nextAuditAt = view.loadWithinKeepingLive(AUDIT_WAIT_LIMIT);

        assertEquals(OptionalLong.of(failedAt + SHORTEST_AUDIT_INTERVAL), nextAuditAt,
                "after a failed audit load, the next audit is 20 min later");
    }

    static void failedAuditLoadDoesNotBackOff(ViewStarter starter) {
        ViewDriver view = auditLoading(starter);
        long auditStartedAt = view.now();
        view.advance(1_000);
        view.failLoad();
        view.advance(100);
        view.batch(batch(EPOCH + 1, 2, 3).build());

        OptionalLong reloadAt = view.loadWithin(RELOAD_WAIT_LIMIT);

        assertEquals(OptionalLong.of(auditStartedAt + LOAD_SPACING), reloadAt,
                "only the 2 s spacing applies; a failed reload would delay it to 2 s after the failure");
    }

    static void auditSnapshotFromAnotherEpochReloads(ViewStarter starter) {
        ViewDriver view = auditLoading(starter);
        long auditStartedAt = view.now();
        view.loadSnapshot(network(EPOCH + 1, 3).build());
        Optional<AuditOutcome> outcome = view.lastAuditOutcome();
        boolean liveAfterAnswer = view.readsStayOffHttp();

        OptionalLong reloadAt = view.loadWithin(RELOAD_WAIT_LIMIT);

        assertEquals(Optional.empty(), outcome, "an audit snapshot from another epoch is no audit outcome");
        assertFalse(liveAfterAnswer, "an audit snapshot from another epoch means reload");
        assertEquals(OptionalLong.of(auditStartedAt + LOAD_SPACING), reloadAt);
    }

    /**
     * Returns a view live at revision 10 whose first audit load is running.
     */
    private static ViewDriver auditLoading(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        view.waitForLoadKeepingLive(AUDIT_WAIT_LIMIT);
        return view;
    }

    // Own writes (Answers).

    static void readWaitsWhileCopyBelowOwnWrite(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        view.wrote(EPOCH, 12);
        ReadRoute afterWrite = view.route();
        view.advance(100);
        view.batch(serverStateBatch(10, 11, "STARTING"));

        assertEquals(ReadRoute.WAIT, afterWrite, "a read waits for the copy to reach an own write");
        assertEquals(ReadRoute.WAIT, view.route(), "a copy below the write's revision keeps it waiting");
    }

    static void copyAnswersOnceAtOrAboveOwnWrite(ViewStarter starter) {
        ViewDriver atRevision = ViewDriver.liveAt(starter, network(10));
        atRevision.wrote(EPOCH, 12);
        atRevision.advance(100);
        atRevision.batch(serverStateBatch(10, 12, "STOPPING"));
        ViewDriver aboveRevision = ViewDriver.liveAt(starter, network(10));
        aboveRevision.wrote(EPOCH, 12);
        aboveRevision.advance(100);
        aboveRevision.batch(serverStateBatch(10, 13, "STOPPING"));
        ViewDriver alreadyInCopy = ViewDriver.liveAt(starter, network(10));
        alreadyInCopy.wrote(EPOCH, 9);

        assertEquals(ReadRoute.FROM_COPY, atRevision.route(), "a copy at the write's revision has reached it");
        assertEquals(ReadRoute.FROM_COPY, aboveRevision.route(), "so has a copy above it");
        assertEquals(ReadRoute.FROM_COPY, alreadyInCopy.route(), "a write the copy already has makes no read wait");
    }

    static void ownWriteRevisionsUnsigned(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(HIGHEST_SIGNED - 1));
        view.wrote(EPOCH, HIGHEST_SIGNED);
        view.advance(100);
        view.batch(serverStateBatch(HIGHEST_SIGNED - 1, TWO_TO_THE_63, "STOPPING"));

        assertTrue(view.mayAnswer(), "a copy at 2^63 has reached a write at 2^63 - 1");
    }

    static void copyAnswersThreeSecondsAfterOwnWrite(ViewStarter starter) {
        ViewDriver view = waitingForOwnWrite(starter);
        long wroteAt = view.now();

        assertEquals(ReadRoute.WAIT, view.routeAt(wroteAt + OWN_WRITE_WAIT - 1),
                "a read waits for the copy until 3 s after the write");
        assertEquals(ReadRoute.FROM_COPY, view.routeAt(wroteAt + OWN_WRITE_WAIT),
                "exactly 3 s after the write the copy answers, though it lacks the write");
    }

    static void laterWriteRestartsWaitAndKeepsHigherRevision(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        view.wrote(EPOCH, 14);
        long firstWriteAt = view.now();
        view.advance(OWN_WRITE_WAIT - 1_000);
        view.wrote(EPOCH, 12);
        long laterWriteAt = view.now();
        view.advance(100);
        view.batch(serverStateBatch(10, 12, "STOPPING"));

        assertEquals(ReadRoute.WAIT, view.route(), "the higher revision stays, also when the later write's is lower");
        assertEquals(ReadRoute.WAIT, view.routeAt(firstWriteAt + OWN_WRITE_WAIT), "the later write restarts the 3 s");
        assertEquals(ReadRoute.FROM_COPY, view.routeAt(laterWriteAt + OWN_WRITE_WAIT),
                "they end 3 s after the later write");
    }

    static void keptOwnWriteRevisionsUnsigned(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(HIGHEST_SIGNED - 1));
        view.wrote(EPOCH, TWO_TO_THE_63);
        view.wrote(EPOCH, HIGHEST_SIGNED);
        view.advance(100);
        view.batch(serverStateBatch(HIGHEST_SIGNED - 1, HIGHEST_SIGNED, "STOPPING"));

        assertTrue(view.awaitsOwnWrite(), "a write at 2^63 stays over a later one at 2^63 - 1");
    }

    static void otherEpochWriteReplacesKeptWrite(ViewStarter starter) {
        ViewDriver higherRevisionReplaced = replaceByWriteFromOtherEpoch(starter, 50, 3);
        ViewDriver lowerRevisionReplaced = replaceByWriteFromOtherEpoch(starter, 3, 50);

        assertTrue(higherRevisionReplaced.mayAnswer(),
                "a copy at the later write answers, though the replaced write had a higher revision");
        assertTrue(lowerRevisionReplaced.mayAnswer(),
                "a copy at the later write answers, though the replaced write had a higher epoch");
    }

    /**
     * Hands over a write in epoch {@code EPOCH + 1} at 200 ms, which reloads, then at 1.2 s, before the load, a
     * write in {@code EPOCH}, and makes the view live on a snapshot at that later write within its 3 s.
     */
    private static ViewDriver replaceByWriteFromOtherEpoch(ViewStarter starter, long replacedRevision,
                                                           long laterRevision) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        view.wrote(EPOCH + 1, replacedRevision);
        view.advance(1_000);
        view.wrote(EPOCH, laterRevision);
        view.waitForLoad(RELOAD_WAIT_LIMIT);
        view.advance(100);
        view.loadSnapshot(network(EPOCH, laterRevision).build());
        view.advance(100);
        view.pulse(pulse(EPOCH, laterRevision));
        return view;
    }

    static void otherEpochWriteReloadsSyncedView(ViewStarter starter) {
        ViewDriver higherEpoch = ViewDriver.liveAt(starter, network(10));
        higherEpoch.wrote(EPOCH + 1, 3);
        boolean liveAfterHigherEpoch = higherEpoch.readsStayOffHttp();
        OptionalLong higherEpochLoadAt = higherEpoch.loadWithin(RELOAD_WAIT_LIMIT);
        ViewDriver lowerEpoch = ViewDriver.liveAt(starter, network(10));
        lowerEpoch.wrote(EPOCH - 1, 3);
        boolean liveAfterLowerEpoch = lowerEpoch.readsStayOffHttp();
        OptionalLong lowerEpochLoadAt = lowerEpoch.loadWithin(RELOAD_WAIT_LIMIT);

        assertFalse(liveAfterHigherEpoch, "a write from a higher epoch than the copy's ends live at once");
        assertEquals(OptionalLong.of(LOAD_SPACING), higherEpochLoadAt, "and reloads, 2 s after the previous load");
        assertFalse(liveAfterLowerEpoch, "so does a write from a lower epoch");
        assertEquals(OptionalLong.of(LOAD_SPACING), lowerEpochLoadAt);
    }

    static void copyInOtherEpochWaitsOnlyThreeSeconds(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        view.wrote(EPOCH + 1, 3);
        long wroteAt = view.now();
        view.waitForLoad(RELOAD_WAIT_LIMIT);
        view.advance(100);
        view.loadSnapshot(network(12));
        view.advance(100);
        view.pulse(pulse(12));
        ReadRoute afterReload = view.route();
        view.advanceWithoutLoadTo(wroteAt + OWN_WRITE_WAIT,
                "a copy in another epoch than the kept write reloads no more");

        assertEquals(ReadRoute.WAIT, afterReload, "a copy in another epoch than the write has not reached it");
        assertEquals(ReadRoute.FROM_COPY, view.route(), "3 s after the write the copy answers");
    }

    static void writeOutsideSyncedViewStartsNoLoad(ViewStarter starter) {
        ViewDriver disconnected = ViewDriver.liveAt(starter, network(10));
        disconnected.advance(LOAD_SPACING);
        disconnected.loseConnection();
        disconnected.wrote(EPOCH + 1, 3);
        disconnected.advanceWithoutLoadTo(10_000, "a disconnected view starts no load");
        disconnected.subscribe();
        boolean loadedAfterReconnect = disconnected.requestedLoad();
        ViewDriver reloadWanted = ViewDriver.start(starter, 0.5);
        reloadWanted.subscribe();
        reloadWanted.advance(500);
        reloadWanted.wrote(EPOCH + 1, 3);
        OptionalLong wantedLoadAt = reloadWanted.loadWithin(RELOAD_WAIT_LIMIT);
        ViewDriver reloading = ViewDriver.start(starter);
        reloading.subscribe();
        reloading.advance(50);
        reloading.wrote(EPOCH + 1, 3);
        reloading.advance(50);
        reloading.loadSnapshot(network(10));

        assertTrue(loadedAfterReconnect, "a reconnect means reload");
        assertEquals(OptionalLong.of(RANDOM_LOAD_DELAY_BOUND / 2), wantedLoadAt,
                "a write does not move a wanted reload");
        assertEquals(Optional.of(10L), reloading.copyIfAny().map(NetworkCopy::revision),
                "a write does not abandon the running reload's load");
    }

    // Own write wait (Answers).

    static void noWaitWhileBatchHeld(ViewStarter starter) {
        ViewDriver view = waitingForOwnWrite(starter);
        view.advance(100);
        view.batch(serverStateBatch(11, 12, "STOPPING"));

        assertFalse(view.awaitsOwnWrite(), "a view that holds a batch is not live");
    }

    static void noWaitWhileBehindPulse(ViewStarter starter) {
        ViewDriver view = waitingForOwnWrite(starter);
        view.advance(100);
        view.pulse(pulse(12));

        assertFalse(view.awaitsOwnWrite(), "a copy behind a pulse is not live");
    }

    static void noWaitOnceGoodPulseFifteenSecondsOld(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        long goodPulseAt = view.now();
        view.jumpTo(goodPulseAt + GOOD_PULSE_LIFETIME - 2_000);
        view.wrote(EPOCH, 12);

        boolean waitingJustBefore = view.routeAt(goodPulseAt + GOOD_PULSE_LIFETIME - 1) == ReadRoute.WAIT;
        boolean waitingAtFifteenSeconds = view.routeAt(goodPulseAt + GOOD_PULSE_LIFETIME) == ReadRoute.WAIT;

        assertTrue(waitingJustBefore, "a read waits while the view is live");
        assertFalse(waitingAtFifteenSeconds, "time alone ends live, and the wait with it");
    }

    static void noWaitAfterConnectionLoss(ViewStarter starter) {
        ViewDriver view = waitingForOwnWrite(starter);
        view.loseConnection();

        assertFalse(view.awaitsOwnWrite(), "a lost connection ends live at once");
    }

    static void noWaitWhileReloading(ViewStarter starter) {
        ViewDriver view = waitingForOwnWrite(starter);
        view.advance(100);
        view.batch(batch(EPOCH + 1, 2, 3).build());
        boolean waitingWhileReloadWanted = view.awaitsOwnWrite();
        view.waitForLoad(RELOAD_WAIT_LIMIT);

        assertFalse(waitingWhileReloadWanted, "a view that wants a reload is not live");
        assertFalse(view.awaitsOwnWrite(), "a view whose reload runs is not live");
    }

    static void noWaitWhileAnswersSwitchedOff(ViewStarter starter) {
        ViewDriver view = waitingForOwnWrite(starter);
        view.advance(100);
        view.pulse(pulse(10).toBuilder().setViewsMayAnswer(false).build());
        boolean waitingWhileSwitchedOff = view.awaitsOwnWrite();
        view.advance(100);
        view.pulse(pulse(10));

        assertFalse(waitingWhileSwitchedOff, "views_may_answer false sends every read over HTTP");
        assertTrue(view.awaitsOwnWrite(), "the next true pulse lets the read wait again");
    }

    /**
     * Returns a view live at revision 10 in which a read waits for an own write at revision 12, handed over at
     * 200 ms.
     */
    private static ViewDriver waitingForOwnWrite(ViewStarter starter) {
        ViewDriver view = ViewDriver.liveAt(starter, network(10));
        view.wrote(EPOCH, 12);
        if (!view.awaitsOwnWrite()) {
            throw new SetUpFailed("a read in a live view does not wait for an own write");
        }
        return view;
    }

    private static String serverState(ViewDriver view, int serverNumber) {
        return view.copy().servers().get(id(serverNumber)).getState();
    }
}
