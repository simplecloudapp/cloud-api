package app.simplecloud.api.internal.networkstate;

import app.simplecloud.api.internal.networkstate.read.CopyPersistentServerList;
import app.simplecloud.api.internal.networkstate.read.CopyServerGroupList;
import app.simplecloud.api.internal.networkstate.read.CopyServerList;
import app.simplecloud.api.internal.networkstate.read.PersistentServerListParameters;
import app.simplecloud.api.internal.networkstate.read.ServerGroupListParameters;
import app.simplecloud.api.internal.networkstate.read.ServerListParameters;
import app.simplecloud.api.web.models.ModelsListPersistentServersResponse;
import app.simplecloud.api.web.models.ModelsListServerGroupsResponse;
import app.simplecloud.api.web.models.ModelsListServersResponse;
import com.google.protobuf.ByteString;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Answers the list routes from a view's copy, where the view says the copy may answer, as the controller
 * would answer them over HTTP. Empty means the caller asks the controller. {@link #NONE} answers nothing, for
 * an instance whose credential may not use a view.
 */
public final class CopyReads {

    public static final CopyReads NONE = new CopyReads(null, ByteString.EMPTY);

    private static final Logger LOGGER = Logger.getLogger(CopyReads.class.getName());

    private final @Nullable NetworkViewShell shell;
    private final ByteString networkId;
    private final AtomicBoolean failureReported = new AtomicBoolean();

    private CopyReads(@Nullable NetworkViewShell shell, ByteString networkId) {
        this.shell = shell;
        this.networkId = networkId;
    }

    /**
     * @param networkId the view's network, a UUID
     */
    public static CopyReads of(NetworkViewShell shell, String networkId) {
        return new CopyReads(shell, uuidBytes(networkId));
    }

    public Optional<ModelsListServersResponse> listServers(ServerListParameters parameters) {
        Optional<NetworkCopy> copy = copyToAnswer(ReadKind.SERVER);
        if (copy.isEmpty()) {
            return Optional.empty();
        }
        try {
            return CopyServerList.answer(copy.get(), networkId, parameters);
        } catch (RuntimeException e) {
            reportFailedAnswer(e);
            return Optional.empty();
        }
    }

    public Optional<ModelsListServerGroupsResponse> listServerGroups(ServerGroupListParameters parameters) {
        Optional<NetworkCopy> copy = copyToAnswer(ReadKind.SERVER_GROUP);
        if (copy.isEmpty()) {
            return Optional.empty();
        }
        try {
            return CopyServerGroupList.answer(copy.get(), parameters);
        } catch (RuntimeException e) {
            reportFailedAnswer(e);
            return Optional.empty();
        }
    }

    public Optional<ModelsListPersistentServersResponse> listPersistentServers(
            PersistentServerListParameters parameters
    ) {
        Optional<NetworkCopy> copy = copyToAnswer(ReadKind.PERSISTENT_SERVER);
        if (copy.isEmpty()) {
            return Optional.empty();
        }
        try {
            return CopyPersistentServerList.answer(copy.get(), parameters);
        } catch (RuntimeException e) {
            reportFailedAnswer(e);
            return Optional.empty();
        }
    }

    private Optional<NetworkCopy> copyToAnswer(ReadKind kind) {
        if (shell == null) {
            return Optional.empty();
        }
        return shell.copyToAnswer(kind);
    }

    /**
     * The read side throws where the HTTP client would fail to read the controller's body. The request over
     * HTTP then fails in the same way, with the HTTP client's own error, so the caller asks the controller.
     */
    private void reportFailedAnswer(RuntimeException failure) {
        Level level = failureReported.compareAndSet(false, true) ? Level.WARNING : Level.FINE;
        LOGGER.log(level, "Answering a read from the network view failed; it goes over HTTP", failure);
    }

    private static ByteString uuidBytes(String uuid) {
        UUID parsed = UUID.fromString(uuid);
        ByteBuffer bytes = ByteBuffer.allocate(16);
        bytes.putLong(parsed.getMostSignificantBits());
        bytes.putLong(parsed.getLeastSignificantBits());
        return ByteString.copyFrom(bytes.array());
    }
}
