package com.aircargo.warehouseservice.repository;

import com.aircargo.warehouseservice.entity.ReceiptCalcConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReceiptCalcConfigRepository extends JpaRepository<ReceiptCalcConfig, UUID> {

    Optional<ReceiptCalcConfig> findTopByAirlineId(UUID airlineId);

    Optional<ReceiptCalcConfig> findTopByAirlineIdIsNull();

    List<ReceiptCalcConfig> findAllByOrderByAirlineIdAscIdAsc();

    void deleteByAirlineId(UUID airlineId);
}