package app.simplecloud.api.internal.integration.player;

/**
 * Result of a player login request.
 */
public record LoginResult(boolean success, String sessionId, String errorMessage) {

    public static LoginResult success(String sessionId) {
        return new LoginResult(true, sessionId, null);
    }

    public static LoginResult failure(String errorMessage) {
        return new LoginResult(false, null, errorMessage);
    }
}
