package app.simplecloud.api.testing.controller;

import app.simplecloud.api.group.GroupStartQueue;
import app.simplecloud.api.server.ServerState;
import app.simplecloud.api.testing.server.TestServer;

import java.util.List;

public interface TestController {

    TestController autoStart(boolean autoStart);

    boolean isAutoStart();

    TestController startState(ServerState state);

    ServerState startState();

    TestController scaling(boolean scaling);

    boolean isScaling();

    List<TestServer> scale();

    GroupStartQueue startQueue();

    List<TestServer> processStartQueue();

    void failQueuedStarts(String groupName, String reason);
}
