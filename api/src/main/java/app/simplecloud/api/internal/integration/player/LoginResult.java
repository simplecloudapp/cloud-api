package app.simplecloud.api.internal.integration.player;

import org.jetbrains.annotations.Nullable;

public record LoginResult(
        boolean success,
        @Nullable String sessionId,
        @Nullable String errorMessage) {

    public static LoginResult success(String sessionId) {
        return new LoginResult(true, sessionId, null);
    }

    public static LoginResult failure(String errorMessage) {
        return new LoginResult(false, null, errorMessage);
    }
}
