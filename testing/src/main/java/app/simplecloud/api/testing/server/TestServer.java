package app.simplecloud.api.testing.server;

import app.simplecloud.api.server.Server;
import app.simplecloud.api.server.ServerState;
import app.simplecloud.api.server.UpdateServerRequest;
import app.simplecloud.api.testing.group.TestGroup;
import app.simplecloud.api.testing.persistentserver.TestPersistentServer;
import org.jetbrains.annotations.Nullable;

public interface TestServer extends Server {

    String getName();

    @Override
    @Nullable TestGroup getGroup();

    @Override
    @Nullable TestPersistentServer getPersistentServer();

    TestServer update(UpdateServerRequest request);

    TestServer setState(ServerState state);

    TestServer setPlayerCount(int playerCount);

    TestServer setMaxPlayers(int maxPlayers);

    TestServer setProperty(String key, Object value);

    TestServer removeProperty(String key);

    void stop();

    void crash();

    void crash(@Nullable Integer exitCode, @Nullable String reason);

    boolean isRunning();

    Server snapshot();
}
