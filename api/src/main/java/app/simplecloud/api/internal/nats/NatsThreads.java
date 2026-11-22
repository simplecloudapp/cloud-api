package app.simplecloud.api.internal.nats;

import io.nats.client.Options;

/**
 * The threads a NATS connection runs on. They outlive a single connection, because a failover reconnect
 * opens a new one.
 */
interface NatsThreads {

    NatsThreads JNATS_DEFAULT = new NatsThreads() {
        @Override
        public void applyTo(Options.Builder optionsBuilder) {
        }

        @Override
        public void shutdown() {
        }
    };

    void applyTo(Options.Builder optionsBuilder);

    void shutdown();
}
