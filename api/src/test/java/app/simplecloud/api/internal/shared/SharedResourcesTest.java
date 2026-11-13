package app.simplecloud.api.internal.shared;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class SharedResourcesTest {

    @Test
    void leasesOnOneKeyShareTheResourceUntilTheLastOneCloses() {
        Object key = new Object();
        AtomicInteger created = new AtomicInteger();
        List<Object> closed = new ArrayList<>();

        SharedResources.Lease<Object> first = SharedResources.acquire(key, () -> created.incrementAndGet(), closed::add);
        SharedResources.Lease<Object> second = SharedResources.acquire(key, () -> created.incrementAndGet(), closed::add);

        assertSame(first.get(), second.get());
        assertEquals(1, created.get());
        assertEquals(2, SharedResources.openLeases(key));

        first.close();
        first.close();
        assertEquals(List.of(), closed);
        assertEquals(1, SharedResources.openLeases(key));

        second.close();
        assertEquals(List.of(1), closed);
        assertEquals(0, SharedResources.openLeases(key));

        SharedResources.Lease<Object> third = SharedResources.acquire(key, () -> created.incrementAndGet(), closed::add);
        assertEquals(2, third.get());
        third.close();
    }

    @Test
    void dedicatedLeasesAreNeverShared() {
        List<Object> closed = new ArrayList<>();
        Object value = new Object();

        SharedResources.Lease<Object> lease = SharedResources.dedicated(value, closed::add);
        SharedResources.Lease<Object> other = SharedResources.dedicated(new Object(), closed::add);

        assertNotSame(lease.get(), other.get());
        lease.close();
        lease.close();
        assertEquals(List.of(value), closed);
        other.close();
    }
}
