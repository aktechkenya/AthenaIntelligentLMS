package com.athena.lms.reporting.controller;

import com.athena.lms.common.auth.TenantContextHolder;
import com.athena.lms.common.dto.PageResponse;
import com.athena.lms.reporting.dto.response.EventMetricResponse;
import com.athena.lms.reporting.dto.response.PortfolioSnapshotResponse;
import com.athena.lms.reporting.dto.response.PortfolioSummaryResponse;
import com.athena.lms.reporting.dto.response.ReportEventResponse;
import com.athena.lms.reporting.service.ReportingService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/reporting")
@RequiredArgsConstructor
@Slf4j
public class ReportingController {

    private final ReportingService reportingService;

    @GetMapping("/events")
    public ResponseEntity<PageResponse<ReportEventResponse>> getEvents(
            HttpServletRequest request,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        String tenantId = tenantId(request);
        Instant fromInstant = from != null ? Instant.parse(from) : null;
        Instant toInstant = to != null ? Instant.parse(to) : null;
        Pageable pageable = PageRequest.of(page, size);

        return ResponseEntity.ok(reportingService.getEvents(tenantId, eventType, fromInstant, toInstant, pageable));
    }

    @GetMapping("/snapshots")
    public ResponseEntity<PageResponse<PortfolioSnapshotResponse>> getSnapshots(
            HttpServletRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {

        String tenantId = tenantId(request);
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(reportingService.getSnapshots(tenantId, pageable));
    }

    @GetMapping("/snapshots/latest")
    public ResponseEntity<PortfolioSnapshotResponse> getLatestSnapshot(HttpServletRequest request) {
        String tenantId = tenantId(request);
        return ResponseEntity.ok(reportingService.getLatestSnapshot(tenantId));
    }

    @GetMapping("/metrics")
    public ResponseEntity<List<EventMetricResponse>> getMetrics(
            HttpServletRequest request,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        String tenantId = tenantId(request);
        return ResponseEntity.ok(reportingService.getMetrics(tenantId, from, to));
    }

    @GetMapping("/summary")
    public ResponseEntity<PortfolioSummaryResponse> getSummary(HttpServletRequest request) {
        String tenantId = tenantId(request);

        PortfolioSummaryResponse summary = new PortfolioSummaryResponse();
        summary.setTenantId(tenantId);
        summary.setAsOfDate(LocalDate.now());

        try {
            PortfolioSnapshotResponse latest = reportingService.getLatestSnapshot(tenantId);
            summary.setTotalLoans(latest.getTotalLoans());
            summary.setActiveLoans(latest.getActiveLoans());
            summary.setClosedLoans(latest.getClosedLoans());
            summary.setDefaultedLoans(latest.getDefaultedLoans());
            summary.setTotalDisbursed(latest.getTotalDisbursed());
            summary.setTotalOutstanding(latest.getTotalOutstanding());
            summary.setTotalCollected(latest.getTotalCollected());
            summary.setPar30(latest.getPar30());
            summary.setPar90(latest.getPar90());
            summary.setWatchLoans(latest.getWatchLoans());
            summary.setSubstandardLoans(latest.getSubstandardLoans());
            summary.setDoubtfulLoans(latest.getDoubtfulLoans());
            summary.setLossLoans(latest.getLossLoans());
        } catch (Exception e) {
            log.warn("No latest snapshot available for tenant={}, returning empty summary", tenantId);
            summary.setTotalLoans(0);
            summary.setActiveLoans(0);
            summary.setClosedLoans(0);
            summary.setDefaultedLoans(0);
            summary.setTotalDisbursed(BigDecimal.ZERO);
            summary.setTotalOutstanding(BigDecimal.ZERO);
            summary.setTotalCollected(BigDecimal.ZERO);
            summary.setPar30(BigDecimal.ZERO);
            summary.setPar90(BigDecimal.ZERO);
            summary.setWatchLoans(0);
            summary.setSubstandardLoans(0);
            summary.setDoubtfulLoans(0);
            summary.setLossLoans(0);
        }

        List<EventMetricResponse> todayMetrics = reportingService.getMetrics(
                tenantId, LocalDate.now(), LocalDate.now());
        log.debug("Today's metrics count={} for tenant={}", todayMetrics.size(), tenantId);

        return ResponseEntity.ok(summary);
    }

    @PostMapping("/snapshots/generate")
    public ResponseEntity<Void> generateSnapshot(HttpServletRequest request) {
        String tenantId = tenantId(request);
        reportingService.generateDailySnapshot(tenantId);
        return ResponseEntity.accepted().build();
    }

    private String tenantId(HttpServletRequest request) {
        String tenantId = TenantContextHolder.getTenantId();
        if (tenantId != null && !tenantId.isBlank()) {
            return tenantId;
        }
        String header = request.getHeader("X-Tenant-ID");
        if (header != null && !header.isBlank()) {
            return header;
        }
        return "default";
    }
}
