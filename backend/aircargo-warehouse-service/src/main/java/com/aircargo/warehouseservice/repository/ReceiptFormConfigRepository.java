package com.aircargo.warehouseservice.repository;

import com.aircargo.warehouseservice.entity.ReceiptFormConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReceiptFormConfigRepository extends JpaRepository<ReceiptFormConfig, UUID> {

    /** The settings table holds a single row, so this simply returns it. */
    Optional<ReceiptFormConfig> findFirstByOrderByUpdatedAtAsc();
}
