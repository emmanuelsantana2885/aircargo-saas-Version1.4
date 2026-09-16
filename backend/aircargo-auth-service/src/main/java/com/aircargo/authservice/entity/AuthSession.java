package com.aircargo.authservice.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Una fila por refresh token emitido (sesión 5). En BD solo vive el SHA-256
 * del JWT de refresco (token_hash), nunca el valor crudo.
 *
 * Cadena de rotación: la fila "activa" tiene rotated_at NULL y es el
 * refresh token vigente. Al refrescar, un UPDATE atómico la marca rotada
 * (replaced_by = hash del sucesor) y se inserta la fila sucesora.
 * Si el MISMO token se presenta de nuevo tras rotar:
 *   · 1ª vez (multi-pestaña legítima) → se rota "en gracia" (grace_used=true).
 *   · 2ª vez → robo → revoked=true en toda la familia del usuario.
 */
@Entity
@Table(name = "auth_session", indexes = {
        @Index(name = "uq_auth_session_token_hash", columnList = "token_hash", unique = true),
        @Index(name = "idx_auth_session_user_id", columnList = "user_id"),
        @Index(name = "idx_auth_session_expires_at", columnList = "expires_at")
})
public class AuthSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "issued_at", nullable = false)
    private OffsetDateTime issuedAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    /** NULL = fila vigente (refresh actual); al refrescar se marca con el instante de rotación. */
    @Column(name = "rotated_at")
    private OffsetDateTime rotatedAt;

    /** SHA-256 del refresh token sucesor (une la cadena). */
    @Column(name = "replaced_by", length = 64)
    private String replacedBy;

    /** true cuando este token ya se reusó una vez en gracia (segundo reuso = robo). */
    @Column(name = "grace_used", nullable = false)
    private Boolean graceUsed = false;

    /** true cuando la familia se revocó por reuso malicioso. */
    @Column(name = "revoked", nullable = false)
    private Boolean revoked = false;

    /** Origen: LOGIN (por defecto) — el resto de proveniencias se etiquetan aquí (p.ej. SET_PASSWORD). */
    @Column(name = "created_by", nullable = false, length = 20)
    private String createdBy = "LOGIN";

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private OffsetDateTime createdAt;

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }
    public OffsetDateTime getIssuedAt() { return issuedAt; }
    public void setIssuedAt(OffsetDateTime issuedAt) { this.issuedAt = issuedAt; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(OffsetDateTime expiresAt) { this.expiresAt = expiresAt; }
    public OffsetDateTime getRotatedAt() { return rotatedAt; }
    public void setRotatedAt(OffsetDateTime rotatedAt) { this.rotatedAt = rotatedAt; }
    public String getReplacedBy() { return replacedBy; }
    public void setReplacedBy(String replacedBy) { this.replacedBy = replacedBy; }
    public Boolean getGraceUsed() { return graceUsed; }
    public void setGraceUsed(Boolean graceUsed) { this.graceUsed = graceUsed; }
    public Boolean getRevoked() { return revoked; }
    public void setRevoked(Boolean revoked) { this.revoked = revoked; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}