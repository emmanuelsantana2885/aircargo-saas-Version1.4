package com.aircargo.loadplanningservice.dto;

import java.util.List;

/**
 * Result of a weight &amp; balance computation for one flight load plan.
 *
 * <p>When {@link #configured} is false the aircraft type has no usable W&amp;B
 * reference data and no CG figures should be shown.</p>
 */
public class WeightBalanceDTO {

    private Boolean configured;
    private Boolean placeholder;
    private String aircraftLabel;

    private Double downWeightLbs;
    private Double payloadLbs;
    private Double zeroFuelWeightLbs;
    private Double takeoffWeightLbs;

    private Double cgArmIn;
    private Double cgPercentMac;
    private Double cgForwardLimitPctMac;
    private Double cgAftLimitPctMac;
    private Boolean withinEnvelope;

    private Integer mappedUldCount;
    private List<String> unmappedPositions;
    private List<String> warnings;

    public WeightBalanceDTO() {}

    public Boolean getConfigured() { return configured; }
    public void setConfigured(Boolean configured) { this.configured = configured; }
    public Boolean getPlaceholder() { return placeholder; }
    public void setPlaceholder(Boolean placeholder) { this.placeholder = placeholder; }
    public String getAircraftLabel() { return aircraftLabel; }
    public void setAircraftLabel(String aircraftLabel) { this.aircraftLabel = aircraftLabel; }
    public Double getDownWeightLbs() { return downWeightLbs; }
    public void setDownWeightLbs(Double downWeightLbs) { this.downWeightLbs = downWeightLbs; }
    public Double getPayloadLbs() { return payloadLbs; }
    public void setPayloadLbs(Double payloadLbs) { this.payloadLbs = payloadLbs; }
    public Double getZeroFuelWeightLbs() { return zeroFuelWeightLbs; }
    public void setZeroFuelWeightLbs(Double zeroFuelWeightLbs) { this.zeroFuelWeightLbs = zeroFuelWeightLbs; }
    public Double getTakeoffWeightLbs() { return takeoffWeightLbs; }
    public void setTakeoffWeightLbs(Double takeoffWeightLbs) { this.takeoffWeightLbs = takeoffWeightLbs; }
    public Double getCgArmIn() { return cgArmIn; }
    public void setCgArmIn(Double cgArmIn) { this.cgArmIn = cgArmIn; }
    public Double getCgPercentMac() { return cgPercentMac; }
    public void setCgPercentMac(Double cgPercentMac) { this.cgPercentMac = cgPercentMac; }
    public Double getCgForwardLimitPctMac() { return cgForwardLimitPctMac; }
    public void setCgForwardLimitPctMac(Double cgForwardLimitPctMac) { this.cgForwardLimitPctMac = cgForwardLimitPctMac; }
    public Double getCgAftLimitPctMac() { return cgAftLimitPctMac; }
    public void setCgAftLimitPctMac(Double cgAftLimitPctMac) { this.cgAftLimitPctMac = cgAftLimitPctMac; }
    public Boolean getWithinEnvelope() { return withinEnvelope; }
    public void setWithinEnvelope(Boolean withinEnvelope) { this.withinEnvelope = withinEnvelope; }
    public Integer getMappedUldCount() { return mappedUldCount; }
    public void setMappedUldCount(Integer mappedUldCount) { this.mappedUldCount = mappedUldCount; }
    public List<String> getUnmappedPositions() { return unmappedPositions; }
    public void setUnmappedPositions(List<String> unmappedPositions) { this.unmappedPositions = unmappedPositions; }
    public List<String> getWarnings() { return warnings; }
    public void setWarnings(List<String> warnings) { this.warnings = warnings; }
}
