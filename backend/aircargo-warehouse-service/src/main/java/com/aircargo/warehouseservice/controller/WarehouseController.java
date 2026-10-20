package com.aircargo.warehouseservice.controller;

import com.aircargo.warehouseservice.dto.ReceiptPieceDTO;
import com.aircargo.warehouseservice.dto.WarehouseReceiptDTO;
import com.aircargo.warehouseservice.service.WarehouseReceiptService;
import com.aircargo.warehouseservice.service.WarehouseService;
import com.aircargo.common.audit.AuditService;
import com.aircargo.common.auth.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/warehouse/receipts")
public class WarehouseController {

    private final WarehouseService warehouseService;
    private final WarehouseReceiptService receiptService;
    private final AuditService auditService;

    public WarehouseController(WarehouseService warehouseService,
                               WarehouseReceiptService receiptService,
                               AuditService auditService) {
        this.warehouseService = warehouseService;
        this.receiptService = receiptService;
        this.auditService = auditService;
    }

    @PostMapping("/emit")
    public ResponseEntity<WarehouseReceiptDTO> emitReceipt(@Valid @RequestBody WarehouseReceiptDTO dto,
                                                            @AuthenticationPrincipal UserPrincipal principal,
                                                            HttpServletRequest request) {
        WarehouseReceiptDTO created = warehouseService.emitReceipt(dto, principal, request);
        auditService.log(
                principal != null ? principal.getUserIdAsUuid() : null,
                principal != null ? principal.email() : "system",
                principal != null ? principal.fullName() : "system",
                "RECEIPT_EMIT", "RECEIPT", created.getId().toString(),
                "{\"mawbId\":\"" + created.getMawbId() + "\"}",
                request.getRemoteAddr()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/draft")
    public ResponseEntity<WarehouseReceiptDTO> saveDraft(@Valid @RequestBody WarehouseReceiptDTO dto,
                                                          @AuthenticationPrincipal UserPrincipal principal,
                                                          HttpServletRequest request) {
        WarehouseReceiptDTO draft = warehouseService.saveDraft(dto, principal, request);
        auditService.log(
                principal != null ? principal.getUserIdAsUuid() : null,
                principal != null ? principal.email() : "system",
                principal != null ? principal.fullName() : "system",
                "RECEIPT_DRAFT", "RECEIPT", draft.getId().toString(),
                "{\"mawbId\":\"" + draft.getMawbId() + "\"}",
                request.getRemoteAddr()
        );
        return ResponseEntity.ok(draft);
    }

    @PutMapping("/{receiptId}")
    public ResponseEntity<WarehouseReceiptDTO> updateReceipt(@PathVariable UUID receiptId,
                                                               @Valid @RequestBody WarehouseReceiptDTO dto,
                                                               @AuthenticationPrincipal UserPrincipal principal,
                                                               HttpServletRequest request) {
        Optional<WarehouseReceiptDTO> updated = warehouseService.updateReceipt(receiptId, dto, principal, request);
        if (updated.isPresent()) {
            auditService.log(
                    principal != null ? principal.getUserIdAsUuid() : null,
                    principal != null ? principal.email() : "system",
                    principal != null ? principal.fullName() : "system",
                    "RECEIPT_UPDATE", "RECEIPT", updated.get().getId().toString(),
                    "{\"actualKg\":" + (updated.get().getActualWeightKg() != null ? updated.get().getActualWeightKg() : "null") +
                            ",\"actualLbs\":" + (updated.get().getActualWeightLbs() != null ? updated.get().getActualWeightLbs() : "null") +
                            ",\"chargeableKg\":" + (updated.get().getChargeableWeightKg() != null ? updated.get().getChargeableWeightKg() : "null") +
                            ",\"chargeableLbs\":" + (updated.get().getChargeableWeightLbs() != null ? updated.get().getChargeableWeightLbs() : "null") + "}",
                    request.getRemoteAddr()
            );
            return ResponseEntity.ok(updated.get());
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/validate")
    public ResponseEntity<WarehouseReceiptDTO> validateReceipt(@Valid @RequestBody WarehouseReceiptDTO dto) {
        WarehouseReceiptDTO validated = warehouseService.validateReceipt(dto);
        return ResponseEntity.ok(validated);
    }

    @GetMapping("/{receiptId}/pieces")
    public ResponseEntity<List<ReceiptPieceDTO>> getPieces(@PathVariable UUID receiptId) {
        return ResponseEntity.ok(warehouseService.getPieces(receiptId));
    }

    @GetMapping("/{receiptId}/supporting-docs")
    public ResponseEntity<String> getSupportingDocsJson(@PathVariable UUID receiptId) {
        return ResponseEntity.ok(warehouseService.getSupportingDocsJson(receiptId));
    }

    @GetMapping("/{receiptId}/supporting-docs/html")
    public ResponseEntity<String> getSupportingDocsHtml(@PathVariable UUID receiptId) {
        return ResponseEntity.ok(warehouseService.getSupportingDocsHtml(receiptId));
    }

    @GetMapping("/{receiptId}/supporting-docs/pdf")
    public ResponseEntity<byte[]> getSupportingDocsPdf(@PathVariable UUID receiptId) {
        return ResponseEntity.ok(warehouseService.getSupportingDocsPdf(receiptId));
    }

    @GetMapping("/{receiptId}/export")
    public ResponseEntity<byte[]> exportReceipt(@PathVariable UUID receiptId) {
        return ResponseEntity.ok(warehouseService.exportReceipt(receiptId));
    }

    @GetMapping("/{receiptId}/export-url")
    public ResponseEntity<String> getExportUrl(@PathVariable UUID receiptId) {
        return ResponseEntity.ok(warehouseService.getExportUrl(receiptId));
    }

    @GetMapping("/{receiptId}/pdf")
    public ResponseEntity<byte[]> getReceiptPdf(@PathVariable UUID receiptId) {
        return ResponseEntity.ok(warehouseService.getReceiptPdf(receiptId));
    }
}