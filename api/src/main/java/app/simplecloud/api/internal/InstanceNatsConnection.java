package app.simplecloud.api.internal;

import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Subscription;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The NATS connection of one {@code CloudApi} instance, on top of a connection that other instances
 * may share.
 *
 * <p>It remembers the dispatchers and subscriptions the instance creates. {@code close} and
 * {@code drain} close only those, never the shared connection. Once closed, {@code getStatus}
 * returns {@code CLOSED}, {@code close} and {@code drain} do nothing, and every other {@code Connection}
 * method throws {@link IllegalStateException}.
 */
final class InstanceNatsConnection {

    private static final Logger LOGGER = Logger.getLogger(InstanceNatsConnection.class.getName());

    private final Connection sharedConnection;
    private final List<Dispatcher> dispatchers = new ArrayList<>();
    private final List<Subscription> subscriptions = new ArrayList<>();
    private final Connection connection;
    private boolean closed;

    InstanceNatsConnection(Connection sharedConnection) {
        this.sharedConnection = sharedConnection;
        this.connection = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                this::handleCall
        );
    }

    Connection asConnection() {
        return connection;
    }

    // Synchronized with subscribe and createDispatcher, so nothing is created after close went through the lists.
    synchronized void close() {
        closed = true;
        for (Dispatcher dispatcher : dispatchers) {
            closeDispatcherQuietly(dispatcher);
        }
        dispatchers.clear();
        for (Subscription subscription : subscriptions) {
            unsubscribeQuietly(subscription);
        }
        subscriptions.clear();
    }

    private Object handleCall(Object proxy, Method method, Object[] args) throws Throwable {
        return switch (method.getName()) {
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            case "toString" -> "NATS connection of one CloudApi instance on " + sharedConnection;
            case "getStatus" -> getStatus();
            case "close" -> {
                close();
                yield null;
            }
            case "drain" -> {
                close();
                yield CompletableFuture.completedFuture(Boolean.TRUE);
            }
            case "subscribe" -> subscribe(method, args);
            case "createDispatcher" -> createDispatcher(method, args);
            case "closeDispatcher" -> closeDispatcher(method, args);
            default -> invokeUnlessClosed(method, args);
        };
    }

    private synchronized Connection.Status getStatus() {
        if (closed) {
            return Connection.Status.CLOSED;
        }
        return sharedConnection.getStatus();
    }

    private synchronized Subscription subscribe(Method method, Object[] args) throws Throwable {
        Subscription subscription = (Subscription) invokeUnlessClosed(method, args);
        subscriptions.add(subscription);
        return subscription;
    }

    private synchronized Dispatcher createDispatcher(Method method, Object[] args) throws Throwable {
        Dispatcher dispatcher = (Dispatcher) invokeUnlessClosed(method, args);
        dispatchers.add(dispatcher);
        return dispatcher;
    }

    private synchronized Object closeDispatcher(Method method, Object[] args) throws Throwable {
        Dispatcher dispatcher = (Dispatcher) args[0];
        Object result = invokeUnlessClosed(method, args);
        // By identity: once connected, the failover manager's dispatcher passes equals on to the jnats
        // dispatcher behind it, so it is not equal to itself.
        dispatchers.removeIf(remembered -> remembered == dispatcher);
        return result;
    }

    private void closeDispatcherQuietly(Dispatcher dispatcher) {
        try {
            sharedConnection.closeDispatcher(dispatcher);
        } catch (RuntimeException e) {
            // Thrown for a dispatcher that was drained already.
            LOGGER.log(Level.FINE, "Failed to close NATS dispatcher", e);
        }
    }

    private void unsubscribeQuietly(Subscription subscription) {
        if (!subscription.isActive()) {
            return;
        }
        try {
            subscription.unsubscribe();
        } catch (RuntimeException e) {
            // Thrown when the subscription ended between the check and the call.
            LOGGER.log(Level.FINE, "Failed to unsubscribe NATS subscription", e);
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

    private synchronized boolean isClosed() {
        return closed;
    }
}
