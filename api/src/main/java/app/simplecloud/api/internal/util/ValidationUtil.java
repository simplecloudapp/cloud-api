package app.simplecloud.api.internal.util;

import app.simplecloud.api.group.GroupServerType;
import app.simplecloud.api.group.ScalingConfig;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

public final class ValidationUtil {

    private static final Pattern UUID_PATTERN =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final int MAX_PLAYER_NAME_BYTES = 16;

    private ValidationUtil() {}

    public static boolean isUuid(String value) {
        return value != null && UUID_PATTERN.matcher(value).matches();
    }

    public static String serverTypeName(GroupServerType type) {
        if (type == GroupServerType.UNKNOWN_SERVER) {
            throw new IllegalArgumentException("type must be SERVER or PROXY");
        }
        return type.name();
    }

    public static void requireCompleteScaling(ScalingConfig config) {
        if (config.getScalingMode() == null || !config.hasMaxServers() || !config.hasPlayerThreshold()) {
            throw new IllegalArgumentException(
                    "scaling replaces the stored config as a whole, so scalingMode, maxServers and playerThreshold must be set");
        }
    }

    public static boolean isPlayerName(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        return value.getBytes(StandardCharsets.UTF_8).length <= MAX_PLAYER_NAME_BYTES;
    }
}
