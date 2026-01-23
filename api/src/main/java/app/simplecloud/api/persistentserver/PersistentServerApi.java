package app.simplecloud.api.persistentserver;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * API for managing persistent servers.
 *
 * <p>Persistent servers are long-lived server instances that maintain state across restarts.
 * Unlike servers spawned from a group, persistent servers don't have scaling or deployment
 * configuration - they are single instances that run on a specific host.
 */
public interface PersistentServerApi {

    /**
     * Retrieves a persistent server by its unique identifier.
     *
     * @param id the unique ID of the persistent server
     * @return a CompletableFuture that completes with the persistent server, or fails if not found
     */
    CompletableFuture<PersistentServer> getPersistentServerById(String id);

    /**
     * Retrieves a persistent server by its name.
     *
     * @param name the name of the persistent server
     * @return a CompletableFuture that completes with the persistent server, or fails if not found
     */
    CompletableFuture<PersistentServer> getPersistentServerByName(String name);

    /**
     * Retrieves all persistent servers, optionally filtered by a query.
     *
     * @param query optional query parameters to filter results
     * @return a CompletableFuture that completes with a list of matching persistent servers
     */
    CompletableFuture<List<PersistentServer>> getAllPersistentServers(@Nullable PersistentServerQuery query);

    /**
     * Retrieves all persistent servers without any filtering.
     *
     * @return a CompletableFuture that completes with a list of all persistent servers
     */
    default CompletableFuture<List<PersistentServer>> getAllPersistentServers() {
        return getAllPersistentServers(null);
    }

    /**
     * Creates a new persistent server.
     *
     * @param request the configuration for the new persistent server
     * @return a CompletableFuture that completes with the created persistent server
     */
    CompletableFuture<PersistentServer> createPersistentServer(CreatePersistentServerRequest request);

    /**
     * Updates an existing persistent server.
     *
     * @param id      the unique ID of the persistent server to update
     * @param request the updated configuration (only specified fields will be updated)
     * @return a CompletableFuture that completes with the updated persistent server
     */
    CompletableFuture<PersistentServer> updatePersistentServer(String id, UpdatePersistentServerRequest request);

    /**
     * Deletes a persistent server.
     *
     * @param id the unique ID of the persistent server to delete
     * @return a CompletableFuture that completes when the persistent server is deleted
     */
    CompletableFuture<Void> deletePersistentServer(String id);

    /**
     * Updates persistent server properties by merging with existing properties (deep merge).
     *
     * @param id the unique ID of the persistent server
     * @param properties the properties to merge
     * @return a CompletableFuture that completes with the updated properties
     */
    CompletableFuture<Map<String, Object>> updatePersistentServerProperties(String id, Map<String, Object> properties);

    /**
     * Deletes specific property keys from a persistent server.
     *
     * @param id the unique ID of the persistent server
     * @param keys the property keys to delete
     * @return a CompletableFuture that completes with the remaining properties
     */
    CompletableFuture<Map<String, Object>> deletePersistentServerProperties(String id, List<String> keys);
}
