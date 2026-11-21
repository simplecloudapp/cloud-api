package app.simplecloud.api.testing.group;

import app.simplecloud.api.group.Group;
import app.simplecloud.api.group.UpdateGroupRequest;
import app.simplecloud.api.testing.server.TestServer;

import java.util.List;
import java.util.function.Consumer;

public interface TestGroup extends Group {

    TestServer startServer();

    List<TestServer> getServers();

    TestGroup update(Consumer<UpdateGroupRequest.Builder> configurer);

    TestGroup setProperty(String key, Object value);

    TestGroup removeProperty(String key);

    void delete();

    boolean exists();

    Group snapshot();
}
