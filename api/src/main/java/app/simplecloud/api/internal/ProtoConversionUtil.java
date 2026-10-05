package app.simplecloud.api.internal;

import app.simplecloud.api.server.ServerState;

/**
 * Utility class for converting protobuf enums to API models.
 */
public final class ProtoConversionUtil {

    private ProtoConversionUtil() {
    }

    public static String convertServerStateToString(build.buf.gen.simplecloud.controller.v2.ServerState protoState) {
        if (protoState == null) {
            return null;
        }

        String name = protoState.name();
        if (name == null || name.isEmpty() || name.equals("UNRECOGNIZED")) {
            return null;
        }

        if (name.startsWith("SERVER_STATE_")) {
            return name.substring("SERVER_STATE_".length());
        }

        return name;
    }

    public static ServerState convertServerState(build.buf.gen.simplecloud.controller.v2.ServerState protoState) {
        if (protoState == null) {
            return null;
        }

        String name = protoState.name();
        if (name == null || name.isEmpty() || name.equals("UNRECOGNIZED")) {
            return null;
        }

        if (name.startsWith("SERVER_STATE_")) {
            name = name.substring("SERVER_STATE_".length());
        }

        try {
            return ServerState.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static String convertServerTypeToString(build.buf.gen.simplecloud.controller.v2.ServerType protoType) {
        if (protoType == null) {
            return null;
        }

        String name = protoType.name();
        if (name == null || name.isEmpty() || name.equals("UNRECOGNIZED")) {
            return null;
        }

        if (name.startsWith("SERVER_TYPE_")) {
            return name.substring("SERVER_TYPE_".length());
        }

        return name;
    }

    public static String convertChatModeToString(build.buf.gen.simplecloud.player.v2.ChatMode protoChatMode) {
        return convertPlayerEnumToString(protoChatMode, "CHAT_MODE_");
    }

    public static String convertMainHandToString(build.buf.gen.simplecloud.player.v2.MainHand protoMainHand) {
        return convertPlayerEnumToString(protoMainHand, "MAIN_HAND_");
    }

    private static String convertPlayerEnumToString(Enum<?> protoEnum, String prefix) {
        if (protoEnum == null) {
            return null;
        }

        String name = protoEnum.name();
        if (name.isEmpty() || name.equals("UNRECOGNIZED") || name.endsWith("_UNSPECIFIED")) {
            return null;
        }

        if (name.startsWith(prefix)) {
            return name.substring(prefix.length());
        }

        return name;
    }

}
