package app.simplecloud.api.blueprint;

import app.simplecloud.api.CloudApiException;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * API for managing blueprints.
 */
public interface BlueprintApi {

    /**
     * Retrieves all blueprints of the network.
     *
     * @return a future that completes with all blueprints
     */
    CompletableFuture<List<Blueprint>> getAllBlueprints();

    /**
     * Retrieves a blueprint by its ID.
     *
     * @param id the blueprint ID
     * @return a future that completes with the blueprint, or fails with a
     * {@link CloudApiException} ({@link CloudApiException.Reason#NOT_FOUND}) if it does not exist
     */
    CompletableFuture<Blueprint> getBlueprintById(String id);

    /**
     * Finds a blueprint by its ID.
     *
     * @param id the blueprint ID
     * @return a future that completes with the blueprint, or an empty Optional if it does not exist
     */
    CompletableFuture<Optional<Blueprint>> findBlueprintById(String id);

    /**
     * Retrieves a blueprint by its name.
     *
     * @param name the blueprint name
     * @return a future that completes with the blueprint, or fails with a
     * {@link CloudApiException} ({@link CloudApiException.Reason#NOT_FOUND}) if it does not exist
     */
    CompletableFuture<Blueprint> getBlueprintByName(String name);

    /**
     * Finds a blueprint by its name.
     *
     * @param name the blueprint name
     * @return a future that completes with the blueprint, or an empty Optional if it does not exist
     */
    CompletableFuture<Optional<Blueprint>> findBlueprintByName(String name);

    /**
     * Creates a blueprint.
     *
     * <p>Without a server URL, the URL is resolved from the software manifest, the same way as for inline
     * blueprints of new groups and persistent servers.
     *
     * @param name the name of the new blueprint
     * @param request the blueprint configuration
     * @return a future that completes with the created blueprint
     */
    CompletableFuture<Blueprint> createBlueprint(String name, CreateBlueprintRequest request);

    /**
     * Updates a blueprint. Fields left {@code null} in the request stay unchanged.
     *
     * @param id the blueprint ID
     * @param request the fields to change
     * @return a future that completes with the updated blueprint
     */
    CompletableFuture<Blueprint> updateBlueprint(String id, UpdateBlueprintRequest request);

    /**
     * Deletes a blueprint.
     *
     * <p>The controller refuses to delete a blueprint that a group or persistent server still uses; the
     * future then fails with {@link CloudApiException.Reason#CONFLICT}.
     *
     * @param id the blueprint ID
     * @return a future that completes when the blueprint is deleted
     */
    CompletableFuture<Void> deleteBlueprint(String id);
}
