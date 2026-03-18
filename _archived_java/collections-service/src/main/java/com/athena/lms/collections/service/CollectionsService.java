package com.athena.lms.collections.service;

import com.athena.lms.collections.dto.request.AddActionRequest;
import com.athena.lms.collections.dto.request.AddPtpRequest;
import com.athena.lms.collections.dto.request.UpdateCaseRequest;
import com.athena.lms.collections.dto.response.*;
import com.athena.lms.collections.entity.CollectionAction;
import com.athena.lms.collections.entity.CollectionCase;
import com.athena.lms.collections.entity.PromiseToPay;
import com.athena.lms.collections.enums.*;
import com.athena.lms.collections.event.CollectionsEventPublisher;
import com.athena.lms.collections.repository.CollectionActionRepository;
import com.athena.lms.collections.repository.CollectionCaseRepository;
import com.athena.lms.collections.repository.PtpRepository;
import com.athena.lms.common.exception.ResourceNotFoundException;
import com.athena.lms.common.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class CollectionsService {

    private final CollectionCaseRepository caseRepository;
    private final CollectionActionRepository actionRepository;
    private final PtpRepository ptpRepository;
    private final CollectionsEventPublisher eventPublisher;

    // -----------------------------------------------------------------------
    // Case lifecycle
    // -----------------------------------------------------------------------

    public void openOrUpdateCase(UUID loanId, String customerId, int dpd,
                                  String stage, BigDecimal outstandingAmount, String tenantId) {
        Optional<CollectionCase> existing = caseRepository.findByLoanId(loanId);
        if (existing.isEmpty()) {
            if (dpd < 1) return;
            CollectionCase newCase = CollectionCase.builder()
                    .tenantId(tenantId)
                    .loanId(loanId)
                    .customerId(customerId)
                    .caseNumber(generateCaseNumber(tenantId))
                    .status(CaseStatus.OPEN)
                    .priority(dpd > 90 ? CasePriority.CRITICAL : CasePriority.NORMAL)
                    .currentDpd(dpd)
                    .currentStage(mapStage(stage))
                    .outstandingAmount(outstandingAmount != null ? outstandingAmount : BigDecimal.ZERO)
                    .build();
            CollectionCase saved = caseRepository.save(newCase);
            eventPublisher.publishCaseCreated(saved.getId(), loanId, tenantId);
            log.info("Opened new collection case {} for loan {}", saved.getCaseNumber(), loanId);
        } else {
            CollectionCase c = existing.get();
            c.setCurrentDpd(dpd);
            if (outstandingAmount != null) c.setOutstandingAmount(outstandingAmount);
            c.setCurrentStage(mapStage(stage));
            if (dpd > 90) c.setPriority(CasePriority.CRITICAL);
            caseRepository.save(c);
        }
    }

    public void updateDpd(UUID loanId, int dpd, BigDecimal outstandingAmount, String tenantId) {
        Optional<CollectionCase> existing = caseRepository.findByLoanId(loanId);
        if (existing.isEmpty()) {
            if (dpd >= 1) {
                openOrUpdateCase(loanId, null, dpd, "WATCH", outstandingAmount, tenantId);
            }
            return;
        }
        CollectionCase c = existing.get();
        if (c.getStatus() == CaseStatus.CLOSED || c.getStatus() == CaseStatus.WRITTEN_OFF) return;
        c.setCurrentDpd(dpd);
        if (outstandingAmount != null) c.setOutstandingAmount(outstandingAmount);
        if (dpd > 90) c.setPriority(CasePriority.CRITICAL);
        else if (dpd > 60) c.setPriority(CasePriority.HIGH);
        caseRepository.save(c);
    }

    public void handleStageChange(UUID loanId, String stage, String tenantId) {
        Optional<CollectionCase> existing = caseRepository.findByLoanId(loanId);
        if (existing.isEmpty()) return;
        CollectionCase c = existing.get();
        if (c.getStatus() == CaseStatus.CLOSED || c.getStatus() == CaseStatus.WRITTEN_OFF) return;

        CollectionStage newStage = mapStage(stage);
        CollectionStage oldStage = c.getCurrentStage();
        c.setCurrentStage(newStage);

        if (isWorseStage(oldStage, newStage)) {
            caseRepository.save(c);
            eventPublisher.publishCaseEscalated(c.getId(), loanId, newStage, tenantId);
        } else if ("PERFORMING".equalsIgnoreCase(stage) || "CLOSED".equalsIgnoreCase(stage)) {
            c.setStatus(CaseStatus.CLOSED);
            c.setClosedAt(OffsetDateTime.now());
            caseRepository.save(c);
            eventPublisher.publishCaseClosed(c.getId(), loanId, tenantId);
        } else {
            caseRepository.save(c);
        }
    }

    @Transactional(readOnly = true)
    public CollectionCaseResponse getCase(UUID id, String tenantId) {
        CollectionCase c = caseRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Collection case not found: " + id));
        return toResponse(c);
    }

    @Transactional(readOnly = true)
    public CollectionCaseResponse getCaseByLoan(UUID loanId, String tenantId) {
        CollectionCase c = caseRepository.findByLoanId(loanId)
                .filter(cc -> cc.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("No collection case for loan: " + loanId));
        return toResponse(c);
    }

    @Transactional(readOnly = true)
    public PageResponse<CollectionCaseResponse> listCases(String tenantId, CaseStatus status, Pageable pageable) {
        Page<CollectionCase> page = status != null
                ? caseRepository.findByTenantIdAndStatus(tenantId, status, pageable)
                : caseRepository.findByTenantId(tenantId, pageable);
        return PageResponse.from(page.map(this::toResponse));
    }

    public CollectionCaseResponse updateCase(UUID id, UpdateCaseRequest req, String tenantId) {
        CollectionCase c = caseRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Collection case not found: " + id));
        if (req.getAssignedTo() != null) c.setAssignedTo(req.getAssignedTo());
        if (req.getPriority() != null) c.setPriority(req.getPriority());
        if (req.getNotes() != null) c.setNotes(req.getNotes());
        return toResponse(caseRepository.save(c));
    }

    public CollectionCaseResponse closeCase(UUID caseId, String tenantId) {
        CollectionCase c = caseRepository.findByTenantIdAndId(tenantId, caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Collection case not found: " + caseId));
        c.setStatus(CaseStatus.CLOSED);
        c.setClosedAt(OffsetDateTime.now());
        CollectionCase saved = caseRepository.save(c);
        eventPublisher.publishCaseClosed(saved.getId(), saved.getLoanId(), tenantId);
        return toResponse(saved);
    }

    // -----------------------------------------------------------------------
    // Actions
    // -----------------------------------------------------------------------

    public CollectionActionResponse addAction(UUID caseId, AddActionRequest req, String tenantId) {
        CollectionCase c = caseRepository.findByTenantIdAndId(tenantId, caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Collection case not found: " + caseId));

        CollectionAction action = CollectionAction.builder()
                .tenantId(tenantId)
                .caseId(caseId)
                .actionType(req.getActionType())
                .outcome(req.getOutcome())
                .notes(req.getNotes())
                .contactPerson(req.getContactPerson())
                .contactMethod(req.getContactMethod())
                .performedBy(req.getPerformedBy())
                .nextActionDate(req.getNextActionDate())
                .build();

        CollectionAction saved = actionRepository.save(action);

        c.setLastActionAt(OffsetDateTime.now());
        if (c.getStatus() == CaseStatus.OPEN) c.setStatus(CaseStatus.IN_PROGRESS);
        caseRepository.save(c);

        eventPublisher.publishActionTaken(caseId, req.getActionType(), tenantId);
        return toActionResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<CollectionActionResponse> listActions(UUID caseId, String tenantId) {
        caseRepository.findByTenantIdAndId(tenantId, caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Collection case not found: " + caseId));
        return actionRepository.findByCaseIdOrderByPerformedAtDesc(caseId)
                .stream().map(this::toActionResponse).collect(Collectors.toList());
    }

    // -----------------------------------------------------------------------
    // PTPs
    // -----------------------------------------------------------------------

    public PtpResponse addPtp(UUID caseId, AddPtpRequest req, String tenantId) {
        caseRepository.findByTenantIdAndId(tenantId, caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Collection case not found: " + caseId));

        PromiseToPay ptp = PromiseToPay.builder()
                .tenantId(tenantId)
                .caseId(caseId)
                .promisedAmount(req.getPromisedAmount())
                .promiseDate(req.getPromiseDate())
                .status(PtpStatus.PENDING)
                .notes(req.getNotes())
                .createdBy(req.getCreatedBy())
                .build();

        return toPtpResponse(ptpRepository.save(ptp));
    }

    @Transactional(readOnly = true)
    public List<PtpResponse> listPtps(UUID caseId, String tenantId) {
        caseRepository.findByTenantIdAndId(tenantId, caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Collection case not found: " + caseId));
        return ptpRepository.findByCaseIdOrderByCreatedAtDesc(caseId)
                .stream().map(this::toPtpResponse).collect(Collectors.toList());
    }

    // -----------------------------------------------------------------------
    // Summary
    // -----------------------------------------------------------------------

    @Transactional(readOnly = true)
    public CollectionSummaryResponse getSummary(String tenantId) {
        CollectionSummaryResponse summary = new CollectionSummaryResponse();
        summary.setTenantId(tenantId);
        summary.setTotalOpenCases(caseRepository.countByTenantIdAndStatus(tenantId, CaseStatus.OPEN)
                + caseRepository.countByTenantIdAndStatus(tenantId, CaseStatus.IN_PROGRESS)
                + caseRepository.countByTenantIdAndStatus(tenantId, CaseStatus.PENDING_LEGAL));
        summary.setWatchCases(caseRepository.countByTenantIdAndCurrentStage(tenantId, CollectionStage.WATCH));
        summary.setSubstandardCases(caseRepository.countByTenantIdAndCurrentStage(tenantId, CollectionStage.SUBSTANDARD));
        summary.setDoubtfulCases(caseRepository.countByTenantIdAndCurrentStage(tenantId, CollectionStage.DOUBTFUL));
        summary.setLossCases(caseRepository.countByTenantIdAndCurrentStage(tenantId, CollectionStage.LOSS));
        summary.setCriticalPriorityCases(caseRepository.countByTenantIdAndPriority(tenantId, CasePriority.CRITICAL));
        return summary;
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private String generateCaseNumber(String tenantId) {
        String prefix = tenantId != null && tenantId.length() >= 3
                ? tenantId.toUpperCase().substring(0, 3)
                : tenantId != null ? tenantId.toUpperCase() : "GEN";
        return "COL-" + prefix + "-" + System.currentTimeMillis();
    }

    private CollectionStage mapStage(String stage) {
        if (stage == null) return CollectionStage.WATCH;
        return switch (stage.toUpperCase()) {
            case "SUBSTANDARD" -> CollectionStage.SUBSTANDARD;
            case "DOUBTFUL" -> CollectionStage.DOUBTFUL;
            case "LOSS" -> CollectionStage.LOSS;
            default -> CollectionStage.WATCH;
        };
    }

    private boolean isWorseStage(CollectionStage current, CollectionStage next) {
        return next.ordinal() > current.ordinal();
    }

    private CollectionCaseResponse toResponse(CollectionCase c) {
        CollectionCaseResponse r = new CollectionCaseResponse();
        r.setId(c.getId());
        r.setTenantId(c.getTenantId());
        r.setLoanId(c.getLoanId());
        r.setCustomerId(c.getCustomerId());
        r.setCaseNumber(c.getCaseNumber());
        r.setStatus(c.getStatus());
        r.setPriority(c.getPriority());
        r.setCurrentDpd(c.getCurrentDpd());
        r.setCurrentStage(c.getCurrentStage());
        r.setOutstandingAmount(c.getOutstandingAmount());
        r.setAssignedTo(c.getAssignedTo());
        r.setOpenedAt(c.getOpenedAt());
        r.setClosedAt(c.getClosedAt());
        r.setLastActionAt(c.getLastActionAt());
        r.setNotes(c.getNotes());
        r.setCreatedAt(c.getCreatedAt());
        r.setUpdatedAt(c.getUpdatedAt());
        return r;
    }

    private CollectionActionResponse toActionResponse(CollectionAction a) {
        CollectionActionResponse r = new CollectionActionResponse();
        r.setId(a.getId());
        r.setCaseId(a.getCaseId());
        r.setActionType(a.getActionType());
        r.setOutcome(a.getOutcome());
        r.setNotes(a.getNotes());
        r.setContactPerson(a.getContactPerson());
        r.setContactMethod(a.getContactMethod());
        r.setPerformedBy(a.getPerformedBy());
        r.setPerformedAt(a.getPerformedAt());
        r.setNextActionDate(a.getNextActionDate());
        r.setCreatedAt(a.getCreatedAt());
        return r;
    }

    private PtpResponse toPtpResponse(PromiseToPay p) {
        PtpResponse r = new PtpResponse();
        r.setId(p.getId());
        r.setCaseId(p.getCaseId());
        r.setPromisedAmount(p.getPromisedAmount());
        r.setPromiseDate(p.getPromiseDate());
        r.setStatus(p.getStatus());
        r.setNotes(p.getNotes());
        r.setCreatedBy(p.getCreatedBy());
        r.setFulfilledAt(p.getFulfilledAt());
        r.setBrokenAt(p.getBrokenAt());
        r.setCreatedAt(p.getCreatedAt());
        r.setUpdatedAt(p.getUpdatedAt());
        return r;
    }
}
