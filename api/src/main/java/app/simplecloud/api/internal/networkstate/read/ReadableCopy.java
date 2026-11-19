package app.simplecloud.api.internal.networkstate.read;

import app.simplecloud.api.internal.group.ServerGroupSource;
import app.simplecloud.api.internal.networkstate.NetworkCopy;
import app.simplecloud.api.internal.persistentserver.PersistentServerSource;
import app.simplecloud.api.internal.server.ServerSource;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * One copy as reads see it: the sources of its servers, groups and persistent servers.
 *
 * <p>Each kind is built on the first read that needs it, once, and then shared by every reader of this copy.
 * A build that fails stays failed: every later read of that kind throws the same exception, since the copy
 * never changes.
 */
public final class ReadableCopy {

    private final NetworkCopy copy;
    private final Lazy<ServerIndex> servers;
    private final Lazy<ServerGroupIndex> serverGroups;
    private final Lazy<PersistentServerIndex> persistentServers;

    /**
     * @param networkId the copy's network, a UUID, which the rows do not carry
     */
    public ReadableCopy(NetworkCopy copy, String networkId) {
        this.copy = copy;
        this.servers = new Lazy<>(() -> new ServerIndex(copy, networkId));
        this.serverGroups = new Lazy<>(() -> new ServerGroupIndex(copy));
        this.persistentServers = new Lazy<>(() -> new PersistentServerIndex(copy));
    }

    public NetworkCopy copy() {
        return copy;
    }

    public ServerSource servers() {
        return servers.get();
    }

    public ServerGroupSource serverGroups() {
        return serverGroups.get();
    }

    public PersistentServerSource persistentServers() {
        return persistentServers.get();
    }

    private static final class Lazy<T> {

        private final Supplier<T> build;
        private volatile @Nullable Built<T> built;

        Lazy(Supplier<T> build) {
            this.build = build;
        }

        T get() {
            Built<T> once = built;
            if (once == null) {
                once = buildOnce();
            }
            return once.valueOrThrow();
        }

        private synchronized Built<T> buildOnce() {
            if (built == null) {
                built = Built.of(build);
            }
            return built;
        }
    }

    /**
     * The value a build gave, or the exception it threw.
     */
    private record Built<T>(@Nullable T value, @Nullable RuntimeException failure) {

        static <T> Built<T> of(Supplier<T> build) {
            try {
                return new Built<>(build.get(), null);
            } catch (RuntimeException failure) {
                return new Built<>(null, failure);
            }
        }

        T valueOrThrow() {
            if (failure != null) {
                throw failure;
            }
            return value;
        }
    }
}
