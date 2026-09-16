package com.aircargo.common.audit;

import com.aircargo.common.event.AuditLogEvent;
import com.aircargo.common.util.IpAnonymizer;
import com.aircargo.common.util.TextUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Auditoría compartida entre servicios — FUENTE ÚNICA DE VERDAD.
 *
 * Estrategia de escritura (Fase 1 — aislamiento de auth): publish AMQP como vía
 * PRIMARIA (routing key {@code audit.log} → auth-service consume y persiste en
 * {@code auth.audit_log} vía cola durable/DLQ). Si el broker no está disponible
 * o el publish falla → fallback JDBC directo en la tabla compartida calificada
 * {@code auth.audit_log}. Es SIEMPRE una sola escritura (AMQP o JDBC, nunca ambas).
 *
 * Bean name explícito para no colisionar con el AuditService local de auth-service.
 * Condicional a AMQP además de JDBC: los servicios sin spring-amqp (p.ej. export)
 * no instancian el bean (evita NoClassDefFoundError al cargar la clase).
 */
@Service("sharedAuditService")
@ConditionalOnClass({JdbcTemplate.class, RabbitTemplate.class})
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    public static final String AUDIT_EXCHANGE = "aircargo.events";
    public static final String AUDIT_ROUTING_KEY = "audit.log";

    private static final String INSERT_SQL =
            "INSERT INTO auth.audit_log (id, user_id, email, full_name, action, entity_type, entity_id, details, ip_address, created_at) "
          + "VALUES (gen_random_uuid(), ?, ?, ?, ?, ?, ?, ?, ?, now())";

    private final Optional<JdbcTemplate> jdbcTemplate;
    /** Opcional (setter): si presente, la escritura va por AMQP en lugar de JDBC. */
    private RabbitTemplate rabbitTemplate;

    public AuditService(@Autowired(required = false) JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Optional.ofNullable(jdbcTemplate);
        if (jdbcTemplate == null) {
            log.warn("AuditService sin JdbcTemplate: el fallback JDBC no podrá persistir eventos");
        }
    }

    /** Inyección opcional por setter para no romper los constructores usados en tests. */
    @Autowired(required = false)
    public void setRabbitTemplate(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void log(UUID userId, String email, String fullName, String action,
                    String entityType, String entityId, String details, String ipAddress) {
        write(userId, email, fullName, action, entityType, entityId, details,
                IpAnonymizer.truncate(ipAddress));
    }

    private void write(UUID userId, String email, String fullName, String action,
                       String entityType, String entityId, String details, String safeIp) {
        if (rabbitTemplate != null) {
            try {
                rabbitTemplate.convertAndSend(AUDIT_EXCHANGE, AUDIT_ROUTING_KEY,
                        new AuditLogEvent(userId, email, fullName, action, entityType,
                                entityId, TextUtil.safe(details), safeIp));
                return;
            } catch (Exception e) {
                log.warn("Publish AMQP de auditoría falló (fallback JDBC): {}", e.getMessage());
            }
        }

        boolean persisted = jdbcTemplate.map(jdbc -> {
            try {
                jdbc.update(INSERT_SQL, userId, email, fullName, action,
                        entityType, entityId, TextUtil.safe(details), safeIp);
                return true;
            } catch (Exception e) {
                log.error("Audit INSERT falló: {}", e.getMessage());
                return false;
            }
        }).orElse(false);
        if (!persisted) {
            // sin BD ni broker no hay dónde registrar el evento: dejar rastro en el log local
            log.warn("Auditoría NO persistida | {} | {} | {}", action, email, safeIp);
        }
    }

    public void logLogin(UUID userId, String email, String fullName, String ipAddress) {
        log(userId, email, fullName, "LOGIN", null, null, null, ipAddress);
    }
}