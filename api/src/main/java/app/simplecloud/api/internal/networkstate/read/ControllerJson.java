package app.simplecloud.api.internal.networkstate.read;

import app.simplecloud.api.internal.web.Identifiers;
import app.simplecloud.api.web.JSON;
import com.google.gson.reflect.TypeToken;
import com.google.protobuf.ByteString;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/**
 * Reads and writes values as the controller's HTTP API does, so that an object built from a copy holds the
 * same values as an object read over HTTP: the JSON the controller rendered goes through the HTTP client's
 * Gson, and ids and times get the controller's text forms.
 */
final class ControllerJson {

    private static final HexFormat HEX = HexFormat.of();
    private static final Type PROPERTIES = new TypeToken<Map<String, Object>>() {
    }.getType();

    private ControllerJson() {
    }

    /**
     * Reads JSON the controller rendered into a generated model, as the HTTP client reads a response body.
     */
    static <T> T toModel(ByteString json, Class<T> model) {
        return JSON.deserialize(json.toStringUtf8(), model);
    }

    /**
     * Reads a properties object, or null where the controller wrote null.
     */
    static @Nullable Map<String, Object> toProperties(ByteString json) {
        return JSON.deserialize(json.toStringUtf8(), PROPERTIES);
    }

    /**
     * Formats a 16-byte id as pgx does: lowercase hex in 8-4-4-4-12 groups.
     */
    static String uuid(ByteString id) {
        String hex = HEX.formatHex(id.toByteArray());
        return hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-" + hex.substring(12, 16) + "-"
                + hex.substring(16, 20) + "-" + hex.substring(20);
    }

    /**
     * Returns the 16 bytes of an id in the 8-4-4-4-12 form, in either case, or null for text in any other
     * form, which matches no item.
     */
    static @Nullable ByteString idBytes(@Nullable String uuid) {
        if (!Identifiers.isUuid(uuid)) {
            return null;
        }
        UUID parsed = UUID.fromString(uuid);
        ByteBuffer bytes = ByteBuffer.allocate(16);
        bytes.putLong(parsed.getMostSignificantBits());
        bytes.putLong(parsed.getLeastSignificantBits());
        return ByteString.copyFrom(bytes.array());
    }

    /**
     * Formats a time as Go's {@code time.RFC3339Nano} in UTC: the fraction loses its trailing zeros, and a
     * zero fraction loses its dot, so {@code 10:00:00.120Z} becomes {@code 10:00:00.12Z}.
     */
    static String timestamp(Instant time) {
        LocalDateTime utc = LocalDateTime.ofEpochSecond(time.getEpochSecond(), 0, ZoneOffset.UTC);
        StringBuilder text = new StringBuilder();
        appendYear(text, utc.getYear());
        text.append('-');
        appendTwoDigits(text, utc.getMonthValue());
        text.append('-');
        appendTwoDigits(text, utc.getDayOfMonth());
        text.append('T');
        appendTwoDigits(text, utc.getHour());
        text.append(':');
        appendTwoDigits(text, utc.getMinute());
        text.append(':');
        appendTwoDigits(text, utc.getSecond());
        appendFraction(text, time.getNano());
        text.append('Z');
        return text.toString();
    }

    static Instant fromMicros(long micros) {
        return Instant.EPOCH.plus(micros, ChronoUnit.MICROS);
    }

    static Instant fromNanos(long nanos) {
        return Instant.EPOCH.plusNanos(nanos);
    }

    private static void appendYear(StringBuilder text, int year) {
        if (year < 0) {
            text.append('-');
        }
        String digits = Integer.toString(Math.abs(year));
        text.append("0".repeat(Math.max(0, 4 - digits.length())));
        text.append(digits);
    }

    private static void appendTwoDigits(StringBuilder text, int value) {
        if (value < 10) {
            text.append('0');
        }
        text.append(value);
    }

    private static void appendFraction(StringBuilder text, int nanos) {
        if (nanos == 0) {
            return;
        }
        String nineDigits = String.format("%09d", nanos);
        int end = nineDigits.length();
        while (nineDigits.charAt(end - 1) == '0') {
            end--;
        }
        text.append('.').append(nineDigits, 0, end);
    }
}
