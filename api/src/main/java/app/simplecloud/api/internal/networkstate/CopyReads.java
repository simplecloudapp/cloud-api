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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Answers the list routes from a view's copy, where the view says the copy may answer, as the controller
 * would answer them over HTTP, and otherwise hands the read to the caller's path to the controller.
 * {@link #NONE} answers nothing, for an instance whose credential may not use a view.
 *
 * <p>Each list method returns what {@code read} returns. When the copy's answer is there at once, which is
 * the common case, {@code read} runs before the method returns, and its future, or what it throws, is the
 * method's. When the read waits for the copy to reach an own write, {@code read} runs on a thread of its own,
 * and its result is relayed with the same value or the same exception, not wrapped in another.
 */
public final class CopyReads {

    /**
     * Turns the copy's answer, or empty when the copy does not answer, into the read's result.
     */
    @FunctionalInterface
    public interface ReadFromCopyOrController<R, T> {

        CompletableFuture<T> read(Optional<R> fromCopy);
    }

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

    public <T> CompletableFuture<T> listServers(
            ServerListParameters parameters,
            ReadFromCopyOrController<ModelsListServersResponse, T> read
    ) {
        CompletableFuture<Optional<ModelsListServersResponse>> fromCopy = serversFromCopy(parameters);
        return readWhenCopyAnswered(fromCopy, read);
    }

    public <T> CompletableFuture<T> listServerGroups(
            ServerGroupListParameters parameters,
            ReadFromCopyOrController<ModelsListServerGroupsResponse, T> read
    ) {
        CompletableFuture<Optional<ModelsListServerGroupsResponse>> fromCopy = serverGroupsFromCopy(parameters);
        return readWhenCopyAnswered(fromCopy, read);
    }

    public <T> CompletableFuture<T> listPersistentServers(
            PersistentServerListParameters parameters,
            ReadFromCopyOrController<ModelsListPersistentServersResponse, T> read
    ) {
        CompletableFuture<Optional<ModelsListPersistentServersResponse>> fromCopy =
                persistentServersFromCopy(parameters);
        return readWhenCopyAnswered(fromCopy, read);
    }

    private CompletableFuture<Optional<ModelsListServersResponse>> serversFromCopy(ServerListParameters parameters) {
        CompletableFuture<Optional<NetworkCopy>> answeringCopy = copyToAnswer(ReadKind.SERVER);
        return answeringCopy.thenApply(copy -> serversFrom(copy, parameters));
    }

    private CompletableFuture<Optional<ModelsListServerGroupsResponse>> serverGroupsFromCopy(
            ServerGroupListParameters parameters
    ) {
        CompletableFuture<Optional<NetworkCopy>> answeringCopy = copyToAnswer(ReadKind.SERVER_GROUP);
        return answeringCopy.thenApply(copy -> serverGroupsFrom(copy, parameters));
    }

    private CompletableFuture<Optional<ModelsListPersistentServersResponse>> persistentServersFromCopy(
            PersistentServerListParameters parameters
    ) {
        CompletableFuture<Optional<NetworkCopy>> answeringCopy = copyToAnswer(ReadKind.PERSISTENT_SERVER);
        return answeringCopy.thenApply(copy -> persistentServersFrom(copy, parameters));
    }

    private static <R, T> CompletableFuture<T> readWhenCopyAnswered(
            CompletableFuture<Optional<R>> fromCopy,
            ReadFromCopyOrController<R, T> read
    ) {
        if (fromCopy.isDone()) {
            return read.read(fromCopy.join());
        }
        CompletableFuture<T> relayed = new CompletableFuture<>();
        fromCopy.whenComplete((copied, copyFailure) -> readAndRelay(read, copied, copyFailure, relayed));
        return relayed;
    }

    /**
     * Relays the exception a future holds as it is: {@code thenCompose} would wrap one that is not a
     * {@link java.util.concurrent.CompletionException} yet, so a caller's {@code exceptionally} would see
     * another type than over the controller's path alone.
     */
    private static <R, T> void readAndRelay(
            ReadFromCopyOrController<R, T> read,
            Optional<R> copied,
            @Nullable Throwable copyFailure,
            CompletableFuture<T> relayed
    ) {
        if (copyFailure != null) {
            relayed.completeExceptionally(copyFailure);
            return;
        }
        try {
            CompletableFuture<T> result = read.read(copied);
            result.whenComplete((value, failure) -> complete(relayed, value, failure));
        } catch (RuntimeException | Error failure) {
            relayed.completeExceptionally(failure);
        }
    }

    private static <T> void complete(CompletableFuture<T> relayed, T value, @Nullable Throwable failure) {
        if (failure != null) {
            relayed.completeExceptionally(failure);
            return;
        }
        relayed.complete(value);
    }

    private CompletableFuture<Optional<NetworkCopy>> copyToAnswer(ReadKind kind) {
        if (shell == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return shell.copyToAnswer(kind);
    }

    private Optional<ModelsListServersResponse> serversFrom(
            Optional<NetworkCopy> copy,
            ServerListParameters parameters
    ) {
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

    private Optional<ModelsListServerGroupsResponse> serverGroupsFrom(
            Optional<NetworkCopy> copy,
            ServerGroupListParameters parameters
    ) {
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

    private Optional<ModelsListPersistentServersResponse> persistentServersFrom(
            Optional<NetworkCopy> copy,
            PersistentServerListParameters parameters
    ) {
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
