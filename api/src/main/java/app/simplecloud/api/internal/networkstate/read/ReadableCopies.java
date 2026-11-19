package app.simplecloud.api.internal.networkstate.read;

import app.simplecloud.api.internal.networkstate.AnsweringView;
import app.simplecloud.api.internal.networkstate.NetworkCopy;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * The view's copy in its readable form. One instance serves all API classes of a runtime, so that their reads
 * of one copy share its built objects.
 */
public final class ReadableCopies {

    // Without a view no copy is ever handed out, so its network id is never read.
    private static final ReadableCopies NONE = new ReadableCopies(AnsweringView.NO_VIEW, "");

    private final AnsweringView view;
    private final String networkId;
    /**
     * Compared by identity: equals would compare every item of both copies. A reader that still holds an older
     * copy may replace a newer one here; that costs a build, never a wrong answer.
     */
    private volatile @Nullable ReadableCopy latestReadableCopy;

    /**
     * @param networkId the network of the copies, a UUID, which their rows do not carry
     */
    public ReadableCopies(AnsweringView view, String networkId) {
        this.view = view;
        this.networkId = networkId;
    }

    /**
     * For an instance without a view: it never hands out a copy.
     */
    public static ReadableCopies none() {
        return NONE;
    }

    /**
     * Returns the copy that may answer a read now, or empty, so the controller answers it. The future is
     * complete on return unless the read waits for an own write.
     */
    public CompletableFuture<Optional<ReadableCopy>> copyToAnswer() {
        CompletableFuture<Optional<NetworkCopy>> copy = view.copyToAnswer();
        return copy.thenApply(this::readableFormOf);
    }

    private Optional<ReadableCopy> readableFormOf(Optional<NetworkCopy> copy) {
        return copy.map(this::readableCopyOf);
    }

    private ReadableCopy readableCopyOf(NetworkCopy copy) {
        ReadableCopy latest = latestReadableCopy;
        if (latest != null && latest.copy() == copy) {
            return latest;
        }
        ReadableCopy readable = new ReadableCopy(copy, networkId);
        latestReadableCopy = readable;
        return readable;
    }
}
