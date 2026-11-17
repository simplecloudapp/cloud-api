package app.simplecloud.api.internal.networkstate;

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
     * An audit ended. The shell counts each outcome on its own; only {@link AuditOutcome#DIFFERED} and
     * {@link AuditOutcome#NOT_REACHED} are errors.
     */
    record AuditFinished(AuditOutcome outcome) implements ViewEffect {
    }
}
