package com.athena.lms.fraud.controller;

import com.athena.lms.common.auth.TenantContextHolder;
import com.athena.lms.common.dto.PageResponse;
import com.athena.lms.fraud.dto.request.*;
import com.athena.lms.fraud.dto.response.*;
import com.athena.lms.fraud.enums.CaseStatus;
import com.athena.lms.fraud.enums.SarReportType;
import com.athena.lms.fraud.enums.SarStatus;
import com.athena.lms.fraud.entity.ScoringHistory;
import com.athena.lms.fraud.ml.MLScoringClient;
import com.athena.lms.fraud.ml.MLScoringResponse;
import com.athena.lms.fraud.entity.FraudEvent;
import com.athena.lms.fraud.entity.WatchlistEntry;
import com.athena.lms.fraud.repository.FraudEventRepository;
import com.athena.lms.fraud.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/fraud")
@RequiredArgsConstructor
@Tag(name = "Fraud Cases & Network", description = "Case management, network analysis, SAR/CTR reports, and watchlist")
public class FraudCaseController {

    private final CaseManagementService caseManagementService;
    private final NetworkAnalysisService networkAnalysisService;
    private final SarReportService sarReportService;
    private final WatchlistService watchlistService;
    private final FraudScoringService fraudScoringService;
    private final MLScoringClient mlScoringClient;
    private final BatchScreeningService batchScreeningService;
    private final FraudEventRepository fraudEventRepository;

    // ─── Cases ────────────────────────────────────────────────────────────────

    @PostMapping("/cases")
    @Operation(summary = "Create investigation case")
    public ResponseEntity<CaseResponse> createCase(@Valid @RequestBody CreateCaseRequest request) {
        String tenantId = TenantContextHolder.getTenantId();
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(caseManagementService.createCase(request, tenantId));
    }

    @GetMapping("/cases")
    @Operation(summary = "List investigation cases")
    public ResponseEntity<PageResponse<CaseResponse>> listCases(
            @RequestParam(required = false) CaseStatus status,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        String tenantId = TenantContextHolder.getTenantId();
        return ResponseEntity.ok(caseManagementService.listCases(tenantId, status, pageable));
    }

    @GetMapping("/cases/{id}")
    @Operation(summary = "Get case details with notes")
    public ResponseEntity<CaseResponse> getCase(@PathVariable UUID id) {
        String tenantId = TenantContextHolder.getTenantId();
        return ResponseEntity.ok(caseManagementService.getCase(id, tenantId));
    }

    @PutMapping("/cases/{id}")
    @Operation(summary = "Update case")
    public ResponseEntity<CaseResponse> updateCase(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCaseRequest request) {
        String tenantId = TenantContextHolder.getTenantId();
        return ResponseEntity.ok(caseManagementService.updateCase(id, request, tenantId));
    }

    @PostMapping("/cases/{id}/notes")
    @Operation(summary = "Add note to case")
    public ResponseEntity<CaseNoteResponse> addNote(
            @PathVariable UUID id,
            @Valid @RequestBody AddCaseNoteRequest request) {
        String tenantId = TenantContextHolder.getTenantId();
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(caseManagementService.addNote(id, request, tenantId));
    }

    // ─── Audit ────────────────────────────────────────────────────────────────

    @GetMapping("/audit-log")
    @Operation(summary = "View audit trail")
    public ResponseEntity<PageResponse<AuditLogResponse>> getAuditLog(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) UUID entityId,
            @PageableDefault(size = 50) Pageable pageable) {
        String tenantId = TenantContextHolder.getTenantId();
        return ResponseEntity.ok(caseManagementService.getAuditLog(tenantId, entityType, entityId, pageable));
    }

    // ─── Network Analysis ─────────────────────────────────────────────────────

    @GetMapping("/network/{customerId}")
    @Operation(summary = "Get customer network graph")
    public ResponseEntity<NetworkNodeResponse> getCustomerNetwork(@PathVariable String customerId) {
        String tenantId = TenantContextHolder.getTenantId();
        return ResponseEntity.ok(networkAnalysisService.getCustomerNetwork(tenantId, customerId));
    }

    @GetMapping("/network/flagged")
    @Operation(summary = "Get flagged fraud ring clusters")
    public ResponseEntity<List<NetworkNodeResponse>> getFlaggedClusters() {
        String tenantId = TenantContextHolder.getTenantId();
        return ResponseEntity.ok(networkAnalysisService.getFlaggedClusters(tenantId));
    }

    @PostMapping("/network/{linkId}/flag")
    @Operation(summary = "Flag a suspicious network link")
    public ResponseEntity<Void> flagLink(@PathVariable UUID linkId) {
        String tenantId = TenantContextHolder.getTenantId();
        networkAnalysisService.flagLink(tenantId, linkId);
        return ResponseEntity.ok().build();
    }

    // ─── SAR / CTR Reports ────────────────────────────────────────────────────

    @PostMapping("/sar")
    @Operation(summary = "Create SAR/CTR report")
    public ResponseEntity<SarReportResponse> createSarReport(
            @Valid @RequestBody CreateSarReportRequest request) {
        String tenantId = TenantContextHolder.getTenantId();
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(sarReportService.createReport(request, tenantId));
    }

    @GetMapping("/sar")
    @Operation(summary = "List SAR/CTR reports")
    public ResponseEntity<PageResponse<SarReportResponse>> listSarReports(
            @RequestParam(required = false) SarStatus status,
            @RequestParam(required = false) SarReportType reportType,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        String tenantId = TenantContextHolder.getTenantId();
        return ResponseEntity.ok(sarReportService.listReports(tenantId, status, reportType, pageable));
    }

    @GetMapping("/sar/{id}")
    @Operation(summary = "Get SAR/CTR report details")
    public ResponseEntity<SarReportResponse> getSarReport(@PathVariable UUID id) {
        String tenantId = TenantContextHolder.getTenantId();
        return ResponseEntity.ok(sarReportService.getReport(id, tenantId));
    }

    @PutMapping("/sar/{id}")
    @Operation(summary = "Update SAR/CTR report")
    public ResponseEntity<SarReportResponse> updateSarReport(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateSarReportRequest request) {
        String tenantId = TenantContextHolder.getTenantId();
        return ResponseEntity.ok(sarReportService.updateReport(id, request, tenantId));
    }

    @PostMapping("/sar/from-case/{caseId}")
    @Operation(summary = "Generate SAR from investigation case")
    public ResponseEntity<SarReportResponse> generateSarFromCase(@PathVariable UUID caseId) {
        String tenantId = TenantContextHolder.getTenantId();
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(sarReportService.generateFromCase(caseId, tenantId));
    }

    // ─── Watchlist ────────────────────────────────────────────────────────────

    @GetMapping("/watchlist")
    @Operation(summary = "List watchlist entries")
    public ResponseEntity<PageResponse<WatchlistEntryResponse>> listWatchlistEntries(
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        String tenantId = TenantContextHolder.getTenantId();
        return ResponseEntity.ok(watchlistService.listEntries(tenantId, active, pageable));
    }

    @PostMapping("/watchlist")
    @Operation(summary = "Create watchlist entry")
    public ResponseEntity<WatchlistEntryResponse> createWatchlistEntry(
            @Valid @RequestBody CreateWatchlistEntryRequest request) {
        String tenantId = TenantContextHolder.getTenantId();
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(watchlistService.createEntry(request, tenantId));
    }

    @DeleteMapping("/watchlist/{id}")
    @Operation(summary = "Deactivate watchlist entry")
    public ResponseEntity<WatchlistEntryResponse> deactivateWatchlistEntry(@PathVariable UUID id) {
        String tenantId = TenantContextHolder.getTenantId();
        return ResponseEntity.ok(watchlistService.deactivateEntry(id, tenantId));
    }

    // ─── ML Scoring ───────────────────────────────────────────────────────────

    @PostMapping("/score")
    @Operation(summary = "Score a transaction via ML models")
    public ResponseEntity<MLScoringResponse> scoreTransaction(@RequestBody Map<String, Object> request) {
        String tenantId = TenantContextHolder.getTenantId();
        String customerId = (String) request.get("customerId");
        String eventType = (String) request.getOrDefault("eventType", "manual.score");
        BigDecimal amount = request.get("amount") != null
                ? new BigDecimal(request.get("amount").toString()) : null;
        double ruleScore = request.get("ruleScore") != null
                ? ((Number) request.get("ruleScore")).doubleValue() : 0.0;
        return ResponseEntity.ok(fraudScoringService.scoreTransaction(
                tenantId, customerId, eventType, amount, ruleScore));
    }

    @GetMapping("/score/customer/{customerId}")
    @Operation(summary = "Get ML scoring history for a customer")
    public ResponseEntity<PageResponse<ScoringHistory>> getCustomerScoringHistory(
            @PathVariable String customerId,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        String tenantId = TenantContextHolder.getTenantId();
        return ResponseEntity.ok(PageResponse.from(
                fraudScoringService.getCustomerScoringHistory(tenantId, customerId,
                        pageable.getPageNumber(), pageable.getPageSize())));
    }

    @GetMapping("/score/stats")
    @Operation(summary = "Get ML scoring dashboard stats")
    public ResponseEntity<Map<String, Object>> getScoringStats() {
        String tenantId = TenantContextHolder.getTenantId();
        return ResponseEntity.ok(fraudScoringService.getScoringStats(tenantId));
    }

    @GetMapping("/ml/health")
    @Operation(summary = "Check ML service health")
    public ResponseEntity<Map<String, Object>> getMLHealth() {
        boolean healthy = mlScoringClient.checkHealth();
        return ResponseEntity.ok(Map.of("healthy", healthy));
    }

    @PostMapping("/ml/train/{modelType}")
    @Operation(summary = "Trigger ML model retraining")
    public ResponseEntity<Map<String, Object>> triggerTraining(@PathVariable String modelType) {
        return ResponseEntity.ok(mlScoringClient.triggerTraining(modelType));
    }

    @GetMapping("/ml/train/status")
    @Operation(summary = "Get ML training status")
    public ResponseEntity<Map<String, Object>> getTrainingStatus() {
        return ResponseEntity.ok(mlScoringClient.getTrainingStatus());
    }

    // ─── Screening ───────────────────────────────────────────────────────────

    @PostMapping("/screening/batch")
    @Operation(summary = "Trigger batch watchlist screening for all customers")
    public ResponseEntity<BatchScreeningResult> batchScreen() {
        String tenantId = TenantContextHolder.getTenantId();
        return ResponseEntity.ok(batchScreeningService.screenAllCustomers(tenantId));
    }

    @PostMapping("/screening/customer")
    @Operation(summary = "Screen a single customer against watchlists")
    public ResponseEntity<List<WatchlistEntryResponse>> screenCustomer(
            @RequestBody ScreenCustomerRequest request) {
        String tenantId = TenantContextHolder.getTenantId();
        List<WatchlistEntry> matches = batchScreeningService.screenCustomer(
                tenantId, request.getCustomerId(), request.getName(),
                request.getNationalId(), request.getPhone());
        List<WatchlistEntryResponse> responses = matches.stream()
                .map(this::mapWatchlistEntry)
                .toList();
        return ResponseEntity.ok(responses);
    }

    // ─── Timeline ────────────────────────────────────────────────────────────

    @GetMapping("/cases/{id}/timeline")
    @Operation(summary = "Get case activity timeline")
    public ResponseEntity<CaseTimelineResponse> getCaseTimeline(@PathVariable UUID id) {
        String tenantId = TenantContextHolder.getTenantId();
        return ResponseEntity.ok(caseManagementService.getCaseTimeline(id, tenantId));
    }

    // ─── Live Transaction Feed ───────────────────────────────────────────────

    @GetMapping("/events/recent")
    @Operation(summary = "Get recent fraud events for live feed")
    public ResponseEntity<PageResponse<FraudEvent>> getRecentEvents(
            @PageableDefault(size = 20, sort = "processedAt") Pageable pageable) {
        String tenantId = TenantContextHolder.getTenantId();
        return ResponseEntity.ok(PageResponse.from(
                fraudEventRepository.findByTenantId(tenantId, pageable)));
    }

    private WatchlistEntryResponse mapWatchlistEntry(WatchlistEntry entry) {
        WatchlistEntryResponse resp = new WatchlistEntryResponse();
        resp.setId(entry.getId());
        resp.setTenantId(entry.getTenantId());
        resp.setListType(entry.getListType());
        resp.setEntryType(entry.getEntryType());
        resp.setName(entry.getName());
        resp.setNationalId(entry.getNationalId());
        resp.setPhone(entry.getPhone());
        resp.setReason(entry.getReason());
        resp.setSource(entry.getSource());
        resp.setActive(entry.getActive());
        resp.setExpiresAt(entry.getExpiresAt());
        resp.setCreatedAt(entry.getCreatedAt());
        resp.setUpdatedAt(entry.getUpdatedAt());
        return resp;
    }
}
