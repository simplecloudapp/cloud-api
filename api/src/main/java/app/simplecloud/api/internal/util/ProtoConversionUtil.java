package app.simplecloud.api.internal.util;

import app.simplecloud.api.server.ServerState;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;

/**
 * Utility class for converting protobuf values to API models.
 */
public final class ProtoConversionUtil {

    private ProtoConversionUtil() {}

    @Nullable
    public static String convertServerStateToString(
            @Nullable build.buf.gen.simplecloud.controller.v2.ServerState protoState) {
        return enumName(protoState, "SERVER_STATE_");
    }

    @Nullable
    public static ServerState convertServerState(
            @Nullable build.buf.gen.simplecloud.controller.v2.ServerState protoState) {
        String name = convertServerStateToString(protoState);
        if (name == null) {
            return null;
        }
        try {
            return ServerState.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Nullable
    public static String convertServerTypeToString(
            @Nullable build.buf.gen.simplecloud.controller.v2.ServerType protoType) {
        return enumName(protoType, "SERVER_TYPE_");
    }

    @Nullable
    public static String convertChatModeToString(
            @Nullable build.buf.gen.simplecloud.player.v2.ChatMode protoChatMode) {
        return enumName(protoChatMode, "CHAT_MODE_");
    }

    @Nullable
    public static String convertMainHandToString(
            @Nullable build.buf.gen.simplecloud.player.v2.MainHand protoMainHand) {
        return enumName(protoMainHand, "MAIN_HAND_");
    }

    public static String epochSecondsToIso(long epochSeconds) {
        return Instant.ofEpochSecond(epochSeconds).toString();
    }

    public static String epochMillisToIso(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis).toString();
    }

    @Nullable
    public static String optionalEpochMillisToIso(long epochMillis) {
        return epochMillis > 0 ? epochMillisToIso(epochMillis) : null;
    }

    @Nullable
    public static String emptyToNull(@Nullable String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    @Nullable
    public static String enumName(@Nullable Enum<?> protoEnum, String prefix) {
        if (protoEnum == null) {
            return null;
        }
        String name = protoEnum.name();
        if (name.isEmpty() || name.equals("UNRECOGNIZED") || name.endsWith("_UNSPECIFIED")) {
            return null;
        }
        return name.startsWith(prefix) ? name.substring(prefix.length()) : name;
    }
}
