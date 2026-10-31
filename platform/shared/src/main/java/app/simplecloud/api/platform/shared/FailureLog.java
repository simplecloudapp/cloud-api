package app.simplecloud.api.platform.shared;

/**
 * Writes failures to the platform's log.
 */
public interface FailureLog {

    void logFailure(String message, Throwable cause);
}
