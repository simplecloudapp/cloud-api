package app.simplecloud.api.internal.networkstate.read;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/**
 * Times as the controller writes them: Go's {@code time.RFC3339Nano} in UTC. The fraction loses its trailing
 * zeros, and a zero fraction loses its dot, so {@code 10:00:00.120Z} becomes {@code 10:00:00.12Z}.
 */
final class ControllerTimestamps {

    private ControllerTimestamps() {
    }

    static String ofMicros(long micros) {
        Instant time = Instant.EPOCH.plus(micros, ChronoUnit.MICROS);
        return textOf(time);
    }

    static String ofNanos(long nanos) {
        Instant time = Instant.EPOCH.plusNanos(nanos);
        return textOf(time);
    }

    private static String textOf(Instant time) {
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
