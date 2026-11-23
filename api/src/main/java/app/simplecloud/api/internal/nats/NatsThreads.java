package app.simplecloud.api.internal.nats;

import io.nats.client.Options;

/**
 * The threads a NATS connection runs on. An implementation that supplies executors keeps them across
 * connections, because a failover reconnect opens a new connection.
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
