package app.simplecloud.api.internal.server;

import app.simplecloud.api.CloudApiException;
import app.simplecloud.api.internal.networkcopy.NetworkContents;
import app.simplecloud.api.server.Server;
import app.simplecloud.api.server.ServerQuery;
import app.simplecloud.api.web.models.ModelsServerSummary;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

final class NetworkCopyServerReadApi implements InternalServerReadApi {
    private final NetworkContents contents;

    NetworkCopyServerReadApi(NetworkContents contents) {
        this.contents = contents;
    }

    @Override
    public CompletableFuture<Optional<Server>> findServerById(String id) {
        ModelsServerSummary summary = contents.findServerSummaryById(id);
        if (summary == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return CompletableFuture.completedFuture(Optional.of(new ServerImpl(summary)));
    }

    @Override
    public CompletableFuture<Optional<Server>> findServerByNumericalId(String groupName, int numericalId) {
        ServerQuery query = ServerQuery.create()
                .filterByServerGroupName(groupName)
                .filterByNumericalId(numericalId);
        List<ModelsServerSummary> matching = contents.listServerSummaries(query);
        if (matching.isEmpty()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return CompletableFuture.completedFuture(Optional.of(new ServerImpl(matching.getFirst())));
    }

    @Override
    public CompletableFuture<List<Server>> getServersByGroup(String groupName) {
        ServerQuery query = ServerQuery.create()
                .filterByServerGroupName(groupName);
        List<ModelsServerSummary> matching = contents.listServerSummaries(query);
        return CompletableFuture.completedFuture(toServers(matching));
    }

    @Override
    public CompletableFuture<List<Server>> getAllServers(@Nullable ServerQuery query) {
        try {
            List<ModelsServerSummary> matching = contents.listServerSummaries(query);
            return CompletableFuture.completedFuture(toServers(matching));
        } catch (CloudApiException invalidQuery) {
            return CompletableFuture.failedFuture(invalidQuery);
        }
    }

    private static List<Server> toServers(List<ModelsServerSummary> summaries) {
        List<Server> servers = new ArrayList<>(summaries.size());
        for (ModelsServerSummary summary : summaries) {
            servers.add(new ServerImpl(summary));
        }
        return Collections.unmodifiableList(servers);
    }
}
