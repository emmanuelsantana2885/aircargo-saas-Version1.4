package com.aircargo.authservice.dto;

import com.aircargo.authservice.entity.AuditLog;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogDTO {
    private UUID id;
    private UUID userId;
    private String email;
    private String fullName;
    private String action;
    private String entityType;
    private String entityId;
    private String details;
    private String beforeValue;
    private String afterValue;
    private String ipAddress;
    private OffsetDateTime createdAt;

    public static AuditLogDTO fromEntity(AuditLog entity) {
        if (entity == null) return null;
        return AuditLogDTO.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .email(entity.getEmail())
                .fullName(entity.getFullName())
                .action(entity.getAction())
                .entityType(entity.getEntityType())
                .entityId(entity.getEntityId())
                .details(entity.getDetails())
                .beforeValue(entity.getBeforeValue())
                .afterValue(entity.getAfterValue())
                .ipAddress(entity.getIpAddress())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
