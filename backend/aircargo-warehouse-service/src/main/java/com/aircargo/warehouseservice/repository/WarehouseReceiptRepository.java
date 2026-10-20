package com.aircargo.warehouseservice.repository;

import com.aircargo.warehouseservice.entity.WarehouseReceipt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WarehouseReceiptRepository extends JpaRepository<WarehouseReceipt, UUID> {
    List<WarehouseReceipt> findByMawbId(UUID mawbId);
    Page<WarehouseReceipt> findByMawbId(UUID mawbId, Pageable pageable);

    List<WarehouseReceipt> findBySupersededFalse();
    Page<WarehouseReceipt> findBySupersededFalse(Pageable pageable);

    List<WarehouseReceipt> findByCreatedAtAfter(OffsetDateTime since);
    Page<WarehouseReceipt> findByCreatedAtAfter(OffsetDateTime since, Pageable pageable);

    List<WarehouseReceipt> findByMawbIdAndStatusAndSupersededFalse(UUID mawbId, String status);
    Optional<WarehouseReceipt> findFirstByMawbIdAndStatusAndSupersededFalseOrderByCreatedAtDesc(UUID mawbId, String status);

    @Modifying
    @Query("UPDATE WarehouseReceipt r SET r.superseded = true WHERE r.mawbId = :mawbId AND r.id <> :excludeId")
    void supersedeAllByMawbId(UUID mawbId, UUID excludeId);

    @Modifying
    @Query("UPDATE WarehouseReceipt r SET r.superseded = true WHERE r.mawbId = :mawbId")
    void supersedeAllByMawbId(UUID mawbId);
}