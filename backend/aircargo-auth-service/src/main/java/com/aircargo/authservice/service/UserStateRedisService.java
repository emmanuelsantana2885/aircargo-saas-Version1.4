package com.aircargo.authservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Publica el snapshot de estado de revocación de un usuario en Redis para que el
 * {@code JwtAuthFilter} compartido (common) de TODOS los servicios lo lea sin
 * golpear la BD por-request.
 *
 * Contrato (JSON, claves exactas que lee el filtro):
 * <pre>{ "tokensValidFrom": &lt;epoch-millis|null&gt;, "blocked": &lt;bool&gt;, "active": &lt;bool&gt; }</pre>
 *
 * TTL = 8 días (el refresh token vive 7): si un servicio no lee el snapshot en 8
 * días cae al fallback fail-open (no bloquear por datos perdidos). El TTL se
 * refresca en cada publicador, así que el snapshot del usuario activo perdura.
 */
@Service
public class UserStateRedisService {

    private static final Logger log = LoggerFactory.getLogger(UserStateRedisService.class);

    static final String KEY_PREFIX = "aircargo:user-state:";
    private static final long TTL_SECONDS = 8L * 24 * 3600;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper mapper = new ObjectMapper();

    public UserStateRedisService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** tokensValidFrom null → JSON null (sin restricción global). */
    public void publish(UUID userId, OffsetDateTime tokensValidFrom, boolean blocked, boolean active) {
        try {
            Map<String, Object> state = new LinkedHashMap<>();
            state.put("tokensValidFrom",
                    tokensValidFrom == null ? null : tokensValidFrom.toInstant().toEpochMilli());
            state.put("blocked", blocked);
            state.put("active", active);
            String json = mapper.writeValueAsString(state);
            redisTemplate.opsForValue().set(KEY_PREFIX + userId, json, TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Redis user-state publish falló para {} (fail-open): {}", userId, e.getMessage());
        }
    }

    public void evict(UUID userId) {
        try {
            redisTemplate.delete(KEY_PREFIX + userId);
        } catch (Exception e) {
            log.warn("Redis user-state evict falló para {}: {}", userId, e.getMessage());
        }
    }
}