package com.aircargo.mawbservice.controller;

import com.aircargo.mawbservice.dto.HawbDTO;
import com.aircargo.mawbservice.service.HawbService;
import com.aircargo.common.audit.AuditService;
import com.aircargo.common.auth.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/hawbs")
public class HawbController {

    private final HawbService hawbService;
    private final AuditService auditService;

    public HawbController(HawbService hawbService, AuditService auditService) {
        this.hawbService = hawbService;
        this.auditService = auditService;
    }

    @GetMapping("/mawb/{mawbId}")
    public List<HawbDTO> getByMawb(@PathVariable UUID mawbId) {
        return hawbService.getByMawbId(mawbId);
    }

    @GetMapping("/{id}")
    public ResponseEntity<HawbDTO> getById(@PathVariable UUID id) {
        return hawbService.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/number/{hawbNumber}")
    public ResponseEntity<HawbDTO> getByHawbNumber(@PathVariable String hawbNumber) {
        return hawbService.getByHawbNumber(hawbNumber)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<HawbDTO> create(@Valid @RequestBody HawbDTO dto,
                                           @AuthenticationPrincipal UserPrincipal principal,
                                           HttpServletRequest request) {
        HawbDTO created = hawbService.create(dto);
        auditService.log(
                principal != null ? principal.getUserIdAsUuid() : null,
                principal != null ? principal.email() : "system",
                principal != null ? principal.fullName() : "system",
                "CREATE", "HAWB", created.getId().toString(),
                "{\"hawbNumber\":\"" + created.getHawbNumber() + "\"}", request.getRemoteAddr()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<HawbDTO> update(@PathVariable UUID id, @Valid @RequestBody HawbDTO dto,
                                           @AuthenticationPrincipal UserPrincipal principal,
                                           HttpServletRequest request) {
        return hawbService.update(id, dto)
                .map(updated -> {
                    auditService.log(
                            principal != null ? principal.getUserIdAsUuid() : null,
                            principal != null ? principal.email() : "system",
                            principal != null ? principal.fullName() : "system",
                            "UPDATE", "HAWB", id.toString(),
                            "{\"hawbNumber\":\"" + updated.getHawbNumber() + "\"}", request.getRemoteAddr()
                    );
                    return ResponseEntity.ok(updated);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id,
                                        @AuthenticationPrincipal UserPrincipal principal,
                                        HttpServletRequest request) {
        boolean deleted = hawbService.delete(id);
        if (!deleted) return ResponseEntity.notFound().build();
        auditService.log(
                principal != null ? principal.getUserIdAsUuid() : null,
                principal != null ? principal.email() : "system",
                principal != null ? principal.fullName() : "system",
                "DELETE", "HAWB", id.toString(), null, request.getRemoteAddr()
        );
        return ResponseEntity.noContent().build();
    }
}
