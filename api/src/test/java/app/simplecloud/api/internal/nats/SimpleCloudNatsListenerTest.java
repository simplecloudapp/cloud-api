package app.simplecloud.api.internal.nats;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimpleCloudNatsListenerTest {

    @Test
    void recognizesRoutineReadChannelClosure() {
        assertTrue(SimpleCloudNatsListener.isRoutineChannelClosure(
                new IOException("Read channel closed.")
        ));
    }

    @Test
    void doesNotHideOtherIoFailures() {
        assertFalse(SimpleCloudNatsListener.isRoutineChannelClosure(
                new IOException("Connection reset")
        ));
    }

    @Test
    void doesNotHideUnrelatedExceptionsWithSameMessage() {
        assertFalse(SimpleCloudNatsListener.isRoutineChannelClosure(
                new IllegalStateException("Read channel closed.")
        ));
    }
}
