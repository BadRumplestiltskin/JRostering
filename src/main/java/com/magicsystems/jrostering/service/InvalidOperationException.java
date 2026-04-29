package com.magicsystems.jrostering.service;

/**
 * Thrown when a requested operation is not permitted given the current state
 * of the domain — for example, editing a shift on a period that is currently
 * being solved, or creating a period 2 before period 1 is solved.
 * Maps to HTTP 409 at the API layer.
 */
public final class InvalidOperationException extends JRosteringException {

    @java.io.Serial
    private static final long serialVersionUID = 1L;

    public InvalidOperationException(String message) {
        super(message);
    }
}
