package app.simplecloud.api.internal.networkstate.read;

import app.simplecloud.api.web.JSON;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.protobuf.ByteString;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Reads and writes JSON values as the controller's HTTP API does, so that an object built from a copy goes
 * through the same Gson, and the same generated model adapters, as an object read over HTTP.
 */
final class ControllerJson {

    private static final HexFormat HEX = HexFormat.of();
    private static final BigDecimal SMALLEST_PLAIN_FLOAT = new BigDecimal("1e-6");
    private static final BigDecimal LARGEST_PLAIN_FLOAT = new BigDecimal("1e21");

    private ControllerJson() {
    }

    /**
     * Parses JSON text the controller rendered, with the adapter the HTTP client uses for a response body.
     */
    static JsonElement parse(ByteString json) {
        return JSON.getGson().fromJson(json.toStringUtf8(), JsonElement.class);
    }

    static JsonObject parseObject(ByteString json) {
        return parse(json).getAsJsonObject();
    }

    static <T> T toModel(JsonElement json, Class<T> model) {
        return JSON.getGson().fromJson(json, model);
    }

    /**
     * Returns a text member, or null where the controller wrote null or nothing.
     */
    static @Nullable String text(JsonObject object, String member) {
        JsonElement value = object.get(member);
        return value == null || value.isJsonNull() ? null : value.getAsString();
    }

    /**
     * Returns a member that holds a list of texts, or null where the controller wrote null or nothing.
     */
    static @Nullable List<String> texts(JsonObject object, String member) {
        JsonElement value = object.get(member);
        if (value == null || value.isJsonNull()) {
            return null;
        }
        List<String> texts = new ArrayList<>();
        for (JsonElement element : value.getAsJsonArray()) {
            texts.add(element.isJsonNull() ? null : element.getAsString());
        }
        return texts;
    }

    static @Nullable Boolean bool(JsonObject object, String member) {
        JsonElement value = object.get(member);
        return value == null || value.isJsonNull() ? null : value.getAsBoolean();
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

    /**
     * Returns the number Go's JSON encoder writes for {@code value}: the shortest digits that read back as
     * {@code value}, plain from 1e-6 up to 1e21 and in exponent form outside. The generated models hold such
     * numbers as {@link BigDecimal}, whose equality counts the digits after the point, so {@code 0} must stay
     * {@code 0} and not become {@code 0.0}.
     *
     * @throws IllegalArgumentException for NaN and the infinities, which Go's encoder refuses
     */
    static JsonPrimitive goFloat(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Go's JSON encoder cannot write " + value);
        }
        BigDecimal shortest = new BigDecimal(Double.toString(value)).stripTrailingZeros();
        BigDecimal magnitude = shortest.abs();
        boolean plain = magnitude.signum() == 0
                || (magnitude.compareTo(SMALLEST_PLAIN_FLOAT) >= 0 && magnitude.compareTo(LARGEST_PLAIN_FLOAT) < 0);
        BigDecimal written = plain ? shortest.setScale(Math.max(0, shortest.scale())) : shortest;
        return new JsonPrimitive(written);
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
