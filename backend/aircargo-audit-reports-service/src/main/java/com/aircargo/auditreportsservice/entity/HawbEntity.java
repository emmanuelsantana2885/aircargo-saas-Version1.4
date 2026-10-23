package com.aircargo.auditreportsservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "hawb")
@Getter
@Setter
@NoArgsConstructor
public class HawbEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "mawb_id", nullable = false)
    private UUID mawbId;

    @Column(name = "hawb_number", length = 50)
    private String hawbNumber;

    @Column(name = "consignee_name", length = 150)
    private String consigneeName;

    @Column(name = "pieces")
    private Integer pieces;

    @Column(name = "weight_kg", precision = 10, scale = 3)
    private BigDecimal weightKg;

    @Column(name = "destination", length = 3)
    private String destination;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
