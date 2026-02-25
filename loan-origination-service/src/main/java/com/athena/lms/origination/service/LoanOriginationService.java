package com.athena.lms.origination.service;

import com.athena.lms.common.dto.PageResponse;
import com.athena.lms.common.exception.BusinessException;
import com.athena.lms.common.exception.ResourceNotFoundException;
import com.athena.lms.origination.dto.request.*;
import com.athena.lms.origination.dto.response.*;
import com.athena.lms.origination.entity.*;
import com.athena.lms.origination.enums.ApplicationStatus;
import com.athena.lms.origination.enums.RiskGrade;
import com.athena.lms.origination.event.LoanOriginationEventPublisher;
import com.athena.lms.origination.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LoanOriginationService {

    private final LoanApplicationRepository applicationRepo;
    private final LoanOriginationEventPublisher eventPublisher;

    @Transactional
    public ApplicationResponse create(CreateApplicationRequest req, String tenantId, String userId) {
        LoanApplication app = LoanApplication.builder()
            .tenantId(tenantId)
            .customerId(req.getCustomerId())
            .productId(req.getProductId())
            .requestedAmount(req.getRequestedAmount())
            .tenorMonths(req.getTenorMonths())
            .purpose(req.getPurpose())
            .currency(req.getCurrency() != null ? req.getCurrency() : "KES")
            .disbursementAccount(req.getDisbursementAccount())
            .status(ApplicationStatus.DRAFT)
            .createdBy(userId)
            .updatedBy(userId)
            .build();
        return toResponse(applicationRepo.save(app));
    }

    public ApplicationResponse getById(UUID id, String tenantId) {
        LoanApplication app = applicationRepo.findByIdWithDetails(id, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("LoanApplication", id.toString()));
        return toResponse(app);
    }

    @Transactional
    public ApplicationResponse update(UUID id, CreateApplicationRequest req, String tenantId, String userId) {
        LoanApplication app = findDraft(id, tenantId);
        app.setRequestedAmount(req.getRequestedAmount());
        app.setTenorMonths(req.getTenorMonths());
        app.setPurpose(req.getPurpose());
        app.setUpdatedBy(userId);
        return toResponse(applicationRepo.save(app));
    }

    @Transactional
    public ApplicationResponse submit(UUID id, String tenantId, String userId) {
        LoanApplication app = findWithStatus(id, tenantId, ApplicationStatus.DRAFT);
        transition(app, ApplicationStatus.SUBMITTED, null, userId);
        applicationRepo.save(app);
        eventPublisher.publishSubmitted(app);
        return toResponse(app);
    }

    @Transactional
    public ApplicationResponse startReview(UUID id, String tenantId, String userId) {
        LoanApplication app = findWithStatus(id, tenantId, ApplicationStatus.SUBMITTED);
        app.setReviewerId(userId);
        transition(app, ApplicationStatus.UNDER_REVIEW, null, userId);
        return toResponse(applicationRepo.save(app));
    }

    @Transactional
    public ApplicationResponse approve(UUID id, ApproveApplicationRequest req, String tenantId, String userId) {
        LoanApplication app = findWithStatus(id, tenantId, ApplicationStatus.UNDER_REVIEW);
        app.setApprovedAmount(req.getApprovedAmount());
        app.setInterestRate(req.getInterestRate());
        app.setReviewNotes(req.getReviewNotes());
        app.setReviewedAt(OffsetDateTime.now());
        if (req.getCreditScore() != null) app.setCreditScore(req.getCreditScore());
        if (req.getRiskGrade() != null) app.setRiskGrade(RiskGrade.valueOf(req.getRiskGrade()));
        transition(app, ApplicationStatus.APPROVED, null, userId);
        applicationRepo.save(app);
        eventPublisher.publishApproved(app);
        return toResponse(app);
    }

    @Transactional
    public ApplicationResponse reject(UUID id, RejectApplicationRequest req, String tenantId, String userId) {
        LoanApplication app = findWithStatus(id, tenantId, ApplicationStatus.UNDER_REVIEW);
        app.setReviewNotes(req.getReason());
        app.setReviewedAt(OffsetDateTime.now());
        transition(app, ApplicationStatus.REJECTED, req.getReason(), userId);
        applicationRepo.save(app);
        eventPublisher.publishRejected(app, req.getReason());
        return toResponse(app);
    }

    @Transactional
    public ApplicationResponse disburse(UUID id, DisburseRequest req, String tenantId, String userId) {
        LoanApplication app = findWithStatus(id, tenantId, ApplicationStatus.APPROVED);
        app.setDisbursedAmount(req.getDisbursedAmount());
        app.setDisbursementAccount(req.getDisbursementAccount());
        app.setDisbursedAt(OffsetDateTime.now());
        transition(app, ApplicationStatus.DISBURSED, null, userId);
        applicationRepo.save(app);
        eventPublisher.publishDisbursed(app);
        return toResponse(app);
    }

    @Transactional
    public ApplicationResponse cancel(UUID id, String reason, String tenantId, String userId) {
        LoanApplication app = applicationRepo.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("LoanApplication", id.toString()));
        if (app.getStatus() == ApplicationStatus.DISBURSED) {
            throw new BusinessException("Cannot cancel a disbursed application");
        }
        transition(app, ApplicationStatus.CANCELLED, reason, userId);
        return toResponse(applicationRepo.save(app));
    }

    @Transactional
    public CollateralResponse addCollateral(UUID id, AddCollateralRequest req, String tenantId) {
        LoanApplication app = applicationRepo.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("LoanApplication", id.toString()));
        ApplicationCollateral collateral = ApplicationCollateral.builder()
            .application(app)
            .tenantId(tenantId)
            .collateralType(req.getCollateralType())
            .description(req.getDescription())
            .estimatedValue(req.getEstimatedValue())
            .currency(req.getCurrency() != null ? req.getCurrency() : "KES")
            .documentRef(req.getDocumentRef())
            .build();
        app.getCollaterals().add(collateral);
        applicationRepo.save(app);
        return toCollateralResponse(collateral);
    }

    @Transactional
    public NoteResponse addNote(UUID id, AddNoteRequest req, String tenantId, String userId) {
        LoanApplication app = applicationRepo.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("LoanApplication", id.toString()));
        ApplicationNote note = ApplicationNote.builder()
            .application(app)
            .tenantId(tenantId)
            .content(req.getContent())
            .noteType(req.getNoteType() != null ? req.getNoteType() : "UNDERWRITER")
            .authorId(userId)
            .build();
        app.getNotes().add(note);
        applicationRepo.save(app);
        return toNoteResponse(note);
    }

    public PageResponse<ApplicationResponse> list(String tenantId, ApplicationStatus status, Pageable pageable) {
        Page<LoanApplication> page = status != null
            ? applicationRepo.findByTenantIdAndStatus(tenantId, status, pageable)
            : applicationRepo.findByTenantId(tenantId, pageable);
        return PageResponse.from(page.map(this::toResponse));
    }

    public List<ApplicationResponse> listByCustomer(String customerId, String tenantId) {
        return applicationRepo.findByTenantIdAndCustomerId(tenantId, customerId)
            .stream().map(this::toResponse).collect(Collectors.toList());
    }

    // --- private helpers ---

    private LoanApplication findDraft(UUID id, String tenantId) {
        return findWithStatus(id, tenantId, ApplicationStatus.DRAFT);
    }

    private LoanApplication findWithStatus(UUID id, String tenantId, ApplicationStatus expected) {
        LoanApplication app = applicationRepo.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("LoanApplication", id.toString()));
        if (app.getStatus() != expected) {
            throw new BusinessException("Application must be in " + expected + " status, current: " + app.getStatus());
        }
        return app;
    }

    private void transition(LoanApplication app, ApplicationStatus to, String reason, String changedBy) {
        ApplicationStatusHistory history = ApplicationStatusHistory.builder()
            .application(app)
            .tenantId(app.getTenantId())
            .fromStatus(app.getStatus() != null ? app.getStatus().name() : null)
            .toStatus(to.name())
            .reason(reason)
            .changedBy(changedBy)
            .build();
        app.getStatusHistory().add(history);
        app.setStatus(to);
        app.setUpdatedBy(changedBy);
    }

    private ApplicationResponse toResponse(LoanApplication app) {
        return ApplicationResponse.builder()
            .id(app.getId())
            .tenantId(app.getTenantId())
            .customerId(app.getCustomerId())
            .productId(app.getProductId())
            .requestedAmount(app.getRequestedAmount())
            .approvedAmount(app.getApprovedAmount())
            .currency(app.getCurrency())
            .tenorMonths(app.getTenorMonths())
            .purpose(app.getPurpose())
            .status(app.getStatus())
            .riskGrade(app.getRiskGrade())
            .creditScore(app.getCreditScore())
            .interestRate(app.getInterestRate())
            .disbursedAmount(app.getDisbursedAmount())
            .disbursedAt(app.getDisbursedAt())
            .disbursementAccount(app.getDisbursementAccount())
            .reviewNotes(app.getReviewNotes())
            .createdAt(app.getCreatedAt())
            .updatedAt(app.getUpdatedAt())
            .collaterals(app.getCollaterals() != null
                ? app.getCollaterals().stream().map(this::toCollateralResponse).collect(Collectors.toList())
                : List.of())
            .notes(app.getNotes() != null
                ? app.getNotes().stream().map(this::toNoteResponse).collect(Collectors.toList())
                : List.of())
            .statusHistory(app.getStatusHistory() != null
                ? app.getStatusHistory().stream().map(this::toHistoryResponse).collect(Collectors.toList())
                : List.of())
            .build();
    }

    private CollateralResponse toCollateralResponse(ApplicationCollateral c) {
        return CollateralResponse.builder()
            .id(c.getId())
            .collateralType(c.getCollateralType())
            .description(c.getDescription())
            .estimatedValue(c.getEstimatedValue())
            .currency(c.getCurrency())
            .documentRef(c.getDocumentRef())
            .createdAt(c.getCreatedAt())
            .build();
    }

    private NoteResponse toNoteResponse(ApplicationNote n) {
        return NoteResponse.builder()
            .id(n.getId())
            .noteType(n.getNoteType())
            .content(n.getContent())
            .authorId(n.getAuthorId())
            .createdAt(n.getCreatedAt())
            .build();
    }

    private StatusHistoryResponse toHistoryResponse(ApplicationStatusHistory h) {
        return StatusHistoryResponse.builder()
            .id(h.getId())
            .fromStatus(h.getFromStatus())
            .toStatus(h.getToStatus())
            .reason(h.getReason())
            .changedBy(h.getChangedBy())
            .changedAt(h.getChangedAt())
            .build();
    }
}
