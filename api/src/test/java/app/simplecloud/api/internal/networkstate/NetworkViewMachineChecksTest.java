package app.simplecloud.api.internal.networkstate;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.List;

@Disabled("NetworkViewMachine is a stub; its implementation removes this line and must pass every check")
class NetworkViewMachineChecksTest {

    @TestFactory
    List<DynamicTest> networkViewMachinePassesEveryCheck() {
        return ViewCheckTests.dynamicTests(NetworkViewMachine::start);
    }
}
