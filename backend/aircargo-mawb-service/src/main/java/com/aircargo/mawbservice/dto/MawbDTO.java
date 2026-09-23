package com.aircargo.mawbservice.dto;

import com.aircargo.mawbservice.entity.Mawb;
import com.aircargo.mawbservice.entity.MawbStatus;
import com.aircargo.common.entity.CommodityType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public class MawbDTO {

    private UUID id;
    private UUID airlineId;
    private UUID flightId;
    private String awbNumber;
    private String shipperName;
    private String consigneeName;
    private String origin;
    private String destination;
    private Integer pieces;
    private BigDecimal reportedWeightKg;
    private BigDecimal chargeableWeightKg;
    private CommodityType commodityType;
    private MawbStatus status;
    private Boolean cashOnly;
    private Boolean bookedInAcoms;
    private Boolean docsProvided;
    private Boolean customsCompleted;
    private Boolean preBuilt;
    private Boolean looseTender;
    private String supportingDocs;
    private String notes;
    private OffsetDateTime createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getAirlineId() { return airlineId; }
    public void setAirlineId(UUID airlineId) { this.airlineId = airlineId; }
    public UUID getFlightId() { return flightId; }
    public void setFlightId(UUID flightId) { this.flightId = flightId; }
    public String getAwbNumber() { return awbNumber; }
    public void setAwbNumber(String awbNumber) { this.awbNumber = awbNumber; }
    public String getShipperName() { return shipperName; }
    public void setShipperName(String shipperName) { this.shipperName = shipperName; }
    public String getConsigneeName() { return consigneeName; }
    public void setConsigneeName(String consigneeName) { this.consigneeName = consigneeName; }
    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }
    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }
    public Integer getPieces() { return pieces; }
    public void setPieces(Integer pieces) { this.pieces = pieces; }
    public BigDecimal getReportedWeightKg() { return reportedWeightKg; }
    public void setReportedWeightKg(BigDecimal reportedWeightKg) { this.reportedWeightKg = reportedWeightKg; }
    public BigDecimal getChargeableWeightKg() { return chargeableWeightKg; }
    public void setChargeableWeightKg(BigDecimal chargeableWeightKg) { this.chargeableWeightKg = chargeableWeightKg; }
    public CommodityType getCommodityType() { return commodityType; }
    public void setCommodityType(CommodityType commodityType) { this.commodityType = commodityType; }
    public MawbStatus getStatus() { return status; }
    public void setStatus(MawbStatus status) { this.status = status; }
    public Boolean getCashOnly() { return cashOnly; }
    public void setCashOnly(Boolean cashOnly) { this.cashOnly = cashOnly; }
    public Boolean getBookedInAcoms() { return bookedInAcoms; }
    public void setBookedInAcoms(Boolean bookedInAcoms) { this.bookedInAcoms = bookedInAcoms; }
    public Boolean getDocsProvided() { return docsProvided; }
    public void setDocsProvided(Boolean docsProvided) { this.docsProvided = docsProvided; }
    public Boolean getCustomsCompleted() { return customsCompleted; }
    public void setCustomsCompleted(Boolean customsCompleted) { this.customsCompleted = customsCompleted; }
    public Boolean getPreBuilt() { return preBuilt; }
    public void setPreBuilt(Boolean preBuilt) { this.preBuilt = preBuilt; }
    public Boolean getLooseTender() { return looseTender; }
    public void setLooseTender(Boolean looseTender) { this.looseTender = looseTender; }
    public String getSupportingDocs() { return supportingDocs; }
    public void setSupportingDocs(String supportingDocs) { this.supportingDocs = supportingDocs; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public static MawbDTO fromEntity(Mawb mawb) {
        if (mawb == null) return null;
        MawbDTO dto = new MawbDTO();
        dto.setId(mawb.getId());
        dto.setAirlineId(mawb.getAirlineId());
        dto.setFlightId(mawb.getFlightId());
        dto.setAwbNumber(mawb.getAwbNumber());
        dto.setShipperName(mawb.getShipperName());
        dto.setConsigneeName(mawb.getConsigneeName());
        dto.setOrigin(mawb.getOrigin());
        dto.setDestination(mawb.getDestination());
        dto.setPieces(mawb.getPieces());
        dto.setReportedWeightKg(mawb.getReportedWeightKg());
        dto.setChargeableWeightKg(mawb.getChargeableWeightKg());
        dto.setCommodityType(mawb.getCommodityType());
        dto.setStatus(mawb.getStatus());
        dto.setCashOnly(mawb.getCashOnly());
        dto.setBookedInAcoms(mawb.getBookedInAcoms());
        dto.setDocsProvided(mawb.getDocsProvided());
        dto.setCustomsCompleted(mawb.getCustomsCompleted());
        dto.setPreBuilt(mawb.getPreBuilt());
        dto.setLooseTender(mawb.getLooseTender());
        dto.setSupportingDocs(mawb.getSupportingDocs());
        dto.setNotes(mawb.getNotes());
        dto.setCreatedAt(mawb.getCreatedAt());
        return dto;
    }

    public static Mawb toEntity(MawbDTO dto) {
        if (dto == null) return null;
        Mawb entity = new Mawb();
        entity.setId(dto.getId());
        entity.setAirlineId(dto.getAirlineId());
        entity.setFlightId(dto.getFlightId());
        entity.setAwbNumber(dto.getAwbNumber());
        entity.setShipperName(dto.getShipperName());
        entity.setConsigneeName(dto.getConsigneeName());
        entity.setOrigin(dto.getOrigin());
        entity.setDestination(dto.getDestination());
        entity.setPieces(dto.getPieces());
        entity.setReportedWeightKg(dto.getReportedWeightKg());
        entity.setChargeableWeightKg(dto.getChargeableWeightKg());
        entity.setCommodityType(dto.getCommodityType());
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : MawbStatus.BOOKED);
        entity.setCashOnly(dto.getCashOnly());
        entity.setBookedInAcoms(dto.getBookedInAcoms());
        entity.setDocsProvided(dto.getDocsProvided());
        entity.setCustomsCompleted(dto.getCustomsCompleted());
        entity.setPreBuilt(dto.getPreBuilt());
        entity.setLooseTender(dto.getLooseTender());
        entity.setSupportingDocs(dto.getSupportingDocs());
        entity.setNotes(dto.getNotes());
        return entity;
    }
}