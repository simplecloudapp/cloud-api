package app.simplecloud.api.internal.event;

import app.simplecloud.api.event.Subscription;
import io.nats.client.Dispatcher;

public class SubscriptionImpl implements Subscription {

    private final Dispatcher dispatcher;
    private final io.nats.client.Subscription natsSubscription;
    private volatile boolean unsubscribed = false;

    public SubscriptionImpl(Dispatcher dispatcher, io.nats.client.Subscription natsSubscription) {
        this.dispatcher = dispatcher;
        this.natsSubscription = natsSubscription;
    }

    @Override
    public void unsubscribe() {
        if (!unsubscribed) {
            synchronized (this) {
                if (!unsubscribed) {
                    dispatcher.unsubscribe(natsSubscription);
                    unsubscribed = true;
                }
            }
        }
    }
}
