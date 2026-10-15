package com.aircargo.warehouseservice.service;

import com.aircargo.warehouseservice.dto.WarehouseReceiptDTO;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WarehouseReceiptService {
    List<WarehouseReceiptDTO> getAll();
    List<WarehouseReceiptDTO> getReceiptsSince(OffsetDateTime since);
    Optional<WarehouseReceiptDTO> getById(UUID id);
    WarehouseReceiptDTO save(WarehouseReceiptDTO dto);
    void delete(UUID id);
}