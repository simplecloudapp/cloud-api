package app.simplecloud.api.server;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * API for managing server instances.
 *
 * <p>Provides methods to query running servers and stop/update server instances.
 */
public interface ServerApi {

    /**
     * Retrieves a server by its unique identifier.
     *
     * @param id the unique server ID
     * @return a CompletableFuture that completes with the server, or fails if not found
     */
    CompletableFuture<Server> getServerById(String id);

    /**
     * Retrieves a server by its runtime name.
     *
     * <p>The provided name may refer either to a persistent server or to a group-backed server
     * instance. The lookup uses the provided split character to separate the base name from the
     * trailing numerical suffix, then resolves either the persistent server name with numerical ID
     * {@code -1}, or the base name with the parsed numerical ID.
     *
     * <p>If the provided name does not contain the split character, or the trailing segment is not
     * a valid numerical suffix, the whole input is treated as a persistent server base name.
     *
     * @param serverName the runtime server name
     * @param splitChar the character used to split the base name from the numerical suffix
     * @return a CompletableFuture that completes with the server, or null if the provided name is blank
     */
    CompletableFuture<Server> getServerByName(String serverName, char splitChar);

    /**
     * Retrieves a server by its numerical ID.
     *
     * @param serverBaseName the persistent server name or group name used by the server query filter
     * @param numericalId the numerical server ID
     * @return a CompletableFuture that completes with the server, or fails if not found
     */
    CompletableFuture<Server> getServerByNumericalId(String serverBaseName, int numericalId);

    /**
     * Retrieves all servers whose base configuration name matches the provided server base name.
     *
     * <p>This matches either a persistent server name or a group name.
     *
     * @param serverBaseName the server base name to filter by
     * @return a CompletableFuture that completes with a list of matching servers
     */
    CompletableFuture<List<Server>> getServersByServerBaseName(String serverBaseName);

    /**
     * @deprecated Use {@link #getServersByServerBaseName(String)} instead.
     */
    @Deprecated
    default CompletableFuture<List<Server>> getServersByGroup(String groupName) {
        return getServersByServerBaseName(groupName);
    }

    /**
     * Retrieves all servers, optionally filtered by a query.
     *
     * @param query optional query parameters to filter results (group, host, state, sorting, limit)
     * @return a CompletableFuture that completes with a list of matching servers
     */
    CompletableFuture<List<Server>> getAllServers(@Nullable ServerQuery query);

    /**
     * Retrieves all servers without any filters.
     *
     * @return a CompletableFuture that completes with a list of all servers
     */
    default CompletableFuture<List<Server>> getAllServers() {
        return getAllServers(null);
    }

    /**
     * Stops a running server.
     *
     * @param id the unique ID of the server to stop
     * @return a CompletableFuture that completes when the server stop is initiated
     */
    CompletableFuture<Void> stopServer(String id);

    /**
     * Updates an existing server instance.
     *
     * @param id the unique ID of the server to update
     * @param request the update request containing fields to update
     * @return a CompletableFuture that completes with the updated Server instance
     */
    CompletableFuture<Server> updateServer(String id, UpdateServerRequest request);

    /**
     * Retrieves the current server instance using the SIMPLECLOUD_UNIQUE_ID environment variable.
     *
     * @return a CompletableFuture that completes with the current server, or fails if not found
     */
    CompletableFuture<Server> getCurrentServer();

    /**
     * Updates server properties by merging with existing properties (deep merge).
     *
     * @param id the unique ID of the server
     * @param properties the properties to merge
     * @return a CompletableFuture that completes with the updated properties
     */
    CompletableFuture<Map<String, Object>> updateServerProperties(String id, Map<String, Object> properties);

    /**
     * Updates a single server property by merging it with existing properties.
     *
     * @param id the unique ID of the server
     * @param key the property key to update
     * @param value the property value to set
     * @return a CompletableFuture that completes with the updated properties
     */
    default CompletableFuture<Map<String, Object>> updateServerProperty(String id, String key, Object value) {
        return updateServerProperties(id, Map.of(key, value));
    }

    /**
     * Deletes specific property keys from a server.
     *
     * @param id the unique ID of the server
     * @param keys the property keys to delete
     * @return a CompletableFuture that completes with the remaining properties
     */
    CompletableFuture<Map<String, Object>> deleteServerProperties(String id, List<String> keys);

    /**
     * Deletes a single property key from a server.
     *
     * @param id the unique ID of the server
     * @param key the property key to delete
     * @return a CompletableFuture that completes with the remaining properties
     */
    default CompletableFuture<Map<String, Object>> deleteServerProperty(String id, String key) {
        return deleteServerProperties(id, List.of(key));
    }
}
