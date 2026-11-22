package app.simplecloud.api.support;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

final class NatsContainer {

    private static final int CLIENT_PORT = 4222;
    private static GenericContainer<?> container;

    private NatsContainer() {
    }

    static synchronized String url() {
        if (container == null) {
            GenericContainer<?> started = new GenericContainer<>(DockerImageName.parse("nats:2.11"))
                    .withExposedPorts(CLIENT_PORT)
                    .waitingFor(Wait.forLogMessage(".*Server is ready.*", 1));
            started.start();
            container = started;
        }
        return "nats://" + container.getHost() + ":" + container.getMappedPort(CLIENT_PORT);
    }
}
