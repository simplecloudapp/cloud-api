package app.simplecloud.api.internal.networkstate;

import java.util.Set;

/**
 * Something a view asks the shell to do or to report.
 */
public sealed interface ViewEffect {

    /**
     * Load a snapshot with {@code GET /v0/network-state}, and answer with exactly one
     * {@link ViewInput.SnapshotLoaded} or {@link ViewInput.SnapshotFailed}. A view asks for a new load only
     * after the last one was answered, so the shell runs at most one load at a time.
     */
    record LoadSnapshot() implements ViewEffect {
    }

    /**
     * Items of the copy changed in this step: each one was added, replaced by an item that differs, or
     * removed. An item that a batch repeats unchanged is not in the set. A step reports at most one such
     * effect, and the copy of the step's view holds the new items.
     */
    record ItemsChanged(Set<ItemId> items) implements ViewEffect {

        public ItemsChanged {
            items = Set.copyOf(items);
        }
    }

    /**
     * An audit ended. The shell counts {@link AuditOutcome#DIFFERED} and {@link AuditOutcome#JUMPED_PAST}
     * as errors.
     */
    record AuditFinished(AuditOutcome outcome) implements ViewEffect {
    }
}
