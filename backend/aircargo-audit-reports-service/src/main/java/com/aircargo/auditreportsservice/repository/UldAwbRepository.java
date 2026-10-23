package com.aircargo.auditreportsservice.repository;

import com.aircargo.auditreportsservice.entity.UldAwbEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UldAwbRepository extends JpaRepository<UldAwbEntity, UUID> {
    List<UldAwbEntity> findByUldId(UUID uldId);
    List<UldAwbEntity> findByMawbId(UUID mawbId);
    List<UldAwbEntity> findByUldIdIn(List<UUID> uldIds);
}
