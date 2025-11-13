package app.simplecloud.api.future.server;

import java.util.concurrent.CompletableFuture;

public interface ServerApi {

    /**
     * Gets a server by its ID.
     *
     * @param id the server ID
     * @return a future completing with the server
     */
    CompletableFuture<Server> getServerById(String id);

    /**
     * Lists all servers.
     *
     * @return a future completing with all servers
     */
    CompletableFuture<Server[]> getAllServers();

    /**
     * Lists all servers in a specific group.
     *
     * @param groupId the server group ID
     * @return a future completing with the list of servers
     */
    CompletableFuture<Server[]> getServersByGroup(String groupId);

    /**
     * Starts a new server.
     *
     * @param request the start request
     * @return a future completing with the created server info
     */
    CompletableFuture<Void> startServer(StartServerRequest request);

    /**
     * Stops a running server.
     *
     * @param id the server ID
     * @return a future completing when the stop request is acknowledged
     */
    CompletableFuture<Void> stopServer(String id);
}
