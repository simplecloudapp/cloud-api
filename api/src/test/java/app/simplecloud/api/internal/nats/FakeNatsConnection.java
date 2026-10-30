package app.simplecloud.api.internal.nats;

import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Message;
import io.nats.client.MessageHandler;
import io.nats.client.impl.NatsMessage;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A NATS connection for tests. Requests are answered by a handler, subscriptions and publishes are recorded.
 */
public final class FakeNatsConnection {

    @FunctionalInterface
    public interface RequestHandler {
        Message request(String subject, byte[] data, Duration timeout) throws Exception;
    }

    private final RequestHandler requestHandler;
    private final Map<String, MessageHandler> subscriptions = new ConcurrentHashMap<>();
    private final List<Message> published = new CopyOnWriteArrayList<>();
    private final Connection connection;

    private FakeNatsConnection(RequestHandler requestHandler) {
        this.requestHandler = requestHandler;
        this.connection = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> handleConnectionCall(method.getName(), args)
        );
    }

    public static FakeNatsConnection answering(RequestHandler requestHandler) {
        return new FakeNatsConnection(requestHandler);
    }

    public static FakeNatsConnection withoutRequests() {
        return new FakeNatsConnection((subject, data, timeout) -> {
            throw new AssertionError("Unexpected request on " + subject);
        });
    }

    public static Message reply(byte[] data) {
        return NatsMessage.builder().subject("reply").data(data).build();
    }

    public Connection connection() {
        return connection;
    }

    /**
     * Delivers a message to the handler subscribed to {@code subscribedSubject}.
     */
    public void deliver(String subscribedSubject, Message message) throws InterruptedException {
        MessageHandler handler = subscriptions.get(subscribedSubject);
        if (handler == null) {
            throw new AssertionError("No subscription on " + subscribedSubject);
        }
        handler.onMessage(message);
    }

    public List<Message> published() {
        return published;
    }

    private Object handleConnectionCall(String methodName, Object[] args) throws Exception {
        if (methodName.equals("request") && args.length == 3 && args[2] instanceof Duration timeout) {
            return requestHandler.request((String) args[0], (byte[]) args[1], timeout);
        }
        if (methodName.equals("publish") && args.length == 2 && args[1] instanceof byte[] data) {
            Message message = NatsMessage.builder().subject((String) args[0]).data(data).build();
            published.add(message);
            return null;
        }
        if (methodName.equals("createDispatcher")) {
            return createDispatcher();
        }
        throw new AssertionError("Unexpected connection call: " + methodName);
    }

    private Dispatcher createDispatcher() {
        return (Dispatcher) Proxy.newProxyInstance(
                Dispatcher.class.getClassLoader(),
                new Class<?>[]{Dispatcher.class},
                (dispatcher, method, args) -> handleDispatcherCall(dispatcher, method.getName(), args)
        );
    }

    private Object handleDispatcherCall(Object dispatcher, String methodName, Object[] args) {
        if (methodName.equals("subscribe") && args.length == 2 && args[1] instanceof MessageHandler handler) {
            subscriptions.put((String) args[0], handler);
            return null;
        }
        if (methodName.equals("unsubscribe") && args.length == 1 && args[0] instanceof String subject) {
            subscriptions.remove(subject);
            return dispatcher;
        }
        throw new AssertionError("Unexpected dispatcher call: " + methodName);
    }
}
