package app.simplecloud.api.testing.internal.group;

import app.simplecloud.api.group.CreateGroupRequest;
import app.simplecloud.api.group.Group;
import app.simplecloud.api.group.GroupApi;
import app.simplecloud.api.group.GroupQuery;
import app.simplecloud.api.group.GroupStartQueue;
import app.simplecloud.api.group.UpdateGroupRequest;
import app.simplecloud.api.testing.internal.CloudState;
import app.simplecloud.api.testing.internal.Futures;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class TestGroupApi implements GroupApi {

    private final CloudState cloud;

    public TestGroupApi(CloudState cloud) {
        this.cloud = cloud;
    }

    @Override
    public CompletableFuture<GroupStartQueue> getServerStartQueue() {
        return Futures.call(cloud::startQueue);
    }

    @Override
    public CompletableFuture<Void> requestServerStart(String serverGroupId) {
        return Futures.run(() -> cloud.requestServerStart(serverGroupId));
    }

    @Override
    public CompletableFuture<Void> clearServerStartQueue(String serverGroupId) {
        return Futures.run(() -> cloud.clearStartQueue(serverGroupId));
    }

    @Override
    public CompletableFuture<Group> getGroupByName(String name) {
        return Futures.call(() -> findByName(name)
                .orElseThrow(() -> CloudState.notFound("Group '" + name + "' not found")));
    }

    @Override
    public CompletableFuture<Optional<Group>> findGroupByName(String name) {
        return Futures.call(() -> findByName(name));
    }

    @Override
    public CompletableFuture<Group> getGroupById(String id) {
        return Futures.call(() -> findById(id)
                .orElseThrow(() -> CloudState.notFound("Group with ID '" + id + "' not found")));
    }

    @Override
    public CompletableFuture<Optional<Group>> findGroupById(String id) {
        return Futures.call(() -> findById(id));
    }

    @Override
    public CompletableFuture<List<Group>> getAllGroups(@Nullable GroupQuery query) {
        return Futures.call(() -> cloud.read(() -> cloud.groups(query).stream()
                .map(GroupData::snapshot)
                .toList()));
    }

    @Override
    public CompletableFuture<Group> createGroup(CreateGroupRequest request) {
        return Futures.call(() -> {
            GroupData group = cloud.createGroup(request);
            return cloud.read(group::snapshot);
        });
    }

    @Override
    public CompletableFuture<Group> updateGroup(String id, UpdateGroupRequest request) {
        return Futures.call(() -> {
            GroupData group = cloud.updateGroup(id, request);
            return cloud.read(group::snapshot);
        });
    }

    @Override
    public CompletableFuture<Void> deleteGroup(String id) {
        return Futures.run(() -> cloud.deleteGroup(id));
    }

    @Override
    public CompletableFuture<Map<String, Object>> updateGroupProperties(String id, Map<String, Object> properties) {
        return Futures.call(() -> cloud.updateGroupProperties(id, properties));
    }

    @Override
    public CompletableFuture<Map<String, Object>> deleteGroupProperties(String id, List<String> keys) {
        return Futures.call(() -> cloud.deleteGroupProperties(id, keys));
    }

    private Optional<Group> findByName(String name) {
        return cloud.read(() -> cloud.groupByName(name).map(GroupData::snapshot));
    }

    private Optional<Group> findById(String id) {
        return cloud.read(() -> cloud.groupById(id).map(GroupData::snapshot));
    }
}
