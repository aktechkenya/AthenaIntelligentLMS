package com.athena.lms.compliance.service;

import com.athena.lms.common.dto.PageResponse;
import com.athena.lms.common.exception.ResourceNotFoundException;
import com.athena.lms.compliance.dto.request.*;
import com.athena.lms.compliance.dto.response.*;
import com.athena.lms.compliance.entity.AmlAlert;
import com.athena.lms.compliance.entity.ComplianceEvent;
import com.athena.lms.compliance.entity.KycRecord;
import com.athena.lms.compliance.entity.SarFiling;
import com.athena.lms.compliance.enums.AlertSeverity;
import com.athena.lms.compliance.enums.AlertStatus;
import com.athena.lms.compliance.enums.KycStatus;
import com.athena.lms.compliance.event.ComplianceEventPublisher;
import com.athena.lms.compliance.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class ComplianceService {

    private final AmlAlertRepository alertRepository;
    private final KycRepository kycRepository;
    private final ComplianceEventRepository eventRepository;
    private final SarRepository sarRepository;
    private final ComplianceEventPublisher eventPublisher;

    // ─── AML Alerts ────────────────────────────────────────────────────────────

    public AlertResponse createAlert(CreateAlertRequest req, String tenantId) {
        AmlAlert alert = AmlAlert.builder()
                .tenantId(tenantId)
                .alertType(req.getAlertType())
                .severity(req.getSeverity() != null ? req.getSeverity() : AlertSeverity.MEDIUM)
                .subjectType(req.getSubjectType())
                .subjectId(req.getSubjectId())
                .customerId(req.getCustomerId())
                .description(req.getDescription())
                .triggerEvent(req.getTriggerEvent())
                .triggerAmount(req.getTriggerAmount())
                .build();

        alert = alertRepository.save(alert);
        log.info("Created AML alert id={} type={} tenant={}", alert.getId(), alert.getAlertType(), tenantId);

        eventPublisher.publishAmlAlertRaised(
                alert.getId(),
                alert.getAlertType().name(),
                alert.getCustomerId(),
                tenantId
        );

        return mapToAlertResponse(alert);
    }

    @Transactional(readOnly = true)
    public AlertResponse getAlert(UUID id, String tenantId) {
        AmlAlert alert = alertRepository.findById(id)
                .filter(a -> a.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("AML alert not found: " + id));
        return mapToAlertResponse(alert);
    }

    @Transactional(readOnly = true)
    public PageResponse<AlertResponse> listAlerts(String tenantId, AlertStatus status, Pageable pageable) {
        Page<AmlAlert> page = (status != null)
                ? alertRepository.findByTenantIdAndStatus(tenantId, status, pageable)
                : alertRepository.findByTenantId(tenantId, pageable);

        return PageResponse.from(page.map(this::mapToAlertResponse));
    }

    public AlertResponse resolveAlert(UUID id, ResolveAlertRequest req, String tenantId) {
        AmlAlert alert = alertRepository.findById(id)
                .filter(a -> a.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("AML alert not found: " + id));

        alert.setStatus(AlertStatus.CLOSED_ACTIONED);
        alert.setResolvedBy(req.getResolvedBy());
        alert.setResolvedAt(OffsetDateTime.now());
        alert.setResolutionNotes(req.getResolutionNotes());

        alert = alertRepository.save(alert);
        log.info("Resolved AML alert id={} by={} tenant={}", id, req.getResolvedBy(), tenantId);
        return mapToAlertResponse(alert);
    }

    public SarResponse fileSar(UUID alertId, FileSarRequest req, String tenantId) {
        AmlAlert alert = alertRepository.findById(alertId)
                .filter(a -> a.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("AML alert not found: " + alertId));

        SarFiling sar = SarFiling.builder()
                .tenantId(tenantId)
                .alertId(alertId)
                .referenceNumber(req.getReferenceNumber())
                .filingDate(req.getFilingDate() != null ? req.getFilingDate() : LocalDate.now())
                .regulator(req.getRegulator() != null ? req.getRegulator() : "FRC Kenya")
                .submittedBy(req.getSubmittedBy())
                .notes(req.getNotes())
                .build();

        sar = sarRepository.save(sar);

        alert.setStatus(AlertStatus.SAR_FILED);
        alert.setSarFiled(true);
        alert.setSarReference(req.getReferenceNumber());
        alertRepository.save(alert);

        eventPublisher.publishSarFiled(alertId, req.getReferenceNumber(), tenantId);
        log.info("Filed SAR id={} for alertId={} tenant={}", sar.getId(), alertId, tenantId);

        return mapToSarResponse(sar);
    }

    // ─── KYC ──────────────────────────────────────────────────────────────────

    public KycResponse createOrUpdateKyc(KycRequest req, String tenantId) {
        KycRecord record = kycRepository.findByTenantIdAndCustomerId(tenantId, req.getCustomerId())
                .orElseGet(() -> KycRecord.builder()
                        .tenantId(tenantId)
                        .customerId(req.getCustomerId())
                        .build());

        record.setCheckType(req.getCheckType() != null ? req.getCheckType() : "FULL_KYC");
        record.setNationalId(req.getNationalId());
        record.setFullName(req.getFullName());
        record.setPhone(req.getPhone());
        record.setRiskLevel(req.getRiskLevel());
        record.setStatus(KycStatus.IN_PROGRESS);

        record = kycRepository.save(record);
        log.info("Upserted KYC record for customerId={} tenant={}", req.getCustomerId(), tenantId);
        return mapToKycResponse(record);
    }

    public KycResponse passKyc(Long customerId, String tenantId) {
        KycRecord record = kycRepository.findByTenantIdAndCustomerId(tenantId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("KYC record not found for customerId: " + customerId));

        record.setStatus(KycStatus.PASSED);
        record.setCheckedAt(OffsetDateTime.now());
        record = kycRepository.save(record);

        eventPublisher.publishKycPassed(customerId, tenantId);
        log.info("KYC passed for customerId={} tenant={}", customerId, tenantId);
        return mapToKycResponse(record);
    }

    public KycResponse failKyc(Long customerId, String failureReason, String tenantId) {
        KycRecord record = kycRepository.findByTenantIdAndCustomerId(tenantId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("KYC record not found for customerId: " + customerId));

        record.setStatus(KycStatus.FAILED);
        record.setFailureReason(failureReason);
        record.setCheckedAt(OffsetDateTime.now());
        record = kycRepository.save(record);

        eventPublisher.publishKycFailed(customerId, failureReason, tenantId);
        log.info("KYC failed for customerId={} tenant={}", customerId, tenantId);
        return mapToKycResponse(record);
    }

    @Transactional(readOnly = true)
    public KycResponse getKyc(Long customerId, String tenantId) {
        KycRecord record = kycRepository.findByTenantIdAndCustomerId(tenantId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("KYC record not found for customerId: " + customerId));
        return mapToKycResponse(record);
    }

    // ─── Summary ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ComplianceSummaryResponse getSummary(String tenantId) {
        ComplianceSummaryResponse summary = new ComplianceSummaryResponse();
        summary.setTenantId(tenantId);
        summary.setOpenAlerts(alertRepository.countByTenantIdAndStatus(tenantId, AlertStatus.OPEN));
        summary.setCriticalAlerts(alertRepository.countByTenantIdAndSeverityAndStatus(
                tenantId, AlertSeverity.CRITICAL, AlertStatus.OPEN));
        summary.setUnderReviewAlerts(alertRepository.countByTenantIdAndStatus(tenantId, AlertStatus.UNDER_REVIEW));
        summary.setSarFiledAlerts(alertRepository.countByTenantIdAndStatus(tenantId, AlertStatus.SAR_FILED));
        summary.setPendingKyc(kycRepository.countByTenantIdAndStatus(tenantId, KycStatus.PENDING));
        summary.setFailedKyc(kycRepository.countByTenantIdAndStatus(tenantId, KycStatus.FAILED));
        return summary;
    }

    // ─── Events ───────────────────────────────────────────────────────────────

    public void logEvent(String eventType, String sourceService, String subjectId, String payload, String tenantId) {
        ComplianceEvent event = ComplianceEvent.builder()
                .tenantId(tenantId)
                .eventType(eventType)
                .sourceService(sourceService)
                .subjectId(subjectId)
                .payload(payload)
                .build();
        eventRepository.save(event);
    }

    @Transactional(readOnly = true)
    public PageResponse<ComplianceEvent> listEvents(String tenantId, Pageable pageable) {
        Page<ComplianceEvent> page = eventRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, pageable);
        return PageResponse.from(page);
    }

    // ─── Mappers ──────────────────────────────────────────────────────────────

    private AlertResponse mapToAlertResponse(AmlAlert alert) {
        AlertResponse resp = new AlertResponse();
        resp.setId(alert.getId());
        resp.setTenantId(alert.getTenantId());
        resp.setAlertType(alert.getAlertType());
        resp.setSeverity(alert.getSeverity());
        resp.setStatus(alert.getStatus());
        resp.setSubjectType(alert.getSubjectType());
        resp.setSubjectId(alert.getSubjectId());
        resp.setCustomerId(alert.getCustomerId());
        resp.setDescription(alert.getDescription());
        resp.setTriggerEvent(alert.getTriggerEvent());
        resp.setTriggerAmount(alert.getTriggerAmount());
        resp.setSarFiled(alert.getSarFiled());
        resp.setSarReference(alert.getSarReference());
        resp.setAssignedTo(alert.getAssignedTo());
        resp.setResolvedBy(alert.getResolvedBy());
        resp.setResolvedAt(alert.getResolvedAt());
        resp.setResolutionNotes(alert.getResolutionNotes());
        resp.setCreatedAt(alert.getCreatedAt());
        resp.setUpdatedAt(alert.getUpdatedAt());
        return resp;
    }

    private KycResponse mapToKycResponse(KycRecord record) {
        KycResponse resp = new KycResponse();
        resp.setId(record.getId());
        resp.setTenantId(record.getTenantId());
        resp.setCustomerId(record.getCustomerId());
        resp.setStatus(record.getStatus());
        resp.setCheckType(record.getCheckType());
        resp.setNationalId(record.getNationalId());
        resp.setFullName(record.getFullName());
        resp.setPhone(record.getPhone());
        resp.setRiskLevel(record.getRiskLevel());
        resp.setFailureReason(record.getFailureReason());
        resp.setCheckedBy(record.getCheckedBy());
        resp.setCheckedAt(record.getCheckedAt());
        resp.setExpiresAt(record.getExpiresAt());
        resp.setCreatedAt(record.getCreatedAt());
        resp.setUpdatedAt(record.getUpdatedAt());
        return resp;
    }

    private SarResponse mapToSarResponse(SarFiling sar) {
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
    }
}
