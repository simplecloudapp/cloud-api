package app.simplecloud.api.internal.nats;

import io.nats.client.Connection;
import io.nats.client.Dispatcher;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * One {@code CloudApi} instance's view of a shared NATS connection.
 *
 * <p>It remembers the dispatchers the instance creates, so closing the instance stops only its
 * own subscriptions. Closing the view never closes the shared connection.
 */
public final class ScopedNatsConnection {

    private static final Logger LOGGER = Logger.getLogger(ScopedNatsConnection.class.getName());

    private final Connection shared;
    private final List<Dispatcher> dispatchers = new CopyOnWriteArrayList<>();
    private final Connection connection;

    public ScopedNatsConnection(Connection shared) {
        this.shared = shared;
        this.connection = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "createDispatcher" -> {
                        Dispatcher dispatcher = (Dispatcher) invoke(method, args);
                        dispatchers.add(dispatcher);
                        yield dispatcher;
                    }
                    case "closeDispatcher" -> {
                        dispatchers.remove((Dispatcher) args[0]);
                        yield invoke(method, args);
                    }
                    case "close" -> {
                        closeDispatchers();
                        yield null;
                    }
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "Scoped " + shared;
                    default -> invoke(method, args);
                }
        );
    }

    public Connection connection() {
        return connection;
    }

    public void closeDispatchers() {
        for (Dispatcher dispatcher : dispatchers) {
            dispatchers.remove(dispatcher);
            try {
                shared.closeDispatcher(dispatcher);
            } catch (RuntimeException e) {
                // A dispatcher that was already drained is no longer known to the connection.
                LOGGER.log(Level.FINE, "Failed to close NATS dispatcher", e);
            }
        }
    }

    private Object invoke(Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(shared, args);
        } catch (InvocationTargetException e) {
            throw e.getTargetException();
        }
    }
}
