package app.simplecloud.api.internal.networkstate.read;

import app.simplecloud.api.internal.web.Identifiers;
import com.google.protobuf.ByteString;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * Ids in the text form the controller writes and accepts, and in the 16 bytes a copy keys its items by.
 */
final class ControllerIds {

    private static final int ID_BYTES = 16;

    private ControllerIds() {
    }

    /**
     * Formats a 16-byte id as pgx does: lowercase hex in 8-4-4-4-12 groups. Throws
     * {@link IllegalArgumentException} for an id of another length, which is a fault of the copy.
     */
    static String text(ByteString id) {
        if (id.size() != ID_BYTES) {
            throw new IllegalArgumentException("An id has " + id.size() + " bytes, not " + ID_BYTES);
        }
        ByteBuffer idBytes = id.asReadOnlyByteBuffer();
        long mostSignificant = idBytes.getLong();
        long leastSignificant = idBytes.getLong();
        UUID uuid = new UUID(mostSignificant, leastSignificant);
        return uuid.toString();
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
        ByteBuffer bytes = ByteBuffer.allocate(ID_BYTES);
        bytes.putLong(parsed.getMostSignificantBits());
        bytes.putLong(parsed.getLeastSignificantBits());
        return ByteString.copyFrom(bytes.array());
    }
}
