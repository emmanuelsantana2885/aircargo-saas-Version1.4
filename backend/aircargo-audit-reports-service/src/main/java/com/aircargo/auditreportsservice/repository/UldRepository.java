package com.aircargo.auditreportsservice.repository;

import com.aircargo.auditreportsservice.entity.UldEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UldRepository extends JpaRepository<UldEntity, UUID> {
    List<UldEntity> findByFlightId(UUID flightId);
}
