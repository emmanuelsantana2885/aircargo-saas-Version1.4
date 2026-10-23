package com.aircargo.auditreportsservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "booking")
@Getter
@Setter
@NoArgsConstructor
public class BookingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "airline_id")
    private UUID airlineId;

    @Column(name = "flight_id")
    private UUID flightId;

    @Column(name = "mawb_id")
    private UUID mawbId;

    @Column(name = "awb_number", length = 20)
    private String awbNumber;

    @Column(name = "client_name", length = 150)
    private String clientName;

    @Column(name = "shipper_name", length = 150)
    private String shipperName;

    @Column(name = "cnee", length = 150)
    private String cnee;

    @Column(name = "destination", length = 3)
    private String destination;

    @Column(name = "skids")
    private Integer skids;

    @Column(name = "units")
    private Integer units;

    @Column(name = "reserved_kg", precision = 10, scale = 2)
    private BigDecimal reservedKg;

    @Column(name = "confirmed_kg", precision = 10, scale = 2)
    private BigDecimal confirmedKg;

    @Column(name = "received_kg", precision = 10, scale = 2)
    private BigDecimal receivedKg;

    @Column(name = "fulfillment_pct", precision = 5, scale = 2)
    private BigDecimal fulfillmentPct;

    @Column(name = "commodity_type")
    private String commodityType;

    @Column(name = "priority", length = 20)
    private String priority;

    @Column(name = "is_confirmed")
    private Boolean isConfirmed;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
