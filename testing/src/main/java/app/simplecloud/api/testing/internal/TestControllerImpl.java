package app.simplecloud.api.testing.internal;

import app.simplecloud.api.group.GroupStartQueue;
import app.simplecloud.api.server.ServerState;
import app.simplecloud.api.testing.controller.TestController;
import app.simplecloud.api.testing.internal.server.ServerData;
import app.simplecloud.api.testing.server.TestServer;

import java.util.List;

final class TestControllerImpl implements TestController {

    private final CloudState cloud;

    TestControllerImpl(CloudState cloud) {
        this.cloud = cloud;
    }

    @Override
    public TestController autoStart(boolean autoStart) {
        cloud.autoStart(autoStart);
        return this;
    }

    @Override
    public boolean isAutoStart() {
        return cloud.isAutoStart();
    }

    @Override
    public TestController startState(ServerState state) {
        cloud.startState(state);
        return this;
    }

    @Override
    public ServerState startState() {
        return cloud.startState();
    }

    @Override
    public TestController scaling(boolean scaling) {
        cloud.scaling(scaling);
        return this;
    }

    @Override
    public boolean isScaling() {
        return cloud.isScaling();
    }

    @Override
    public List<TestServer> scale() {
        return handles(cloud.scale());
    }

    @Override
    public GroupStartQueue startQueue() {
        return cloud.startQueue();
    }

    @Override
    public List<TestServer> processStartQueue() {
        return handles(cloud.processStartQueue());
    }

    @Override
    public void failQueuedStarts(String groupName, String reason) {
        cloud.failQueuedStarts(groupName, reason);
    }

    private List<TestServer> handles(List<ServerData> servers) {
        return servers.stream()
                .<TestServer>map(cloud::handle)
                .toList();
    }
}
