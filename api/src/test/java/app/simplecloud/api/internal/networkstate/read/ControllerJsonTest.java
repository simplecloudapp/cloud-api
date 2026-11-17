package app.simplecloud.api.internal.networkstate.read;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The expected texts are what Go 1.26 printed: {@code json.Marshal} for the numbers and
 * {@code Format(time.RFC3339Nano)} for the times.
 */
class ControllerJsonTest {

    @ParameterizedTest(name = "{1}")
    @CsvSource(delimiter = '|', value = {
            "0|0",
            "9223372036854775808|-0",
            "4623226492472524800|12.5",
            "4636737291354636288|100",
            "4906019910204099648|100000000000000000000",
            "4921056587992461136|1e+21",
            "9097811302482466869|1.5e+300",
            "4517329193108106637|0.000001",
            "4502148214488346440|1e-7",
            "4503759836881607566|1.2345e-7",
            "4728057454355546112|123456789.125",
            "13854550197419376640|-42.5",
            "9218868437227405311|1.7976931348623157e+308",
    })
    void goFloatReadsAsTheNumberGoWrites(String bits, String goText) {
        double value = Double.longBitsToDouble(Long.parseUnsignedLong(bits));

        BigDecimal written = ControllerJson.goFloat(value).getAsBigDecimal();

        assertEquals(new BigDecimal(goText), written);
    }

    /**
     * Java writes the smallest subnormal as 4.9E-324, where Go writes 5e-324. Both read back as the same
     * double, which is what a getter returns.
     */
    @Test
    void goFloatOfASubnormalReadsBackAsTheSameDouble() {
        double smallest = Double.MIN_VALUE;

        BigDecimal written = ControllerJson.goFloat(smallest).getAsBigDecimal();

        assertEquals(new BigDecimal("5e-324").doubleValue(), written.doubleValue());
    }

    @Test
    void goFloatRefusesNaNAsGoDoes() {
        assertThrows(IllegalArgumentException.class, () -> ControllerJson.goFloat(Double.NaN));
    }

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
