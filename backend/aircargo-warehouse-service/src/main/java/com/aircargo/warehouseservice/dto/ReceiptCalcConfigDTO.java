package com.aircargo.warehouseservice.dto;

import com.aircargo.warehouseservice.calc.CalcParams;
import com.aircargo.warehouseservice.calc.ChargeableMethod;
import com.aircargo.warehouseservice.entity.ReceiptCalcConfig;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public class ReceiptCalcConfigDTO {

    private UUID id;
    private UUID airlineId;
    private String chargeableMethod;
    private Integer dimFactorDom;
    private Integer dimFactorIntl;
    private BigDecimal roundUpKg;
    private BigDecimal roundUpLbs;
    private BigDecimal minChargeableKg;
    private BigDecimal minChargeableLbs;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getAirlineId() { return airlineId; }
    public void setAirlineId(UUID airlineId) { this.airlineId = airlineId; }
    public String getChargeableMethod() { return chargeableMethod; }
    public void setChargeableMethod(String chargeableMethod) { this.chargeableMethod = chargeableMethod; }
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

    public static ReceiptCalcConfigDTO fromEntity(ReceiptCalcConfig entity) {
        if (entity == null) return null;
        ReceiptCalcConfigDTO dto = new ReceiptCalcConfigDTO();
        dto.setId(entity.getId());
        dto.setAirlineId(entity.getAirlineId());
        dto.setChargeableMethod(entity.getChargeableMethod() != null ? entity.getChargeableMethod().name() : null);
        dto.setDimFactorDom(entity.getDimFactorDom());
        dto.setDimFactorIntl(entity.getDimFactorIntl());
        dto.setRoundUpKg(entity.getRoundUpKg());
        dto.setRoundUpLbs(entity.getRoundUpLbs());
        dto.setMinChargeableKg(entity.getMinChargeableKg());
        dto.setMinChargeableLbs(entity.getMinChargeableLbs());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    public void applyTo(ReceiptCalcConfig entity) {
        entity.setChargeableMethod(ChargeableMethod.lookup(chargeableMethod));
        entity.setDimFactorDom(dimFactorDom != null && dimFactorDom > 0 ? dimFactorDom : CalcParams.DEFAULT_DIM_FACTOR_DOM);
        entity.setDimFactorIntl(dimFactorIntl != null && dimFactorIntl > 0 ? dimFactorIntl : CalcParams.DEFAULT_DIM_FACTOR_INTL);
        entity.setRoundUpKg(roundUpKg != null ? roundUpKg : BigDecimal.ZERO);
        entity.setRoundUpLbs(roundUpLbs != null ? roundUpLbs : BigDecimal.ZERO);
        entity.setMinChargeableKg(minChargeableKg != null ? minChargeableKg : BigDecimal.ZERO);
        entity.setMinChargeableLbs(minChargeableLbs != null ? minChargeableLbs : BigDecimal.ZERO);
    }
}