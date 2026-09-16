package com.aircargo.authservice.config;

import com.aircargo.authservice.entity.AuditLog;
import com.aircargo.authservice.repository.AuditLogRepository;
import com.aircargo.common.event.AuditLogEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Consume {@code audit.log} y persiste en {@code auth.audit_log} los eventos de
 * auditoría emitidos por los demás servicios (vía sharedAuditService de common).
 *
 * Estrategia: si un evento no puede persistirse se relanza la excepción → el
 * interceptor de retry reintenta 3 veces y, si sigue fallando, el mensaje va a la
 * DLQ ({@code aircargo.auth.audit-log.dlq}) para reproceso — nunca se pierde ni
 * se re-encola infinito.
 */
@Component
@ConditionalOnProperty(name = "app.rabbitmq.enabled", havingValue = "true", matchIfMissing = false)
public class AuditLogListener {

    private static final Logger log = LoggerFactory.getLogger(AuditLogListener.class);

    private final AuditLogRepository auditLogRepository;

    public AuditLogListener(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @RabbitListener(queues = RabbitConfig.QUEUE_AUDIT_LOG, containerFactory = "retryListenerFactory")
    public void onAuditLog(AuditLogEvent event) {
        try {
            AuditLog entity = AuditLog.builder()
                    .userId(event.userId())
                    .email(event.email())
                    .fullName(event.fullName())
                    .action(event.action())
                    .entityType(event.entityType())
                    .entityId(event.entityId())
                    .details(event.details())
                    .ipAddress(event.ipAddress())
                    .createdAt(event.createdAt())
                    .build();
            auditLogRepository.save(entity);
        } catch (Exception e) {
            log.error("No se pudo persistir evento de auditoría ({}) de {}: {}",
                    event.action(), event.email(), e.getMessage());
            throw e;
        }
    }
}