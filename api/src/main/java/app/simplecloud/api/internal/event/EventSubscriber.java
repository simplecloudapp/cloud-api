package app.simplecloud.api.internal.event;

import app.simplecloud.api.event.Subscription;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;

import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class EventSubscriber {
    private static final Logger LOGGER = Logger.getLogger(EventSubscriber.class.getName());

    private final String networkId;
    private final Dispatcher dispatcher;

    public EventSubscriber(Connection natsConnection, String networkId) {
        this.networkId = networkId;
        this.dispatcher = natsConnection.createDispatcher(null);
    }

    public <E> Subscription subscribe(String event, Parser<E> parser, Consumer<? super E> handler) {
        String subject = networkId + ".event." + event;
        io.nats.client.Subscription subscription = dispatcher.subscribe(subject, message -> {
            try {
                handler.accept(parser.parse(message.getData()));
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error handling event " + subject, e);
            }
        });
        return new SubscriptionImpl(dispatcher, subscription);
    }

    @FunctionalInterface
    public interface Parser<E> {
        E parse(byte[] data) throws Exception;
    }
}
