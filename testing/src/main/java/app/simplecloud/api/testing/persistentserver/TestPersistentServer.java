package app.simplecloud.api.testing.persistentserver;

import app.simplecloud.api.persistentserver.PersistentServer;
import app.simplecloud.api.persistentserver.UpdatePersistentServerRequest;
import app.simplecloud.api.testing.server.TestServer;

import java.util.Optional;

public interface TestPersistentServer extends PersistentServer {

    TestServer start();

    Optional<TestServer> getServer();

    boolean isRunning();

    TestPersistentServer update(UpdatePersistentServerRequest request);

    TestPersistentServer setProperty(String key, Object value);

    TestPersistentServer removeProperty(String key);

    void delete();

    boolean exists();

    PersistentServer snapshot();
}
