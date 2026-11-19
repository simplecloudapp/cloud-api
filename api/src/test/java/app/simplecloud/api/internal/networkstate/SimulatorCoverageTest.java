package app.simplecloud.api.internal.networkstate;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shows that the simulator's runs exercise what they claim, so that its checks passing means something.
 */
class SimulatorCoverageTest {

    private static final double LEAST_LIVE_SHARE = 0.4;
    private static final int LEAST_LOADS = 1_000;
    private static final int LEAST_OF_EACH_FAULT = 100;
    /**
     * Such a loss needs an owner on another NATS server and a message in the 100 ms after a reconnect.
     */
    private static final int LEAST_LOST_BEFORE_INTEREST_SPREAD = 10;

    @Test
    void shortRuns_exerciseEveryFault() {
        ViewSimulation.Coverage coverage = ViewSimulation.runAll(ReferenceMachine.STARTER, ViewSimulation.shortRuns());

        double liveShare = (double) coverage.liveSteps() / coverage.steps();
        assertTrue(liveShare > LEAST_LIVE_SHARE, "the view is live in only " + liveShare + " of the steps");
        assertTrue(coverage.loads() > LEAST_LOADS, "loads: " + coverage.loads());
        for (ViewSimulation.Fault fault : ViewSimulation.Fault.values()) {
            int count = coverage.count(fault);
            assertTrue(count >= leastCountOf(fault), fault + ": " + count + " in " + coverage.faults());
        }
    }

    private static int leastCountOf(ViewSimulation.Fault fault) {
        if (fault == ViewSimulation.Fault.MESSAGE_LOST_BEFORE_INTEREST_SPREAD) {
            return LEAST_LOST_BEFORE_INTEREST_SPREAD;
        }
        return LEAST_OF_EACH_FAULT;
    }

    @Test
    void longRuns_audit() {
        ViewSimulation.Coverage coverage = ViewSimulation.runAll(ReferenceMachine.STARTER, ViewSimulation.longRuns());

        assertEquals(EnumSet.of(AuditOutcome.MATCHED, AuditOutcome.JUMPED_PAST), coverage.auditOutcomes(),
                "the long runs reach these audit outcomes; the examples cover the other two");
    }
}
