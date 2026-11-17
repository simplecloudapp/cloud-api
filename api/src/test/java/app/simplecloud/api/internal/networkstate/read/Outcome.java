package app.simplecloud.api.internal.networkstate.read;

import java.util.concurrent.Callable;

/**
 * What a read gives a caller: a value, or the exception it throws. Two reads that a caller cannot tell apart
 * give equal outcomes.
 *
 * @param failure the exception's class and message, or null for a value
 */
record Outcome(Object value, String failure) {

    static Outcome of(Callable<?> read) {
        try {
            return new Outcome(read.call(), null);
        } catch (Exception thrown) {
            return new Outcome(null, thrown.getClass().getName() + ": " + thrown.getMessage());
        }
    }
}
