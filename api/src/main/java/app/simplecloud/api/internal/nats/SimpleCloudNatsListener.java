package app.simplecloud.api.internal.nats;

import io.nats.client.Connection;
import io.nats.client.ConnectionListener;
import io.nats.client.impl.ErrorListenerLoggerImpl;

import java.io.IOException;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

final class SimpleCloudNatsListener extends ErrorListenerLoggerImpl implements ConnectionListener {

    private static final Logger LOGGER = Logger.getLogger(SimpleCloudNatsListener.class.getName());
    private static final String MAX_PINGS_EXCEEDED = "Max outgoing Ping count exceeded";

    @Override
    public void exceptionOccurred(Connection connection, Exception exception) {
        if (isTransportFailure(exception)) {
            LOGGER.log(Level.FINE, "NATS connection lost; the client will reconnect automatically", exception);
            return;
        }

        super.exceptionOccurred(connection, exception);
    }

    @Override
    public void connectionEvent(Connection connection, Events event) {
        switch (event) {
            case DISCONNECTED -> LOGGER.fine("NATS connection interrupted; reconnecting automatically");
            case RECONNECTED -> LOGGER.fine("NATS connection restored");
            default -> {}
        }
    }

    static boolean isTransportFailure(Exception exception) {
        return exception instanceof IOException
                || exception instanceof TimeoutException
                || exception instanceof IllegalStateException
                        && exception.getMessage() != null
                        && exception.getMessage().startsWith(MAX_PINGS_EXCEEDED);
    }
}
