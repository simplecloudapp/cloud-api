package app.simplecloud.api.internal.nats;

import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Subscription;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstanceNatsConnectionTest {

    private final SharedConnection shared = new SharedConnection();

    @Test
    void closeEndsOnlyThisInstancesDispatchersAndSubscriptions() {
        InstanceNatsConnection closing = new InstanceNatsConnection(shared.connection);
        InstanceNatsConnection remaining = new InstanceNatsConnection(shared.connection);
        Dispatcher closingDispatcher = closing.asConnection().createDispatcher(null);
        Subscription closingSubscription = closing.asConnection().subscribe("network-1.closing");
        remaining.asConnection().createDispatcher(null);
        Subscription remainingSubscription = remaining.asConnection().subscribe("network-1.remaining");

        closing.close();

        assertEquals(List.of(closingDispatcher), shared.closedDispatchers);
        assertFalse(closingSubscription.isActive());
        assertTrue(remainingSubscription.isActive());
        assertFalse(shared.closed.get());
        assertEquals(Connection.Status.CONNECTED, remaining.asConnection().getStatus());
    }

    @Test
    void closedConnectionReportsClosedAndRejectsPublishes() {
        InstanceNatsConnection closed = new InstanceNatsConnection(shared.connection);

        closed.close();

        byte[] payload = new byte[0];
        assertEquals(Connection.Status.CLOSED, closed.asConnection().getStatus());
        assertThrows(IllegalStateException.class, () -> closed.asConnection().publish("network-1.closed", payload));
        assertTrue(shared.published.isEmpty());
    }

    @Test
    void closingTheConnectionLeavesTheSharedConnectionOpen() throws InterruptedException {
        InstanceNatsConnection instance = new InstanceNatsConnection(shared.connection);
        Dispatcher dispatcher = instance.asConnection().createDispatcher(null);

        instance.asConnection().close();

        assertEquals(List.of(dispatcher), shared.closedDispatchers);
        assertFalse(shared.closed.get());
        assertEquals(Connection.Status.CLOSED, instance.asConnection().getStatus());
    }

    @Test
    void drainingTheConnectionLeavesTheSharedConnectionOpen() throws Exception {
        InstanceNatsConnection instance = new InstanceNatsConnection(shared.connection);
        Subscription subscription = instance.asConnection().subscribe("network-1.draining");

        instance.asConnection().drain(Duration.ofSeconds(1)).get();

        assertFalse(subscription.isActive());
        assertFalse(shared.closed.get());
        assertEquals(Connection.Status.CLOSED, instance.asConnection().getStatus());
    }

    @Test
    void closeSkipsWhatTheCallerClosedAlready() {
        InstanceNatsConnection instance = new InstanceNatsConnection(shared.connection);
        Dispatcher dispatcher = instance.asConnection().createDispatcher(null);
        Subscription subscription = instance.asConnection().subscribe("network-1.unsubscribed");
        instance.asConnection().closeDispatcher(dispatcher);
        subscription.unsubscribe();

        assertDoesNotThrow(instance::close);

        assertEquals(List.of(dispatcher), shared.closedDispatchers);
        assertEquals(1, shared.unsubscribeCalls.get());
    }

    /**
     * Records what the instance connections do to the connection they share. A subscription that is
     * unsubscribed twice throws, as in jnats.
     */
    private static final class SharedConnection {
        private final List<Dispatcher> closedDispatchers = new CopyOnWriteArrayList<>();
        private final List<String> published = new CopyOnWriteArrayList<>();
        private final AtomicBoolean closed = new AtomicBoolean(false);
        private final AtomicInteger unsubscribeCalls = new AtomicInteger();
        private final Connection connection = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> handleCall(method, args)
        );

        private Object handleCall(Method method, Object[] args) {
            return switch (method.getName()) {
                case "getStatus" -> closed.get() ? Connection.Status.CLOSED : Connection.Status.CONNECTED;
                case "createDispatcher" -> newDispatcher();
                case "closeDispatcher" -> closedDispatchers.add((Dispatcher) args[0]);
                case "subscribe" -> newSubscription();
                case "publish" -> published.add((String) args[0]);
                case "close" -> closed.getAndSet(true);
                default -> throw new AssertionError("Unexpected call on the shared connection: " + method.getName());
            };
        }

        private Subscription newSubscription() {
            AtomicBoolean active = new AtomicBoolean(true);
            return (Subscription) Proxy.newProxyInstance(
                    Subscription.class.getClassLoader(),
                    new Class<?>[]{Subscription.class},
                    (proxy, method, args) -> handleSubscriptionCall(active, method)
            );
        }

        private Object handleSubscriptionCall(AtomicBoolean active, Method method) {
            return switch (method.getName()) {
                case "isActive" -> active.get();
                case "unsubscribe" -> unsubscribe(active);
                default -> throw new AssertionError("Unexpected call on a subscription: " + method.getName());
            };
        }

        private Object unsubscribe(AtomicBoolean active) {
            unsubscribeCalls.incrementAndGet();
            if (!active.getAndSet(false)) {
                throw new IllegalStateException("This subscription is inactive.");
            }
            return null;
        }

        private static Dispatcher newDispatcher() {
            return (Dispatcher) Proxy.newProxyInstance(
                    Dispatcher.class.getClassLoader(),
                    new Class<?>[]{Dispatcher.class},
                    (dispatcher, method, args) -> handleDispatcherCall(dispatcher, method, args)
            );
        }

        private static Object handleDispatcherCall(Object dispatcher, Method method, Object[] args) {
            return switch (method.getName()) {
                case "hashCode" -> System.identityHashCode(dispatcher);
                case "equals" -> dispatcher == args[0];
                default -> throw new AssertionError("Unexpected call on a dispatcher: " + method.getName());
            };
        }
    }
}
