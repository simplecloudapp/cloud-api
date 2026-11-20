package app.simplecloud.api.internal.persistentserver;

import app.simplecloud.api.persistentserver.PersistentServer;
import app.simplecloud.api.persistentserver.PersistentServerQuery;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** The persistent server reads, answered either from the network copy or by the controller. */
interface InternalPersistentServerReadApi {
    CompletableFuture<Optional<PersistentServer>> findPersistentServerById(String id);

    CompletableFuture<Optional<PersistentServer>> findPersistentServerByName(String name);

    CompletableFuture<List<PersistentServer>> getAllPersistentServers(@Nullable PersistentServerQuery query);
}
