package app.simplecloud.api.internal.event;

import app.simplecloud.api.event.Subscription;

public class SubscriptionImpl implements Subscription {

    private final io.nats.client.Subscription natsSubscription;
    private volatile boolean unsubscribed = false;

    public SubscriptionImpl(io.nats.client.Subscription natsSubscription) {
        this.natsSubscription = natsSubscription;
    }

    @Override
    public void unsubscribe() {
        if (!unsubscribed) {
            synchronized (this) {
                if (!unsubscribed) {
                    natsSubscription.unsubscribe();
                    unsubscribed = true;
                }
            }
        }
    }

}