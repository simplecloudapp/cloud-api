package app.simplecloud.api.testing.persistentserver;

import app.simplecloud.api.persistentserver.PersistentServer;
import app.simplecloud.api.persistentserver.UpdatePersistentServerRequest;
import app.simplecloud.api.testing.server.TestServer;
import java.util.Optional;

/**
 * A persistent server in the test cloud. At most one server runs for it at a time.
 *
 * <pre>{@code
 * TestPersistentServer survival = cloud.createPersistentServer("survival");
 * TestServer server = survival.start();
 * server.stop();
 * }</pre>
 */
public interface TestPersistentServer extends PersistentServer {

    /**
     * Starts the server.
     *
     * @throws app.simplecloud.api.CloudApiException with reason {@code CONFLICT} if it is already running
     */
    TestServer start();

    /**
     * Returns the running server, if any.
     */
    Optional<TestServer> getServer();

    /**
     * Returns whether a server is running.
     */
    boolean isRunning();

    /**
     * Applies all set fields of the request at once. Properties and tags are replaced.
     */
    TestPersistentServer update(UpdatePersistentServerRequest request);

    /**
     * Sets a single property.
     */
    TestPersistentServer setProperty(String key, Object value);

    /**
     * Removes a single property.
     */
    TestPersistentServer removeProperty(String key);

    /**
     * Deletes the persistent server and stops its running server.
     */
    void delete();

    /**
     * Returns whether the persistent server still exists.
     */
    boolean exists();

    /**
     * Returns an immutable copy of the current state.
     */
    PersistentServer snapshot();
}
