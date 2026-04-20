package app.simplecloud.api.blueprint;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * API for managing blueprints.
 *
 * <p>Blueprints define reusable runtime and software templates that groups and servers can use
 * as their base source configuration.
 */
public interface BlueprintApi {

    /**
     * Retrieves a blueprint by its unique identifier.
     *
     * @param id the blueprint ID
     * @return a CompletableFuture that completes with the blueprint, or fails if not found
     */
    CompletableFuture<Blueprint> getBlueprintById(String id);

    /**
     * Retrieves a blueprint by its name.
     *
     * @param name the blueprint name
     * @return a CompletableFuture that completes with the blueprint, or fails if not found
     */
    CompletableFuture<Blueprint> getBlueprintByName(String name);

    /**
     * Retrieves all blueprints.
     *
     * @return a CompletableFuture that completes with all available blueprints
     */
    CompletableFuture<List<Blueprint>> getAllBlueprints();

    /**
     * Creates a new blueprint.
     *
     * @param request the blueprint configuration to create
     * @return a CompletableFuture that completes with the created blueprint
     */
    CompletableFuture<Blueprint> createBlueprint(CreateBlueprintRequest request);

    /**
     * Updates an existing blueprint.
     *
     * @param id the blueprint ID to update
     * @param request the fields to update
     * @return a CompletableFuture that completes with the updated blueprint
     */
    CompletableFuture<Blueprint> updateBlueprint(String id, UpdateBlueprintRequest request);

    /**
     * Deletes a blueprint.
     *
     * @param id the blueprint ID to delete
     * @return a CompletableFuture that completes when the blueprint has been deleted
     */
    CompletableFuture<Void> deleteBlueprint(String id);
}
