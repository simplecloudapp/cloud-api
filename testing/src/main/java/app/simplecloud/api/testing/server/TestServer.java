package app.simplecloud.api.testing.server;

import app.simplecloud.api.server.Server;
import app.simplecloud.api.server.ServerState;
import app.simplecloud.api.server.UpdateServerRequest;
import app.simplecloud.api.testing.group.TestGroup;
import app.simplecloud.api.testing.persistentserver.TestPersistentServer;
import org.jetbrains.annotations.Nullable;

/**
 * A running server in the test cloud. Getters always return the current state, use {@link #snapshot()} for an unchangeable copy.
 *
 * <pre>{@code
 * TestServer server = cloud.createGroup("bedwars").startServer();
 * server.setState(ServerState.INGAME).setPlayerCount(8);
 * server.crash(1, "boom");
 * }</pre>
 */
public interface TestServer extends Server {

    /**
     * Returns the server name, {@code <group>-<numericalId>} or the persistent server name.
     */
    String getName();

    /**
     * Returns the group of this server, or {@code null} if it belongs to a persistent server.
     */
    @Override
    @Nullable TestGroup getGroup();

    /**
     * Returns the persistent server of this server, or {@code null} if it belongs to a group.
     */
    @Override
    @Nullable TestPersistentServer getPersistentServer();

    /**
     * Applies all set fields of the request at once.
     */
    TestServer update(UpdateServerRequest request);

    /**
     * Changes the state and publishes a state change event.
     */
    TestServer setState(ServerState state);

    /**
     * Sets the player count. Joins and disconnects of test players update it automatically.
     */
    TestServer setPlayerCount(int playerCount);

    /**
     * Sets the max player count.
     */
    TestServer setMaxPlayers(int maxPlayers);

    /**
     * Sets a single property.
     */
    TestServer setProperty(String key, Object value);

    /**
     * Removes a single property.
     */
    TestServer removeProperty(String key);

    /**
     * Stops the server gracefully.
     */
    void stop();

    /**
     * Stops the server as crashed without exit code and reason.
     */
    void crash();

    /**
     * Stops the server as crashed.
     */
    void crash(@Nullable Integer exitCode, @Nullable String reason);

    /**
     * Returns whether the server is still running.
     */
    boolean isRunning();

    /**
     * Returns an immutable copy of the current state.
     */
    Server snapshot();
}
