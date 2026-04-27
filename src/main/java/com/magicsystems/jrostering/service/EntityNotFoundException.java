package com.magicsystems.jrostering.service;

/**
 * Thrown when a requested entity cannot be found in the database.
 * Maps to HTTP 404 at the API layer.
 */
public class EntityNotFoundException extends RuntimeException {

    @java.io.Serial
    private static final long serialVersionUID = 1L;

    public EntityNotFoundException(String message) {
        super(message);
    }

    public static EntityNotFoundException of(String entityName, Long id) {
        return new EntityNotFoundException(entityName + " not found with id: " + id);
    }
}
