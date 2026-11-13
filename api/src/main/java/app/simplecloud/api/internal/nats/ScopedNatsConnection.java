package app.simplecloud.api.internal.nats;

import io.nats.client.Connection;
import io.nats.client.Dispatcher;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * One {@code CloudApi} instance's view of a shared NATS connection.
 *
 * <p>It remembers the dispatchers the instance creates, so closing the instance stops only its
 * own subscriptions. {@code close} and {@code drain} on the view close only the view, never the
 * shared connection, and a closed view rejects every further call.
 */
public final class ScopedNatsConnection {

    private static final Logger LOGGER = Logger.getLogger(ScopedNatsConnection.class.getName());

    private final Connection shared;
    private final List<Dispatcher> dispatchers = new CopyOnWriteArrayList<>();
    private final Connection connection;
    private boolean closed;

    public ScopedNatsConnection(Connection shared) {
        this.shared = shared;
        this.connection = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                this::handleCall
        );
    }

    public Connection connection() {
        return connection;
    }

    // Synchronized with createDispatcher, so no dispatcher is created after close went through the list.
    public synchronized void close() {
        closed = true;
        for (Dispatcher dispatcher : dispatchers) {
            dispatchers.remove(dispatcher);
            closeSharedDispatcher(dispatcher);
        }
    }

    private Object handleCall(Object proxy, Method method, Object[] args) throws Throwable {
        return switch (method.getName()) {
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            case "toString" -> "Scoped " + shared;
            case "close" -> {
                close();
                yield null;
            }
            case "drain" -> {
                close();
                yield CompletableFuture.completedFuture(Boolean.TRUE);
            }
            default -> forward(method, args);
        };
    }

    private Object forward(Method method, Object[] args) throws Throwable {
        return switch (method.getName()) {
            case "createDispatcher" -> createDispatcher(method, args);
            case "closeDispatcher" -> closeDispatcher(method, args);
            default -> invokeUnlessClosed(method, args);
        };
    }

    private synchronized Dispatcher createDispatcher(Method method, Object[] args) throws Throwable {
        Dispatcher dispatcher = (Dispatcher) invokeUnlessClosed(method, args);
        dispatchers.add(dispatcher);
        return dispatcher;
    }

    private Object closeDispatcher(Method method, Object[] args) throws Throwable {
        dispatchers.remove((Dispatcher) args[0]);
        return invokeUnlessClosed(method, args);
    }

    private synchronized boolean isClosed() {
        return closed;
    }

    private void closeSharedDispatcher(Dispatcher dispatcher) {
        try {
            shared.closeDispatcher(dispatcher);
        } catch (RuntimeException e) {
            // A dispatcher that was already drained is no longer known to the connection.
            LOGGER.log(Level.FINE, "Failed to close NATS dispatcher", e);
        }
    }

    private Object invokeUnlessClosed(Method method, Object[] args) throws Throwable {
        if (isClosed()) {
            throw new IllegalStateException("NATS connection of a closed CloudApi instance");
        }
        try {
            return method.invoke(shared, args);
        } catch (InvocationTargetException e) {
            throw e.getTargetException();
        }
    }
}
