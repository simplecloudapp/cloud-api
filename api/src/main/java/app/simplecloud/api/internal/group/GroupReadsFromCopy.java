package app.simplecloud.api.internal.group;

import app.simplecloud.api.CloudApiException;
import app.simplecloud.api.group.Group;
import app.simplecloud.api.group.GroupQuery;
import app.simplecloud.api.internal.networkcopy.NetworkContents;
import app.simplecloud.api.web.models.ModelsServerGroupSummary;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

final class GroupReadsFromCopy implements GroupReads {
    private final NetworkContents contents;

    GroupReadsFromCopy(NetworkContents contents) {
        this.contents = contents;
    }

    @Override
    public CompletableFuture<Optional<Group>> findGroupByName(String name) {
        ModelsServerGroupSummary summary = contents.groupByName(name);
        if (summary == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return CompletableFuture.completedFuture(Optional.of(new GroupImpl(summary)));
    }

    @Override
    public CompletableFuture<Optional<Group>> findGroupById(String id) {
        ModelsServerGroupSummary summary = contents.groupById(id);
        if (summary == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return CompletableFuture.completedFuture(Optional.of(new GroupImpl(summary)));
    }

    @Override
    public CompletableFuture<List<Group>> getAllGroups(@Nullable GroupQuery query) {
        try {
            List<ModelsServerGroupSummary> matching = contents.groups(query);
            return CompletableFuture.completedFuture(toGroups(matching));
        } catch (CloudApiException invalidQuery) {
            return CompletableFuture.failedFuture(invalidQuery);
        }
    }

    private static List<Group> toGroups(List<ModelsServerGroupSummary> summaries) {
        List<Group> groups = new ArrayList<>(summaries.size());
        for (ModelsServerGroupSummary summary : summaries) {
            groups.add(new GroupImpl(summary));
        }
        return Collections.unmodifiableList(groups);
    }
}
