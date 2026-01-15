package app.simplecloud.api.internal.integration.player;

/**
 * Result of a player login request.
 */
public class LoginResult {

    private final boolean success;
    private final String sessionId;
    private final String errorMessage;

    private LoginResult(boolean success, String sessionId, String errorMessage) {
        this.success = success;
        this.sessionId = sessionId;
        this.errorMessage = errorMessage;
    }

    public static LoginResult success(String sessionId) {
        return new LoginResult(true, sessionId, null);
    }

    public static LoginResult failure(String errorMessage) {
        return new LoginResult(false, null, errorMessage);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
