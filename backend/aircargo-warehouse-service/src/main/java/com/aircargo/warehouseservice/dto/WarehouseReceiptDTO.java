package com.aircargo.warehouseservice.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public class WarehouseReceiptDTO {

    private UUID id;
    private UUID airlineId;
    private UUID mawbId;
    private String mawbNumber;
    private UUID createdByUserId;
    private String gatewayCfs;
    private String shipperName;
    private String consigneeName;
    private String agentName;
    private String origin;
    private String destination;
    private Integer awbReportedPieces;
    private BigDecimal mawbWeightGreatest;
    private BigDecimal shipperReportedWeight;
    private OffsetDateTime startDatetime;
    private OffsetDateTime receiptDate;
    private Boolean cashOnly;
    private Boolean bookedInAcoms;
    private Boolean docsProvided;
    private Boolean customsCompleted;
    private Boolean preBuilt;
    private Boolean looseTender;
    private Integer pieceCount;
    private Integer dimFactorDom;
    private Integer dimFactorIntl;
    private String chargeableMethod;
    private BigDecimal roundUpKg;
    private BigDecimal roundUpLbs;
    private BigDecimal minChargeableKg;
    private BigDecimal minChargeableLbs;
    private BigDecimal actualWeightLbs;
    private BigDecimal actualWeightKg;
    private BigDecimal chargeableWeightLbs;
    private BigDecimal chargeableWeightKg;
    private String shipperComment;
    private String observations;
    private String remarks;
    private String createdByName;
    private String deliveredByName;
    private String deliveredByIdNum;
    private String deliveredByIdDocUrl;
    private String deliveredBySigUrl;
    private String receivedByName;
    private String receivedByIdNum;
    private String receivedByIdDocUrl;
    private String receivedBySigUrl;
    private String brokerName;
    private String brokerIdNum;
    private String brokerIdDocUrl;
    private String brokerSigUrl;
    private String receiptDocUrl;
    private String dockSignature;
    private String supportingDocs;
    private UUID hawbId;
    private String printName;
    private byte[] excelData;
    private byte[] pdfData;
    private UUID correctionOfId;
    private Integer correctionNumber;
    private Boolean superseded;
    private String correctionReason;
    private String correctedByName;
    private List<ReceiptPieceDTO> pieces;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getAirlineId() { return airlineId; }
    public void setAirlineId(UUID airlineId) { this.airlineId = airlineId; }
    public UUID getMawbId() { return mawbId; }
    public void setMawbId(UUID mawbId) { this.mawbId = mawbId; }
    public String getMawbNumber() { return mawbNumber; }
    public void setMawbNumber(String mawbNumber) { this.mawbNumber = mawbNumber; }
    public UUID getCreatedByUserId() { return createdByUserId; }
    public void setCreatedByUserId(UUID createdByUserId) { this.createdByUserId = createdByUserId; }
    public String getGatewayCfs() { return gatewayCfs; }
    public void setGatewayCfs(String gatewayCfs) { this.gatewayCfs = gatewayCfs; }
    public String getShipperName() { return shipperName; }
    public void setShipperName(String shipperName) { this.shipperName = shipperName; }
    public String getConsigneeName() { return consigneeName; }
    public void setConsigneeName(String consigneeName) { this.consigneeName = consigneeName; }
    public String getAgentName() { return agentName; }
    public void setAgentName(String agentName) { this.agentName = agentName; }
    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }
    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }
    public Integer getAwbReportedPieces() { return awbReportedPieces; }
    public void setAwbReportedPieces(Integer awbReportedPieces) { this.awbReportedPieces = awbReportedPieces; }
    public BigDecimal getMawbWeightGreatest() { return mawbWeightGreatest; }
    public void setMawbWeightGreatest(BigDecimal mawbWeightGreatest) { this.mawbWeightGreatest = mawbWeightGreatest; }
    public BigDecimal getShipperReportedWeight() { return shipperReportedWeight; }
    public void setShipperReportedWeight(BigDecimal shipperReportedWeight) { this.shipperReportedWeight = shipperReportedWeight; }
    public OffsetDateTime getStartDatetime() { return startDatetime; }
    public void setStartDatetime(OffsetDateTime startDatetime) { this.startDatetime = startDatetime; }
    public OffsetDateTime getReceiptDate() { return receiptDate; }
    public void setReceiptDate(OffsetDateTime receiptDate) { this.receiptDate = receiptDate; }
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
    public Integer getPieceCount() { return pieceCount; }
    public void setPieceCount(Integer pieceCount) { this.pieceCount = pieceCount; }
    public Integer getDimFactorDom() { return dimFactorDom; }
    public void setDimFactorDom(Integer dimFactorDom) { this.dimFactorDom = dimFactorDom; }
    public Integer getDimFactorIntl() { return dimFactorIntl; }
    public void setDimFactorIntl(Integer dimFactorIntl) { this.dimFactorIntl = dimFactorIntl; }
    public String getChargeableMethod() { return chargeableMethod; }
    public void setChargeableMethod(String chargeableMethod) { this.chargeableMethod = chargeableMethod; }
    public BigDecimal getRoundUpKg() { return roundUpKg; }
    public void setRoundUpKg(BigDecimal roundUpKg) { this.roundUpKg = roundUpKg; }
    public BigDecimal getRoundUpLbs() { return roundUpLbs; }
    public void setRoundUpLbs(BigDecimal roundUpLbs) { this.roundUpLbs = roundUpLbs; }
    public BigDecimal getMinChargeableKg() { return minChargeableKg; }
    public void setMinChargeableKg(BigDecimal minChargeableKg) { this.minChargeableKg = minChargeableKg; }
    public BigDecimal getMinChargeableLbs() { return minChargeableLbs; }
    public void setMinChargeableLbs(BigDecimal minChargeableLbs) { this.minChargeableLbs = minChargeableLbs; }
    public BigDecimal getActualWeightLbs() { return actualWeightLbs; }
    public void setActualWeightLbs(BigDecimal actualWeightLbs) { this.actualWeightLbs = actualWeightLbs; }
    public BigDecimal getActualWeightKg() { return actualWeightKg; }
    public void setActualWeightKg(BigDecimal actualWeightKg) { this.actualWeightKg = actualWeightKg; }
    public BigDecimal getChargeableWeightLbs() { return chargeableWeightLbs; }
    public void setChargeableWeightLbs(BigDecimal chargeableWeightLbs) { this.chargeableWeightLbs = chargeableWeightLbs; }
    public BigDecimal getChargeableWeightKg() { return chargeableWeightKg; }
    public void setChargeableWeightKg(BigDecimal chargeableWeightKg) { this.chargeableWeightKg = chargeableWeightKg; }
    public String getShipperComment() { return shipperComment; }
    public void setShipperComment(String shipperComment) { this.shipperComment = shipperComment; }
    public String getObservations() { return observations; }
    public void setObservations(String observations) { this.observations = observations; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
    public String getCreatedByName() { return createdByName; }
    public void setCreatedByName(String createdByName) { this.createdByName = createdByName; }
    public String getDeliveredByName() { return deliveredByName; }
    public void setDeliveredByName(String deliveredByName) { this.deliveredByName = deliveredByName; }
    public String getDeliveredByIdNum() { return deliveredByIdNum; }
    public void setDeliveredByIdNum(String deliveredByIdNum) { this.deliveredByIdNum = deliveredByIdNum; }
    public String getDeliveredByIdDocUrl() { return deliveredByIdDocUrl; }
    public void setDeliveredByIdDocUrl(String deliveredByIdDocUrl) { this.deliveredByIdDocUrl = deliveredByIdDocUrl; }
    public String getDeliveredBySigUrl() { return deliveredBySigUrl; }
    public void setDeliveredBySigUrl(String deliveredBySigUrl) { this.deliveredBySigUrl = deliveredBySigUrl; }
    public String getReceivedByName() { return receivedByName; }
    public void setReceivedByName(String receivedByName) { this.receivedByName = receivedByName; }
    public String getReceivedByIdNum() { return receivedByIdNum; }
    public void setReceivedByIdNum(String receivedByIdNum) { this.receivedByIdNum = receivedByIdNum; }
    public String getReceivedByIdDocUrl() { return receivedByIdDocUrl; }
    public void setReceivedByIdDocUrl(String receivedByIdDocUrl) { this.receivedByIdDocUrl = receivedByIdDocUrl; }
    public String getReceivedBySigUrl() { return receivedBySigUrl; }
    public void setReceivedBySigUrl(String receivedBySigUrl) { this.receivedBySigUrl = receivedBySigUrl; }
    public String getBrokerName() { return brokerName; }
    public void setBrokerName(String brokerName) { this.brokerName = brokerName; }
    public String getBrokerIdNum() { return brokerIdNum; }
    public void setBrokerIdNum(String brokerIdNum) { this.brokerIdNum = brokerIdNum; }
    public String getBrokerIdDocUrl() { return brokerIdDocUrl; }
    public void setBrokerIdDocUrl(String brokerIdDocUrl) { this.brokerIdDocUrl = brokerIdDocUrl; }
    public String getBrokerSigUrl() { return brokerSigUrl; }
    public void setBrokerSigUrl(String brokerSigUrl) { this.brokerSigUrl = brokerSigUrl; }
    public String getReceiptDocUrl() { return receiptDocUrl; }
    public void setReceiptDocUrl(String receiptDocUrl) { this.receiptDocUrl = receiptDocUrl; }
    public String getDockSignature() { return dockSignature; }
    public void setDockSignature(String dockSignature) { this.dockSignature = dockSignature; }
    public String getSupportingDocs() { return supportingDocs; }
    public void setSupportingDocs(String supportingDocs) { this.supportingDocs = supportingDocs; }
    public UUID getHawbId() { return hawbId; }
    public void setHawbId(UUID hawbId) { this.hawbId = hawbId; }
    public String getPrintName() { return printName; }
    public void setPrintName(String printName) { this.printName = printName; }
    public byte[] getExcelData() { return excelData; }
    public void setExcelData(byte[] excelData) { this.excelData = excelData; }
    public byte[] getPdfData() { return pdfData; }
    public void setPdfData(byte[] pdfData) { this.pdfData = pdfData; }
    public UUID getCorrectionOfId() { return correctionOfId; }
    public void setCorrectionOfId(UUID correctionOfId) { this.correctionOfId = correctionOfId; }
    public Integer getCorrectionNumber() { return correctionNumber; }
    public void setCorrectionNumber(Integer correctionNumber) { this.correctionNumber = correctionNumber; }
    public Boolean getSuperseded() { return superseded; }
    public void setSuperseded(Boolean superseded) { this.superseded = superseded; }
    public String getCorrectionReason() { return correctionReason; }
    public void setCorrectionReason(String correctionReason) { this.correctionReason = correctionReason; }
    public String getCorrectedByName() { return correctedByName; }
    public void setCorrectedByName(String correctedByName) { this.correctedByName = correctedByName; }
    public List<ReceiptPieceDTO> getPieces() { return pieces; }
    public void setPieces(List<ReceiptPieceDTO> pieces) { this.pieces = pieces; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    public static WarehouseReceiptDTO fromEntity(com.aircargo.warehouseservice.entity.WarehouseReceipt entity) {
        if (entity == null) return null;
        WarehouseReceiptDTO dto = new WarehouseReceiptDTO();
        dto.setId(entity.getId());
        dto.setAirlineId(entity.getAirlineId());
        dto.setMawbId(entity.getMawbId());
        dto.setMawbNumber(entity.getMawbNumber());
        dto.setCreatedByUserId(entity.getCreatedByUserId());
        dto.setGatewayCfs(entity.getGatewayCfs());
        dto.setShipperName(entity.getShipperName());
        dto.setConsigneeName(entity.getConsigneeName());
        dto.setAgentName(entity.getAgentName());
        dto.setOrigin(entity.getOrigin());
        dto.setDestination(entity.getDestination());
        dto.setAwbReportedPieces(entity.getAwbReportedPieces());
        dto.setMawbWeightGreatest(entity.getMawbWeightGreatest());
        dto.setShipperReportedWeight(entity.getShipperReportedWeight());
        dto.setStartDatetime(entity.getStartDatetime());
        dto.setReceiptDate(entity.getReceiptDate());
        dto.setCashOnly(entity.getCashOnly());
        dto.setBookedInAcoms(entity.getBookedInAcoms());
        dto.setDocsProvided(entity.getDocsProvided());
        dto.setCustomsCompleted(entity.getCustomsCompleted());
        dto.setPreBuilt(entity.getPreBuilt());
        dto.setLooseTender(entity.getLooseTender());
        dto.setPieceCount(entity.getPieceCount());
        dto.setDimFactorDom(entity.getDimFactorDom());
        dto.setDimFactorIntl(entity.getDimFactorIntl());
        dto.setChargeableMethod(entity.getChargeableMethod());
        dto.setRoundUpKg(entity.getRoundUpKg());
        dto.setRoundUpLbs(entity.getRoundUpLbs());
        dto.setMinChargeableKg(entity.getMinChargeableKg());
        dto.setMinChargeableLbs(entity.getMinChargeableLbs());
        dto.setActualWeightLbs(entity.getActualWeightLbs());
        dto.setActualWeightKg(entity.getActualWeightKg());
        dto.setChargeableWeightLbs(entity.getChargeableWeightLbs());
        dto.setChargeableWeightKg(entity.getChargeableWeightKg());
        dto.setShipperComment(entity.getShipperComment());
        dto.setObservations(entity.getObservations());
        dto.setRemarks(entity.getRemarks());
        dto.setCreatedByName(entity.getCreatedByName());
        dto.setDeliveredByName(entity.getDeliveredByName());
        dto.setDeliveredByIdNum(entity.getDeliveredByIdNum());
        dto.setDeliveredByIdDocUrl(entity.getDeliveredByIdDocUrl());
        dto.setDeliveredBySigUrl(entity.getDeliveredBySigUrl());
        dto.setReceivedByName(entity.getReceivedByName());
        dto.setReceivedByIdNum(entity.getReceivedByIdNum());
        dto.setReceivedByIdDocUrl(entity.getReceivedByIdDocUrl());
        dto.setReceivedBySigUrl(entity.getReceivedBySigUrl());
        dto.setBrokerName(entity.getBrokerName());
        dto.setBrokerIdNum(entity.getBrokerIdNum());
        dto.setBrokerIdDocUrl(entity.getBrokerIdDocUrl());
        dto.setBrokerSigUrl(entity.getBrokerSigUrl());
        dto.setReceiptDocUrl(entity.getReceiptDocUrl());
        dto.setDockSignature(entity.getDockSignature());
        dto.setSupportingDocs(entity.getSupportingDocs());
        dto.setHawbId(entity.getHawbId());
        dto.setPrintName(entity.getPrintName());
        dto.setCorrectionOfId(entity.getCorrectionOfId());
        dto.setCorrectionNumber(entity.getCorrectionNumber());
        dto.setSuperseded(entity.getSuperseded());
        dto.setCorrectionReason(entity.getCorrectionReason());
        dto.setCorrectedByName(entity.getCorrectedByName());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    public static com.aircargo.warehouseservice.entity.WarehouseReceipt toEntity(WarehouseReceiptDTO dto) {
        if (dto == null) return null;
        com.aircargo.warehouseservice.entity.WarehouseReceipt entity = new com.aircargo.warehouseservice.entity.WarehouseReceipt();
        entity.setId(dto.getId());
        entity.setAirlineId(dto.getAirlineId());
        entity.setMawbId(dto.getMawbId());
        entity.setMawbNumber(dto.getMawbNumber());
        entity.setCreatedByUserId(dto.getCreatedByUserId());
        entity.setGatewayCfs(dto.getGatewayCfs());
        entity.setShipperName(dto.getShipperName());
        entity.setConsigneeName(dto.getConsigneeName());
        entity.setAgentName(dto.getAgentName());
        entity.setOrigin(dto.getOrigin());
        entity.setDestination(dto.getDestination());
        entity.setAwbReportedPieces(dto.getAwbReportedPieces());
        entity.setMawbWeightGreatest(dto.getMawbWeightGreatest());
        entity.setShipperReportedWeight(dto.getShipperReportedWeight());
        entity.setStartDatetime(dto.getStartDatetime());
        entity.setReceiptDate(dto.getReceiptDate());
        entity.setCashOnly(dto.getCashOnly());
        entity.setBookedInAcoms(dto.getBookedInAcoms());
        entity.setDocsProvided(dto.getDocsProvided());
        entity.setCustomsCompleted(dto.getCustomsCompleted());
        entity.setPreBuilt(dto.getPreBuilt());
        entity.setLooseTender(dto.getLooseTender());
        entity.setPieceCount(dto.getPieceCount());
        entity.setDimFactorDom(dto.getDimFactorDom());
        entity.setDimFactorIntl(dto.getDimFactorIntl());
        entity.setChargeableMethod(dto.getChargeableMethod());
        entity.setRoundUpKg(dto.getRoundUpKg());
        entity.setRoundUpLbs(dto.getRoundUpLbs());
        entity.setMinChargeableKg(dto.getMinChargeableKg());
        entity.setMinChargeableLbs(dto.getMinChargeableLbs());
        entity.setActualWeightLbs(dto.getActualWeightLbs());
        entity.setActualWeightKg(dto.getActualWeightKg());
        entity.setChargeableWeightLbs(dto.getChargeableWeightLbs());
        entity.setChargeableWeightKg(dto.getChargeableWeightKg());
        entity.setShipperComment(dto.getShipperComment());
        entity.setObservations(dto.getObservations());
        entity.setRemarks(dto.getRemarks());
        entity.setCreatedByName(dto.getCreatedByName());
        entity.setDeliveredByName(dto.getDeliveredByName());
        entity.setDeliveredByIdNum(dto.getDeliveredByIdNum());
        entity.setDeliveredByIdDocUrl(dto.getDeliveredByIdDocUrl());
        entity.setDeliveredBySigUrl(dto.getDeliveredBySigUrl());
        entity.setReceivedByName(dto.getReceivedByName());
        entity.setReceivedByIdNum(dto.getReceivedByIdNum());
        entity.setReceivedByIdDocUrl(dto.getReceivedByIdDocUrl());
        entity.setReceivedBySigUrl(dto.getReceivedBySigUrl());
        entity.setBrokerName(dto.getBrokerName());
        entity.setBrokerIdNum(dto.getBrokerIdNum());
        entity.setBrokerIdDocUrl(dto.getBrokerIdDocUrl());
        entity.setBrokerSigUrl(dto.getBrokerSigUrl());
        entity.setReceiptDocUrl(dto.getReceiptDocUrl());
        entity.setDockSignature(dto.getDockSignature());
        entity.setSupportingDocs(dto.getSupportingDocs());
        entity.setHawbId(dto.getHawbId());
        entity.setPrintName(dto.getPrintName());
        entity.setCorrectionOfId(dto.getCorrectionOfId());
        entity.setCorrectionNumber(dto.getCorrectionNumber());
        entity.setSuperseded(dto.getSuperseded());
        entity.setCorrectionReason(dto.getCorrectionReason());
        entity.setCorrectedByName(dto.getCorrectedByName());
        return entity;
    }
}