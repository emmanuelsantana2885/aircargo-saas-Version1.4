package com.aircargo.mawbservice.controller;

import com.aircargo.common.auth.UserPrincipal;
import com.aircargo.common.dto.PageResponse;
import com.aircargo.mawbservice.dto.MawbDTO;
import com.aircargo.mawbservice.entity.MawbStatus;
import com.aircargo.mawbservice.service.MawbService;
import com.aircargo.common.audit.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/mawbs")
public class MawbController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MawbController.class);

    private final MawbService mawbService;
    private final AuditService auditService;

    public MawbController(MawbService mawbService, AuditService auditService) {
        this.mawbService = mawbService;
        this.auditService = auditService;
    }

    @GetMapping
    public List<MawbDTO> getAll(
            @RequestParam(required = false) UUID airlineId,
            @RequestParam(required = false) UUID flightId,
            @RequestParam(required = false) MawbStatus status) {
        return mawbService.getAll(airlineId, flightId, status);
    }

    @GetMapping(params = {"page", "size"})
    public PageResponse<MawbDTO> getAllPaginated(
            @RequestParam(required = false) UUID airlineId,
            @RequestParam(required = false) UUID flightId,
            @RequestParam(required = false) MawbStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return mawbService.getAll(airlineId, flightId, status, page, size);
    }

    @GetMapping("/awb/{awbNumber}")
    public ResponseEntity<MawbDTO> getByAwbNumber(@PathVariable String awbNumber) {
        return mawbService.getByAwbNumber(awbNumber)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/flight/{flightId}")
    public List<MawbDTO> getByFlight(@PathVariable UUID flightId) {
        return mawbService.getByFlightId(flightId);
    }

    @GetMapping("/{id}")
    public ResponseEntity<MawbDTO> getById(@PathVariable UUID id) {
        return mawbService.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<MawbDTO> create(@Valid @RequestBody MawbDTO dto,
                                           @AuthenticationPrincipal UserPrincipal principal,
                                           HttpServletRequest request) {
        MawbDTO created = mawbService.create(dto);
        auditService.log(
                principal != null ? principal.getUserIdAsUuid() : null,
                principal != null ? principal.email() : "system",
                principal != null ? principal.fullName() : "system",
                "CREATE", "MAWB", created.getId().toString(),
                "{\"awbNumber\":\"" + safe(created.getAwbNumber()) + "\"}",
                request.getRemoteAddr()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable UUID id, @Valid @RequestBody MawbDTO dto,
                                     @AuthenticationPrincipal UserPrincipal principal,
                                     HttpServletRequest request) {
        try {
            return mawbService.update(id, dto)
                    .map(updated -> {
                        auditService.log(
                                principal != null ? principal.getUserIdAsUuid() : null,
                                principal != null ? principal.email() : "system",
                                principal != null ? principal.fullName() : "system",
                                "UPDATE", "MAWB", id.toString(),
                                "{\"awbNumber\":\"" + safe(updated.getAwbNumber()) + "\"}",
                                request.getRemoteAddr()
                        );
                        return ResponseEntity.ok(updated);
                    })
                    .orElse(ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error updating MAWB {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error updating MAWB", "details", e.getMessage()));
        }
    }

    @RequestMapping(value = "/{id}/status", method = {RequestMethod.PUT, RequestMethod.PATCH})
    public ResponseEntity<MawbDTO> updateStatus(@PathVariable UUID id, @RequestBody String rawStatus,
                                                 @AuthenticationPrincipal UserPrincipal principal,
                                                 HttpServletRequest request) {
        MawbStatus status;
        try {
            status = MawbStatus.valueOf(rawStatus.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(null);
        }
        try {
            return mawbService.updateStatus(id, status)
                    .map(updated -> {
                        auditService.log(
                                principal != null ? principal.getUserIdAsUuid() : null,
                                principal != null ? principal.email() : "system",
                                principal != null ? principal.fullName() : "system",
                                "UPDATE_STATUS", "MAWB", id.toString(),
                                "{\"status\":\"" + status.name() + "\"}",
                                request.getRemoteAddr()
                        );
                        return ResponseEntity.ok(updated);
                    })
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("Error updating MAWB status {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(null);
        }
    }

    @GetMapping("/{id}/supporting-docs")
    public ResponseEntity<String> getSupportingDocs(@PathVariable UUID id) {
        return mawbService.getById(id)
                .map(m -> m.getSupportingDocs() != null ? m.getSupportingDocs() : "[]")
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/supporting-docs")
    public ResponseEntity<Void> updateSupportingDocs(@PathVariable UUID id,
                                                      @RequestBody Map<String, Object> body,
                                                      @AuthenticationPrincipal UserPrincipal principal,
                                                      HttpServletRequest request) {
        try {
            mawbService.updateSupportingDocs(id, body);
            auditService.log(
                    principal != null ? principal.getUserIdAsUuid() : null,
                    principal != null ? principal.email() : "system",
                    principal != null ? principal.fullName() : "system",
                    "UPDATE_SUPPORTING_DOCS", "MAWB", id.toString(), null, request.getRemoteAddr()
            );
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Error updating supporting docs for MAWB {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{id}/supporting-docs/pdf")
    public ResponseEntity<byte[]> getSupportingDocsPdf(@PathVariable UUID id) {
        byte[] pdf = mawbService.getSupportingDocsPdf(id);
        if (pdf == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "inline; filename=MAWB_" + id + "_docs.pdf")
                .body(pdf);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id,
                                        @AuthenticationPrincipal UserPrincipal principal,
                                        HttpServletRequest request) {
        boolean deleted = mawbService.delete(id);
        if (!deleted) return ResponseEntity.notFound().build();
        auditService.log(
                principal != null ? principal.getUserIdAsUuid() : null,
                principal != null ? principal.email() : "system",
                principal != null ? principal.fullName() : "system",
                "DELETE", "MAWB", id.toString(), null, request.getRemoteAddr()
        );
        return ResponseEntity.noContent().build();
    }

    private static String safe(String s) {
        return com.aircargo.common.util.TextUtil.safe(s);
    }
}
