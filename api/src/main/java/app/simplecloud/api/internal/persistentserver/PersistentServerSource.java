package app.simplecloud.api.internal.persistentserver;

import app.simplecloud.api.persistentserver.PersistentServer;
import app.simplecloud.api.persistentserver.PersistentServerQuery;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Answers the reads of the {@link app.simplecloud.api.persistentserver.PersistentServerApi}. Two sources do: the
 * view's copy and the controller over HTTP. A lookup answers null when the source has no such persistent
 * server.
 */
public interface PersistentServerSource {

    CompletableFuture<@Nullable PersistentServer> persistentServerWithId(String id);

    CompletableFuture<@Nullable PersistentServer> persistentServerWithName(String name);

    CompletableFuture<List<PersistentServer>> persistentServers(@Nullable PersistentServerQuery query);
}
