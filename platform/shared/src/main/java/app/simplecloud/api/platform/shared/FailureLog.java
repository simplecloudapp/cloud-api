package app.simplecloud.api.platform.shared;

public interface FailureLog {

    void logFailure(String message, Throwable cause);
}
