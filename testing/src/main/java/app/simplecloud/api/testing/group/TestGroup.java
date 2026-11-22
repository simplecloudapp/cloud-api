package app.simplecloud.api.testing.group;

import app.simplecloud.api.group.Group;
import app.simplecloud.api.group.UpdateGroupRequest;
import app.simplecloud.api.testing.server.TestServer;
import java.util.List;

/**
 * A group in the test cloud. Getters always return the current state, use {@link #snapshot()}
 * for an unchangeable copy.
 *
 * <pre>{@code
 * TestGroup lobby = cloud.createGroup("lobby");
 * TestServer first = lobby.startServer();
 * lobby.update(UpdateGroupRequest.builder().maxPlayers(100).build());
 * }</pre>
 */
public interface TestGroup extends Group {

    /**
     * Starts a new server immediately, ignoring auto start and the start queue.
     */
    TestServer startServer();

    /**
     * Returns the running servers of this group sorted by numerical id.
     */
    List<TestServer> getServers();

    /**
     * Applies all set fields of the request at once. Properties and tags are replaced.
     */
    TestGroup update(UpdateGroupRequest request);

    /**
     * Sets a single property.
     */
    TestGroup setProperty(String key, Object value);

    /**
     * Removes a single property.
     */
    TestGroup removeProperty(String key);

    /**
     * Deletes the group and stops all its servers.
     */
    void delete();

    /**
     * Returns whether the group still exists.
     */
    boolean exists();

    /**
     * Returns an immutable copy of the current state.
     */
    Group snapshot();
}
