package com.aircargo.mawbservice.entity;

import com.aircargo.common.entity.CommodityType;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "mawb")
public class Mawb {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "airline_id", nullable = false)
    private UUID airlineId;

    @Column(name = "flight_id")
    private UUID flightId;

    @Column(name = "awb_number", nullable = false, unique = true, length = 50)
    private String awbNumber;

    @Column(name = "shipper_name", length = 200)
    private String shipperName;

    @Column(name = "consignee_name", length = 200)
    private String consigneeName;

    @Column(name = "origin", length = 3)
    private String origin;

    @Column(name = "destination", length = 3)
    private String destination;

    @Column(name = "pieces")
    private Integer pieces;

    @Column(name = "reported_weight_kg", precision = 10, scale = 3)
    private BigDecimal reportedWeightKg;

    @Column(name = "chargeable_weight_kg", precision = 10, scale = 3)
    private BigDecimal chargeableWeightKg;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "commodity_type", length = 30)
    private CommodityType commodityType;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, length = 20)
    private MawbStatus status;

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

    @Column(name = "loose_tender")
    private Boolean looseTender;

    @Column(name = "supporting_docs", columnDefinition = "TEXT")
    private String supportingDocs;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", insertable = false, updatable = false, nullable = false)
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
}