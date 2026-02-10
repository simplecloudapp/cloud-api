package app.simplecloud.api.internal.nats;

import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.MessageHandler;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.nats.client.Subscription;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class NatsFailoverConnectionManager {

    private static final Logger LOGGER = Logger.getLogger(NatsFailoverConnectionManager.class.getName());

    private final String natsUrl;
    private final String networkId;
    private final String networkSecret;
    private final Duration failoverReconnectAfter;

    private final AtomicReference<Connection> connectionRef;
    private final Connection connectionProxy;
    private final List<DispatcherProxyState> dispatchers = new CopyOnWriteArrayList<>();
    private final AtomicBoolean reconnecting = new AtomicBoolean(false);
    private final AtomicLong reconnectingSince = new AtomicLong(-1);

    private final ScheduledExecutorService monitorExecutor;
    private final ScheduledExecutorService reconnectExecutor;

    public NatsFailoverConnectionManager(
            String natsUrl,
            String networkId,
            String networkSecret,
            Duration failoverReconnectAfter
    ) throws IOException, InterruptedException {
        this.natsUrl = natsUrl;
        this.networkId = networkId;
        this.networkSecret = networkSecret;
        this.failoverReconnectAfter = failoverReconnectAfter == null ? Duration.ofSeconds(30) : failoverReconnectAfter;

        Connection initialConnection = createConnection();
        this.connectionRef = new AtomicReference<>(initialConnection);
        this.connectionProxy = createConnectionProxy();

        this.monitorExecutor = Executors.newSingleThreadScheduledExecutor(daemonFactory("simplecloud-nats-failover-monitor"));
        this.reconnectExecutor = Executors.newSingleThreadScheduledExecutor(daemonFactory("simplecloud-nats-failover-reconnect"));

        this.monitorExecutor.scheduleAtFixedRate(this::monitorConnection, 2, 2, TimeUnit.SECONDS);
    }

    public Connection getConnection() {
        return connectionProxy;
    }

    public void shutdown() {
        monitorExecutor.shutdownNow();
        reconnectExecutor.shutdownNow();
        Connection connection = connectionRef.get();
        if (connection != null) {
            try {
                connection.close();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void monitorConnection() {
        try {
            Connection connection = connectionRef.get();
            if (connection == null) {
                return;
            }

            Connection.Status status = connection.getStatus();
            long now = System.currentTimeMillis();

            if (status == Connection.Status.CLOSED) {
                reconnectingSince.set(-1);
                scheduleReconnect("connection is CLOSED");
                return;
            }

            if (status == Connection.Status.RECONNECTING || status == Connection.Status.DISCONNECTED) {
                long since = reconnectingSince.updateAndGet(current -> current == -1 ? now : current);
                if (!failoverReconnectAfter.isZero()
                        && !failoverReconnectAfter.isNegative()
                        && now - since >= failoverReconnectAfter.toMillis()) {
                    reconnectingSince.set(-1);
                    scheduleReconnect("connection stayed " + status + " for at least " + failoverReconnectAfter);
                }
                return;
            }

            reconnectingSince.set(-1);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to monitor NATS connection state", e);
        }
    }

    private void scheduleReconnect(String reason) {
        if (!reconnecting.compareAndSet(false, true)) {
            return;
        }

        reconnectExecutor.execute(() -> {
            try {
                reconnectLoop(reason);
            } finally {
                reconnecting.set(false);
            }
        });
    }

    private void reconnectLoop(String reason) {
        LOGGER.warning("Forcing full NATS reconnect: " + reason);

        long backoffMillis = 1000L;
        final long maxBackoffMillis = 30_000L;

        while (!Thread.currentThread().isInterrupted()) {
            try {
                Connection newConnection = createConnection();
                try {
                    rebindDispatchers(newConnection);
                } catch (Exception rebindException) {
                    newConnection.close();
                    throw rebindException;
                }

                Connection oldConnection = connectionRef.getAndSet(newConnection);
                if (oldConnection != null && oldConnection != newConnection) {
                    oldConnection.close();
                }
                LOGGER.info("Successfully established a new NATS connection after forced failover reconnect");
                return;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.log(Level.WARNING, "Interrupted while reconnecting NATS", e);
                return;
            } catch (Exception e) {
                LOGGER.warning("Failed to establish new NATS connection, retrying in "
                        + Duration.ofMillis(backoffMillis) + ": " + e.getMessage());
                try {
                    Thread.sleep(backoffMillis);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    LOGGER.log(Level.WARNING, "Interrupted while waiting for next NATS reconnect retry", interruptedException);
                    return;
                }
                backoffMillis = Math.min(backoffMillis * 2, maxBackoffMillis);
            }
        }
    }

    private void rebindDispatchers(Connection newConnection) {
        for (DispatcherProxyState dispatcherState : dispatchers) {
            dispatcherState.rebind(newConnection);
        }
    }

    private Connection createConnection() throws IOException, InterruptedException {
        return Nats.connect(
                Options.builder()
                        .server(natsUrl)
                        .userInfo(networkId, networkSecret)
                        .maxReconnects(-1)
                        .build()
        );
    }

    private Connection createConnectionProxy() {
        InvocationHandler handler = (proxy, method, args) -> {
            if (method.getName().equals("createDispatcher")) {
                MessageHandler messageHandler = null;
                if (args != null && args.length > 0) {
                    messageHandler = (MessageHandler) args[0];
                }
                DispatcherProxyState dispatcherState = new DispatcherProxyState(messageHandler);
                dispatchers.add(dispatcherState);
                return dispatcherState.proxy;
            }

            if (method.getName().equals("close")) {
                shutdown();
                return null;
            }

            return invoke(method, connectionRef.get(), args);
        };

        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                handler
        );
    }

    private static Object invoke(Method method, Object target, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getTargetException();
        }
    }

    private static ThreadFactory daemonFactory(String threadName) {
        return runnable -> {
            Thread thread = new Thread(runnable, threadName);
            thread.setDaemon(true);
            return thread;
        };
    }

    private final class DispatcherProxyState {
        private final MessageHandler messageHandler;
        private final AtomicReference<Dispatcher> delegateRef;
        private final List<SubscriptionProxyState> subscriptions = new CopyOnWriteArrayList<>();
        private final Dispatcher proxy;

        private DispatcherProxyState(MessageHandler messageHandler) {
            this.messageHandler = messageHandler;
            this.delegateRef = new AtomicReference<>(connectionRef.get().createDispatcher(messageHandler));
            this.proxy = createDispatcherProxy();
        }

        private Dispatcher createDispatcherProxy() {
            InvocationHandler handler = (proxy, method, args) -> {
                if (isSubscribeMethod(method)) {
                    String subject = (String) args[0];
                    MessageHandler handlerArg = (MessageHandler) args[1];
                    Subscription created = (Subscription) invoke(method, delegateRef.get(), args);
                    SubscriptionProxyState subState = new SubscriptionProxyState(subject, handlerArg, created);
                    subscriptions.add(subState);
                    return subState.proxy;
                }

                if (isUnsubscribeBySubject(method, args)) {
                    String subject = (String) args[0];
                    for (SubscriptionProxyState subscription : subscriptions) {
                        if (subscription.subject.equals(subject)) {
                            subscription.deactivate();
                        }
                    }
                }

                return invoke(method, delegateRef.get(), args);
            };

            return (Dispatcher) Proxy.newProxyInstance(
                    Dispatcher.class.getClassLoader(),
                    new Class<?>[]{Dispatcher.class},
                    handler
            );
        }

        private void rebind(Connection newConnection) {
            Dispatcher newDispatcher = newConnection.createDispatcher(messageHandler);
            for (SubscriptionProxyState subscription : subscriptions) {
                subscription.rebind(newDispatcher);
            }
            Dispatcher previous = delegateRef.getAndSet(newDispatcher);
            if (previous != null && previous != newDispatcher) {
                try {
                    previous.drain(Duration.ofSeconds(1));
                } catch (Exception ignored) {
                }
            }
        }
    }

    private final class SubscriptionProxyState {
        private final String subject;
        private final MessageHandler messageHandler;
        private final AtomicReference<Subscription> delegateRef;
        private final AtomicBoolean active = new AtomicBoolean(true);
        private final Subscription proxy;

        private SubscriptionProxyState(String subject, MessageHandler messageHandler, Subscription initialDelegate) {
            this.subject = subject;
            this.messageHandler = messageHandler;
            this.delegateRef = new AtomicReference<>(initialDelegate);
            this.proxy = createSubscriptionProxy();
        }

        private Subscription createSubscriptionProxy() {
            InvocationHandler handler = (proxy, method, args) -> {
                if (method.getName().equals("unsubscribe") || method.getName().equals("drain")) {
                    deactivate();
                }
                return invoke(method, delegateRef.get(), args);
            };

            return (Subscription) Proxy.newProxyInstance(
                    Subscription.class.getClassLoader(),
                    new Class<?>[]{Subscription.class},
                    handler
            );
        }

        private void deactivate() {
            active.set(false);
        }

        private void rebind(Dispatcher newDispatcher) {
            if (!active.get()) {
                return;
            }
            Subscription newSubscription = newDispatcher.subscribe(subject, messageHandler);
            delegateRef.set(newSubscription);
        }
    }

    private static boolean isSubscribeMethod(Method method) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        return method.getName().equals("subscribe")
                && parameterTypes.length == 2
                && parameterTypes[0] == String.class
                && MessageHandler.class.isAssignableFrom(parameterTypes[1]);
    }

    private static boolean isUnsubscribeBySubject(Method method, Object[] args) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        return method.getName().equals("unsubscribe")
                && args != null
                && args.length == 1
                && parameterTypes.length == 1
                && parameterTypes[0] == String.class;
    }
}
