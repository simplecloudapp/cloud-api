package app.simplecloud.api.internal.networkstate.read;

import com.google.protobuf.ByteString;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Reads query parameter values by the controller's rules ({@code internal/web/request_params.go} and
 * {@code list_filters.go}), which differ from Java's own: Go trims Unicode white space, pgx accepts ids
 * without dashes, and {@code strconv.ParseInt} accepts only ASCII digits.
 */
final class ControllerQueryValues {

    private static final int DASHED_UUID_BYTES = 36;
    private static final int UNDASHED_UUID_BYTES = 32;

    private ControllerQueryValues() {
    }

    /**
     * Splits a comma-separated value as {@code getCSVQueryParam} does: each part trimmed, empty parts left
     * out, and nothing at all for a missing or blank value.
     */
    static List<String> splitCsv(@Nullable String value) {
        if (value == null) {
            return List.of();
        }
        String received = asReceived(value);
        if (trimSpace(received).isEmpty()) {
            return List.of();
        }
        List<String> parts = new ArrayList<>();
        for (String part : received.split(",", -1)) {
            String trimmed = trimSpace(part);
            if (!trimmed.isEmpty()) {
                parts.add(trimmed);
            }
        }
        return List.copyOf(parts);
    }

    /**
     * Parses every value as pgx's {@code pgtype.UUID} does, or returns empty if one of them does not parse.
     */
    static Optional<List<ByteString>> parseUuids(List<String> values) {
        List<ByteString> ids = new ArrayList<>();
        for (String value : values) {
            Optional<ByteString> id = parseUuid(value);
            if (id.isEmpty()) {
                return Optional.empty();
            }
            ids.add(id.get());
        }
        return Optional.of(List.copyOf(ids));
    }

    /**
     * Parses every value as {@link #parseInt32} does, or returns empty if one of them does not parse.
     */
    static Optional<List<Integer>> parseInt32s(List<String> values) {
        List<Integer> numbers = new ArrayList<>();
        for (String value : values) {
            OptionalInt number = parseInt32(value);
            if (number.isEmpty()) {
                return Optional.empty();
            }
            numbers.add(number.getAsInt());
        }
        return Optional.of(List.copyOf(numbers));
    }

    /**
     * Parses a value as {@code strconv.ParseInt(value, 10, 32)} does: an optional sign, then ASCII digits,
     * within the range of an int32.
     */
    private static OptionalInt parseInt32(String value) {
        int start = value.startsWith("+") || value.startsWith("-") ? 1 : 0;
        if (value.length() == start) {
            return OptionalInt.empty();
        }
        for (int index = start; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character < '0' || character > '9') {
                return OptionalInt.empty();
            }
        }
        try {
            return OptionalInt.of(Integer.parseInt(value));
        } catch (NumberFormatException outOfRange) {
            return OptionalInt.empty();
        }
    }

    /**
     * Trims as Go's {@code strings.TrimSpace}: every Unicode white space character at both ends, including
     * the no-break spaces that Java's {@link String#strip()} keeps.
     */
    static String trimSpace(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && isGoSpace(value.codePointAt(start))) {
            start += Character.charCount(value.codePointAt(start));
        }
        while (end > start && isGoSpace(value.codePointBefore(end))) {
            end -= Character.charCount(value.codePointBefore(end));
        }
        return value.substring(start, end);
    }

    /**
     * Returns the value as the controller receives it: the client encodes the query in UTF-8, which turns a
     * lone surrogate into {@code ?}.
     */
    private static String asReceived(String value) {
        return new String(value.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
    }

    /**
     * Returns whether Postgres refuses the value as text, which makes the controller answer with an error.
     */
    static boolean isRejectedAsText(String value) {
        return value.indexOf('\0') >= 0;
    }

    /**
     * pgx takes 36 bytes as dashed, without checking that the four removed bytes are dashes, and 32 bytes as
     * hex. Other lengths fail.
     */
    private static Optional<ByteString> parseUuid(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        byte[] hex;
        if (bytes.length == DASHED_UUID_BYTES) {
            hex = withoutDashPositions(bytes);
        } else if (bytes.length == UNDASHED_UUID_BYTES) {
            hex = bytes;
        } else {
            return Optional.empty();
        }
        return decodeHex(hex);
    }

    private static byte[] withoutDashPositions(byte[] dashed) {
        ByteArrayOutputStream hex = new ByteArrayOutputStream(UNDASHED_UUID_BYTES);
        for (int index = 0; index < dashed.length; index++) {
            boolean dashPosition = index == 8 || index == 13 || index == 18 || index == 23;
            if (!dashPosition) {
                hex.write(dashed[index]);
            }
        }
        return hex.toByteArray();
    }

    private static Optional<ByteString> decodeHex(byte[] hex) {
        byte[] decoded = new byte[hex.length / 2];
        for (int index = 0; index < decoded.length; index++) {
            int high = hexValue(hex[2 * index]);
            int low = hexValue(hex[2 * index + 1]);
            if (high < 0 || low < 0) {
                return Optional.empty();
            }
            decoded[index] = (byte) (high << 4 | low);
        }
        return Optional.of(ByteString.copyFrom(decoded));
    }

    private static int hexValue(byte digit) {
        if (digit >= '0' && digit <= '9') {
            return digit - '0';
        }
        if (digit >= 'a' && digit <= 'f') {
            return digit - 'a' + 10;
        }
        if (digit >= 'A' && digit <= 'F') {
            return digit - 'A' + 10;
        }
        return -1;
    }

    /**
     * Go's {@code unicode.IsSpace}: the Unicode White_Space property.
     */
    private static boolean isGoSpace(int codePoint) {
        return switch (codePoint) {
            case '\t', '\n', 0x0B, '\f', '\r', ' ', 0x85, 0xA0, 0x1680, 0x2028, 0x2029, 0x202F, 0x205F, 0x3000 -> true;
            default -> codePoint >= 0x2000 && codePoint <= 0x200A;
        };
    }
}
