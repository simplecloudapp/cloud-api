package app.simplecloud.api.internal.networkstate;

/**
 * Where a read goes at one moment: the shell's decision for a new read and for each waiting one.
 */
enum ReadRoute {
    FROM_COPY,
    WAIT,
    OVER_HTTP;

    /**
     * A read whose wait ends at {@code waitEndsAtMillis} and that the copy may answer exactly then is answered
     * from the copy.
     */
    static ReadRoute of(NetworkView view, ReadKind kind, long nowMillis, long waitEndsAtMillis) {
        if (view.mayAnswer(kind, nowMillis)) {
            return FROM_COPY;
        }
        boolean waitEnded = nowMillis >= waitEndsAtMillis;
        if (waitEnded || !view.awaitsOwnWrite(kind, nowMillis)) {
            return OVER_HTTP;
        }
        return WAIT;
    }
}
