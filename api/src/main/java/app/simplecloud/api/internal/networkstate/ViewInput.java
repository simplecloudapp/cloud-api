package app.simplecloud.api.internal.networkstate;

import build.buf.gen.simplecloud.controller.v2.NetworkState;
import build.buf.gen.simplecloud.controller.v2.StateBatch;
import build.buf.gen.simplecloud.controller.v2.StatePulse;

/**
 * Something the shell tells a view. The shell hands inputs to the view one at a time. Batches and pulses
 * keep the order in which they arrived, so a pulse is never handed over before a batch that arrived ahead
 * of it.
 */
public sealed interface ViewInput {

    /**
     * The subscriptions to {@code <network>.state.v1.batch} and {@code <network>.state.v1.pulse} are active,
     * and a flush (PING/PONG) on the connection has confirmed it. The shell sends this once at the start, and
     * again after every reconnect, once the flush after the reconnect has returned.
     */
    record SubscriptionsActive() implements ViewInput {
    }

    /**
     * The NATS connection went down. The shell sends this as soon as the NATS client reports it.
     */
    record ConnectionLost() implements ViewInput {
    }

    /**
     * The load that the last {@link ViewEffect.LoadSnapshot} started returned this snapshot.
     */
    record SnapshotLoaded(NetworkState snapshot) implements ViewInput {
    }

    /**
     * The load that the last {@link ViewEffect.LoadSnapshot} started failed: an HTTP error, a timeout, or a
     * body that does not parse.
     */
    record SnapshotFailed() implements ViewInput {
    }

    record BatchReceived(StateBatch batch) implements ViewInput {
    }

    record PulseReceived(StatePulse pulse) implements ViewInput {
    }

    /**
     * A write of this process returned successfully, and its response carried the headers
     * {@code X-SC-State-Epoch} and {@code X-SC-State-Revision}. The controller reads them after the commit, so
     * the revision is at or above the write's own.
     */
    record WriteReturned(long epoch, long revision) implements ViewInput {
    }

    /**
     * Only time passed. A view acts on a deadline only during a step, so the shell sends this regularly:
     * every 100 ms keeps each deadline within 100 ms.
     */
    record TimePassed() implements ViewInput {
    }
}
