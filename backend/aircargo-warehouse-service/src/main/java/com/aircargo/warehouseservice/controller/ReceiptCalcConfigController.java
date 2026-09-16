package com.aircargo.warehouseservice.controller;

import com.aircargo.common.audit.AuditService;
import com.aircargo.common.auth.UserPrincipal;
import com.aircargo.warehouseservice.calc.CalcParams;
import com.aircargo.warehouseservice.dto.ReceiptCalcConfigDTO;
import com.aircargo.warehouseservice.service.ReceiptCalcConfigService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/receipt-calc-config")
public class ReceiptCalcConfigController {

    private final ReceiptCalcConfigService service;
    private final AuditService auditService;

    public ReceiptCalcConfigController(ReceiptCalcConfigService service, AuditService auditService) {
        this.service = service;
        this.auditService = auditService;
    }

    @GetMapping
    public ResponseEntity<List<ReceiptCalcConfigDTO>> getAll() {
        return ResponseEntity.ok(service.getAll());
    }

    @GetMapping("/resolve")
    public ResponseEntity<CalcParams> resolve(@RequestParam(required = false) UUID airlineId) {
        return ResponseEntity.ok(service.resolve(airlineId));
    }

    @GetMapping("/default")
    public ResponseEntity<ReceiptCalcConfigDTO> getDefault() {
        return ResponseEntity.ok(service.getDefault());
    }

    @PutMapping("/default")
    public ResponseEntity<ReceiptCalcConfigDTO> saveDefault(@RequestBody ReceiptCalcConfigDTO dto,
                                                            @AuthenticationPrincipal UserPrincipal principal,
                                                            HttpServletRequest request) {
        ReceiptCalcConfigDTO saved = service.saveDefault(dto);
        audit(principal, request, "RECEIPT_CALC_CONFIG_SAVE", null,
                "{\"scope\":\"default\",\"dimFactorDom\":" + saved.getDimFactorDom() +
                        ",\"dimFactorIntl\":" + saved.getDimFactorIntl() +
                        ",\"method\":\"" + saved.getChargeableMethod() + "\"}");
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/airline/{airlineId}")
    public ResponseEntity<ReceiptCalcConfigDTO> getByAirline(@PathVariable UUID airlineId) {
        return ResponseEntity.ok(service.getByAirline(airlineId));
    }

    @PutMapping("/airline/{airlineId}")
    public ResponseEntity<ReceiptCalcConfigDTO> saveForAirline(@PathVariable UUID airlineId,
                                                               @RequestBody ReceiptCalcConfigDTO dto,
                                                               @AuthenticationPrincipal UserPrincipal principal,
                                                               HttpServletRequest request) {
        ReceiptCalcConfigDTO saved = service.saveForAirline(airlineId, dto);
        audit(principal, request, "RECEIPT_CALC_CONFIG_SAVE", saved.getId(),
                "{\"scope\":\"airline\",\"airlineId\":\"" + airlineId + "\",\"dimFactorDom\":" + saved.getDimFactorDom() +
                        ",\"dimFactorIntl\":" + saved.getDimFactorIntl() +
                        ",\"method\":\"" + saved.getChargeableMethod() + "\"}");
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/airline/{airlineId}")
    public ResponseEntity<Void> deleteForAirline(@PathVariable UUID airlineId,
                                                 @AuthenticationPrincipal UserPrincipal principal,
                                                 HttpServletRequest request) {
        service.deleteForAirline(airlineId);
        audit(principal, request, "RECEIPT_CALC_CONFIG_DELETE", null,
                "{\"scope\":\"airline\",\"airlineId\":\"" + airlineId + "\"}");
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    private void audit(UserPrincipal principal, HttpServletRequest request, String action, UUID entityId, String details) {
        auditService.log(
                principal != null ? principal.getUserIdAsUuid() : null,
                principal != null ? principal.email() : "system",
                principal != null ? principal.fullName() : "system",
                action, "RECEIPT_CALC_CONFIG",
                entityId != null ? entityId.toString() : null,
                details,
                request.getRemoteAddr()
        );
    }
}