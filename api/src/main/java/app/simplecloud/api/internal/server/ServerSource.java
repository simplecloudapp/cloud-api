package app.simplecloud.api.internal.server;

import app.simplecloud.api.server.Server;
import app.simplecloud.api.server.ServerQuery;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Answers the reads of the {@link app.simplecloud.api.server.ServerApi}. Two sources do: the view's copy and the
 * controller over HTTP. A lookup answers null when the source has no such server.
 */
public interface ServerSource {

    CompletableFuture<@Nullable Server> serverWithId(String id);

    CompletableFuture<@Nullable Server> serverWithNumericalId(String groupName, int numericalId);

    CompletableFuture<List<Server>> serversOfGroup(String groupName);

    CompletableFuture<List<Server>> servers(@Nullable ServerQuery query);
}
