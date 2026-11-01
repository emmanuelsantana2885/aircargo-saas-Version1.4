package com.aircargo.warehouseservice.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Global receipt-form settings. One row: which reserved dock fields
 * (seal number, dock bay) are enabled for capture.
 */
@Entity
@Table(name = "receipt_form_config")
public class ReceiptFormConfig {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "seal_number_enabled", nullable = false)
    private Boolean sealNumberEnabled = false;

    @Column(name = "dock_bay_enabled", nullable = false)
    private Boolean dockBayEnabled = false;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Boolean getSealNumberEnabled() { return sealNumberEnabled; }
    public void setSealNumberEnabled(Boolean sealNumberEnabled) { this.sealNumberEnabled = sealNumberEnabled; }
    public Boolean getDockBayEnabled() { return dockBayEnabled; }
    public void setDockBayEnabled(Boolean dockBayEnabled) { this.dockBayEnabled = dockBayEnabled; }
    public UUID getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(UUID updatedBy) { this.updatedBy = updatedBy; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
