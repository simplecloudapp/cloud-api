package app.simplecloud.api.internal.networkstate.read;

import app.simplecloud.api.internal.web.Identifiers;
import com.google.protobuf.ByteString;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Ids in the text form the controller writes and accepts, and in the 16 bytes a copy keys its items by.
 */
final class ControllerIds {

    private static final HexFormat HEX = HexFormat.of();

    private ControllerIds() {
    }

    /**
     * Formats a 16-byte id as pgx does: lowercase hex in 8-4-4-4-12 groups.
     */
    static String text(ByteString id) {
        String hex = HEX.formatHex(id.toByteArray());
        return hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-" + hex.substring(12, 16) + "-"
                + hex.substring(16, 20) + "-" + hex.substring(20);
    }

    /**
     * Returns the 16 bytes of an id in the 8-4-4-4-12 form, in either case, or null for text in any other
     * form, which matches no item.
     */
    static @Nullable ByteString bytes(@Nullable String text) {
        if (!Identifiers.isUuid(text)) {
            return null;
        }
        UUID parsed = UUID.fromString(text);
        ByteBuffer bytes = ByteBuffer.allocate(16);
        bytes.putLong(parsed.getMostSignificantBits());
        bytes.putLong(parsed.getLeastSignificantBits());
        return ByteString.copyFrom(bytes.array());
    }
}
