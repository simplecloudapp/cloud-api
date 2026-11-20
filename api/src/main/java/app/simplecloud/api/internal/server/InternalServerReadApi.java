package app.simplecloud.api.internal.server;

import app.simplecloud.api.server.Server;
import app.simplecloud.api.server.ServerQuery;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** The server reads, answered either from the network copy or by the controller. */
interface InternalServerReadApi {
    CompletableFuture<Optional<Server>> findServerById(String id);

    CompletableFuture<Optional<Server>> findServerByNumericalId(String groupName, int numericalId);

    CompletableFuture<List<Server>> getServersByGroup(String groupName);

    CompletableFuture<List<Server>> getAllServers(@Nullable ServerQuery query);
}
