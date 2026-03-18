package com.athena.lms.fraud.service;

import com.athena.lms.common.dto.PageResponse;
import com.athena.lms.common.exception.ResourceNotFoundException;
import com.athena.lms.fraud.dto.request.CreateSarReportRequest;
import com.athena.lms.fraud.dto.request.UpdateSarReportRequest;
import com.athena.lms.fraud.dto.response.SarReportResponse;
import com.athena.lms.fraud.entity.FraudAlert;
import com.athena.lms.fraud.entity.FraudCase;
import com.athena.lms.fraud.entity.SarReport;
import com.athena.lms.fraud.enums.SarReportType;
import com.athena.lms.fraud.enums.SarStatus;
import com.athena.lms.fraud.event.FraudEventPublisher;
import com.athena.lms.fraud.repository.FraudAlertRepository;
import com.athena.lms.fraud.repository.FraudCaseRepository;
import com.athena.lms.fraud.repository.SarReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.OffsetDateTime;
import java.util.*;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class SarReportService {

    private final SarReportRepository sarReportRepository;
    private final FraudCaseRepository caseRepository;
    private final FraudAlertRepository alertRepository;
    private final CaseManagementService caseManagementService;
    private final FraudEventPublisher eventPublisher;

    public SarReportResponse createReport(CreateSarReportRequest req, String tenantId) {
        int maxNum = sarReportRepository.findMaxReportNumber(tenantId);
        String reportNumber = String.format("SAR-%05d", maxNum + 1);

        SarReportType reportType = req.getReportType() != null
                ? SarReportType.valueOf(req.getReportType())
                : SarReportType.SAR;

        SarReport report = SarReport.builder()
                .tenantId(tenantId)
                .reportNumber(reportNumber)
                .reportType(reportType)
                .subjectCustomerId(req.getSubjectCustomerId())
                .subjectName(req.getSubjectName())
                .subjectNationalId(req.getSubjectNationalId())
                .narrative(req.getNarrative())
                .suspiciousAmount(req.getSuspiciousAmount())
                .activityStartDate(req.getActivityStartDate())
                .activityEndDate(req.getActivityEndDate())
                .alertIds(req.getAlertIds() != null ? req.getAlertIds() : new HashSet<>())
                .caseId(req.getCaseId())
                .preparedBy(req.getPreparedBy())
                .filingDeadline(calculateFilingDeadline(OffsetDateTime.now(), 7))
                .build();

        report = sarReportRepository.save(report);

        caseManagementService.audit(tenantId, "SAR_CREATED", "SAR_REPORT", report.getId(),
                req.getPreparedBy() != null ? req.getPreparedBy() : "system",
                "SAR report created: " + reportNumber, null);

        log.info("Created {} report {} for tenant={}", reportType, reportNumber, tenantId);
        return mapToResponse(report);
    }

    @Transactional(readOnly = true)
    public SarReportResponse getReport(UUID id, String tenantId) {
        SarReport report = sarReportRepository.findById(id)
                .filter(r -> r.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("SAR report not found: " + id));
        return mapToResponse(report);
    }

    @Transactional(readOnly = true)
    public PageResponse<SarReportResponse> listReports(String tenantId, SarStatus status,
                                                        SarReportType reportType, Pageable pageable) {
        Page<SarReport> page;
        if (status != null) {
            page = sarReportRepository.findByTenantIdAndStatus(tenantId, status, pageable);
        } else if (reportType != null) {
            page = sarReportRepository.findByTenantIdAndReportType(tenantId, reportType, pageable);
        } else {
            page = sarReportRepository.findByTenantId(tenantId, pageable);
        }
        return PageResponse.from(page.map(this::mapToResponse));
    }

    public SarReportResponse updateReport(UUID id, UpdateSarReportRequest req, String tenantId) {
        SarReport report = sarReportRepository.findById(id)
                .filter(r -> r.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("SAR report not found: " + id));

        if (report.getStatus() == SarStatus.FILED) {
            throw new IllegalStateException("Cannot update a FILED SAR report: " + id);
        }

        Map<String, Object> changes = new HashMap<>();

        if (req.getStatus() != null) {
            SarStatus newStatus = SarStatus.valueOf(req.getStatus());
            changes.put("status", Map.of("from", report.getStatus().name(), "to", newStatus.name()));
            report.setStatus(newStatus);

            if (newStatus == SarStatus.FILED) {
                report.setFiledAt(OffsetDateTime.now());
                report.setFiledBy(req.getFiledBy());
                report.setFilingReference(req.getFilingReference());
                eventPublisher.publishSarFiled(report.getReportNumber(), report.getSubjectCustomerId(), tenantId);
            }
        }
        if (req.getNarrative() != null) {
            report.setNarrative(req.getNarrative());
        }
        if (req.getSuspiciousAmount() != null) {
            report.setSuspiciousAmount(req.getSuspiciousAmount());
        }
        if (req.getActivityStartDate() != null) {
            report.setActivityStartDate(req.getActivityStartDate());
        }
        if (req.getActivityEndDate() != null) {
            report.setActivityEndDate(req.getActivityEndDate());
        }
        if (req.getAlertIds() != null) {
            report.setAlertIds(req.getAlertIds());
        }
        if (req.getReviewedBy() != null) {
            report.setReviewedBy(req.getReviewedBy());
        }

        report = sarReportRepository.save(report);

        String actor = req.getFiledBy() != null ? req.getFiledBy()
                : (req.getReviewedBy() != null ? req.getReviewedBy() : "system");
        caseManagementService.audit(tenantId, "SAR_UPDATED", "SAR_REPORT", report.getId(),
                actor, "SAR report updated", changes);

        return mapToResponse(report);
    }

    public SarReportResponse generateFromCase(UUID caseId, String tenantId) {
        FraudCase fraudCase = caseRepository.findById(caseId)
                .filter(c -> c.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Case not found: " + caseId));

        // Collect alert details for the narrative
        Set<UUID> alertIds = fraudCase.getAlertIds() != null ? fraudCase.getAlertIds() : new HashSet<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        StringBuilder narrativeBuilder = new StringBuilder();
        narrativeBuilder.append("Auto-generated SAR from case ").append(fraudCase.getCaseNumber()).append(".\n");
        narrativeBuilder.append("Case title: ").append(fraudCase.getTitle()).append("\n");
        if (fraudCase.getDescription() != null) {
            narrativeBuilder.append("Description: ").append(fraudCase.getDescription()).append("\n");
        }

        OffsetDateTime earliestActivity = null;
        OffsetDateTime latestActivity = null;

        for (UUID alertId : alertIds) {
            Optional<FraudAlert> alertOpt = alertRepository.findById(alertId);
            if (alertOpt.isPresent()) {
                FraudAlert alert = alertOpt.get();
                narrativeBuilder.append("\nAlert: ").append(alert.getAlertType())
                        .append(" - ").append(alert.getDescription());
                if (alert.getTriggerAmount() != null) {
                    totalAmount = totalAmount.add(alert.getTriggerAmount());
                }
                if (earliestActivity == null || alert.getCreatedAt().isBefore(earliestActivity)) {
                    earliestActivity = alert.getCreatedAt();
                }
                if (latestActivity == null || alert.getCreatedAt().isAfter(latestActivity)) {
                    latestActivity = alert.getCreatedAt();
                }
            }
        }

        int maxNum = sarReportRepository.findMaxReportNumber(tenantId);
        String reportNumber = String.format("SAR-%05d", maxNum + 1);

        SarReport report = SarReport.builder()
                .tenantId(tenantId)
                .reportNumber(reportNumber)
                .reportType(SarReportType.SAR)
                .subjectCustomerId(fraudCase.getCustomerId())
                .narrative(narrativeBuilder.toString())
                .suspiciousAmount(totalAmount)
                .activityStartDate(earliestActivity)
                .activityEndDate(latestActivity)
                .alertIds(alertIds)
                .caseId(caseId)
                .preparedBy("system")
                .filingDeadline(calculateFilingDeadline(OffsetDateTime.now(), 7))
                .build();

        report = sarReportRepository.save(report);

        caseManagementService.audit(tenantId, "SAR_GENERATED_FROM_CASE", "SAR_REPORT", report.getId(),
                "system", "SAR auto-generated from case " + fraudCase.getCaseNumber(), null);

        log.info("Generated SAR {} from case {} for tenant={}", reportNumber, fraudCase.getCaseNumber(), tenantId);
        return mapToResponse(report);
    }

    public SarReportResponse generateCTR(String tenantId, String customerId, BigDecimal amount,
                                          Map<String, Object> eventData) {
        int maxNum = sarReportRepository.findMaxReportNumber(tenantId);
        String reportNumber = String.format("SAR-%05d", maxNum + 1);

        String narrative = String.format(
                "Currency Transaction Report: Customer %s conducted a transaction of %s exceeding the reporting threshold.",
                customerId, amount);

        SarReport report = SarReport.builder()
                .tenantId(tenantId)
                .reportNumber(reportNumber)
                .reportType(SarReportType.CTR)
                .subjectCustomerId(customerId)
                .narrative(narrative)
                .suspiciousAmount(amount)
                .activityStartDate(OffsetDateTime.now())
                .activityEndDate(OffsetDateTime.now())
                .preparedBy("system")
                .filingDeadline(calculateFilingDeadline(OffsetDateTime.now(), 7))
                .metadata(eventData)
                .build();

        report = sarReportRepository.save(report);

        caseManagementService.audit(tenantId, "CTR_GENERATED", "SAR_REPORT", report.getId(),
                "system", "CTR auto-generated for customer " + customerId, null);

        log.info("Generated CTR {} for customer {} tenant={}", reportNumber, customerId, tenantId);
        return mapToResponse(report);
    }

    // ─── Helpers ────────────────────────────────────────────────────────────────

    OffsetDateTime calculateFilingDeadline(OffsetDateTime from, int businessDays) {
        OffsetDateTime deadline = from;
        int added = 0;
        while (added < businessDays) {
            deadline = deadline.plusDays(1);
            DayOfWeek dow = deadline.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
                added++;
            }
        }
        return deadline;
    }

    private SarReportResponse mapToResponse(SarReport r) {
        SarReportResponse resp = new SarReportResponse();
        resp.setId(r.getId());
        resp.setTenantId(r.getTenantId());
        resp.setReportNumber(r.getReportNumber());
        resp.setReportType(r.getReportType());
        resp.setStatus(r.getStatus());
        resp.setSubjectCustomerId(r.getSubjectCustomerId());
        resp.setSubjectName(r.getSubjectName());
        resp.setSubjectNationalId(r.getSubjectNationalId());
        resp.setNarrative(r.getNarrative());
        resp.setSuspiciousAmount(r.getSuspiciousAmount());
        resp.setActivityStartDate(r.getActivityStartDate());
        resp.setActivityEndDate(r.getActivityEndDate());
        resp.setAlertIds(r.getAlertIds());
        resp.setCaseId(r.getCaseId());
        resp.setPreparedBy(r.getPreparedBy());
        resp.setReviewedBy(r.getReviewedBy());
        resp.setFiledBy(r.getFiledBy());
        resp.setFiledAt(r.getFiledAt());
        resp.setFilingReference(r.getFilingReference());
        resp.setRegulator(r.getRegulator());
        resp.setFilingDeadline(r.getFilingDeadline());
        resp.setCreatedAt(r.getCreatedAt());
        resp.setUpdatedAt(r.getUpdatedAt());
        return resp;
    }
}
