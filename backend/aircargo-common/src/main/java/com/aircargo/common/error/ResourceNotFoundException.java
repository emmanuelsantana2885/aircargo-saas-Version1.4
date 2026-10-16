package com.aircargo.common.error;

/**
 * Excepción de recurso no encontrado (HTTP 404).
 *
 * Distinta de {@link IllegalArgumentException} (400 Bad Request): esta se lanza
 * cuando el recurso solicitado NO existe (p. ej. un recibo con un id inexistente),
 * no cuando la petición trae datos inválidos. El {@link GlobalExceptionHandler}
 * la mapea a 404 Not Found.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
