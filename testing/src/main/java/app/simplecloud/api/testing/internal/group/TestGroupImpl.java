package app.simplecloud.api.testing.internal.group;

import app.simplecloud.api.group.Group;
import app.simplecloud.api.group.UpdateGroupRequest;
import app.simplecloud.api.testing.group.TestGroup;
import app.simplecloud.api.testing.internal.CloudState;
import app.simplecloud.api.testing.server.TestServer;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class TestGroupImpl extends GroupImpl implements TestGroup {

    private final CloudState cloud;

    public TestGroupImpl(CloudState cloud, GroupData data) {
        super(data);
        this.cloud = cloud;
    }

    @Override
    public TestServer startServer() {
        return cloud.handle(cloud.startServer(data.id));
    }

    @Override
    public List<TestServer> getServers() {
        return cloud.read(() ->
                cloud.serversOf(data).stream().<TestServer>map(cloud::handle).toList());
    }

    @Override
    public TestGroup update(UpdateGroupRequest request) {
        cloud.updateGroup(data.id, Objects.requireNonNull(request, "request"));
        return this;
    }

    @Override
    public TestGroup setProperty(String key, Object value) {
        cloud.updateGroupProperties(data.id, Collections.singletonMap(key, value));
        return this;
    }

    @Override
    public TestGroup removeProperty(String key) {
        cloud.deleteGroupProperties(data.id, List.of(key));
        return this;
    }

    @Override
    public void delete() {
        cloud.deleteGroup(data.id);
    }

    @Override
    public boolean exists() {
        return cloud.read(() -> !data.deleted);
    }

    @Override
    public Group snapshot() {
        return cloud.read(data::snapshot);
    }
}
