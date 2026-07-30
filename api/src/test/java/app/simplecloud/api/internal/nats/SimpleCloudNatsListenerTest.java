package app.simplecloud.api.internal.nats;

import io.nats.client.Options;
import io.nats.client.impl.ErrorListenerLoggerImpl;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
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

    @Test
    void suppressesRoutineReadChannelClosureInsteadOfUsingDefaultErrorLogger() {
        List<LogRecord> records = captureDefaultErrorLogger(() ->
                new SimpleCloudNatsListener().exceptionOccurred(
                        null,
                        new IOException("Read channel closed.")
                )
        );

        assertTrue(records.isEmpty());
    }

    @Test
    void retainsDefaultLoggingForUnexpectedExceptions() {
        List<LogRecord> records = captureDefaultErrorLogger(() ->
                new SimpleCloudNatsListener().exceptionOccurred(
                        null,
                        new IOException("Unexpected transport failure")
                )
        );

        assertTrue(records.stream().anyMatch(record ->
                record.getLevel() == Level.SEVERE
                        && record.getMessage().contains("Unexpected transport failure")
        ));
    }

    @Test
    void failoverConnectionsInstallSimpleCloudListenerForErrorsAndConnectionEvents() {
        SimpleCloudNatsListener listener = new SimpleCloudNatsListener();
        Options options = NatsFailoverConnectionManager.createOptions(
                "nats://localhost:4222",
                "network",
                "secret",
                listener
        );

        assertSame(listener, options.getErrorListener());
        assertSame(listener, options.getConnectionListener());
    }

    private static List<LogRecord> captureDefaultErrorLogger(Runnable action) {
        Logger logger = Logger.getLogger(ErrorListenerLoggerImpl.class.getName());
        List<LogRecord> records = new ArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };

        Level previousLevel = logger.getLevel();
        boolean previousUseParentHandlers = logger.getUseParentHandlers();
        logger.setLevel(Level.ALL);
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);
        try {
            action.run();
        } finally {
            logger.removeHandler(handler);
            logger.setUseParentHandlers(previousUseParentHandlers);
            logger.setLevel(previousLevel);
        }
        return records;
    }
}
