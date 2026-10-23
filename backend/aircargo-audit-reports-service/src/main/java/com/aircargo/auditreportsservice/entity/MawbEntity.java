package com.aircargo.auditreportsservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "mawb")
@Getter
@Setter
@NoArgsConstructor
public class MawbEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "airline_id", nullable = false)
    private UUID airlineId;

    @Column(name = "flight_id")
    private UUID flightId;

    @Column(name = "awb_number", nullable = false, length = 20)
    private String awbNumber;

    @Column(name = "shipper_name", length = 150)
    private String shipperName;

    @Column(name = "consignee_name", length = 150)
    private String consigneeName;

    @Column(name = "origin", nullable = false)
    private String origin;

    @Column(name = "destination", nullable = false)
    private String destination;

    @Column(name = "pieces", nullable = false)
    private Integer pieces;

    @Column(name = "reported_weight_kg", precision = 10, scale = 2)
    private BigDecimal reportedWeightKg;

    @Column(name = "chargeable_weight_kg", precision = 10, scale = 2)
    private BigDecimal chargeableWeightKg;

    @Column(name = "commodity_type", nullable = false)
    private String commodityType;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "cash_only")
    private Boolean cashOnly;

    @Column(name = "pre_built")
    private Boolean preBuilt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
