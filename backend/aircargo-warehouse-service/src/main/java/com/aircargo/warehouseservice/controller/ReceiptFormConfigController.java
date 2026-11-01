package com.aircargo.warehouseservice.controller;

import com.aircargo.common.audit.AuditService;
import com.aircargo.common.auth.UserPrincipal;
import com.aircargo.warehouseservice.dto.ReceiptFormConfigDTO;
import com.aircargo.warehouseservice.service.ReceiptFormConfigService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/receipt-form-config")
public class ReceiptFormConfigController {

    private final ReceiptFormConfigService service;
    private final AuditService auditService;

    public ReceiptFormConfigController(ReceiptFormConfigService service, AuditService auditService) {
        this.service = service;
        this.auditService = auditService;
    }

    @GetMapping
    public ResponseEntity<ReceiptFormConfigDTO> get() {
        return ResponseEntity.ok(service.get());
    }

    /** Only a SUPER_USER can turn the reserved fields on (enforced in SecurityConfig). */
    @PutMapping
    public ResponseEntity<ReceiptFormConfigDTO> save(@RequestBody ReceiptFormConfigDTO dto,
                                                     @AuthenticationPrincipal UserPrincipal principal,
                                                     HttpServletRequest request) {
        ReceiptFormConfigDTO saved = service.save(dto, principal != null ? principal.getUserIdAsUuid() : null);
        auditService.log(
                principal != null ? principal.getUserIdAsUuid() : null,
                principal != null ? principal.email() : "system",
                principal != null ? principal.fullName() : "system",
                "RECEIPT_FORM_CONFIG_SAVE", "RECEIPT_FORM_CONFIG", null,
                "{\"sealNumberEnabled\":" + Boolean.TRUE.equals(saved.getSealNumberEnabled()) +
                        ",\"dockBayEnabled\":" + Boolean.TRUE.equals(saved.getDockBayEnabled()) + "}",
                request.getRemoteAddr());
        return ResponseEntity.ok(saved);
    }
}
