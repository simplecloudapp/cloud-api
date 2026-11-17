package app.simplecloud.api.internal.networkstate;

import org.junit.jupiter.api.DynamicTest;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.DynamicTest.dynamicTest;

final class ViewCheckTests {

    private ViewCheckTests() {
    }

    static List<DynamicTest> dynamicTests(ViewStarter starter) {
        List<DynamicTest> tests = new ArrayList<>();
        for (ViewCheck check : ViewCheck.all()) {
            tests.add(dynamicTest(check.name(), () -> check.body().run(starter)));
        }
        return tests;
    }
}
