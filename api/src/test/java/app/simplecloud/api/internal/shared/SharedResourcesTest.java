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

        first.close();
        first.close();
        assertEquals(List.of(), closed);

        second.close();
        assertEquals(List.of(1), closed);

        SharedResources.Lease<Object> third = SharedResources.acquire(key, () -> created.incrementAndGet(), closed::add);
        assertEquals(2, third.get());
        third.close();
    }

    @Test
    void unsharedLeasesCloseTheirOwnResource() {
        List<Object> closed = new ArrayList<>();
        Object value = new Object();

        SharedResources.Lease<Object> lease = SharedResources.Lease.unshared(value, closed::add);
        SharedResources.Lease<Object> other = SharedResources.Lease.unshared(new Object(), closed::add);

        assertNotSame(lease.get(), other.get());
        lease.close();
        lease.close();
        assertEquals(List.of(value), closed);
        other.close();
    }
}
