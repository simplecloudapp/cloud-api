package app.simplecloud.api.testing.internal.server;

import app.simplecloud.api.server.Server;
import app.simplecloud.api.server.ServerState;
import app.simplecloud.api.testing.group.TestGroup;
import app.simplecloud.api.testing.internal.CloudState;
import app.simplecloud.api.testing.internal.group.TestGroupImpl;
import app.simplecloud.api.testing.server.TestServer;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class TestServerImpl extends ServerImpl implements TestServer {

    private final CloudState cloud;
    private final TestGroupImpl group;

    public TestServerImpl(CloudState cloud, ServerData data, TestGroupImpl group) {
        super(data, group);
        this.cloud = cloud;
        this.group = group;
    }

    @Override
    public TestGroup getGroup() {
        return group;
    }

    @Override
    public TestServer setState(ServerState state) {
        Objects.requireNonNull(state, "state");
        cloud.modifyServer(data.id, server -> server.state = state);
        return this;
    }

    @Override
    public TestServer setPlayerCount(int playerCount) {
        if (playerCount < 0) {
            throw new IllegalArgumentException("playerCount must be >= 0");
        }
        cloud.modifyServer(data.id, server -> server.playerCount = playerCount);
        return this;
    }

    @Override
    public TestServer setMaxPlayers(int maxPlayers) {
        if (maxPlayers < 0) {
            throw new IllegalArgumentException("maxPlayers must be >= 0");
        }
        cloud.modifyServer(data.id, server -> server.maxPlayers = maxPlayers);
        return this;
    }

    @Override
    public TestServer setProperty(String key, Object value) {
        cloud.updateServerProperties(data.id, Collections.singletonMap(key, value));
        return this;
    }

    @Override
    public TestServer removeProperty(String key) {
        cloud.deleteServerProperties(data.id, List.of(key));
        return this;
    }

    @Override
    public void stop() {
        cloud.stopServer(data.id, false, null, null);
    }

    @Override
    public void crash() {
        crash(null, null);
    }

    @Override
    public void crash(@Nullable Integer exitCode, @Nullable String reason) {
        cloud.stopServer(data.id, true, exitCode, reason);
    }

    @Override
    public boolean isRunning() {
        return cloud.read(() -> data.running);
    }

    @Override
    public Server snapshot() {
        return cloud.read(data::snapshot);
    }
}
