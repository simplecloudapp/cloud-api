package app.simplecloud.api.internal.networkstate;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shows that the simulator's runs exercise what they claim, so that its checks passing means something.
 */
class SimulatorCoverageTest {

    private static final ViewStarter REFERENCE = ReferenceMachine.STARTER;

    @Test
    void shortRuns_exerciseEveryFault() {
        ViewSimulation.Coverage coverage = ViewSimulation.runAll(REFERENCE, ViewSimulation.shortRuns());

        double liveShare = (double) coverage.liveSteps() / coverage.steps();
        assertTrue(liveShare > 0.4, "the view is live in only " + liveShare + " of the steps");
        assertTrue(coverage.failovers() > 100, "failovers: " + coverage.failovers());
        assertTrue(coverage.ownerChanges() > 100, "owner changes: " + coverage.ownerChanges());
        assertTrue(coverage.connectionCuts() > 100, "connection cuts: " + coverage.connectionCuts());
        assertTrue(coverage.loads() > 1_000, "loads: " + coverage.loads());
    }

    @Test
    void longRuns_audit() {
        ViewSimulation.Coverage coverage = ViewSimulation.runAll(REFERENCE, ViewSimulation.longRuns());

        assertEquals(EnumSet.of(AuditOutcome.MATCHED, AuditOutcome.JUMPED_PAST), coverage.auditOutcomes(),
                "the long runs reach these audit outcomes; the examples cover the other two");
    }
}
