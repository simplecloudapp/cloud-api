package app.simplecloud.api.blueprint;

import app.simplecloud.api.CloudApiException;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * API for managing blueprints.
 *
 * <p>Blueprints describe the server software that groups and persistent servers run. They are referenced
 * through {@code SourceConfig.blueprint(id)}.
 */
public interface BlueprintApi {

    /**
     * Retrieves a blueprint by its unique identifier.
     *
     * @param id the unique ID of the blueprint
     * @return a CompletableFuture that completes with the blueprint, or fails with a
     * {@link CloudApiException} ({@link CloudApiException.Reason#NOT_FOUND}) if it does not exist
     */
    CompletableFuture<Blueprint> getBlueprintById(String id);

    /**
     * Finds a blueprint by its unique identifier.
     *
     * @param id the unique ID of the blueprint
     * @return a CompletableFuture that completes with the blueprint, or an empty Optional if it does not exist
     */
    CompletableFuture<Optional<Blueprint>> findBlueprintById(String id);

    /**
     * Retrieves a blueprint by its name.
     *
     * @param name the name of the blueprint
     * @return a CompletableFuture that completes with the blueprint, or fails with a
     * {@link CloudApiException} ({@link CloudApiException.Reason#NOT_FOUND}) if it does not exist
     */
    CompletableFuture<Blueprint> getBlueprintByName(String name);

    /**
     * Finds a blueprint by its name.
     *
     * @param name the name of the blueprint
     * @return a CompletableFuture that completes with the blueprint, or an empty Optional if it does not exist
     */
    CompletableFuture<Optional<Blueprint>> findBlueprintByName(String name);

    /**
     * Retrieves all blueprints of the network.
     *
     * @return a CompletableFuture that completes with all blueprints
     */
    CompletableFuture<List<Blueprint>> getAllBlueprints();

    /**
     * Creates a new blueprint.
     *
     * <p>The name is required. Without a runtime config the same Java defaults as for inline blueprints are used,
     * without workflow steps {@code internal/setup} is used, and without a server URL it is resolved from the
     * server software and version.
     *
     * @param request the configuration for the new blueprint
     * @return a CompletableFuture that completes with the created blueprint, or fails with a
     * {@link CloudApiException} ({@link CloudApiException.Reason#CONFLICT}) if the name is already taken
     */
    CompletableFuture<Blueprint> createBlueprint(CreateBlueprintRequest request);

    /**
     * Replaces all fields of a blueprint. Defaults are applied like in {@link #createBlueprint(CreateBlueprintRequest)}.
     *
     * @param id      the unique ID of the blueprint to replace
     * @param request the new configuration
     * @return a CompletableFuture that completes with the replaced blueprint
     */
    CompletableFuture<Blueprint> replaceBlueprint(String id, CreateBlueprintRequest request);

    /**
     * Updates an existing blueprint. Only specified fields are changed, the server URL is not resolved again.
     * A runtime config replaces the stored one and needs a type.
     *
     * @param id      the unique ID of the blueprint to update
     * @param request the fields to change
     * @return a CompletableFuture that completes with the updated blueprint
     */
    CompletableFuture<Blueprint> updateBlueprint(String id, UpdateBlueprintRequest request);

    /**
     * Deletes a blueprint.
     *
     * @param id the unique ID of the blueprint to delete
     * @return a CompletableFuture that completes when the blueprint is deleted, or fails with a
     * {@link CloudApiException} ({@link CloudApiException.Reason#CONFLICT}) if a group or persistent server
     * still uses it
     */
    CompletableFuture<Void> deleteBlueprint(String id);
}
