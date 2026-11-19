package app.simplecloud.api.internal.networkstate;

import java.util.ArrayList;
import java.util.List;

/**
 * One named check of the rules of {@link NetworkView}. It throws an {@link AssertionError} when the machine
 * breaks them.
 */
record ViewCheck(String name, Body body) {

    /**
     * Returns every check: the examples first, because they are fast, then the simulator.
     */
    static List<ViewCheck> all() {
        List<ViewCheck> checks = new ArrayList<>(ViewRuleChecks.all());
        checks.addAll(ViewSimulation.all());
        return checks;
    }

    @FunctionalInterface
    interface Body {

        void run(ViewStarter starter);
    }
}
