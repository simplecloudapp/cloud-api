package app.simplecloud.api.internal.web;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

public final class Identifiers {

    private static final Pattern UUID_PATTERN = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final int MAX_PLAYER_NAME_BYTES = 16;

    private Identifiers() {
    }

    public static boolean isUuid(String value) {
        return value != null && UUID_PATTERN.matcher(value).matches();
    }

    public static boolean isPlayerName(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        return value.getBytes(StandardCharsets.UTF_8).length <= MAX_PLAYER_NAME_BYTES;
    }
}
