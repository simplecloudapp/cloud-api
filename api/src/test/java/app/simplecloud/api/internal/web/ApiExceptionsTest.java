package app.simplecloud.api.internal.web;

import app.simplecloud.api.CloudApiException;
import app.simplecloud.api.web.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApiExceptionsTest {

    @ParameterizedTest
    @CsvSource({
        "400, INVALID_REQUEST",
        "422, INVALID_REQUEST",
        "401, UNAUTHORIZED",
        "403, UNAUTHORIZED",
        "404, NOT_FOUND",
        "409, CONFLICT",
        "429, UNAVAILABLE",
        "503, UNAVAILABLE",
        "500, UNKNOWN"
    })
    void statusCodeMapsToReason(int status, CloudApiException.Reason reason) {
        CloudApiException exception = ApiExceptions.from(new ApiException(status, Map.of(), "{}"));

        assertEquals(reason, exception.getReason());
        assertEquals(status, exception.getStatusCode());
    }

    @Test
    void messageUsesErrorFieldOfResponse() {
        CloudApiException exception =
                ApiExceptions.from(new ApiException(409, Map.of(), "{\"error\": \"name already taken\"}"));

        assertEquals("name already taken (409)", exception.getMessage());
    }

    @Test
    void unreachableControllerIsUnavailable() {
        CloudApiException exception =
                ApiExceptions.from(new ApiException(new IOException("Connection refused")));

        assertEquals(CloudApiException.Reason.UNAVAILABLE, exception.getReason());
        assertEquals("Controller unreachable: Connection refused", exception.getMessage());
    }
}
