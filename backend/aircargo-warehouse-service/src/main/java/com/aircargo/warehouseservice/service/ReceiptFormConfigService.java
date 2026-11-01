package com.aircargo.warehouseservice.service;

import com.aircargo.warehouseservice.dto.ReceiptFormConfigDTO;
import com.aircargo.warehouseservice.entity.ReceiptFormConfig;
import com.aircargo.warehouseservice.repository.ReceiptFormConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ReceiptFormConfigService {

    private final ReceiptFormConfigRepository repository;

    public ReceiptFormConfigService(ReceiptFormConfigRepository repository) {
        this.repository = repository;
    }

    /** Current settings; creates the single row with both fields disabled when missing. */
    @Transactional
    public ReceiptFormConfigDTO get() {
        return ReceiptFormConfigDTO.fromEntity(loadOrCreate());
    }

    @Transactional
    public ReceiptFormConfigDTO save(ReceiptFormConfigDTO dto, UUID updatedBy) {
        ReceiptFormConfig config = loadOrCreate();
        // Partial payloads keep the current value instead of silently disabling a field.
        if (dto.getSealNumberEnabled() != null) config.setSealNumberEnabled(dto.getSealNumberEnabled());
        if (dto.getDockBayEnabled() != null) config.setDockBayEnabled(dto.getDockBayEnabled());
        config.setUpdatedBy(updatedBy);
        return ReceiptFormConfigDTO.fromEntity(repository.save(config));
    }

    private ReceiptFormConfig loadOrCreate() {
        return repository.findFirstByOrderByUpdatedAtAsc().orElseGet(() -> {
            ReceiptFormConfig created = new ReceiptFormConfig();
            created.setId(UUID.randomUUID());
            return repository.save(created);
        });
    }
}
