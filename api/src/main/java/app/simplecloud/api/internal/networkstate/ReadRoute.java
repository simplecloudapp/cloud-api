package app.simplecloud.api.internal.networkstate;

/**
 * Where a read goes at one moment, as {@link NetworkView#routeRead} decides it.
 */
public enum ReadRoute {
    FROM_COPY,
    /**
     * The copy has not reached the view's own write, and that write returned under 3 s ago; the shell asks again
     * after every step.
     */
    WAIT,
    OVER_HTTP
}
