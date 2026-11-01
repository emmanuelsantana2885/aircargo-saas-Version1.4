package com.aircargo.warehouseservice.dto;

import com.aircargo.warehouseservice.entity.ReceiptFormConfig;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Receipt-form settings exposed to the UI (which reserved fields can be captured). */
public class ReceiptFormConfigDTO {

    private UUID updatedBy;
    private OffsetDateTime updatedAt;
    // Null means "not sent" on a PUT so each function can be toggled on its own
    // without wiping the other flags; the GET always fills both explicitly.
    private Boolean sealNumberEnabled;
    private Boolean dockBayEnabled;

    public static ReceiptFormConfigDTO fromEntity(ReceiptFormConfig entity) {
        ReceiptFormConfigDTO dto = new ReceiptFormConfigDTO();
        if (entity == null) return dto;
        dto.setSealNumberEnabled(Boolean.TRUE.equals(entity.getSealNumberEnabled()));
        dto.setDockBayEnabled(Boolean.TRUE.equals(entity.getDockBayEnabled()));
        dto.setUpdatedBy(entity.getUpdatedBy());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    public UUID getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(UUID updatedBy) { this.updatedBy = updatedBy; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Boolean getSealNumberEnabled() { return sealNumberEnabled; }
    public void setSealNumberEnabled(Boolean sealNumberEnabled) { this.sealNumberEnabled = sealNumberEnabled; }
    public Boolean getDockBayEnabled() { return dockBayEnabled; }
    public void setDockBayEnabled(Boolean dockBayEnabled) { this.dockBayEnabled = dockBayEnabled; }
}
