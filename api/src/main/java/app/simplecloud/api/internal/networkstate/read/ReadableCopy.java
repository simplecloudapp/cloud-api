package app.simplecloud.api.internal.networkstate.read;

import app.simplecloud.api.CloudApiException;
import app.simplecloud.api.group.Group;
import app.simplecloud.api.group.GroupQuery;
import app.simplecloud.api.internal.networkstate.NetworkCopy;
import app.simplecloud.api.persistentserver.PersistentServer;
import app.simplecloud.api.persistentserver.PersistentServerQuery;
import app.simplecloud.api.server.Server;
import app.simplecloud.api.server.ServerQuery;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

/**
 * One copy as reads see it: its servers, groups and persistent servers as API objects.
 *
 * <p>Each kind is built on the first read that needs it, once, and then shared by every reader of this copy.
 * The objects are the classes HTTP reads give, holding the same values the controller's lists show for the
 * copy's revision, in the controller's order. An id in another form than 8-4-4-4-12 matches nothing.
 */
public final class ReadableCopy {

    private final NetworkCopy copy;
    private final Lazy<CopyServers> servers;
    private final Lazy<CopyServerGroups> serverGroups;
    private final Lazy<CopyPersistentServers> persistentServers;

    /**
     * @param networkId the copy's network, a UUID, which the rows do not carry
     */
    public ReadableCopy(NetworkCopy copy, String networkId) {
        this.copy = copy;
        this.servers = new Lazy<>(() -> new CopyServers(copy, networkId));
        this.serverGroups = new Lazy<>(() -> new CopyServerGroups(copy));
        this.persistentServers = new Lazy<>(() -> new CopyPersistentServers(copy));
    }

    public NetworkCopy copy() {
        return copy;
    }

    /**
     * Returns the servers that match every filter the query sets, all of them for no query.
     *
     * @throws CloudApiException with {@link CloudApiException.Reason#INVALID_REQUEST} for an unknown sort field
     *                           or sort order
     */
    public List<Server> servers(@Nullable ServerQuery query) {
        return servers.get().servers(query);
    }

    public @Nullable Server serverWithId(String id) {
        return servers.get().serverWithId(id);
    }

    /**
     * Returns the groups that match the query's type and tags, up to its limit. Like a read over HTTP, it
     * does not sort by the query's sorting.
     *
     * @throws CloudApiException with {@link CloudApiException.Reason#INVALID_REQUEST} for a limit below 1
     */
    public List<Group> serverGroups(@Nullable GroupQuery query) {
        return serverGroups.get().serverGroups(query);
    }

    public @Nullable Group serverGroupWithId(String id) {
        return serverGroups.get().serverGroupWithId(id);
    }

    public @Nullable Group serverGroupWithName(String name) {
        return serverGroups.get().serverGroupWithName(name);
    }

    /**
     * Returns the persistent servers that match every filter the query sets, up to its limit.
     *
     * @throws CloudApiException with {@link CloudApiException.Reason#INVALID_REQUEST} for a limit below 1
     */
    public List<PersistentServer> persistentServers(@Nullable PersistentServerQuery query) {
        return persistentServers.get().persistentServers(query);
    }

    public @Nullable PersistentServer persistentServerWithId(String id) {
        return persistentServers.get().persistentServerWithId(id);
    }

    public @Nullable PersistentServer persistentServerWithName(String name) {
        return persistentServers.get().persistentServerWithName(name);
    }

    /**
     * Builds its value on the first {@link #get} of any thread, and only once. A build that throws keeps
     * nothing, so the next read builds again.
     */
    private static final class Lazy<T> {

        private final Supplier<T> build;
        private volatile @Nullable T value;

        Lazy(Supplier<T> build) {
            this.build = build;
        }

        T get() {
            T built = value;
            if (built != null) {
                return built;
            }
            synchronized (this) {
                if (value == null) {
                    value = build.get();
                }
                return value;
            }
        }
    }
}
