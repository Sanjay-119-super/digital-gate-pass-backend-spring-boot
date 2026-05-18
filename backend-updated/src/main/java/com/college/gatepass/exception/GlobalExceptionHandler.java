package com.college.gatepass.exception;

import jakarta.persistence.OptimisticLockException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Catches every exception thrown by controllers and services and converts it
 * into a consistent JSON error body.
 *
 * <p>Every error response looks like this:
 * <pre>
 * {
 *   "timestamp": "2024-08-01T10:00:00Z",
 *   "status": 404,
 *   "error": "Pass not found"
 * }
 * </pre>
 *
 * <p>This means controllers never need their own try/catch blocks —
 * they just throw the right exception and this class handles the rest.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Builds the standard error response body.
     *
     * @param status  the HTTP status code to include in the body
     * @param message the human-readable error description
     * @return a map that Jackson will serialize to JSON
     */
    private Map<String, Object> body(int status, String message) {
        // LinkedHashMap preserves insertion order so the JSON fields appear
        // in the same order every time (timestamp, status, error).
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("timestamp", Instant.now().toString());
        map.put("status", status);
        map.put("error", message);
        return map;
    }

    /**
     * Handles our own {@code ApiException}.
     * Uses the status code embedded inside the exception.
     *
     * @param e the ApiException thrown by a service or controller
     * @return a response with the correct HTTP status and a JSON body
     */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<?> handleApiException(ApiException e) {
        return ResponseEntity.status(e.getStatus()).body(body(e.getStatus(), e.getMessage()));
    }

    /**
     * Handles JPA optimistic locking failures.
     *
     * <p>This happens when two requests try to update the same gate pass at
     * the same time (e.g. two wardens clicking approve simultaneously).
     * The losing request gets a 409 Conflict so it can retry.
     *
     * @param e the optimistic lock exception from JPA or Spring Data
     * @return a 409 Conflict response with a retry suggestion
     */
    @ExceptionHandler({OptimisticLockException.class, OptimisticLockingFailureException.class})
    public ResponseEntity<?> handleOptimisticLock(Exception e) {
        return ResponseEntity.status(409)
                .body(body(409, "This pass was modified by someone else at the same time. Please reload and try again."));
    }

    /**
     * Handles Bean Validation failures (from {@code @Valid} on request bodies).
     *
     * <p>Joins all field errors into one readable string so the client knows
     * exactly which fields are wrong and why.
     *
     * @param e the validation exception with the list of field errors
     * @return a 400 Bad Request response listing every invalid field
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.status(400).body(body(400, message));
    }

    /**
     * Handles Spring Security's access denied error (user is logged in but
     * does not have the required role).
     *
     * @param e the access denied exception
     * @return a 403 Forbidden response
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<?> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity.status(403).body(body(403, "You do not have permission to perform this action."));
    }

    /**
     * Handles Spring Security authentication failures (e.g. expired or missing token).
     *
     * @param e the authentication exception
     * @return a 401 Unauthorized response
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<?> handleAuthentication(AuthenticationException e) {
        return ResponseEntity.status(401).body(body(401, e.getMessage()));
    }

    /**
     * Catches any other unhandled exception to prevent stack traces from leaking to clients.
     *
     * <p>The full exception is logged by Spring's default logging — the client only
     * sees a generic 500 message.
     *
     * @param e the unexpected exception
     * @return a 500 Internal Server Error response with a generic message
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGeneric(Exception e) {
        // Log it (Spring logs it automatically via the filter chain)
        return ResponseEntity.status(500)
                .body(body(500, "An unexpected error occurred. Please try again later."));
    }
}