package com.aircargo.common.error;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Guard de contrato de errores del advice compartido (AGENTS.md workstream API/DTO).
 *
 * <p>Los "no encontrado" de recursos (evidencias/PDF/Excel de un recibo inexistente)
 * deben responder 404, no 400. Se conserva la regresión de que
 * {@link IllegalArgumentException} sigue mapeando a 400.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private HttpServletRequest request(String uri) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn(uri);
        return request;
    }

    @Test
    void resourceNotFound_mapsTo404() {
        ResponseEntity<Map<String, Object>> response = handler.handleResourceNotFound(
                new ResourceNotFoundException("Recibo no encontrado: 123"),
                request("/api/receipts/123/supporting-docs"));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertEquals(HttpStatus.NOT_FOUND.value(), body.get("status"));
        assertEquals("Not Found", body.get("error"));
        assertEquals("Recibo no encontrado: 123", body.get("message"));
        assertEquals("/api/receipts/123/supporting-docs", body.get("path"));
    }

    @Test
    void illegalArgument_stillMapsTo400_regression() {
        ResponseEntity<Map<String, Object>> response = handler.handleIllegalArgument(
                new IllegalArgumentException("dato inválido"),
                request("/api/receipts"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertEquals(HttpStatus.BAD_REQUEST.value(), body.get("status"));
        assertEquals("Bad Request", body.get("error"));
        assertEquals("dato inválido", body.get("message"));
    }
}
