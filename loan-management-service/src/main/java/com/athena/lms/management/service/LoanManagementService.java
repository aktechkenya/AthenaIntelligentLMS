package com.athena.lms.management.service;

import com.athena.lms.common.dto.PageResponse;
import com.athena.lms.common.exception.BusinessException;
import com.athena.lms.common.exception.ResourceNotFoundException;
import com.athena.lms.management.dto.request.RepaymentRequest;
import com.athena.lms.management.dto.request.RestructureRequest;
import com.athena.lms.management.dto.response.*;
import com.athena.lms.management.entity.*;
import com.athena.lms.management.enums.*;
import com.athena.lms.management.event.LoanManagementEventPublisher;
import com.athena.lms.management.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LoanManagementService {

    private final LoanRepository loanRepo;
    private final LoanScheduleRepository scheduleRepo;
    private final LoanRepaymentRepository repaymentRepo;
    private final LoanDpdHistoryRepository dpdHistoryRepo;
    private final ScheduleGenerator scheduleGenerator;
    private final LoanManagementEventPublisher eventPublisher;

    // ─── Activate loan from disbursed event ─────────────────────────────────────

    @Transactional
    public void activateLoan(UUID applicationId, String customerId, UUID productId,
                              String tenantId, BigDecimal amount, BigDecimal interestRate,
                              Integer tenorMonths) {
        log.info("Activating loan for application [{}]", applicationId);

        LocalDate firstRepayment = LocalDate.now().plusMonths(1);
        LocalDate maturityDate = firstRepayment.plusMonths(tenorMonths - 1);

        Loan loan = Loan.builder()
            .tenantId(tenantId)
            .applicationId(applicationId)
            .customerId(customerId)
            .productId(productId)
            .disbursedAmount(amount)
            .outstandingPrincipal(amount)
            .outstandingInterest(BigDecimal.ZERO)
            .outstandingFees(BigDecimal.ZERO)
            .outstandingPenalty(BigDecimal.ZERO)
            .currency("KES")
            .interestRate(interestRate)
            .tenorMonths(tenorMonths)
            .repaymentFrequency(RepaymentFrequency.MONTHLY)
            .scheduleType(ScheduleType.EMI)
            .disbursedAt(OffsetDateTime.now())
            .firstRepaymentDate(firstRepayment)
            .maturityDate(maturityDate)
            .status(LoanStatus.ACTIVE)
            .stage(LoanStage.PERFORMING)
            .dpd(0)
            .build();

        loan = loanRepo.save(loan);

        // Generate schedule
        List<LoanSchedule> schedules = scheduleGenerator.generate(loan);
        loan.getSchedules().addAll(schedules);
        loanRepo.save(loan);

        log.info("Loan [{}] activated with {} installments", loan.getId(), schedules.size());
    }

    // ─── Read operations ─────────────────────────────────────────────────────────

    public LoanResponse getById(UUID id, String tenantId) {
        Loan loan = findLoan(id, tenantId);
        return toResponse(loan);
    }

    public List<InstallmentResponse> getSchedule(UUID loanId, String tenantId) {
        findLoan(loanId, tenantId); // verify access
        return scheduleRepo.findByLoanIdOrderByInstallmentNo(loanId)
            .stream().map(this::toInstallmentResponse).collect(Collectors.toList());
    }

    public InstallmentResponse getInstallment(UUID loanId, Integer installmentNo, String tenantId) {
        findLoan(loanId, tenantId);
        LoanSchedule schedule = scheduleRepo.findByLoanIdAndInstallmentNo(loanId, installmentNo)
            .orElseThrow(() -> new ResourceNotFoundException("LoanSchedule", installmentNo.toString()));
        return toInstallmentResponse(schedule);
    }

    public List<RepaymentResponse> getRepayments(UUID loanId, String tenantId) {
        findLoan(loanId, tenantId);
        return repaymentRepo.findByLoanIdOrderByPaymentDateDesc(loanId)
            .stream().map(this::toRepaymentResponse).collect(Collectors.toList());
    }

    public DpdResponse getDpd(UUID id, String tenantId) {
        Loan loan = findLoan(id, tenantId);
        return DpdResponse.builder()
            .loanId(loan.getId())
            .dpd(loan.getDpd())
            .stage(loan.getStage())
            .description(stageDescription(loan.getStage()))
            .build();
    }

    public PageResponse<LoanResponse> list(String tenantId, LoanStatus status, Pageable pageable) {
        Page<Loan> page = status != null
            ? loanRepo.findByTenantIdAndStatus(tenantId, status, pageable)
            : loanRepo.findByTenantId(tenantId, pageable);
        return PageResponse.from(page.map(this::toResponse));
    }

    public List<LoanResponse> listByCustomer(String customerId, String tenantId) {
        return loanRepo.findByTenantIdAndCustomerId(tenantId, customerId)
            .stream().map(this::toResponse).collect(Collectors.toList());
    }

    // ─── Repayment (waterfall: penalty → fee → interest → principal) ─────────────

    @Transactional
    public RepaymentResponse applyRepayment(UUID loanId, RepaymentRequest req, String tenantId, String userId) {
        Loan loan = findLoan(loanId, tenantId);
        if (loan.getStatus() != LoanStatus.ACTIVE && loan.getStatus() != LoanStatus.RESTRUCTURED) {
            throw new BusinessException("Loan is not in an active state");
        }

        BigDecimal remaining = req.getAmount();

        // Waterfall: penalty first
        BigDecimal penaltyApplied = apply(remaining, loan.getOutstandingPenalty());
        remaining = remaining.subtract(penaltyApplied);
        loan.setOutstandingPenalty(loan.getOutstandingPenalty().subtract(penaltyApplied));

        // Then fees
        BigDecimal feeApplied = apply(remaining, loan.getOutstandingFees());
        remaining = remaining.subtract(feeApplied);
        loan.setOutstandingFees(loan.getOutstandingFees().subtract(feeApplied));

        // Then interest
        BigDecimal interestApplied = apply(remaining, loan.getOutstandingInterest());
        remaining = remaining.subtract(interestApplied);
        loan.setOutstandingInterest(loan.getOutstandingInterest().subtract(interestApplied));

        // Then principal
        BigDecimal principalApplied = apply(remaining, loan.getOutstandingPrincipal());
        loan.setOutstandingPrincipal(loan.getOutstandingPrincipal().subtract(principalApplied));

        // Update schedule installments (oldest first)
        updateScheduleWithRepayment(loan, req.getAmount(), penaltyApplied, feeApplied, interestApplied, principalApplied);

        loan.setLastRepaymentDate(req.getPaymentDate());
        loan.setLastRepaymentAmount(req.getAmount());

        // Check if fully repaid
        BigDecimal totalOutstanding = loan.getOutstandingPrincipal()
            .add(loan.getOutstandingInterest())
            .add(loan.getOutstandingFees())
            .add(loan.getOutstandingPenalty());

        if (totalOutstanding.compareTo(BigDecimal.ZERO) == 0) {
            loan.setStatus(LoanStatus.CLOSED);
            loan.setClosedAt(OffsetDateTime.now());
            eventPublisher.publishLoanClosed(loan);
        }

        LoanRepayment repayment = LoanRepayment.builder()
            .loan(loan)
            .tenantId(tenantId)
            .amount(req.getAmount())
            .currency(req.getCurrency() != null ? req.getCurrency() : "KES")
            .penaltyApplied(penaltyApplied)
            .feeApplied(feeApplied)
            .interestApplied(interestApplied)
            .principalApplied(principalApplied)
            .paymentReference(req.getPaymentReference())
            .paymentMethod(req.getPaymentMethod())
            .paymentDate(req.getPaymentDate())
            .createdBy(userId)
            .build();

        loanRepo.save(loan);
        repayment = repaymentRepo.save(repayment);

        // Publish repayment event for accounting and payment tracking
        eventPublisher.publishRepaymentCompleted(loan, repayment);

        return toRepaymentResponse(repayment);
    }

    // ─── Restructure ─────────────────────────────────────────────────────────────

    @Transactional
    public LoanResponse restructure(UUID loanId, RestructureRequest req, String tenantId) {
        Loan loan = findLoan(loanId, tenantId);
        if (loan.getStatus() != LoanStatus.ACTIVE) {
            throw new BusinessException("Only ACTIVE loans can be restructured");
        }

        loan.setTenorMonths(req.getNewTenorMonths());
        loan.setInterestRate(req.getNewInterestRate());
        if (req.getNewFrequency() != null) loan.setRepaymentFrequency(req.getNewFrequency());
        loan.setStatus(LoanStatus.RESTRUCTURED);

        // Regenerate schedule from current outstanding principal
        loan.getSchedules().clear();
        loan.setDisbursedAmount(loan.getOutstandingPrincipal()); // base new schedule on outstanding
        loan.setFirstRepaymentDate(LocalDate.now().plusMonths(1));
        loan.setMaturityDate(loan.getFirstRepaymentDate().plusMonths(req.getNewTenorMonths() - 1));

        List<LoanSchedule> newSchedules = scheduleGenerator.generate(loan);
        loan.getSchedules().addAll(newSchedules);
        loanRepo.save(loan);

        return toResponse(loan);
    }

    // ─── DPD Refresh (called by scheduler) ───────────────────────────────────────

    @Transactional
    public void refreshAllDpd() {
        List<Loan> activeLoans = loanRepo.findAllActiveLoans();
        log.info("Refreshing DPD for {} active loans", activeLoans.size());
        LocalDate today = LocalDate.now();

        for (Loan loan : activeLoans) {
            try {
                refreshDpdForLoan(loan, today);
            } catch (Exception e) {
                log.error("DPD refresh failed for loan [{}]: {}", loan.getId(), e.getMessage());
            }
        }
    }

    private void refreshDpdForLoan(Loan loan, LocalDate today) {
        // Find the oldest unpaid installment
        List<LoanSchedule> pending = scheduleRepo.findByLoanIdAndStatus(loan.getId(), "PENDING");
        if (pending.isEmpty()) return;

        LoanSchedule oldest = pending.stream()
            .filter(s -> s.getDueDate().isBefore(today))
            .min((a, b) -> a.getDueDate().compareTo(b.getDueDate()))
            .orElse(null);

        int newDpd = 0;
        if (oldest != null) {
            newDpd = (int) ChronoUnit.DAYS.between(oldest.getDueDate(), today);
        }

        LoanStage previousStage = loan.getStage();
        LoanStage newStage = classifyStage(newDpd);

        loan.setDpd(newDpd);
        loan.setStage(newStage);

        // Record DPD snapshot
        LoanDpdHistory history = LoanDpdHistory.builder()
            .loan(loan)
            .tenantId(loan.getTenantId())
            .dpd(newDpd)
            .stage(newStage.name())
            .snapshotDate(today)
            .build();
        dpdHistoryRepo.save(history);

        loanRepo.save(loan);

        // Publish events
        eventPublisher.publishDpdUpdated(loan);
        if (!newStage.equals(previousStage)) {
            eventPublisher.publishStageChanged(loan, previousStage.name());
        }
    }

    // ─── Private helpers ──────────────────────────────────────────────────────────

    private Loan findLoan(UUID id, String tenantId) {
        return loanRepo.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Loan", id.toString()));
    }

    private BigDecimal apply(BigDecimal available, BigDecimal outstanding) {
        if (available.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;
        return available.min(outstanding);
    }

    private void updateScheduleWithRepayment(Loan loan, BigDecimal total,
                                              BigDecimal penalty, BigDecimal fee,
                                              BigDecimal interest, BigDecimal principal) {
        BigDecimal remaining = total;
        List<LoanSchedule> pending = scheduleRepo.findByLoanIdAndStatus(loan.getId(), "PENDING");
        pending.sort((a, b) -> a.getInstallmentNo().compareTo(b.getInstallmentNo()));

        for (LoanSchedule inst : pending) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal instBalance = inst.getTotalDue().subtract(inst.getTotalPaid());
            BigDecimal payment = remaining.min(instBalance);
            inst.setTotalPaid(inst.getTotalPaid().add(payment));
            remaining = remaining.subtract(payment);
            if (inst.getTotalPaid().compareTo(inst.getTotalDue()) >= 0) {
                inst.setStatus("PAID");
                inst.setPaidDate(LocalDate.now());
            } else {
                inst.setStatus("PARTIAL");
            }
        }
    }

    private LoanStage classifyStage(int dpd) {
        if (dpd == 0)        return LoanStage.PERFORMING;
        if (dpd <= 30)       return LoanStage.WATCH;
        if (dpd <= 90)       return LoanStage.SUBSTANDARD;
        if (dpd <= 180)      return LoanStage.DOUBTFUL;
        return LoanStage.LOSS;
    }

    private String stageDescription(LoanStage stage) {
        return switch (stage) {
            case PERFORMING  -> "Current — DPD 0";
            case WATCH       -> "Watch — DPD 1-30";
            case SUBSTANDARD -> "Substandard — DPD 31-90";
            case DOUBTFUL    -> "Doubtful — DPD 91-180";
            case LOSS        -> "Loss — DPD > 180";
        };
    }

    private LoanResponse toResponse(Loan loan) {
        BigDecimal totalOutstanding = loan.getOutstandingPrincipal()
            .add(loan.getOutstandingInterest())
            .add(loan.getOutstandingFees())
            .add(loan.getOutstandingPenalty());

        return LoanResponse.builder()
            .id(loan.getId())
            .tenantId(loan.getTenantId())
            .applicationId(loan.getApplicationId())
            .customerId(loan.getCustomerId())
            .productId(loan.getProductId())
            .disbursedAmount(loan.getDisbursedAmount())
            .outstandingPrincipal(loan.getOutstandingPrincipal())
            .outstandingInterest(loan.getOutstandingInterest())
            .outstandingFees(loan.getOutstandingFees())
            .outstandingPenalty(loan.getOutstandingPenalty())
            .totalOutstanding(totalOutstanding)
            .currency(loan.getCurrency())
            .interestRate(loan.getInterestRate())
            .tenorMonths(loan.getTenorMonths())
            .repaymentFrequency(loan.getRepaymentFrequency())
            .scheduleType(loan.getScheduleType())
            .disbursedAt(loan.getDisbursedAt())
            .firstRepaymentDate(loan.getFirstRepaymentDate())
            .maturityDate(loan.getMaturityDate())
            .status(loan.getStatus())
            .stage(loan.getStage())
            .dpd(loan.getDpd())
            .lastRepaymentDate(loan.getLastRepaymentDate())
            .lastRepaymentAmount(loan.getLastRepaymentAmount())
            .createdAt(loan.getCreatedAt())
            .build();
    }

    private InstallmentResponse toInstallmentResponse(LoanSchedule s) {
        return InstallmentResponse.builder()
            .id(s.getId())
            .installmentNo(s.getInstallmentNo())
            .dueDate(s.getDueDate())
            .principalDue(s.getPrincipalDue())
            .interestDue(s.getInterestDue())
            .feeDue(s.getFeeDue())
            .penaltyDue(s.getPenaltyDue())
            .totalDue(s.getTotalDue())
            .principalPaid(s.getPrincipalPaid())
            .interestPaid(s.getInterestPaid())
            .feePaid(s.getFeePaid())
            .penaltyPaid(s.getPenaltyPaid())
            .totalPaid(s.getTotalPaid())
            .balance(s.getTotalDue().subtract(s.getTotalPaid()))
            .status(s.getStatus())
            .paidDate(s.getPaidDate())
            .build();
    }

    private RepaymentResponse toRepaymentResponse(LoanRepayment r) {
        return RepaymentResponse.builder()
            .id(r.getId())
            .amount(r.getAmount())
            .currency(r.getCurrency())
            .penaltyApplied(r.getPenaltyApplied())
            .feeApplied(r.getFeeApplied())
            .interestApplied(r.getInterestApplied())
            .principalApplied(r.getPrincipalApplied())
            .paymentReference(r.getPaymentReference())
            .paymentMethod(r.getPaymentMethod())
            .paymentDate(r.getPaymentDate())
            .createdAt(r.getCreatedAt())
            .build();
    }
}
