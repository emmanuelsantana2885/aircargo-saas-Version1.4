package com.aircargo.warehouseservice.service;

import com.aircargo.warehouseservice.dto.WarehouseReceiptDTO;
import com.aircargo.warehouseservice.entity.WarehouseReceipt;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WarehouseService {
    WarehouseReceiptDTO emitReceipt(WarehouseReceiptDTO dto, com.aircargo.common.auth.UserPrincipal principal, jakarta.servlet.http.HttpServletRequest request);
    WarehouseReceiptDTO saveDraft(WarehouseReceiptDTO dto, com.aircargo.common.auth.UserPrincipal principal, jakarta.servlet.http.HttpServletRequest request);
    Optional<WarehouseReceiptDTO> updateReceipt(UUID receiptId, WarehouseReceiptDTO dto, com.aircargo.common.auth.UserPrincipal principal, jakarta.servlet.http.HttpServletRequest request);
    WarehouseReceiptDTO validateReceipt(WarehouseReceiptDTO dto);
    List<com.aircargo.warehouseservice.dto.ReceiptPieceDTO> getPieces(UUID receiptId);
    String getSupportingDocsJson(UUID receiptId);
    String getSupportingDocsHtml(UUID receiptId);
    byte[] getSupportingDocsPdf(UUID receiptId);
    byte[] exportReceipt(UUID receiptId);
    String getExportUrl(UUID receiptId);
    byte[] getReceiptPdf(UUID receiptId);
}