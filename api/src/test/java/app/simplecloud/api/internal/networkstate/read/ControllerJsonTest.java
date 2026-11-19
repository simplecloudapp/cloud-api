package app.simplecloud.api.internal.networkstate.read;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The expected texts are what Go 1.26 printed with {@code Format(time.RFC3339Nano)}.
 */
class ControllerJsonTest {

    @ParameterizedTest(name = "{2}")
    @CsvSource(delimiter = '|', value = {
            "1794909600|0|2026-11-17T10:00:00Z",
            "1794909600|120000000|2026-11-17T10:00:00.12Z",
            "1794909600|1000|2026-11-17T10:00:00.000001Z",
            "1794909600|123456789|2026-11-17T10:00:00.123456789Z",
            "-1|999999000|1969-12-31T23:59:59.999999Z",
            "327403479845|0|12345-01-02T03:04:05Z",
            "-62324888155|0|-0005-01-02T03:04:05Z",
            "-62009268955|0|0005-01-02T03:04:05Z",
    })
    void timestampFormatsAsGo(long epochSecond, int nanos, String goText) {
        Instant time = Instant.ofEpochSecond(epochSecond, nanos);

        assertEquals(goText, ControllerJson.timestamp(time));
    }
}
