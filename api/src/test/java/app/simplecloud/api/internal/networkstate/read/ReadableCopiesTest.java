package app.simplecloud.api.internal.networkstate.read;

import app.simplecloud.api.internal.networkstate.AnsweringView;
import app.simplecloud.api.internal.networkstate.NetworkCopy;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class ReadableCopiesTest {

    private static final String NETWORK_ID = "0d000000-0000-4000-8000-000000000001";

    @Test
    void readersOfOneCopyShareItsReadableCopy() {
        AtomicReference<NetworkCopy> current = new AtomicReference<>(copyAt(1));
        AnsweringView currentCopy = () -> CompletableFuture.completedFuture(Optional.of(current.get()));
        ReadableCopies copies = new ReadableCopies(currentCopy, NETWORK_ID);

        ReadableCopy first = copies.copyToAnswer().join().orElseThrow();
        ReadableCopy second = copies.copyToAnswer().join().orElseThrow();
        current.set(copyAt(2));
        ReadableCopy ofNewCopy = copies.copyToAnswer().join().orElseThrow();

        assertSame(first, second);
        assertNotSame(first, ofNewCopy);
        assertSame(current.get(), ofNewCopy.copy());
    }

    @Test
    void noneNeverHandsOutACopy() {
        Optional<ReadableCopy> copy = ReadableCopies.none().copyToAnswer().join();

        assertEquals(Optional.empty(), copy);
    }

    private static NetworkCopy copyAt(long revision) {
        return new NetworkCopy(1, revision, Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
    }
}
