package com.athena.lms.compliance.controller;

import com.athena.lms.common.auth.TenantContextHolder;
import com.athena.lms.common.dto.PageResponse;
import com.athena.lms.compliance.dto.request.*;
import com.athena.lms.compliance.dto.response.*;
import com.athena.lms.compliance.entity.ComplianceEvent;
import com.athena.lms.compliance.enums.AlertStatus;
import com.athena.lms.compliance.repository.SarRepository;
import com.athena.lms.compliance.service.ComplianceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/compliance")
@RequiredArgsConstructor
public class ComplianceController {

    private final ComplianceService complianceService;
    private final SarRepository sarRepository;

    // ─── AML Alerts ────────────────────────────────────────────────────────────

    @PostMapping("/alerts")
    public ResponseEntity<AlertResponse> createAlert(
            @Valid @RequestBody CreateAlertRequest request,
            HttpServletRequest httpRequest) {
        String tenantId = resolveTenantId(httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(complianceService.createAlert(request, tenantId));
    }

    @GetMapping("/alerts")
    public ResponseEntity<PageResponse<AlertResponse>> listAlerts(
            @RequestParam(required = false) AlertStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest httpRequest) {
        String tenantId = resolveTenantId(httpRequest);
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(complianceService.listAlerts(tenantId, status, pageable));
    }

    @GetMapping("/alerts/{id}")
    public ResponseEntity<AlertResponse> getAlert(
            @PathVariable UUID id,
            HttpServletRequest httpRequest) {
        String tenantId = resolveTenantId(httpRequest);
        return ResponseEntity.ok(complianceService.getAlert(id, tenantId));
    }

    @PostMapping("/alerts/{id}/resolve")
    public ResponseEntity<AlertResponse> resolveAlert(
            @PathVariable UUID id,
            @Valid @RequestBody ResolveAlertRequest request,
            HttpServletRequest httpRequest) {
        String tenantId = resolveTenantId(httpRequest);
        return ResponseEntity.ok(complianceService.resolveAlert(id, request, tenantId));
    }

    @PostMapping("/alerts/{id}/sar")
    public ResponseEntity<SarResponse> fileSar(
            @PathVariable UUID id,
            @Valid @RequestBody FileSarRequest request,
            HttpServletRequest httpRequest) {
        String tenantId = resolveTenantId(httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(complianceService.fileSar(id, request, tenantId));
    }

    @GetMapping("/alerts/{id}/sar")
    public ResponseEntity<SarResponse> getSarForAlert(
            @PathVariable UUID id,
            HttpServletRequest httpRequest) {
        String tenantId = resolveTenantId(httpRequest);
        SarResponse sarResponse = sarRepository.findByAlertId(id)
                .map(sar -> {
                    SarResponse resp = new SarResponse();
                    resp.setId(sar.getId());
                    resp.setTenantId(sar.getTenantId());
                    resp.setAlertId(sar.getAlertId());
                    resp.setReferenceNumber(sar.getReferenceNumber());
                    resp.setFilingDate(sar.getFilingDate());
                    resp.setRegulator(sar.getRegulator());
                    resp.setStatus(sar.getStatus());
                    resp.setSubmittedBy(sar.getSubmittedBy());
                    resp.setNotes(sar.getNotes());
                    resp.setCreatedAt(sar.getCreatedAt());
                    return resp;
                })
                .orElseThrow(() -> new com.athena.lms.common.exception.ResourceNotFoundException(
                        "SAR filing not found for alertId: " + id));
        return ResponseEntity.ok(sarResponse);
    }

    // ─── KYC ──────────────────────────────────────────────────────────────────

    @PostMapping("/kyc")
    public ResponseEntity<KycResponse> createOrUpdateKyc(
            @Valid @RequestBody KycRequest request,
            HttpServletRequest httpRequest) {
        String tenantId = resolveTenantId(httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(complianceService.createOrUpdateKyc(request, tenantId));
    }

    @GetMapping("/kyc/{customerId}")
    public ResponseEntity<KycResponse> getKyc(
            @PathVariable Long customerId,
            HttpServletRequest httpRequest) {
        String tenantId = resolveTenantId(httpRequest);
        return ResponseEntity.ok(complianceService.getKyc(customerId, tenantId));
    }

    @PostMapping("/kyc/{customerId}/pass")
    public ResponseEntity<KycResponse> passKyc(
            @PathVariable Long customerId,
            HttpServletRequest httpRequest) {
        String tenantId = resolveTenantId(httpRequest);
        return ResponseEntity.ok(complianceService.passKyc(customerId, tenantId));
    }

    @PostMapping("/kyc/{customerId}/fail")
    public ResponseEntity<KycResponse> failKyc(
            @PathVariable Long customerId,
            @Valid @RequestBody ResolveAlertRequest request,
            HttpServletRequest httpRequest) {
        String tenantId = resolveTenantId(httpRequest);
        return ResponseEntity.ok(complianceService.failKyc(customerId, request.getResolutionNotes(), tenantId));
    }

    // ─── Events ───────────────────────────────────────────────────────────────

    @GetMapping("/events")
    public ResponseEntity<PageResponse<ComplianceEvent>> listEvents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest httpRequest) {
        String tenantId = resolveTenantId(httpRequest);
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(complianceService.listEvents(tenantId, pageable));
    }

    // ─── Summary ──────────────────────────────────────────────────────────────

    @GetMapping("/summary")
    public ResponseEntity<ComplianceSummaryResponse> getSummary(HttpServletRequest httpRequest) {
        String tenantId = resolveTenantId(httpRequest);
        return ResponseEntity.ok(complianceService.getSummary(tenantId));
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private String resolveTenantId(HttpServletRequest request) {
        String tenantId = request.getHeader("X-Tenant-Id");
        if (tenantId != null && !tenantId.isBlank()) {
            return tenantId;
        }
        String fromContext = TenantContextHolder.getTenantId();
        if (fromContext != null && !fromContext.isBlank()) {
            return fromContext;
        }
        return "default";
    }
}
