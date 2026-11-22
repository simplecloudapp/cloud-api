package app.simplecloud.api.testing.internal.server;

import app.simplecloud.api.server.Server;
import app.simplecloud.api.server.ServerApi;
import app.simplecloud.api.server.ServerQuery;
import app.simplecloud.api.server.UpdateServerRequest;
import app.simplecloud.api.testing.internal.CloudState;
import app.simplecloud.api.testing.internal.Futures;
import app.simplecloud.api.workflow.ServerWorkflowResult;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;

public final class TestServerApi implements ServerApi {

    private final CloudState cloud;
    private final Supplier<String> currentServerId;

    public TestServerApi(CloudState cloud, Supplier<String> currentServerId) {
        this.cloud = cloud;
        this.currentServerId = currentServerId;
    }

    @Override
    public CompletableFuture<Server> getServerById(String id) {
        return Futures.call(
                () -> findById(id).orElseThrow(() -> CloudState.notFound("Server with ID '" + id + "' not found")));
    }

    @Override
    public CompletableFuture<Optional<Server>> findServerById(String id) {
        return Futures.call(() -> findById(id));
    }

    @Override
    public CompletableFuture<Server> getServerByNumericalId(String groupName, int numericalId) {
        return Futures.call(() -> findByNumericalId(groupName, numericalId)
                .orElseThrow(() -> CloudState.notFound("Server '" + groupName + "-" + numericalId + "' not found")));
    }

    @Override
    public CompletableFuture<Optional<Server>> findServerByNumericalId(String groupName, int numericalId) {
        return Futures.call(() -> findByNumericalId(groupName, numericalId));
    }

    @Override
    public CompletableFuture<List<Server>> getServersByGroup(String groupName) {
        return getAllServers(ServerQuery.create().filterByServerGroupName(groupName));
    }

    @Override
    public CompletableFuture<List<Server>> getAllServers(@Nullable ServerQuery query) {
        return Futures.call(() -> cloud.read(
                () -> cloud.servers(query).stream().map(ServerData::snapshot).toList()));
    }

    @Override
    public CompletableFuture<Void> stopServer(String id) {
        return Futures.run(() -> cloud.stopServer(id, false, null, null));
    }

    @Override
    public CompletableFuture<Server> updateServer(String id, UpdateServerRequest request) {
        return Futures.call(() -> {
            ServerData server = cloud.updateServer(id, request);
            return cloud.read(server::snapshot);
        });
    }

    @Override
    public CompletableFuture<Server> getCurrentServer() {
        String id = currentServerId.get();
        if (id == null) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "No current server set, use TestCloud.runAs(server) or TestCloud.newApi(server)"));
        }
        return getServerById(id);
    }

    @Override
    public CompletableFuture<Map<String, Object>> updateServerProperties(String id, Map<String, Object> properties) {
        return Futures.call(() -> cloud.updateServerProperties(id, properties));
    }

    @Override
    public CompletableFuture<Void> sendCommand(String id, String command) {
        return Futures.run(() -> cloud.sendCommand(id, command));
    }

    @Override
    public CompletableFuture<ServerWorkflowResult> executeWorkflow(String id, String workflowId) {
        return Futures.call(() -> cloud.executeServerWorkflow(id, workflowId));
    }

    @Override
    public CompletableFuture<Map<String, Object>> setServerProperties(String id, Map<String, Object> properties) {
        return Futures.call(() -> cloud.setServerProperties(id, properties));
    }

    @Override
    public CompletableFuture<Map<String, Object>> deleteServerProperties(String id, List<String> keys) {
        return Futures.call(() -> cloud.deleteServerProperties(id, keys));
    }

    private Optional<Server> findById(String id) {
        return cloud.read(() -> cloud.serverById(id).map(ServerData::snapshot));
    }

    private Optional<Server> findByNumericalId(String groupName, int numericalId) {
        return cloud.read(
                () -> cloud.serverByNumericalId(groupName, numericalId).map(ServerData::snapshot));
    }
}
