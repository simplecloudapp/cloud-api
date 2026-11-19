package app.simplecloud.api.internal.networkstate;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.List;

class ReferenceMachineChecksTest {

    @TestFactory
    List<DynamicTest> referenceMachinePassesEveryCheck() {
        return ViewChecksAsDynamicTests.dynamicTests(ReferenceMachine.STARTER);
    }
}
