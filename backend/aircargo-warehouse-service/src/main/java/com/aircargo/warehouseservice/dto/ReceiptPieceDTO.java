package com.aircargo.warehouseservice.dto;

import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public class ReceiptPieceDTO {

    private UUID id;
    private UUID receiptId;
    private UUID hawbId;
    private Integer pieceNumber;
    @PositiveOrZero
    private Integer pieces;
    @PositiveOrZero
    private BigDecimal lengthIn;
    @PositiveOrZero
    private BigDecimal widthIn;
    @PositiveOrZero
    private BigDecimal heightIn;
    @PositiveOrZero
    private BigDecimal scaleWeightLbs;
    @PositiveOrZero
    private BigDecimal scaleWeightKg;
    private BigDecimal dimWeightLbs;
    private BigDecimal dimWeightKg;
    private BigDecimal chargeableLbs;
    private BigDecimal chargeableKg;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getReceiptId() { return receiptId; }
    public void setReceiptId(UUID receiptId) { this.receiptId = receiptId; }
    public UUID getHawbId() { return hawbId; }
    public void setHawbId(UUID hawbId) { this.hawbId = hawbId; }
    public Integer getPieceNumber() { return pieceNumber; }
    public void setPieceNumber(Integer pieceNumber) { this.pieceNumber = pieceNumber; }
    public Integer getPieces() { return pieces; }
    public void setPieces(Integer pieces) { this.pieces = pieces; }
    public BigDecimal getLengthIn() { return lengthIn; }
    public void setLengthIn(BigDecimal lengthIn) { this.lengthIn = lengthIn; }
    public BigDecimal getWidthIn() { return widthIn; }
    public void setWidthIn(BigDecimal widthIn) { this.widthIn = widthIn; }
    public BigDecimal getHeightIn() { return heightIn; }
    public void setHeightIn(BigDecimal heightIn) { this.heightIn = heightIn; }
    public BigDecimal getScaleWeightLbs() { return scaleWeightLbs; }
    public void setScaleWeightLbs(BigDecimal scaleWeightLbs) { this.scaleWeightLbs = scaleWeightLbs; }
    public BigDecimal getScaleWeightKg() { return scaleWeightKg; }
    public void setScaleWeightKg(BigDecimal scaleWeightKg) { this.scaleWeightKg = scaleWeightKg; }
    public BigDecimal getDimWeightLbs() { return dimWeightLbs; }
    public void setDimWeightLbs(BigDecimal dimWeightLbs) { this.dimWeightLbs = dimWeightLbs; }
    public BigDecimal getDimWeightKg() { return dimWeightKg; }
    public void setDimWeightKg(BigDecimal dimWeightKg) { this.dimWeightKg = dimWeightKg; }
    public BigDecimal getChargeableLbs() { return chargeableLbs; }
    public void setChargeableLbs(BigDecimal chargeableLbs) { this.chargeableLbs = chargeableLbs; }
    public BigDecimal getChargeableKg() { return chargeableKg; }
    public void setChargeableKg(BigDecimal chargeableKg) { this.chargeableKg = chargeableKg; }

    public static ReceiptPieceDTO fromEntity(com.aircargo.warehouseservice.entity.ReceiptPiece entity) {
        if (entity == null) return null;
        ReceiptPieceDTO dto = new ReceiptPieceDTO();
        dto.setId(entity.getId());
        dto.setReceiptId(entity.getReceiptId());
        dto.setHawbId(entity.getHawbId());
        dto.setPieceNumber(entity.getPieceNumber());
        dto.setPieces(entity.getPieces());
        dto.setLengthIn(entity.getLengthIn());
        dto.setWidthIn(entity.getWidthIn());
        dto.setHeightIn(entity.getHeightIn());
        dto.setScaleWeightLbs(entity.getScaleWeightLbs());
        dto.setScaleWeightKg(entity.getScaleWeightKg());
        dto.setDimWeightLbs(entity.getDimWeightLbs());
        dto.setDimWeightKg(entity.getDimWeightKg());
        dto.setChargeableLbs(entity.getChargeableLbs());
        dto.setChargeableKg(entity.getChargeableKg());
        return dto;
    }

    public static com.aircargo.warehouseservice.entity.ReceiptPiece toEntity(ReceiptPieceDTO dto) {
        if (dto == null) return null;
        com.aircargo.warehouseservice.entity.ReceiptPiece entity = new com.aircargo.warehouseservice.entity.ReceiptPiece();
        entity.setId(dto.getId());
        entity.setReceiptId(dto.getReceiptId());
        entity.setHawbId(dto.getHawbId());
        entity.setPieceNumber(dto.getPieceNumber());
        entity.setPieces(dto.getPieces());
        entity.setLengthIn(dto.getLengthIn());
        entity.setWidthIn(dto.getWidthIn());
        entity.setHeightIn(dto.getHeightIn());
        entity.setScaleWeightLbs(dto.getScaleWeightLbs());
        entity.setScaleWeightKg(dto.getScaleWeightKg());
        entity.setDimWeightLbs(dto.getDimWeightLbs());
        entity.setDimWeightKg(dto.getDimWeightKg());
        entity.setChargeableLbs(dto.getChargeableLbs());
        entity.setChargeableKg(dto.getChargeableKg());
        return entity;
    }
}