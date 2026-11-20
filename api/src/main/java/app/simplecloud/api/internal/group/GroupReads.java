package app.simplecloud.api.internal.group;

import app.simplecloud.api.group.Group;
import app.simplecloud.api.group.GroupQuery;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** The group reads, answered either from the network copy or by the controller. */
interface GroupReads {
    CompletableFuture<Optional<Group>> findGroupByName(String name);

    CompletableFuture<Optional<Group>> findGroupById(String id);

    CompletableFuture<List<Group>> getAllGroups(@Nullable GroupQuery query);
}
