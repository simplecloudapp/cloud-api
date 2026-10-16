package app.simplecloud.api.internal.web;

import app.simplecloud.api.CloudApiException;
import app.simplecloud.api.web.ApiException;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;

/**
 * Converts generated client failures into {@link CloudApiException}s.
 */
public final class ApiErrors {

    private ApiErrors() {
    }

    public static CloudApiException from(ApiException exception) {
        return from(message(exception), exception);
    }

    public static CloudApiException from(String message, ApiException exception) {
        return new CloudApiException(reason(exception), exception.getCode(), message, exception);
    }

    public static CloudApiException notFound(String message) {
        return new CloudApiException(CloudApiException.Reason.NOT_FOUND, 404, message, null);
    }

    private static CloudApiException.Reason reason(ApiException exception) {
        int statusCode = exception.getCode();
        if (statusCode <= 0) {
            return isTransportFailure(exception) ? CloudApiException.Reason.UNAVAILABLE : CloudApiException.Reason.UNKNOWN;
        }
        return switch (statusCode) {
            case 400, 422 -> CloudApiException.Reason.INVALID_REQUEST;
            case 401, 403 -> CloudApiException.Reason.UNAUTHORIZED;
            case 404 -> CloudApiException.Reason.NOT_FOUND;
            case 409 -> CloudApiException.Reason.CONFLICT;
            case 408, 429, 502, 503, 504 -> CloudApiException.Reason.UNAVAILABLE;
            default -> CloudApiException.Reason.UNKNOWN;
        };
    }

    private static boolean isTransportFailure(ApiException exception) {
        return exception.getCause() instanceof IOException;
    }

    private static String message(ApiException exception) {
        int statusCode = exception.getCode();
        if (statusCode <= 0) {
            if (isTransportFailure(exception)) {
                return "Controller unreachable: " + causeMessage(exception);
            }
            return "Controller request failed: " + exceptionMessage(exception);
        }
        if (statusCode < 300) {
            return exceptionMessage(exception) + " (" + statusCode + ")";
        }
        return errorMessage(exception) + " (" + statusCode + ")";
    }

    private static String exceptionMessage(ApiException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? causeMessage(exception) : message;
    }

    private static String errorMessage(ApiException exception) {
        String body = exception.getResponseBody();
        if (body != null && !body.isBlank()) {
            try {
                JsonElement json = JsonParser.parseString(body);
                if (json.isJsonObject()) {
                    JsonObject object = json.getAsJsonObject();
                    JsonElement error = object.get("error");
                    if (error != null && error.isJsonPrimitive() && !error.getAsString().isBlank()) {
                        return error.getAsString();
                    }
                }
            } catch (RuntimeException ignored) {
            }
            return body.length() > 200 ? body.substring(0, 200) : body;
        }
        return "Controller request failed";
    }

    private static String causeMessage(ApiException exception) {
        Throwable cause = exception.getCause();
        if (cause == null) {
            return "no response";
        }
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }
}
