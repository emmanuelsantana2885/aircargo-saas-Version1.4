package com.aircargo.auditreportsservice.repository;

import com.aircargo.auditreportsservice.entity.AuditLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLogEntity, String> {
    List<AuditLogEntity> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType, String entityId);

    List<AuditLogEntity> findByEntityTypeOrderByCreatedAtDesc(String entityType);
}
