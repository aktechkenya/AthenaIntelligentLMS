package com.athena.lms.scoring.controller;

import com.athena.lms.common.dto.PageResponse;
import com.athena.lms.common.auth.TenantContextHolder;
import com.athena.lms.scoring.dto.request.ManualScoringRequest;
import com.athena.lms.scoring.dto.response.ScoringRequestResponse;
import com.athena.lms.scoring.dto.response.ScoringResultResponse;
import com.athena.lms.scoring.service.AiScoringService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/scoring")
@RequiredArgsConstructor
public class AiScoringController {

    private final AiScoringService scoringService;

    @PostMapping("/requests")
    public ResponseEntity<ScoringRequestResponse> manualScore(
            @Valid @RequestBody ManualScoringRequest request,
            HttpServletRequest httpRequest) {
        String tenantId = resolveTenantId(httpRequest);
        ScoringRequestResponse response = scoringService.manualScore(request, tenantId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/requests")
    public ResponseEntity<PageResponse<ScoringRequestResponse>> listRequests(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest httpRequest) {
        String tenantId = resolveTenantId(httpRequest);
        PageResponse<ScoringRequestResponse> response =
                scoringService.listRequests(tenantId, PageRequest.of(page, size));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/requests/{id}")
    public ResponseEntity<ScoringRequestResponse> getRequest(
            @PathVariable UUID id,
            HttpServletRequest httpRequest) {
        String tenantId = resolveTenantId(httpRequest);
        return ResponseEntity.ok(scoringService.getRequest(id, tenantId));
    }

    @GetMapping("/applications/{applicationId}/request")
    public ResponseEntity<ScoringRequestResponse> getRequestByApplication(
            @PathVariable UUID applicationId,
            HttpServletRequest httpRequest) {
        String tenantId = resolveTenantId(httpRequest);
        return ResponseEntity.ok(scoringService.getRequestByApplication(applicationId, tenantId));
    }

    @GetMapping("/applications/{applicationId}/result")
    public ResponseEntity<ScoringResultResponse> getResultByApplication(
            @PathVariable UUID applicationId,
            HttpServletRequest httpRequest) {
        String tenantId = resolveTenantId(httpRequest);
        return ResponseEntity.ok(scoringService.getResultByApplication(applicationId, tenantId));
    }

    @GetMapping("/customers/{customerId}/latest")
    public ResponseEntity<ScoringResultResponse> getLatestResultByCustomer(
            @PathVariable Long customerId,
            HttpServletRequest httpRequest) {
        String tenantId = resolveTenantId(httpRequest);
        return ResponseEntity.ok(scoringService.getLatestResultByCustomer(customerId, tenantId));
    }

    private String resolveTenantId(HttpServletRequest request) {
        String tenantId = TenantContextHolder.getTenantId();
        if (tenantId != null && !tenantId.isBlank()) {
            return tenantId;
        }
        String header = request.getHeader("X-Tenant-ID");
        return (header != null && !header.isBlank()) ? header : "default";
    }
}
