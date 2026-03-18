package com.athena.lms.fraud.service;

import com.athena.lms.common.dto.PageResponse;
import com.athena.lms.common.exception.ResourceNotFoundException;
import com.athena.lms.fraud.dto.request.*;
import com.athena.lms.fraud.dto.response.*;
import com.athena.lms.fraud.entity.*;
import com.athena.lms.fraud.enums.*;
import com.athena.lms.fraud.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class CaseManagementService {

    private final FraudCaseRepository caseRepository;
    private final CaseNoteRepository noteRepository;
    private final AuditLogRepository auditLogRepository;
    private final FraudAlertRepository alertRepository;

    public CaseResponse createCase(CreateCaseRequest req, String tenantId) {
        int maxNum = caseRepository.findMaxCaseNumber(tenantId);
        String caseNumber = String.format("FRD-%05d", maxNum + 1);

        FraudCase fraudCase = FraudCase.builder()
            .tenantId(tenantId)
            .caseNumber(caseNumber)
            .title(req.getTitle())
            .description(req.getDescription())
            .priority(req.getPriority() != null ? AlertSeverity.valueOf(req.getPriority()) : AlertSeverity.MEDIUM)
            .customerId(req.getCustomerId())
            .assignedTo(req.getAssignedTo())
            .totalExposure(req.getTotalExposure())
            .alertIds(req.getAlertIds() != null ? req.getAlertIds() : new HashSet<>())
            .tags(req.getTags())
            .build();

        setSlaDeadline(fraudCase);
        fraudCase = caseRepository.save(fraudCase);
        audit(tenantId, "CASE_CREATED", "CASE", fraudCase.getId(),
              req.getAssignedTo() != null ? req.getAssignedTo() : "system",
              "Case created: " + caseNumber, null);

        log.info("Created fraud case {} for tenant={}", caseNumber, tenantId);
        return mapToResponse(fraudCase, tenantId);
    }

    @Transactional(readOnly = true)
    public CaseResponse getCase(UUID id, String tenantId) {
        FraudCase fraudCase = caseRepository.findById(id)
            .filter(c -> c.getTenantId().equals(tenantId))
            .orElseThrow(() -> new ResourceNotFoundException("Case not found: " + id));
        return mapToResponse(fraudCase, tenantId);
    }

    @Transactional(readOnly = true)
    public PageResponse<CaseResponse> listCases(String tenantId, CaseStatus status, Pageable pageable) {
        Page<FraudCase> page = status != null
            ? caseRepository.findByTenantIdAndStatus(tenantId, status, pageable)
            : caseRepository.findByTenantId(tenantId, pageable);
        return PageResponse.from(page.map(c -> mapToResponse(c, tenantId)));
    }

    public CaseResponse updateCase(UUID id, UpdateCaseRequest req, String tenantId) {
        FraudCase fraudCase = caseRepository.findById(id)
            .filter(c -> c.getTenantId().equals(tenantId))
            .orElseThrow(() -> new ResourceNotFoundException("Case not found: " + id));

        Map<String, Object> changes = new HashMap<>();

        if (req.getStatus() != null) {
            CaseStatus newStatus = CaseStatus.valueOf(req.getStatus());
            changes.put("status", Map.of("from", fraudCase.getStatus().name(), "to", newStatus.name()));
            fraudCase.setStatus(newStatus);
            if (newStatus.name().startsWith("CLOSED")) {
                fraudCase.setClosedAt(OffsetDateTime.now());
                fraudCase.setClosedBy(req.getClosedBy());
                fraudCase.setOutcome(req.getOutcome());
            }
        }
        if (req.getPriority() != null) {
            changes.put("priority", Map.of("from", fraudCase.getPriority().name(), "to", req.getPriority()));
            fraudCase.setPriority(AlertSeverity.valueOf(req.getPriority()));
        }
        if (req.getAssignedTo() != null) {
            changes.put("assignedTo", Map.of("from", fraudCase.getAssignedTo(), "to", req.getAssignedTo()));
            fraudCase.setAssignedTo(req.getAssignedTo());
        }
        if (req.getTotalExposure() != null) fraudCase.setTotalExposure(req.getTotalExposure());
        if (req.getConfirmedLoss() != null) fraudCase.setConfirmedLoss(req.getConfirmedLoss());
        if (req.getAlertIds() != null) fraudCase.setAlertIds(req.getAlertIds());
        if (req.getTags() != null) fraudCase.setTags(req.getTags());

        fraudCase = caseRepository.save(fraudCase);

        String actor = req.getClosedBy() != null ? req.getClosedBy() : (req.getAssignedTo() != null ? req.getAssignedTo() : "system");
        audit(tenantId, "CASE_UPDATED", "CASE", fraudCase.getId(), actor, "Case updated", changes);

        return mapToResponse(fraudCase, tenantId);
    }

    public CaseNoteResponse addNote(UUID caseId, AddCaseNoteRequest req, String tenantId) {
        // Verify case exists
        caseRepository.findById(caseId)
            .filter(c -> c.getTenantId().equals(tenantId))
            .orElseThrow(() -> new ResourceNotFoundException("Case not found: " + caseId));

        CaseNote note = CaseNote.builder()
            .caseId(caseId)
            .tenantId(tenantId)
            .author(req.getAuthor())
            .content(req.getContent())
            .noteType(req.getNoteType() != null ? req.getNoteType() : "COMMENT")
            .build();

        note = noteRepository.save(note);
        audit(tenantId, "NOTE_ADDED", "CASE", caseId, req.getAuthor(), "Note added to case", null);

        return mapNoteResponse(note);
    }

    // ─── Audit ──────────────────────────────────────────────────────────────

    public void audit(String tenantId, String action, String entityType, UUID entityId,
                       String performedBy, String description, Map<String, Object> changes) {
        AuditLog entry = AuditLog.builder()
            .tenantId(tenantId)
            .action(action)
            .entityType(entityType)
            .entityId(entityId)
            .performedBy(performedBy)
            .description(description)
            .changes(changes)
            .build();
        auditLogRepository.save(entry);
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> getAuditLog(String tenantId, String entityType,
                                                        UUID entityId, Pageable pageable) {
        Page<AuditLog> page;
        if (entityType != null && entityId != null) {
            page = auditLogRepository.findByTenantIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(
                tenantId, entityType, entityId, pageable);
        } else {
            page = auditLogRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, pageable);
        }
        return PageResponse.from(page.map(this::mapAuditResponse));
    }

    // ─── Timeline ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public CaseTimelineResponse getCaseTimeline(UUID caseId, String tenantId) {
        FraudCase fraudCase = caseRepository.findById(caseId)
            .filter(c -> c.getTenantId().equals(tenantId))
            .orElseThrow(() -> new ResourceNotFoundException("Case not found: " + caseId));

        List<AuditLog> auditEntries = auditLogRepository
                .findByTenantIdAndEntityTypeAndEntityIdOrderByCreatedAtAsc(tenantId, "CASE", caseId);

        List<CaseTimelineResponse.TimelineEvent> events = auditEntries.stream()
                .map(entry -> CaseTimelineResponse.TimelineEvent.builder()
                        .action(entry.getAction())
                        .description(entry.getDescription())
                        .performedBy(entry.getPerformedBy())
                        .timestamp(entry.getCreatedAt())
                        .build())
                .toList();

        return CaseTimelineResponse.builder()
                .caseId(caseId)
                .caseNumber(fraudCase.getCaseNumber())
                .events(events)
                .build();
    }

    // ─── SLA ──────────────────────────────────────────────────────────────────

    public void setSlaDeadline(FraudCase fraudCase) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime deadline = switch (fraudCase.getPriority()) {
            case CRITICAL -> now.plusHours(24);
            case HIGH -> now.plusHours(48);
            case MEDIUM -> now.plusDays(5);
            case LOW -> now.plusDays(10);
        };
        fraudCase.setSlaDeadline(deadline);
    }

    // ─── Mappers ────────────────────────────────────────────────────────────

    private CaseResponse mapToResponse(FraudCase c, String tenantId) {
        CaseResponse resp = new CaseResponse();
        resp.setId(c.getId());
        resp.setTenantId(c.getTenantId());
        resp.setCaseNumber(c.getCaseNumber());
        resp.setTitle(c.getTitle());
        resp.setDescription(c.getDescription());
        resp.setStatus(c.getStatus());
        resp.setPriority(c.getPriority());
        resp.setCustomerId(c.getCustomerId());
        resp.setAssignedTo(c.getAssignedTo());
        resp.setTotalExposure(c.getTotalExposure());
        resp.setConfirmedLoss(c.getConfirmedLoss());
        resp.setAlertIds(c.getAlertIds());
        resp.setTags(c.getTags());
        resp.setClosedBy(c.getClosedBy());
        resp.setOutcome(c.getOutcome());
        resp.setClosedAt(c.getClosedAt());
        resp.setCreatedAt(c.getCreatedAt());
        resp.setUpdatedAt(c.getUpdatedAt());

        List<CaseNote> notes = noteRepository.findByCaseIdAndTenantIdOrderByCreatedAtDesc(c.getId(), tenantId);
        resp.setNotes(notes.stream().map(this::mapNoteResponse).toList());
        return resp;
    }

    private CaseNoteResponse mapNoteResponse(CaseNote note) {
        CaseNoteResponse resp = new CaseNoteResponse();
        resp.setId(note.getId());
        resp.setAuthor(note.getAuthor());
        resp.setContent(note.getContent());
        resp.setNoteType(note.getNoteType());
        resp.setCreatedAt(note.getCreatedAt());
        return resp;
    }

    private AuditLogResponse mapAuditResponse(AuditLog log) {
        AuditLogResponse resp = new AuditLogResponse();
        resp.setId(log.getId());
        resp.setAction(log.getAction());
        resp.setEntityType(log.getEntityType());
        resp.setEntityId(log.getEntityId());
        resp.setPerformedBy(log.getPerformedBy());
        resp.setDescription(log.getDescription());
        resp.setChanges(log.getChanges());
        resp.setCreatedAt(log.getCreatedAt());
        return resp;
    }
}
