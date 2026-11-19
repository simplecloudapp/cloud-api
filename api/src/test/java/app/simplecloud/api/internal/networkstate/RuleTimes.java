package app.simplecloud.api.internal.networkstate;

/**
 * The times in the rules of {@link NetworkView}, in milliseconds, written from its text. The tests do not read
 * them from {@link NetworkViewMachine}, so a wrong time there fails a check.
 */
final class RuleTimes {

    /** Rule 2: a batch held this long means reload. */
    static final long GAP_WAIT = 3_000;
    /** Rule 3: a copy still behind a pulse this long after the first pulse ahead means reload. */
    static final long PULSE_AHEAD_WAIT = 3_000;
    /** Rules 3 and 5: a view is live for less than this after its last good pulse. */
    static final long GOOD_PULSE_LIFETIME = 15_000;
    /** Rule 6. */
    static final long LOAD_SPACING = 2_000;
    /** Rule 6: the back-off after one failed load, doubled for each further one. */
    static final long FIRST_BACK_OFF = 2_000;
    static final long LONGEST_BACK_OFF = 30_000;
    /** Rule 6: a reload waits this times a number from the sequence. */
    static final long RANDOM_LOAD_DELAY_BOUND = 2_000;
    /** Rule 7: the next audit is due this plus the spread times a number from the sequence later. */
    static final long SHORTEST_AUDIT_INTERVAL = 20 * 60_000;
    static final long AUDIT_INTERVAL_SPREAD = 10 * 60_000;
    /** Rule 7: after the copy went past S, the next audit is due this plus the spread times a number later. */
    static final long SHORTEST_AUDIT_RETRY = 2_000;
    static final long AUDIT_RETRY_SPREAD = 8_000;
    /** Rule 7: an audit snapshot the copy has not reached this long after it arrived is taken. */
    static final long AUDIT_REACH_WAIT = 3_000;
    /** Answers: a read waits for an own write until this long after the write was handed over. */
    static final long OWN_WRITE_WAIT = 3_000;

    private RuleTimes() {
    }
}
