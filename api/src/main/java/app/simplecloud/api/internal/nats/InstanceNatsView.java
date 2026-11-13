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
 * One {@code CloudApi} instance's view of its NATS connection, which other instances may share.
 *
 * <p>Subscriptions go through dispatchers, which the view remembers, so closing the instance
 * closes only its own dispatchers and their subscriptions. {@code subscribe} directly on the view
 * is rejected, because such a subscription could not be closed with the instance. {@code close}
 * and {@code drain} on the view close only the view, never the connection, and a closed view
 * rejects every further call.
 */
public final class InstanceNatsView {

    private static final Logger LOGGER = Logger.getLogger(InstanceNatsView.class.getName());

    private final Connection sharedConnection;
    private final List<Dispatcher> dispatchers = new CopyOnWriteArrayList<>();
    private final Connection view;
    private boolean closed;

    public InstanceNatsView(Connection sharedConnection) {
        this.sharedConnection = sharedConnection;
        this.view = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                this::handleCall
        );
    }

    public Connection view() {
        return view;
    }

    // Synchronized with createDispatcher, so no dispatcher is created after close went through the list.
    public synchronized void close() {
        closed = true;
        for (Dispatcher dispatcher : dispatchers) {
            closeDispatcherOnSharedConnection(dispatcher);
        }
        dispatchers.clear();
    }

    private Object handleCall(Object proxy, Method method, Object[] args) throws Throwable {
        return switch (method.getName()) {
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            case "toString" -> "Instance view of " + sharedConnection;
            case "close" -> {
                close();
                yield null;
            }
            case "drain" -> {
                close();
                yield CompletableFuture.completedFuture(Boolean.TRUE);
            }
            case "subscribe" -> throw new UnsupportedOperationException(
                    "Subscribe through a dispatcher, so the subscription closes with its CloudApi instance"
            );
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

    private void closeDispatcherOnSharedConnection(Dispatcher dispatcher) {
        try {
            sharedConnection.closeDispatcher(dispatcher);
        } catch (RuntimeException e) {
            // Thrown for a dispatcher that was drained already, and once the connection is closed,
            // which the platform plugin's instance may do before this instance closes.
            LOGGER.log(Level.FINE, "Failed to close NATS dispatcher", e);
        }
    }

    private Object invokeUnlessClosed(Method method, Object[] args) throws Throwable {
        if (isClosed()) {
            throw new IllegalStateException("NATS connection of a closed CloudApi instance");
        }
        try {
            return method.invoke(sharedConnection, args);
        } catch (InvocationTargetException e) {
            throw e.getTargetException();
        }
    }
}
