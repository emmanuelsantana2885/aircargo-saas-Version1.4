package com.aircargo.auditreportsservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "warehouse_receipt")
@Getter
@Setter
@NoArgsConstructor
public class WarehouseReceiptEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "airline_id", nullable = false)
    private UUID airlineId;

    @Column(name = "mawb_id", nullable = false)
    private UUID mawbId;

    @Column(name = "shipper_name", length = 150)
    private String shipperName;

    @Column(name = "consignee_name", length = 150)
    private String consigneeName;

    @Column(name = "origin")
    private String origin;

    @Column(name = "destination")
    private String destination;

    @Column(name = "piece_count")
    private Integer pieceCount;

    @Column(name = "awb_reported_pieces")
    private Integer awbReportedPieces;

    @Column(name = "actual_weight_kg", precision = 10, scale = 3)
    private BigDecimal actualWeightKg;

    @Column(name = "chargeable_weight_kg", precision = 10, scale = 3)
    private BigDecimal chargeableWeightKg;

    @Column(name = "actual_weight_lbs", precision = 10, scale = 3)
    private BigDecimal actualWeightLbs;

    @Column(name = "chargeable_weight_lbs", precision = 10, scale = 3)
    private BigDecimal chargeableWeightLbs;

    @Column(name = "cash_only")
    private Boolean cashOnly;

    @Column(name = "booked_in_acoms")
    private Boolean bookedInAcoms;

    @Column(name = "docs_provided")
    private Boolean docsProvided;

    @Column(name = "customs_completed")
    private Boolean customsCompleted;

    @Column(name = "pre_built")
    private Boolean preBuilt;

    @Column(name = "created_by_name", length = 150)
    private String createdByName;

    @Column(name = "receipt_date")
    private OffsetDateTime receiptDate;

    @Column(name = "superseded")
    private Boolean superseded;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
