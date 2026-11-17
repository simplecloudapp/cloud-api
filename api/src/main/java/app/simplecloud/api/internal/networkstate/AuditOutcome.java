package app.simplecloud.api.internal.networkstate;

/**
 * How an audit ended. An audit loads a snapshot at revision S while the view is live and compares it with
 * the copy once the copy is at exactly S. The comparison leaves out metrics.
 */
public enum AuditOutcome {
    /** The copy at S equals the snapshot. */
    MATCHED,
    /** The copy at S differs from the snapshot. The view counts an error and takes the snapshot. */
    DIFFERED,
    /**
     * The copy went past S without being at S, because one batch covered several revisions, or it was past
     * S already. Nothing was compared. The view counts an error and audits again soon.
     */
    JUMPED_PAST,
    /** The copy did not reach S within 3 s, so a batch is missing. The view takes the snapshot. */
    NOT_REACHED
}
