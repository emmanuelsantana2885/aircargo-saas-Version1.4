package com.aircargo.authservice.service;

import com.aircargo.authservice.entity.AppUser;
import com.aircargo.authservice.event.AuditEventStore;
import com.aircargo.authservice.event.AuditEventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Application-facing audit facade (command side). Delegates every write to the
 * append-only {@link AuditEventStore}. Existing call sites keep working
 * unchanged; actions are normalized to {@link AuditEventType} names.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private static final com.fasterxml.jackson.databind.ObjectMapper JSON =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private final AuditEventStore eventStore;

    public AuditService(AuditEventStore eventStore) {
        this.eventStore = eventStore;
    }

    public void log(UUID userId, String email, String fullName, String action,
                    String entityType, String entityId, String details, String ipAddress) {
        eventStore.append(userId, email, fullName, action, entityType, entityId, details, null, null, ipAddress);
    }

    /**
     * Diff-capable write (audit 4d): records the entity state before and after
     * a mutation so readers can reconstruct precisely what changed.
     */
    public void log(UUID userId, String email, String fullName, String action,
                    String entityType, String entityId, String details,
                    String beforeValue, String afterValue, String ipAddress) {
        eventStore.append(userId, email, fullName, action, entityType, entityId, details,
                beforeValue, afterValue, ipAddress);
    }

    /**
     * Compact JSON snapshot of an {@link AppUser} for before/after diff capture.
     * Deliberately excludes passwordHash, mfaSecret and tokens (never written to audit).
     */
    public static String snapshot(AppUser u) {
        if (u == null) return null;
        try {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", u.getId().toString());
            map.put("email", u.getEmail());
            map.put("fullName", u.getFullName());
            map.put("role", u.getRole() != null ? u.getRole().name() : null);
            map.put("isActive", u.getIsActive());
            map.put("blocked", u.getBlocked());
            map.put("mustChangePassword", u.getMustChangePassword());
            map.put("mfaEnabled", u.getMfaEnabled());
            map.put("mfaLocked", u.getMfaLocked());
            return JSON.writeValueAsString(map);
        } catch (Exception e) {
            log.warn("Failed to build audit snapshot for {}: {}", u.getEmail(), e.getMessage());
            return null;
        }
    }

    public void logLogin(UUID userId, String email, String fullName, String ipAddress) {
        log(userId, email, fullName, AuditEventType.LOGIN_SUCCEEDED, "USER", userId.toString(), null, ipAddress);
    }

    public void logLoginFailed(UUID userId, String attemptedEmail, int attemptCount,
                               UUID targetUserId, String reason, String ipAddress) {
        String payload = "{\"attemptCount\":" + attemptCount
                + ",\"reason\":\"" + reason + "\",\"targetUserId\":"
                + (targetUserId != null ? "\"" + targetUserId + "\"" : "null") + "}";
        log(userId, attemptedEmail, null, AuditEventType.LOGIN_FAILED, "USER",
                targetUserId != null ? targetUserId.toString() : attemptedEmail, payload, ipAddress);
    }

    public void logAccountLocked(UUID userId, String email, int attemptCount,
                                 java.time.OffsetDateTime lockedUntil, String ipAddress) {
        String payload = "{\"attemptCount\":" + attemptCount
                + ",\"lockedUntil\":\"" + lockedUntil + "\"}";
        log(userId, email, null, AuditEventType.ACCOUNT_LOCKED, "USER", userId.toString(), payload, ipAddress);
    }

    public void logUserCreate(UUID userId, String email, String fullName,
                              UUID targetUserId, String afterSnapshot, String ipAddress) {
        log(userId, email, fullName, AuditEventType.USER_CREATED, "USER", targetUserId.toString(),
                null, null, afterSnapshot, ipAddress);
    }

    public void logUserUpdate(UUID userId, String email, String fullName,
                              UUID targetUserId, String beforeSnapshot, String afterSnapshot,
                              String details, String ipAddress) {
        log(userId, email, fullName, AuditEventType.USER_UPDATED, "USER", targetUserId.toString(),
                details, beforeSnapshot, afterSnapshot, ipAddress);
    }

    public void logUserDelete(UUID userId, String email, String fullName,
                              UUID targetUserId, String beforeSnapshot, String ipAddress) {
        log(userId, email, fullName, AuditEventType.USER_DELETED, "USER", targetUserId.toString(),
                null, beforeSnapshot, null, ipAddress);
    }

    public void logPasswordReset(UUID userId, String email, String fullName,
                                 UUID targetUserId, String targetEmail, String ipAddress) {
        log(userId, email, fullName, AuditEventType.PASSWORD_RESET, "USER", targetUserId.toString(),
                "{\"email\":\"" + targetEmail + "\"}", ipAddress);
    }
}
