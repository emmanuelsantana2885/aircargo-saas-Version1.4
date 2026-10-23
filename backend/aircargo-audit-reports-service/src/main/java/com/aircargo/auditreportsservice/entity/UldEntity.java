package com.aircargo.auditreportsservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "uld")
@Getter
@Setter
@NoArgsConstructor
public class UldEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "airline_id", nullable = false)
    private UUID airlineId;

    @Column(name = "flight_id")
    private UUID flightId;

    @Column(name = "uld_number", nullable = false, length = 20)
    private String uldNumber;

    @Column(name = "uld_type")
    private String uldType;

    @Column(name = "position", length = 10)
    private String position;

    @Column(name = "status")
    private String status;

    @Column(name = "tare_lbs", precision = 10, scale = 2)
    private BigDecimal tareLbs;

    @Column(name = "gross_weight_lbs", precision = 10, scale = 2)
    private BigDecimal grossWeightLbs;

    @Column(name = "net_weight_lbs", precision = 10, scale = 2)
    private BigDecimal netWeightLbs;

    @Column(name = "tare_kg", precision = 10, scale = 2)
    private BigDecimal tareKg;

    @Column(name = "gross_weight_kg", precision = 10, scale = 2)
    private BigDecimal grossWeightKg;

    @Column(name = "net_weight_kg", precision = 10, scale = 2)
    private BigDecimal netWeightKg;

    @Column(name = "seal_number", length = 50)
    private String sealNumber;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
