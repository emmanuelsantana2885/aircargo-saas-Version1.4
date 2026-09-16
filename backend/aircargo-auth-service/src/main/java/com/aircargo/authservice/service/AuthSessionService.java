package com.aircargo.authservice.service;

import com.aircargo.authservice.entity.AuthSession;
import com.aircargo.authservice.entity.AppUser;
import com.aircargo.authservice.event.AuditEventType;
import com.aircargo.authservice.repository.AuthSessionRepository;
import com.aircargo.common.auth.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Rotación de refresh tokens con detección de reuso (sesión 5).
 *
 * Una fila {@link AuthSession} por refresh token; en BD solo el SHA-256.
 * El refresh token "vigente" de un usuario es la fila con rotated_at NULL.
 *
 * · refresh exitoso  → UPDATE atómico (rotated_at IS NULL) marca la fila rotada
 *   y se inserta la fila del sucesor → ROTATED.
 * · re-presentación del MISMO token ya rotado:
 *     - 1ª vez = multi-pestaña legítima → se rota "en gracia" (grace_used=true,
 *       la familia NO se revoca) → GRACE_ROTATED.
 *     - 2ª vez = reuso repetido del mismo token → robo → bump de tokens_valid_from
 *       + revoked=true en TODA la familia del usuario → THEFT_REVOKED.
 * · sin fila para el token = sesión emitida antes del deploy de rotación:
 *   se adopta (insert) y se rota normalmente (una sola vez, sin revocar).
 *
 * Cada reuso va acompañado de su auditoría de seguridad
 * (REFRESH_TOKEN_GRACE_ROTATED / REFRESH_TOKEN_REUSE).
 */
@Service
public class AuthSessionService {

    private static final Logger log = LoggerFactory.getLogger(AuthSessionService.class);

    private final AuthSessionRepository repository;
    private final JwtUtil jwtUtil;
    private final TokenRevocationService tokenRevocationService;
    private final AuditService auditService;

    public AuthSessionService(AuthSessionRepository repository, JwtUtil jwtUtil,
                              TokenRevocationService tokenRevocationService,
                              AuditService auditService) {
        this.repository = repository;
        this.jwtUtil = jwtUtil;
        this.tokenRevocationService = tokenRevocationService;
        this.auditService = auditService;
    }

    public enum Status { ROTATED, GRACE_ROTATED, THEFT_REVOKED, REJECTED }

    /** Resultado de processRefresh: estado + nuevo refresh token (null si no rota). */
    public record RefreshResult(Status status, String newRefreshToken) {
        public static RefreshResult rotated(String token) {
            return new RefreshResult(Status.ROTATED, token);
        }

        public static RefreshResult graceRotated(String token) {
            return new RefreshResult(Status.GRACE_ROTATED, token);
        }

        public static RefreshResult rejected() {
            return new RefreshResult(Status.REJECTED, null);
        }

        public static RefreshResult theftRevoked() {
            return new RefreshResult(Status.THEFT_REVOKED, null);
        }
    }

    /** Registra el refresh token recién emitido en login (cadena de rotación). */
    @Transactional
    public void recordIssued(UUID userId, String refreshToken, String ipAddress, String userAgent) {
        if (refreshToken == null || refreshToken.isBlank()) return;
        AuthSession session = new AuthSession();
        session.setUserId(userId);
        session.setTokenHash(PasswordResetService.sha256Hex(refreshToken));
        session.setIssuedAt(OffsetDateTime.now());
        session.setExpiresAt(expiresAfter());
        session.setIpAddress(truncate(ipAddress, 64));
        session.setUserAgent(truncate(userAgent, 255));
        repository.saveAndFlush(session);
    }

    /**
     * Procesa un refresh token ya validado (firma + tokenType + usuario activo +
     * no bloqueado + no stale + liveness) contra la cadena de rotación.
     * Transaccional: la rotación es atómica; la familia se revoca bajo la misma TX.
     */
    @Transactional
    public RefreshResult processRefresh(AppUser user, String presentedToken,
                                        String ipAddress, String userAgent) {
        String hash = PasswordResetService.sha256Hex(presentedToken);
        var rowOpt = repository.findByTokenHash(hash);

        if (rowOpt.isEmpty()) {
            // Token emitido antes del deploy de rotación: adoptar y rotar una vez.
            String rotated = jwtUtil.generateRefreshToken(user.getId().toString());
            AuthSession adopted = new AuthSession();
            adopted.setUserId(user.getId());
            adopted.setTokenHash(hash);
            adopted.setIssuedAt(OffsetDateTime.now());
            adopted.setExpiresAt(expiresAfter());
            adopted.setIpAddress(truncate(ipAddress, 64));
            adopted.setUserAgent(truncate(userAgent, 255));
            adopted.setCreatedBy("LEGACY");
            repository.save(adopted);
            insertSuccessor(user, rotated, ipAddress, userAgent);
            log.info("Refresh token legacy adoptado para {} y rotado", user.getEmail());
            return RefreshResult.rotated(rotated);
        }

        AuthSession row = rowOpt.get();
        UUID userId = user.getId();

        if (Boolean.TRUE.equals(row.getRevoked())) {
            // Familia ya revocada por un reuso previo: no degradar más.
            return RefreshResult.rejected();
        }

        if (row.getRotatedAt() == null) {
            // Token vigente: rotación atómica (retorna 1 si la tomó este request).
            String rotated = jwtUtil.generateRefreshToken(userId.toString());
            String successorHash = PasswordResetService.sha256Hex(rotated);
            int updated = repository.rotate(hash, OffsetDateTime.now(), successorHash);
            if (updated == 1) {
                insertSuccessor(user, rotated, ipAddress, userAgent);
                return RefreshResult.rotated(rotated);
            }
            // updated == 0 → otro request rotó este token concurrentemente:
            // caemos en el camino de reuso (gracia o robo), refetcheando el estado.
            row = repository.findByTokenHash(hash).orElse(row);
        }

        // Token ya rotado (re-presentado): camino de reuso.
        if (Boolean.TRUE.equals(row.getGraceUsed())) {
            // 2ª re-presentación del mismo token → robo.
            tokenRevocationService.bump(userId);
            repository.revokeAllForUser(userId);
            auditService.log(userId, user.getEmail(), user.getFullName(),
                    AuditEventType.REFRESH_TOKEN_REUSE, "USER", userId.toString(),
                    "{\"hh\":" + hashPrefix(hash) + "}", ipAddress);
            log.warn("POSIBLE ROBO DE SESION para {}: refresh token reusado tras gracia; familia revocada",
                    user.getEmail());
            return RefreshResult.theftRevoked();
        }

        // 1ª re-presentación → gracia (no revoca la familia).
        String rotated = jwtUtil.generateRefreshToken(userId.toString());
        row.setGraceUsed(true);
        repository.save(row);
        insertSuccessor(user, rotated, ipAddress, userAgent);
        auditService.log(userId, user.getEmail(), user.getFullName(),
                AuditEventType.REFRESH_TOKEN_GRACE_ROTATED, "USER", userId.toString(),
                "{\"hh\":" + hashPrefix(hash) + "}", ipAddress);
        log.info("Refresh token reusado en gracia para {} (multi-pestana tolerada)", user.getEmail());
        return RefreshResult.graceRotated(rotated);
    }

    private void insertSuccessor(AppUser user, String successor, String ipAddress, String userAgent) {
        AuthSession next = new AuthSession();
        next.setUserId(user.getId());
        next.setTokenHash(PasswordResetService.sha256Hex(successor));
        next.setIssuedAt(OffsetDateTime.now());
        next.setExpiresAt(expiresAfter());
        next.setIpAddress(truncate(ipAddress, 64));
        next.setUserAgent(truncate(userAgent, 255));
        repository.save(next);
    }

    /** Purga proactiva por antigüedad (10 días > TTL de 7 días) cada 6 horas. */
    @Scheduled(fixedDelay = 6L * 60 * 60 * 1000)
    @Transactional
    public void purgeExpired() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(10);
        int removed = repository.deleteByExpiresAtBefore(cutoff);
        if (removed > 0) {
            log.info("Purga de auth_session: {} filas vencidas eliminadas", removed);
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static java.time.OffsetDateTime expiresAfter() {
        return java.time.OffsetDateTime.now().plus(java.time.Duration.ofMillis(JwtUtil.REFRESH_TOKEN_MS));
    }

    private static String hashPrefix(String hash) {
        return hash == null || hash.length() < 8 ? "unknown" : hash.substring(0, 8);
    }
}