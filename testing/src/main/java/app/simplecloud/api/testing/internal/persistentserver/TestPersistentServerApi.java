package app.simplecloud.api.testing.internal.persistentserver;

import app.simplecloud.api.group.DeploymentHost;
import app.simplecloud.api.group.Group;
import app.simplecloud.api.group.ScalingConfig;
import app.simplecloud.api.persistentserver.CreatePersistentServerRequest;
import app.simplecloud.api.workflow.WorkflowExecutionResult;
import app.simplecloud.api.persistentserver.PersistentServer;
import app.simplecloud.api.persistentserver.PersistentServerApi;
import app.simplecloud.api.persistentserver.PersistentServerQuery;
import app.simplecloud.api.persistentserver.UpdatePersistentServerRequest;
import app.simplecloud.api.testing.internal.CloudState;
import app.simplecloud.api.testing.internal.Futures;
import app.simplecloud.api.testing.internal.group.GroupData;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class TestPersistentServerApi implements PersistentServerApi {

    private final CloudState cloud;

    public TestPersistentServerApi(CloudState cloud) {
        this.cloud = cloud;
    }

    @Override
    public CompletableFuture<PersistentServer> getPersistentServerById(String id) {
        return Futures.call(() -> findById(id)
                .orElseThrow(() -> CloudState.notFound("Persistent server with ID '" + id + "' not found")));
    }

    @Override
    public CompletableFuture<Optional<PersistentServer>> findPersistentServerById(String id) {
        return Futures.call(() -> findById(id));
    }

    @Override
    public CompletableFuture<PersistentServer> getPersistentServerByName(String name) {
        return Futures.call(() -> findByName(name)
                .orElseThrow(() -> CloudState.notFound("Persistent server '" + name + "' not found")));
    }

    @Override
    public CompletableFuture<Optional<PersistentServer>> findPersistentServerByName(String name) {
        return Futures.call(() -> findByName(name));
    }

    @Override
    public CompletableFuture<List<PersistentServer>> getAllPersistentServers(@Nullable PersistentServerQuery query) {
        return Futures.call(() -> cloud.read(() -> cloud.persistentServers(query).stream()
                .map(PersistentServerData::snapshot)
                .toList()));
    }

    @Override
    public CompletableFuture<PersistentServer> createPersistentServer(CreatePersistentServerRequest request) {
        return Futures.call(() -> {
            PersistentServerData server = cloud.createPersistentServer(request);
            return cloud.read(server::snapshot);
        });
    }

    @Override
    public CompletableFuture<PersistentServer> updatePersistentServer(String id, UpdatePersistentServerRequest request) {
        return Futures.call(() -> {
            PersistentServerData server = cloud.updatePersistentServer(id, request);
            return cloud.read(server::snapshot);
        });
    }

    @Override
    public CompletableFuture<Void> deletePersistentServer(String id) {
        return Futures.run(() -> cloud.deletePersistentServer(id));
    }

    @Override
    public CompletableFuture<Map<String, Object>> updatePersistentServerProperties(String id, Map<String, Object> properties) {
        return Futures.call(() -> cloud.updatePersistentServerProperties(id, properties));
    }

    @Override
    public CompletableFuture<PersistentServer> replacePersistentServer(String id, CreatePersistentServerRequest request) {
        return Futures.call(() -> {
            PersistentServerData server = cloud.replacePersistentServer(id, request);
            return cloud.read(server::snapshot);
        });
    }

    @Override
    public CompletableFuture<Map<String, Object>> setPersistentServerProperties(String id, Map<String, Object> properties) {
        return Futures.call(() -> cloud.setPersistentServerProperties(id, properties));
    }

    @Override
    public CompletableFuture<WorkflowExecutionResult> executeWorkflow(String id, String workflowId) {
        return Futures.call(() -> cloud.executePersistentServerWorkflow(id, workflowId));
    }

    @Override
    public CompletableFuture<Group> convertToGroup(String id, List<DeploymentHost> hosts, @Nullable ScalingConfig scaling) {
        return Futures.call(() -> {
            GroupData group = cloud.convertPersistentServerToGroup(id, hosts, scaling);
            return cloud.read(group::snapshot);
        });
    }

    @Override
    public CompletableFuture<Map<String, Object>> deletePersistentServerProperties(String id, List<String> keys) {
        return Futures.call(() -> cloud.deletePersistentServerProperties(id, keys));
    }

    private Optional<PersistentServer> findById(String id) {
        return cloud.read(() -> cloud.persistentServerById(id).map(PersistentServerData::snapshot));
    }

    private Optional<PersistentServer> findByName(String name) {
        return cloud.read(() -> cloud.persistentServerByName(name).map(PersistentServerData::snapshot));
    }
}
