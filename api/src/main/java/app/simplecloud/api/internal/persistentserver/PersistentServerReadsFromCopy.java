package app.simplecloud.api.internal.persistentserver;

import app.simplecloud.api.CloudApiException;
import app.simplecloud.api.internal.networkcopy.NetworkContents;
import app.simplecloud.api.persistentserver.PersistentServer;
import app.simplecloud.api.persistentserver.PersistentServerQuery;
import app.simplecloud.api.web.models.ModelsPersistentServerSummary;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

final class PersistentServerReadsFromCopy implements PersistentServerReads {
    private final NetworkContents contents;

    PersistentServerReadsFromCopy(NetworkContents contents) {
        this.contents = contents;
    }

    @Override
    public CompletableFuture<Optional<PersistentServer>> findPersistentServerById(String id) {
        ModelsPersistentServerSummary summary = contents.persistentServerById(id);
        if (summary == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return CompletableFuture.completedFuture(Optional.of(new PersistentServerImpl(summary)));
    }

    @Override
    public CompletableFuture<Optional<PersistentServer>> findPersistentServerByName(String name) {
        ModelsPersistentServerSummary summary = contents.persistentServerByName(name);
        if (summary == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return CompletableFuture.completedFuture(Optional.of(new PersistentServerImpl(summary)));
    }

    @Override
    public CompletableFuture<List<PersistentServer>> getAllPersistentServers(@Nullable PersistentServerQuery query) {
        try {
            List<ModelsPersistentServerSummary> matching = contents.persistentServers(query);
            return CompletableFuture.completedFuture(toPersistentServers(matching));
        } catch (CloudApiException invalidQuery) {
            return CompletableFuture.failedFuture(invalidQuery);
        }
    }

    private static List<PersistentServer> toPersistentServers(List<ModelsPersistentServerSummary> summaries) {
        List<PersistentServer> persistentServers = new ArrayList<>(summaries.size());
        for (ModelsPersistentServerSummary summary : summaries) {
            persistentServers.add(new PersistentServerImpl(summary));
        }
        return Collections.unmodifiableList(persistentServers);
    }
}
