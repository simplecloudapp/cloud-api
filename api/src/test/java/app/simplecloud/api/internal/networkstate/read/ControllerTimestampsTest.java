package app.simplecloud.api.internal.networkstate.read;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The expected texts are what Go 1.26 printed with {@code Format(time.RFC3339Nano)}.
 */
class ControllerTimestampsTest {

    @ParameterizedTest(name = "{1}")
    @CsvSource(delimiter = '|', value = {
            "1794909600000000|2026-11-17T10:00:00Z",
            "1794909600120000|2026-11-17T10:00:00.12Z",
            "1794909600000001|2026-11-17T10:00:00.000001Z",
            "-1|1969-12-31T23:59:59.999999Z",
            "327403479845000000|12345-01-02T03:04:05Z",
            "-62324888155000000|-0005-01-02T03:04:05Z",
            "-62009268955000000|0005-01-02T03:04:05Z",
    })
    void microsFormatAsGo(long micros, String goText) {
        assertEquals(goText, ControllerTimestamps.ofMicros(micros));
    }

    @Test
    void nanosKeepAllNineDigits() {
        long nanos = 1794909600123456789L;

        assertEquals("2026-11-17T10:00:00.123456789Z", ControllerTimestamps.ofNanos(nanos));
    }
}
