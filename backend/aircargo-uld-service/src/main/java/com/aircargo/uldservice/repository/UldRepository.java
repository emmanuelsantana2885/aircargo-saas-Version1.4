package com.aircargo.uldservice.repository;

import com.aircargo.uldservice.entity.Uld;
import com.aircargo.uldservice.entity.UldStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UldRepository extends JpaRepository<Uld, UUID> {
    List<Uld> findByFlightId(UUID flightId);
    Page<Uld> findByFlightId(UUID flightId, Pageable pageable);
    List<Uld> findByAirlineId(UUID airlineId);
    Page<Uld> findByAirlineId(UUID airlineId, Pageable pageable);
    List<Uld> findByStatus(UldStatus status);
    Optional<Uld> findByUldNumber(String uldNumber);
    List<Uld> findByDispatchedFlightId(UUID dispatchedFlightId);
}
