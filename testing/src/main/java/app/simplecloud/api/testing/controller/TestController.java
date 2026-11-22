package app.simplecloud.api.testing.controller;

import app.simplecloud.api.group.GroupStartQueue;
import app.simplecloud.api.server.ServerState;
import app.simplecloud.api.testing.server.TestServer;
import java.util.List;

/**
 * Controls how the simulated controller starts servers.
 *
 * <pre>{@code
 * cloud.controller().autoStart(false);
 * cloud.api().group().startServer("lobby").join();
 * cloud.controller().processStartQueue();
 * }</pre>
 */
public interface TestController {

    /**
     * Whether start requests from the api start a server immediately. If disabled they are queued. On by default.
     */
    TestController autoStart(boolean autoStart);

    /**
     * Returns whether start requests start a server immediately.
     */
    boolean isAutoStart();

    /**
     * Sets the state new servers start in. Defaults to {@link ServerState#AVAILABLE}.
     *
     * @throws IllegalArgumentException if servers cannot start in that state
     */
    TestController startState(ServerState state);

    /**
     * Returns the state new servers start in.
     */
    ServerState startState();

    /**
     * Whether groups and persistent servers are scaled automatically after every change. Off by default.
     */
    TestController scaling(boolean scaling);

    /**
     * Returns whether automatic scaling is enabled.
     */
    boolean isScaling();

    /**
     * Runs one scaling round now and returns the started servers.
     */
    List<TestServer> scale();

    /**
     * Returns the queued server starts.
     */
    GroupStartQueue startQueue();

    /**
     * Starts all pending queued servers and returns them.
     */
    List<TestServer> processStartQueue();

    /**
     * Marks all pending queued starts of a group as failed.
     */
    void failQueuedStarts(String groupName, String reason);
}
