package com.aircargo.authservice.repository;

import com.aircargo.authservice.entity.AuthSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {

    Optional<AuthSession> findByTokenHash(String tokenHash);

    List<AuthSession> findByUserId(UUID userId);

    /**
     * Rotación atómica: marca la fila como rotada solo si sigue vigente
     * (rotated_at IS NULL). Devuelve 1 si la rotación tomó la fila y 0 si
     * otro request ya la rotó (reuso en curso → camino de gracia/robo).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE AuthSession s SET s.rotatedAt = :rotatedAt, s.replacedBy = :replacedBy "
            + "WHERE s.tokenHash = :tokenHash AND s.rotatedAt IS NULL")
    int rotate(@Param("tokenHash") String tokenHash,
               @Param("rotatedAt") OffsetDateTime rotatedAt,
               @Param("replacedBy") String replacedBy);

    /** Revoca TODA la familia de sesiones de un usuario (robo detectado). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE AuthSession s SET s.revoked = true WHERE s.userId = :userId")
    int revokeAllForUser(@Param("userId") UUID userId);

    /** Vacía el historial vencido (purga programada); expires_at está indexado. */
    @Modifying
    @Query("DELETE FROM AuthSession s WHERE s.expiresAt < :before")
    int deleteByExpiresAtBefore(@Param("before") OffsetDateTime before);
}