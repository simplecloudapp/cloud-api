package app.simplecloud.api.internal;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class SharedResourcesTest {

    private final List<Object> closedResources = new ArrayList<>();
    private final SharedResources<String, Object> resources = new SharedResources<>(closedResources::add);

    @Test
    void handlesOnOneKeyShareTheResourceTheFirstOneOpened() {
        SharedResources.Handle<Object> first = resources.acquire("network-1", Object::new);
        SharedResources.Handle<Object> second = resources.acquire("network-1", SharedResourcesTest::failToOpen);

        assertSame(first.resource(), second.resource());
    }

    @Test
    void handlesOnOtherKeysGetTheirOwnResource() {
        SharedResources.Handle<Object> first = resources.acquire("network-1", Object::new);
        SharedResources.Handle<Object> second = resources.acquire("network-2", Object::new);

        assertNotSame(first.resource(), second.resource());
    }

    @Test
    void theResourceClosesWithTheLastHandleWhicheverClosesFirst() {
        SharedResources.Handle<Object> openedFirst = resources.acquire("network-1", Object::new);
        SharedResources.Handle<Object> openedSecond = resources.acquire("network-1", Object::new);

        openedFirst.close();
        assertEquals(List.of(), closedResources);

        openedSecond.close();
        assertEquals(List.of(openedFirst.resource()), closedResources);
    }

    @Test
    void closingAHandleTwiceCountsOnce() {
        SharedResources.Handle<Object> closedTwice = resources.acquire("network-1", Object::new);
        resources.acquire("network-1", Object::new);

        closedTwice.close();
        closedTwice.close();

        assertEquals(List.of(), closedResources);
    }

    @Test
    void aHandleTakenAfterTheLastCloseOpensANewResource() {
        SharedResources.Handle<Object> beforeDisable = resources.acquire("network-1", Object::new);
        beforeDisable.close();

        SharedResources.Handle<Object> afterEnable = resources.acquire("network-1", Object::new);

        assertNotSame(beforeDisable.resource(), afterEnable.resource());
        assertEquals(List.of(beforeDisable.resource()), closedResources);
    }

    private static Object failToOpen() {
        throw new AssertionError("A second resource was opened for a key that has one");
    }
}
