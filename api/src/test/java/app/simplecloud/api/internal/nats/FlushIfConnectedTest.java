package app.simplecloud.api.internal.nats;

import io.nats.client.Connection;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FlushIfConnectedTest {

    @Test
    void flushesAConnectedConnection() {
        AtomicInteger flushCalls = new AtomicInteger();
        Connection connection = connectionWithStatus(Connection.Status.CONNECTED, flushCalls);

        NatsFailoverConnectionManager.flushIfConnected(connection);

        assertEquals(1, flushCalls.get());
    }

    @Test
    void skipsTheFlushForAConnectionThatIsDown() {
        AtomicInteger flushCalls = new AtomicInteger();
        Connection connection = connectionWithStatus(Connection.Status.RECONNECTING, flushCalls);

        NatsFailoverConnectionManager.flushIfConnected(connection);

        assertEquals(0, flushCalls.get());
    }

    @Test
    void swallowsAFailedFlush() {
        Connection connection = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getStatus" -> Connection.Status.CONNECTED;
                    case "flush" -> throw new TimeoutException("no pong");
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );

        assertDoesNotThrow(() -> NatsFailoverConnectionManager.flushIfConnected(connection));
    }

    private static Connection connectionWithStatus(Connection.Status status, AtomicInteger flushCalls) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getStatus" -> status;
                    case "flush" -> {
                        flushCalls.incrementAndGet();
                        yield null;
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }
}
