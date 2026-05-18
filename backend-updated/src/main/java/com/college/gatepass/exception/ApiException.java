package com.college.gatepass.exception;

/**
 * A runtime exception that carries an HTTP status code alongside the message.
 *
 * <p>Throw this anywhere in the service or controller layer when something
 * goes wrong that the client should know about (bad input, not found, forbidden, etc.).
 * {@code GlobalExceptionHandler} catches it and turns it into the correct HTTP response.
 *
 * <p>Use the static factory methods instead of {@code new ApiException()} directly —
 * they keep the code readable:
 * <pre>
 *   throw ApiException.notFound("Pass not found");
 *   throw ApiException.forbidden("Only students can create passes");
 *   throw ApiException.conflict("Pass is not pending");
 * </pre>
 */
public class ApiException extends RuntimeException {

    /** The HTTP status code this error should produce (e.g. 400, 403, 404, 409). */
    private final int status;

    /**
     * Creates a new ApiException with an explicit status code and message.
     *
     * @param status  the HTTP status code to return to the client (e.g. 404)
     * @param message a short human-readable description of what went wrong
     */
    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    /**
     * Returns the HTTP status code for this exception.
     *
     * @return an integer HTTP status code (e.g. 400, 403, 404, 409)
     */
    public int getStatus() {
        return status;
    }

    // ── Static factory helpers ──────────────────────────────────────────────

    /**
     * Creates a 404 Not Found exception.
     *
     * @param message description of what could not be found
     * @return a new ApiException with status 404
     */
    public static ApiException notFound(String message) {
        return new ApiException(404, message);
    }

    /**
     * Creates a 400 Bad Request exception.
     *
     * @param message description of what the client did wrong
     * @return a new ApiException with status 400
     */
    public static ApiException badRequest(String message) {
        return new ApiException(400, message);
    }

    /**
     * Creates a 403 Forbidden exception.
     *
     * @param message description of why the action is not allowed
     * @return a new ApiException with status 403
     */
    public static ApiException forbidden(String message) {
        return new ApiException(403, message);
    }

    /**
     * Creates a 409 Conflict exception.
     *
     * @param message description of the conflict (e.g. "Pass is not pending")
     * @return a new ApiException with status 409
     */
    public static ApiException conflict(String message) {
        return new ApiException(409, message);
    }

    /**
     * Creates a 429 Too Many Requests exception for rate-limit violations.
     *
     * @param message description telling the client to slow down
     * @return a new ApiException with status 429
     */
    public static ApiException tooManyRequests(String message) {
        return new ApiException(429, message);
    }
}