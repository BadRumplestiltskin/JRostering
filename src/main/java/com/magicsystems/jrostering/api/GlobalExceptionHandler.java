package com.magicsystems.jrostering.api;

import com.magicsystems.jrostering.service.EntityNotFoundException;
import com.magicsystems.jrostering.service.InvalidOperationException;
import com.magicsystems.jrostering.service.JRosteringException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;

/**
 * Translates service-layer exceptions into consistent JSON error responses for all
 * REST controllers under {@code /api/**}.
 *
 * <p>The {@link JRosteringException} handler uses a sealed-type switch so that
 * adding a new permitted subtype is a compile error until this class handles it.</p>
 */
@RestControllerAdvice(basePackages = "com.magicsystems.jrostering.api")
public class GlobalExceptionHandler {

    public record ErrorResponse(int status, String error, String message, OffsetDateTime timestamp) {
        static ErrorResponse of(HttpStatus status, String message) {
            return new ErrorResponse(status.value(), status.getReasonPhrase(), message, OffsetDateTime.now());
        }
    }

    /**
     * Handles all {@link JRosteringException} subtypes via exhaustive sealed switch.
     * Preferred over individual handlers so that new permitted subtypes are caught
     * at compile time rather than falling through to a 500.
     */
    @ExceptionHandler(JRosteringException.class)
    public ResponseEntity<ErrorResponse> handleJRosteringException(JRosteringException ex) {
        HttpStatus status = switch (ex) {
            case EntityNotFoundException ignored    -> HttpStatus.NOT_FOUND;
            case InvalidOperationException ignored  -> HttpStatus.CONFLICT;
        };
        return ResponseEntity.status(status).body(ErrorResponse.of(status, ex.getMessage()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(cv -> cv.getPropertyPath() + ": " + cv.getMessage())
                .sorted()
                .reduce((a, b) -> a + "; " + b)
                .orElse(ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(HttpStatus.BAD_REQUEST, message));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(HttpStatus.BAD_REQUEST, ex.getMessage()));
    }
}
