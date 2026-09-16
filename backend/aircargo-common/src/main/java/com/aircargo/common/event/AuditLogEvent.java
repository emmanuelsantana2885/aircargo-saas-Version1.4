package com.aircargo.common.event;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Evento de auditoría publicado por los servicios (exchange {@code aircargo.events},
 * routing key {@code audit.log}). Lo consume auth-service, que es quien persiste
 * el registro en la tabla compartida {@code audit_log} del schema {@code auth}.
 *
 * Fase 1 (aislamiento de auth): el producto/publicador solo fired-and-forgets el
 * evento; la persistencia y la lectura (query-side de Seguridad) quedan en auth.
 */
public record AuditLogEvent(
        UUID userId,
        String email,
        String fullName,
        String action,
        String entityType,
        String entityId,
        String details,
        String ipAddress,
        OffsetDateTime createdAt) {

    public AuditLogEvent(UUID userId, String email, String fullName, String action,
                         String entityType, String entityId, String details, String ipAddress) {
        this(userId, email, fullName, action, entityType, entityId, details, ipAddress, OffsetDateTime.now());
    }
}