package com.aircargo.common.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guard de las rutas públicas del JwtAuthFilter (el que ejecutan los servicios con
 * este filtro, p. ej. auth-service).
 *
 * Regresión cubierta: con una cookie httpOnly caducada o firmada con otro secreto, el
 * filtro intentaba parsearla y abortaba con 401 en rutas que deben ser públicas. Eso
 * dejaba al usuario sin poder iniciar sesión ni recuperar su MFA (el navegador siempre
 * envía la cookie; curl sin cookie no reproducía el fallo). Debe mantenerse alineada con
 * la lista equivalente del JwtGatewayFilter.
 */
class JwtAuthFilterPublicPathsTest {

    @Test
    void rutasPublicasDeAuth_noValidanToken() {
        String[] publicas = {
                "/api/auth/login",
                "/api/auth/set-password",
                "/api/auth/set-password-token",
                "/api/auth/reset-password/validate",
                "/api/auth/refresh",
                "/api/auth/heartbeat",
                "/api/auth/mfa/enroll/setup",
                "/api/auth/mfa/enroll/enable",
                // Regresión: sin esta entrada, una cookie caducada provocaba 401 en el
                // autoservicio de recuperación de MFA.
                "/api/auth/mfa/recovery/request",
                "/api/auth/mfa/recovery/confirm",
        };
        for (String uri : publicas) {
            assertTrue(JwtAuthFilter.isPublicPath(uri), "debería ser pública: " + uri);
        }
    }

    @Test
    void rutasProtegidas_siguenValidandoToken() {
        String[] protegidas = {
                "/api/auth/logout",
                "/api/auth/mfa/setup",
                "/api/auth/mfa/reset",
                "/api/users",
                "/api/mawbs",
                "/api/receipts",
        };
        for (String uri : protegidas) {
            assertFalse(JwtAuthFilter.isPublicPath(uri), "no debería ser pública: " + uri);
        }
    }

    @Test
    void nullNoEsPublica() {
        assertFalse(JwtAuthFilter.isPublicPath(null));
    }
}
