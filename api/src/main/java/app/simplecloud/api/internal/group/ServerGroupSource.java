package app.simplecloud.api.internal.group;

import app.simplecloud.api.group.Group;
import app.simplecloud.api.group.GroupQuery;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Answers the reads of the {@link app.simplecloud.api.group.GroupApi}. Two sources do: the view's copy and the
 * controller over HTTP. A lookup answers null when the source has no such group.
 */
public interface ServerGroupSource {

    CompletableFuture<@Nullable Group> serverGroupWithId(String id);

    CompletableFuture<@Nullable Group> serverGroupWithName(String name);

    CompletableFuture<List<Group>> serverGroups(@Nullable GroupQuery query);
}
