package app.simplecloud.api.internal.networkstate;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

class WrongMachinesTest {

    @TestFactory
    List<DynamicTest> everyWrongMachineFailsTheChecksNamedForIt() {
        Map<String, ViewCheck> checksByName = new HashMap<>();
        for (ViewCheck check : ViewCheck.all()) {
            checksByName.put(check.name(), check);
        }
        List<DynamicTest> tests = new ArrayList<>();
        for (WrongMachines.WrongMachine machine : WrongMachines.all()) {
            assertFalse(machine.caughtBy().isEmpty(), "every wrong machine names the checks that catch it");
            for (String checkName : machine.caughtBy()) {
                ViewCheck check = checksByName.get(checkName);
                assertNotNull(check, "no check is named \"" + checkName + "\"");
                String testName = machine.fault() + " / " + checkName;
                tests.add(dynamicTest(testName, () -> assertCheckFails(check, machine)));
            }
        }
        return tests;
    }

    /**
     * Only an assertion of the check itself catches the machine: when the set-up fails, the check never reached
     * the rule it names.
     */
    private static void assertCheckFails(ViewCheck check, WrongMachines.WrongMachine machine) {
        try {
            check.body().run(machine.starter());
        } catch (SetUpFailed setUpFailed) {
            fail("the check's set-up failed for a machine that " + machine.fault() + ", so the check never"
                    + " reached its point: " + setUpFailed.getMessage(), setUpFailed);
        } catch (AssertionError caught) {
            return;
        }
        fail("the check passed for a machine that " + machine.fault());
    }
}
