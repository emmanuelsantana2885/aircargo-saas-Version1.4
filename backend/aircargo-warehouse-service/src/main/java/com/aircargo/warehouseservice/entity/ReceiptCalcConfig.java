package com.aircargo.warehouseservice.entity;

import com.aircargo.warehouseservice.calc.ChargeableMethod;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Perfil de cálculo de recibos por aerolínea. airlineId {@code null} = perfil global por defecto.
 */
@Entity
@Table(name = "receipt_calc_config")
public class ReceiptCalcConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "airline_id")
    private UUID airlineId;

    @Enumerated(EnumType.STRING)
    @Column(name = "chargeable_method", length = 10, nullable = false)
    private ChargeableMethod chargeableMethod;

    @Column(name = "dim_factor_dom", nullable = false)
    private Integer dimFactorDom;

    @Column(name = "dim_factor_intl", nullable = false)
    private Integer dimFactorIntl;

    @Column(name = "round_up_kg", precision = 10, scale = 3, nullable = false)
    private BigDecimal roundUpKg;

    @Column(name = "round_up_lbs", precision = 10, scale = 3, nullable = false)
    private BigDecimal roundUpLbs;

    @Column(name = "min_chargeable_kg", precision = 10, scale = 3, nullable = false)
    private BigDecimal minChargeableKg;

    @Column(name = "min_chargeable_lbs", precision = 10, scale = 3, nullable = false)
    private BigDecimal minChargeableLbs;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getAirlineId() { return airlineId; }
    public void setAirlineId(UUID airlineId) { this.airlineId = airlineId; }
    public ChargeableMethod getChargeableMethod() { return chargeableMethod; }
    public void setChargeableMethod(ChargeableMethod chargeableMethod) { this.chargeableMethod = chargeableMethod; }
    public Integer getDimFactorDom() { return dimFactorDom; }
    public void setDimFactorDom(Integer dimFactorDom) { this.dimFactorDom = dimFactorDom; }
    public Integer getDimFactorIntl() { return dimFactorIntl; }
    public void setDimFactorIntl(Integer dimFactorIntl) { this.dimFactorIntl = dimFactorIntl; }
    public BigDecimal getRoundUpKg() { return roundUpKg; }
    public void setRoundUpKg(BigDecimal roundUpKg) { this.roundUpKg = roundUpKg; }
    public BigDecimal getRoundUpLbs() { return roundUpLbs; }
    public void setRoundUpLbs(BigDecimal roundUpLbs) { this.roundUpLbs = roundUpLbs; }
    public BigDecimal getMinChargeableKg() { return minChargeableKg; }
    public void setMinChargeableKg(BigDecimal minChargeableKg) { this.minChargeableKg = minChargeableKg; }
    public BigDecimal getMinChargeableLbs() { return minChargeableLbs; }
    public void setMinChargeableLbs(BigDecimal minChargeableLbs) { this.minChargeableLbs = minChargeableLbs; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}