package app.simplecloud.api.internal.nats;

import io.nats.client.Connection;
import io.nats.client.ConnectionListener;
import io.nats.client.impl.ErrorListenerLoggerImpl;

import java.io.IOException;
import java.net.SocketException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Presents recoverable NATS connection changes without making them look like
 * application failures. All other asynchronous client errors retain jnats'
 * default logging behavior.
 */
final class SimpleCloudNatsListener extends ErrorListenerLoggerImpl implements ConnectionListener {

    private static final Logger LOGGER = Logger.getLogger(SimpleCloudNatsListener.class.getName());
    private static final String READ_CHANNEL_CLOSED = "Read channel closed.";
    private static final String CONNECTION_RESET = "Connection reset";

    @Override
    public void exceptionOccurred(Connection connection, Exception exception) {
        if (isRoutineChannelClosure(exception)) {
            LOGGER.log(Level.FINE, "NATS transport channel closed; the client will reconnect automatically");
            return;
        }

        super.exceptionOccurred(connection, exception);
    }

    @Override
    public void connectionEvent(Connection connection, Events event) {
        switch (event) {
            case DISCONNECTED -> LOGGER.fine("NATS connection interrupted; reconnecting automatically");
            case RECONNECTED -> LOGGER.fine("NATS connection restored");
            default -> {
                // The connection manager already reports initial connections,
                // permanent closures, and subscription rebinding.
            }
        }
    }

    static boolean isRoutineChannelClosure(Exception exception) {
        return exception instanceof IOException && (
                READ_CHANNEL_CLOSED.equals(exception.getMessage())
                        || exception instanceof SocketException
                        && CONNECTION_RESET.equals(exception.getMessage())
        );
    }
}
