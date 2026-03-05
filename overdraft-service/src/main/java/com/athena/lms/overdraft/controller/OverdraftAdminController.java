package com.athena.lms.overdraft.controller;

import com.athena.lms.common.auth.TenantContextHolder;
import com.athena.lms.common.dto.PageResponse;
import com.athena.lms.overdraft.dto.request.CreateBandConfigRequest;
import com.athena.lms.overdraft.dto.response.AuditLogResponse;
import com.athena.lms.overdraft.dto.response.CreditBandConfigResponse;
import com.athena.lms.overdraft.dto.response.OverdraftSummaryResponse;
import com.athena.lms.overdraft.entity.CreditBandConfig;
import com.athena.lms.overdraft.repository.CreditBandConfigRepository;
import com.athena.lms.overdraft.service.AuditService;
import com.athena.lms.overdraft.service.OverdraftFacilityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/overdraft")
@RequiredArgsConstructor
public class OverdraftAdminController {

    private final OverdraftFacilityService overdraftFacilityService;
    private final CreditBandConfigRepository bandConfigRepo;
    private final AuditService auditService;

    @GetMapping("/summary")
    public ResponseEntity<OverdraftSummaryResponse> getSummary() {
        return ResponseEntity.ok(overdraftFacilityService.getSummary(TenantContextHolder.getTenantId()));
    }

    // ─── Audit Log ──────────────────────────────────────────────────────────────
    @GetMapping("/audit-log")
    public ResponseEntity<PageResponse<AuditLogResponse>> getAuditLog(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) UUID entityId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(auditService.getAuditLog(
            TenantContextHolder.getTenantId(), entityType, entityId, PageRequest.of(page, size)));
    }

    // ─── Band Config CRUD ───────────────────────────────────────────────────────
    @GetMapping("/band-configs")
    public ResponseEntity<List<CreditBandConfigResponse>> getBandConfigs() {
        String tenantId = TenantContextHolder.getTenantId();
        List<CreditBandConfig> configs = bandConfigRepo.findByTenantIdOrderByMinScoreDesc(tenantId);
        if (configs.isEmpty()) {
            configs = bandConfigRepo.findByTenantIdOrderByMinScoreDesc("system");
        }
        return ResponseEntity.ok(configs.stream().map(this::toConfigResponse).collect(Collectors.toList()));
    }

    @PostMapping("/band-configs")
    public ResponseEntity<CreditBandConfigResponse> createBandConfig(@Valid @RequestBody CreateBandConfigRequest req) {
        String tenantId = TenantContextHolder.getTenantId();
        CreditBandConfig config = new CreditBandConfig();
        config.setTenantId(tenantId);
        config.setBand(req.getBand());
        config.setMinScore(req.getMinScore());
        config.setMaxScore(req.getMaxScore());
        config.setApprovedLimit(req.getApprovedLimit());
        config.setInterestRate(req.getInterestRate());
        config.setArrangementFee(req.getArrangementFee());
        config.setAnnualFee(req.getAnnualFee());
        config.setEffectiveFrom(req.getEffectiveFrom());
        config.setEffectiveTo(req.getEffectiveTo());
        CreditBandConfig saved = bandConfigRepo.save(config);

        auditService.audit(tenantId, "CONFIG", saved.getId(), "CREATED",
            null,
            Map.of("band", req.getBand(), "limit", req.getApprovedLimit(), "rate", req.getInterestRate()),
            null);

        return ResponseEntity.status(HttpStatus.CREATED).body(toConfigResponse(saved));
    }

    @PutMapping("/band-configs/{configId}")
    public ResponseEntity<CreditBandConfigResponse> updateBandConfig(
            @PathVariable UUID configId,
            @Valid @RequestBody CreateBandConfigRequest req) {
        String tenantId = TenantContextHolder.getTenantId();
        CreditBandConfig config = bandConfigRepo.findById(configId)
            .orElseThrow(() -> new com.athena.lms.common.exception.ResourceNotFoundException("Band config not found: " + configId));

        Map<String, Object> before = Map.of(
            "band", config.getBand(), "limit", config.getApprovedLimit(),
            "rate", config.getInterestRate(), "arrangementFee", config.getArrangementFee());

        config.setBand(req.getBand());
        config.setMinScore(req.getMinScore());
        config.setMaxScore(req.getMaxScore());
        config.setApprovedLimit(req.getApprovedLimit());
        config.setInterestRate(req.getInterestRate());
        config.setArrangementFee(req.getArrangementFee());
        config.setAnnualFee(req.getAnnualFee());
        if (req.getEffectiveFrom() != null) config.setEffectiveFrom(req.getEffectiveFrom());
        if (req.getEffectiveTo() != null) config.setEffectiveTo(req.getEffectiveTo());
        CreditBandConfig saved = bandConfigRepo.save(config);

        auditService.audit(tenantId, "CONFIG", saved.getId(), "UPDATED",
            before,
            Map.of("band", req.getBand(), "limit", req.getApprovedLimit(), "rate", req.getInterestRate()),
            null);

        return ResponseEntity.ok(toConfigResponse(saved));
    }

    private CreditBandConfigResponse toConfigResponse(CreditBandConfig c) {
        CreditBandConfigResponse r = new CreditBandConfigResponse();
        r.setId(c.getId());
        r.setTenantId(c.getTenantId());
        r.setBand(c.getBand());
        r.setMinScore(c.getMinScore());
        r.setMaxScore(c.getMaxScore());
        r.setApprovedLimit(c.getApprovedLimit());
        r.setInterestRate(c.getInterestRate());
        r.setArrangementFee(c.getArrangementFee());
        r.setAnnualFee(c.getAnnualFee());
        r.setStatus(c.getStatus());
        r.setEffectiveFrom(c.getEffectiveFrom());
        r.setEffectiveTo(c.getEffectiveTo());
        r.setCreatedAt(c.getCreatedAt());
        r.setUpdatedAt(c.getUpdatedAt());
        return r;
    }
}
