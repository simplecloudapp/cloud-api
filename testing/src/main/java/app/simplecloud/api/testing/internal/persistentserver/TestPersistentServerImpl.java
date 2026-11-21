package app.simplecloud.api.testing.internal.persistentserver;

import app.simplecloud.api.persistentserver.PersistentServer;
import app.simplecloud.api.persistentserver.UpdatePersistentServerRequest;
import app.simplecloud.api.testing.internal.CloudState;
import app.simplecloud.api.testing.persistentserver.TestPersistentServer;
import app.simplecloud.api.testing.server.TestServer;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class TestPersistentServerImpl extends PersistentServerImpl implements TestPersistentServer {

    private final CloudState cloud;

    public TestPersistentServerImpl(CloudState cloud, PersistentServerData data) {
        super(data);
        this.cloud = cloud;
    }

    @Override
    public int getPlayerCount() {
        return cloud.read(data::playerCount);
    }

    @Override
    public TestServer start() {
        return cloud.handle(cloud.startPersistentServer(data.id));
    }

    @Override
    public Optional<TestServer> getServer() {
        return cloud.read(() -> Optional.ofNullable(data.server).<TestServer>map(cloud::handle));
    }

    @Override
    public boolean isRunning() {
        return cloud.read(() -> data.server != null);
    }

    @Override
    public TestPersistentServer update(UpdatePersistentServerRequest request) {
        cloud.updatePersistentServer(data.id, Objects.requireNonNull(request, "request"));
        return this;
    }

    @Override
    public TestPersistentServer setProperty(String key, Object value) {
        cloud.updatePersistentServerProperties(data.id, Collections.singletonMap(key, value));
        return this;
    }

    @Override
    public TestPersistentServer removeProperty(String key) {
        cloud.deletePersistentServerProperties(data.id, List.of(key));
        return this;
    }

    @Override
    public void delete() {
        cloud.deletePersistentServer(data.id);
    }

    @Override
    public boolean exists() {
        return cloud.read(() -> !data.deleted);
    }

    @Override
    public PersistentServer snapshot() {
        return cloud.read(data::snapshot);
    }
}
