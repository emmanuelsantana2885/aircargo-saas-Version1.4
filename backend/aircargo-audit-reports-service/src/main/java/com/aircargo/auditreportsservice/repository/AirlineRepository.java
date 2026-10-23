package com.aircargo.auditreportsservice.repository;

import com.aircargo.auditreportsservice.entity.AirlineEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AirlineRepository extends JpaRepository<AirlineEntity, UUID> {
}
