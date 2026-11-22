package app.simplecloud.api.internal.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import app.simplecloud.api.CloudApiException;
import app.simplecloud.api.CloudApiException.Reason;
import app.simplecloud.api.web.ApiException;
import java.net.ConnectException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ApiExceptionsTest {

    @ParameterizedTest
    @CsvSource({
        "400, INVALID_REQUEST",
        "422, INVALID_REQUEST",
        "401, UNAUTHORIZED",
        "403, UNAUTHORIZED",
        "404, NOT_FOUND",
        "409, CONFLICT",
        "408, UNAVAILABLE",
        "429, UNAVAILABLE",
        "502, UNAVAILABLE",
        "503, UNAVAILABLE",
        "504, UNAVAILABLE",
        "500, UNKNOWN"
    })
    void mapsStatusCodeToReason(int statusCode, Reason reason) {
        CloudApiException exception = ApiExceptions.from(new ApiException(statusCode, "failed"));

        assertEquals(reason, exception.getReason());
        assertEquals(statusCode, exception.getStatusCode());
    }

    @Test
    void usesErrorFieldOfControllerResponse() {
        ApiException failure = new ApiException("Conflict", 409, Map.of(), "{\"error\":\"name is already taken\"}");

        CloudApiException exception = ApiExceptions.from(failure);

        assertEquals("name is already taken (409)", exception.getMessage());
    }

    @Test
    void truncatesNonJsonResponseBody() {
        ApiException failure = new ApiException("Bad Gateway", 502, Map.of(), "x".repeat(500));

        CloudApiException exception = ApiExceptions.from(failure);

        assertEquals("x".repeat(200) + " (502)", exception.getMessage());
    }

    @Test
    void treatsIoFailureWithoutResponseAsUnavailable() {
        CloudApiException exception = ApiExceptions.from(new ApiException(new ConnectException("Connection refused")));

        assertEquals(Reason.UNAVAILABLE, exception.getReason());
        assertEquals(0, exception.getStatusCode());
        assertEquals("Controller unreachable: Connection refused", exception.getMessage());
    }

    @Test
    void treatsOtherFailuresWithoutResponseAsUnknown() {
        CloudApiException exception = ApiExceptions.from(new ApiException(new IllegalStateException("broken")));

        assertEquals(Reason.UNKNOWN, exception.getReason());
    }
}
