package app.simplecloud.api.testing.internal;

import app.simplecloud.api.group.GroupStartQueue;
import app.simplecloud.api.testing.controller.TestController;
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
    public GroupStartQueue startQueue() {
        return cloud.startQueue();
    }

    @Override
    public List<TestServer> processStartQueue() {
        return cloud.processStartQueue().stream()
                .<TestServer>map(cloud::handle)
                .toList();
    }

    @Override
    public void failQueuedStarts(String groupName, String reason) {
        cloud.failQueuedStarts(groupName, reason);
    }
}
