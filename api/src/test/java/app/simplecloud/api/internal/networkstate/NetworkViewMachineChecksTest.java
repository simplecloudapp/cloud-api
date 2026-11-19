package app.simplecloud.api.internal.networkstate;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.List;

class NetworkViewMachineChecksTest {

    @TestFactory
    List<DynamicTest> networkViewMachinePassesEveryCheck() {
        return ViewChecksAsDynamicTests.dynamicTests(NetworkViewMachine::start);
    }
}
