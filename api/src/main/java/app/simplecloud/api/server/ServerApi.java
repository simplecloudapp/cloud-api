package app.simplecloud.api.server;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * API for managing server instances.
 * 
 * <p>Provides methods to query running servers, start new server instances
 * from server groups, and stop running servers.
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
     * Starts a new server instance from a server group.
     * 
     * @param request the start request specifying which group to start from
     * @return a CompletableFuture that completes when the server start is initiated
     */
    CompletableFuture<Void> startServer(StartServerRequest request);

    /**
     * Stops a running server.
     * 
     * @param id the unique ID of the server to stop
     * @return a CompletableFuture that completes when the server stop is initiated
     */
    CompletableFuture<Void> stopServer(String id);
}

