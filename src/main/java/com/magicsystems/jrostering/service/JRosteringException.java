package com.magicsystems.jrostering.service;

/**
 * Sealed base for all JRostering service-layer exceptions.
 *
 * <p>Permits only {@link EntityNotFoundException} (HTTP 404) and
 * {@link InvalidOperationException} (HTTP 409). Callers that need to catch
 * any service error can catch this type; the exhaustive {@code switch} in
 * {@code GlobalExceptionHandler} ensures every subtype maps to a response.</p>
 */
public abstract sealed class JRosteringException extends RuntimeException
        permits EntityNotFoundException, InvalidOperationException {

    @java.io.Serial
    private static final long serialVersionUID = 1L;

    protected JRosteringException(String message) {
        super(message);
    }
}
