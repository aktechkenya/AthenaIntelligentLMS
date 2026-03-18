package com.athena.lms.fraud.controller;

import com.athena.lms.common.auth.TenantContextHolder;
import com.athena.lms.common.dto.PageResponse;
import com.athena.lms.fraud.dto.request.AssignAlertRequest;
import com.athena.lms.fraud.dto.request.ResolveAlertRequest;
import com.athena.lms.fraud.dto.response.AlertResponse;
import com.athena.lms.fraud.dto.response.CustomerRiskResponse;
import com.athena.lms.fraud.dto.response.FraudSummaryResponse;
import com.athena.lms.fraud.enums.AlertStatus;
import com.athena.lms.fraud.dto.response.FraudAnalyticsResponse;
import com.athena.lms.fraud.service.FraudAnalyticsService;
import com.athena.lms.fraud.service.FraudDetectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.athena.lms.fraud.dto.request.BulkAlertActionRequest;
import com.athena.lms.fraud.dto.request.UpdateRuleRequest;
import com.athena.lms.fraud.dto.response.RuleResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/fraud")
@RequiredArgsConstructor
@Tag(name = "Fraud Detection", description = "Fraud alert management, risk scoring, and monitoring")
public class FraudController {

    private final FraudDetectionService fraudDetectionService;
    private final FraudAnalyticsService fraudAnalyticsService;

    // ─── Alerts ──────────────────────────────────────────────────────────────────

    @GetMapping("/alerts")
    @Operation(summary = "List fraud alerts", description = "Paginated list of fraud alerts, optionally filtered by status")
    public ResponseEntity<PageResponse<AlertResponse>> listAlerts(
            @RequestParam(required = false) AlertStatus status,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        String tenantId = TenantContextHolder.getTenantId();
        return ResponseEntity.ok(fraudDetectionService.listAlerts(tenantId, status, pageable));
    }

    @GetMapping("/alerts/{id}")
    @Operation(summary = "Get fraud alert details")
    public ResponseEntity<AlertResponse> getAlert(@PathVariable UUID id) {
        String tenantId = TenantContextHolder.getTenantId();
        return ResponseEntity.ok(fraudDetectionService.getAlert(id, tenantId));
    }

    @PutMapping("/alerts/{id}/resolve")
    @Operation(summary = "Resolve a fraud alert", description = "Mark alert as confirmed fraud or false positive")
    public ResponseEntity<AlertResponse> resolveAlert(
            @PathVariable UUID id,
            @Valid @RequestBody ResolveAlertRequest request) {
        String tenantId = TenantContextHolder.getTenantId();
        return ResponseEntity.ok(fraudDetectionService.resolveAlert(id, request, tenantId));
    }

    @PutMapping("/alerts/{id}/assign")
    @Operation(summary = "Assign alert to analyst")
    public ResponseEntity<AlertResponse> assignAlert(
            @PathVariable UUID id,
            @Valid @RequestBody AssignAlertRequest request) {
        String tenantId = TenantContextHolder.getTenantId();
        return ResponseEntity.ok(fraudDetectionService.assignAlert(id, request.getAssignee(), tenantId));
    }

    // ─── Customer Risk ───────────────────────────────────────────────────────────

    @GetMapping("/customer/{customerId}/risk")
    @Operation(summary = "Get customer risk profile")
    public ResponseEntity<CustomerRiskResponse> getCustomerRisk(@PathVariable String customerId) {
        String tenantId = TenantContextHolder.getTenantId();
        return ResponseEntity.ok(fraudDetectionService.getCustomerRisk(tenantId, customerId));
    }

    @GetMapping("/customer/{customerId}/alerts")
    @Operation(summary = "List fraud alerts for a customer")
    public ResponseEntity<PageResponse<AlertResponse>> listCustomerAlerts(
            @PathVariable String customerId,
            @PageableDefault(size = 20) Pageable pageable) {
        String tenantId = TenantContextHolder.getTenantId();
        return ResponseEntity.ok(fraudDetectionService.listCustomerAlerts(tenantId, customerId, pageable));
    }

    @GetMapping("/high-risk-customers")
    @Operation(summary = "List high-risk customers")
    public ResponseEntity<PageResponse<CustomerRiskResponse>> listHighRiskCustomers(
            @PageableDefault(size = 20) Pageable pageable) {
        String tenantId = TenantContextHolder.getTenantId();
        return ResponseEntity.ok(fraudDetectionService.listHighRiskCustomers(tenantId, pageable));
    }

    // ─── Summary ─────────────────────────────────────────────────────────────────

    @GetMapping("/summary")
    @Operation(summary = "Get fraud detection summary/dashboard metrics")
    public ResponseEntity<FraudSummaryResponse> getSummary() {
        String tenantId = TenantContextHolder.getTenantId();
        return ResponseEntity.ok(fraudDetectionService.getSummary(tenantId));
    }

    // ─── Analytics ─────────────────────────────────────────────────────────────

    @GetMapping("/analytics")
    @Operation(summary = "Get fraud analytics and rule effectiveness metrics")
    public ResponseEntity<FraudAnalyticsResponse> getAnalytics(
            @RequestParam(defaultValue = "30") int days) {
        String tenantId = TenantContextHolder.getTenantId();
        return ResponseEntity.ok(fraudAnalyticsService.getAnalytics(tenantId, days));
    }

    // ─── Rules Management ─────────────────────────────────────────────────────

    @GetMapping("/rules")
    @Operation(summary = "List all fraud detection rules")
    public ResponseEntity<List<RuleResponse>> listRules() {
        String tenantId = TenantContextHolder.getTenantId();
        return ResponseEntity.ok(fraudDetectionService.listRules(tenantId));
    }

    @GetMapping("/rules/{id}")
    @Operation(summary = "Get rule details")
    public ResponseEntity<RuleResponse> getRule(@PathVariable UUID id) {
        String tenantId = TenantContextHolder.getTenantId();
        return ResponseEntity.ok(fraudDetectionService.getRule(id, tenantId));
    }

    @PutMapping("/rules/{id}")
    @Operation(summary = "Update rule configuration")
    public ResponseEntity<RuleResponse> updateRule(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRuleRequest request) {
        String tenantId = TenantContextHolder.getTenantId();
        return ResponseEntity.ok(fraudDetectionService.updateRule(id, request, tenantId));
    }

    // ─── Bulk Operations ──────────────────────────────────────────────────────

    @PutMapping("/alerts/bulk/assign")
    @Operation(summary = "Bulk assign alerts to analyst")
    public ResponseEntity<Map<String, Object>> bulkAssignAlerts(
            @Valid @RequestBody BulkAlertActionRequest request,
            @RequestParam String assignee) {
        String tenantId = TenantContextHolder.getTenantId();
        return ResponseEntity.ok(fraudDetectionService.bulkAssign(request.getAlertIds(), assignee, request.getPerformedBy(), tenantId));
    }

    @PutMapping("/alerts/bulk/resolve")
    @Operation(summary = "Bulk resolve alerts")
    public ResponseEntity<Map<String, Object>> bulkResolveAlerts(
            @Valid @RequestBody BulkAlertActionRequest request,
            @RequestParam boolean confirmedFraud) {
        String tenantId = TenantContextHolder.getTenantId();
        return ResponseEntity.ok(fraudDetectionService.bulkResolve(request.getAlertIds(), confirmedFraud, request.getPerformedBy(), request.getNotes(), tenantId));
    }
}
