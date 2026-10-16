package app.simplecloud.api;

import org.jetbrains.annotations.Nullable;

/**
 * Thrown when a request to the SimpleCloud controller fails.
 */
public class CloudApiException extends RuntimeException {

    /**
     * Why a request failed.
     */
    public enum Reason {
        /**
         * The requested resource does not exist.
         */
        NOT_FOUND,

        /**
         * The request conflicts with the current state, for example a name that is already taken.
         */
        CONFLICT,

        /**
         * The controller rejected the request as invalid.
         */
        INVALID_REQUEST,

        /**
         * The network credentials are missing or not allowed to perform the request.
         */
        UNAUTHORIZED,

        /**
         * The controller could not be reached or is temporarily unavailable.
         */
        UNAVAILABLE,

        /**
         * Any other failure.
         */
        UNKNOWN
    }

    private final Reason reason;
    private final int statusCode;

    public CloudApiException(Reason reason, String message) {
        this(reason, 0, message, null);
    }

    public CloudApiException(Reason reason, int statusCode, String message, @Nullable Throwable cause) {
        super(message, cause);
        this.reason = reason;
        this.statusCode = statusCode;
    }

    /**
     * Returns why the request failed.
     *
     * @return the failure reason
     */
    public Reason getReason() {
        return reason;
    }

    /**
     * Returns the HTTP status code of the controller response.
     *
     * @return the status code, or {@code 0} if no response was received
     */
    public int getStatusCode() {
        return statusCode;
    }
}
