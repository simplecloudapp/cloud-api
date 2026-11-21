package app.simplecloud.api.testing.internal.blueprint;

import app.simplecloud.api.blueprint.Blueprint;
import app.simplecloud.api.blueprint.BlueprintApi;
import app.simplecloud.api.blueprint.CreateBlueprintRequest;
import app.simplecloud.api.blueprint.UpdateBlueprintRequest;
import app.simplecloud.api.testing.internal.CloudState;
import app.simplecloud.api.testing.internal.Futures;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class TestBlueprintApi implements BlueprintApi {

    private final CloudState cloud;

    public TestBlueprintApi(CloudState cloud) {
        this.cloud = cloud;
    }

    @Override
    public CompletableFuture<Blueprint> getBlueprintById(String id) {
        return Futures.call(() -> findById(id)
                .orElseThrow(() -> CloudState.notFound("Blueprint with ID '" + id + "' not found")));
    }

    @Override
    public CompletableFuture<Optional<Blueprint>> findBlueprintById(String id) {
        return Futures.call(() -> findById(id));
    }

    @Override
    public CompletableFuture<Blueprint> getBlueprintByName(String name) {
        return Futures.call(() -> findByName(name)
                .orElseThrow(() -> CloudState.notFound("Blueprint '" + name + "' not found")));
    }

    @Override
    public CompletableFuture<Optional<Blueprint>> findBlueprintByName(String name) {
        return Futures.call(() -> findByName(name));
    }

    @Override
    public CompletableFuture<List<Blueprint>> getAllBlueprints() {
        return Futures.call(() -> cloud.read(() -> cloud.blueprints().stream()
                .map(BlueprintData::snapshot)
                .toList()));
    }

    @Override
    public CompletableFuture<Blueprint> createBlueprint(CreateBlueprintRequest request) {
        return Futures.call(() -> {
            BlueprintData blueprint = cloud.createBlueprint(request);
            return cloud.read(blueprint::snapshot);
        });
    }

    @Override
    public CompletableFuture<Blueprint> replaceBlueprint(String id, CreateBlueprintRequest request) {
        return Futures.call(() -> {
            BlueprintData blueprint = cloud.replaceBlueprint(id, request);
            return cloud.read(blueprint::snapshot);
        });
    }

    @Override
    public CompletableFuture<Blueprint> updateBlueprint(String id, UpdateBlueprintRequest request) {
        return Futures.call(() -> {
            BlueprintData blueprint = cloud.updateBlueprint(id, request);
            return cloud.read(blueprint::snapshot);
        });
    }

    @Override
    public CompletableFuture<Void> deleteBlueprint(String id) {
        return Futures.run(() -> cloud.deleteBlueprint(id));
    }

    private Optional<Blueprint> findById(String id) {
        return cloud.read(() -> cloud.blueprintById(id).map(BlueprintData::snapshot));
    }

    private Optional<Blueprint> findByName(String name) {
        return cloud.read(() -> cloud.blueprintByName(name).map(BlueprintData::snapshot));
    }
}
