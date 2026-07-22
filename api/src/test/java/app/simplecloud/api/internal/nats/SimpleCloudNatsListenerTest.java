package app.simplecloud.api.internal.nats;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.SocketException;

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
    void recognizesRoutineConnectionReset() {
        assertTrue(SimpleCloudNatsListener.isRoutineChannelClosure(
                new SocketException("Connection reset")
        ));
    }

    @Test
    void doesNotHideOtherIoFailures() {
        assertFalse(SimpleCloudNatsListener.isRoutineChannelClosure(
                new IOException("Connection reset")
        ));
    }

    @Test
    void doesNotHideOtherSocketFailures() {
        assertFalse(SimpleCloudNatsListener.isRoutineChannelClosure(
                new SocketException("Network is unreachable")
        ));
    }

    @Test
    void doesNotHideUnrelatedExceptionsWithSameMessage() {
        assertFalse(SimpleCloudNatsListener.isRoutineChannelClosure(
                new IllegalStateException("Read channel closed.")
        ));
    }
}
