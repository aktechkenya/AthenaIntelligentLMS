package com.athena.lms.floatmgmt.service;

import com.athena.lms.common.exception.BusinessException;
import com.athena.lms.common.exception.ResourceNotFoundException;
import com.athena.lms.common.dto.PageResponse;
import com.athena.lms.floatmgmt.dto.request.CreateFloatAccountRequest;
import com.athena.lms.floatmgmt.dto.request.FloatDrawRequest;
import com.athena.lms.floatmgmt.dto.request.FloatRepayRequest;
import com.athena.lms.floatmgmt.dto.response.FloatAccountResponse;
import com.athena.lms.floatmgmt.dto.response.FloatAllocationResponse;
import com.athena.lms.floatmgmt.dto.response.FloatSummaryResponse;
import com.athena.lms.floatmgmt.dto.response.FloatTransactionResponse;
import com.athena.lms.floatmgmt.entity.FloatAccount;
import com.athena.lms.floatmgmt.entity.FloatAllocation;
import com.athena.lms.floatmgmt.entity.FloatTransaction;
import com.athena.lms.floatmgmt.enums.FloatAccountStatus;
import com.athena.lms.floatmgmt.enums.FloatAllocationStatus;
import com.athena.lms.floatmgmt.enums.FloatTransactionType;
import com.athena.lms.floatmgmt.event.FloatEventPublisher;
import com.athena.lms.floatmgmt.repository.FloatAccountRepository;
import com.athena.lms.floatmgmt.repository.FloatAllocationRepository;
import com.athena.lms.floatmgmt.repository.FloatTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class FloatService {

    private final FloatAccountRepository floatAccountRepository;
    private final FloatTransactionRepository floatTransactionRepository;
    private final FloatAllocationRepository floatAllocationRepository;
    private final FloatEventPublisher floatEventPublisher;

    public FloatAccountResponse createAccount(CreateFloatAccountRequest req, String tenantId) {
        if (floatAccountRepository.existsByTenantIdAndAccountCode(tenantId, req.getAccountCode())) {
            throw new BusinessException("Float account code already exists: " + req.getAccountCode());
        }
        FloatAccount account = new FloatAccount();
        account.setTenantId(tenantId);
        account.setAccountName(req.getAccountName());
        account.setAccountCode(req.getAccountCode());
        account.setCurrency(req.getCurrency() != null ? req.getCurrency() : "KES");
        account.setFloatLimit(req.getFloatLimit());
        account.setDrawnAmount(BigDecimal.ZERO);
        account.setStatus(FloatAccountStatus.ACTIVE);
        account.setDescription(req.getDescription());
        FloatAccount saved = floatAccountRepository.save(account);
        log.info("Created float account {} for tenant {}", saved.getId(), tenantId);
        return toAccountResponse(saved);
    }

    @Transactional(readOnly = true)
    public FloatAccountResponse getAccount(UUID id, String tenantId) {
        FloatAccount account = floatAccountRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Float account not found: " + id));
        return toAccountResponse(account);
    }

    @Transactional(readOnly = true)
    public List<FloatAccountResponse> listAccounts(String tenantId) {
        return floatAccountRepository.findByTenantId(tenantId)
                .stream()
                .map(this::toAccountResponse)
                .collect(Collectors.toList());
    }

    public FloatTransactionResponse draw(UUID accountId, FloatDrawRequest req, String tenantId) {
        FloatAccount account = floatAccountRepository.findByTenantIdAndId(tenantId, accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Float account not found: " + accountId));

        BigDecimal available = account.getAvailable();
        if (available.compareTo(req.getAmount()) < 0) {
            throw new BusinessException(
                    "Insufficient float balance. Available: " + available + ", Requested: " + req.getAmount());
        }

        BigDecimal balanceBefore = account.getDrawnAmount();
        account.setDrawnAmount(account.getDrawnAmount().add(req.getAmount()));
        floatAccountRepository.save(account);

        FloatTransaction tx = buildTransaction(account, FloatTransactionType.DRAW,
                req.getAmount(), balanceBefore, account.getDrawnAmount(),
                req.getReferenceId(), req.getReferenceType(), req.getNarration(), null);
        FloatTransaction saved = floatTransactionRepository.save(tx);

        floatEventPublisher.publishFloatDrawn(accountId, req.getAmount(), null, tenantId);
        return toTransactionResponse(saved);
    }

    public FloatTransactionResponse repay(UUID accountId, FloatRepayRequest req, String tenantId) {
        FloatAccount account = floatAccountRepository.findByTenantIdAndId(tenantId, accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Float account not found: " + accountId));

        if (account.getDrawnAmount().compareTo(req.getAmount()) < 0) {
            throw new BusinessException(
                    "Repayment exceeds drawn amount. Drawn: " + account.getDrawnAmount()
                            + ", Repayment: " + req.getAmount());
        }

        BigDecimal balanceBefore = account.getDrawnAmount();
        account.setDrawnAmount(account.getDrawnAmount().subtract(req.getAmount()));
        floatAccountRepository.save(account);

        FloatTransaction tx = buildTransaction(account, FloatTransactionType.REPAYMENT,
                req.getAmount(), balanceBefore, account.getDrawnAmount(),
                req.getReferenceId(), null, req.getNarration(), null);
        FloatTransaction saved = floatTransactionRepository.save(tx);

        floatEventPublisher.publishFloatRepaid(accountId, req.getAmount(), null, tenantId);
        return toTransactionResponse(saved);
    }

    /**
     * Called by listener when loan.disbursed event received. Draws float and creates an allocation.
     * Does NOT throw on insufficient float — logs warning and exits cleanly.
     */
    public void processDraw(UUID loanId, BigDecimal amount, String tenantId) {
        // Idempotency: check if allocation for this loan already exists
        if (floatAllocationRepository.findByLoanId(loanId).isPresent()) {
            log.info("Float draw already processed for loan {}, skipping", loanId);
            return;
        }

        List<FloatAccount> accounts = floatAccountRepository.findByTenantId(tenantId);
        FloatAccount account = accounts.stream()
                .filter(a -> a.getStatus() == FloatAccountStatus.ACTIVE)
                .findFirst()
                .orElse(null);

        if (account == null) {
            log.warn("No active float account found for tenant {} — cannot process draw for loan {}", tenantId, loanId);
            return;
        }

        if (account.getAvailable().compareTo(amount) < 0) {
            log.warn("Insufficient float for tenant {} account {} — available: {}, requested: {}. Loan {} not drawn.",
                    tenantId, account.getId(), account.getAvailable(), amount, loanId);
            return;
        }

        String eventId = "loan-disbursed-" + loanId.toString();
        if (floatTransactionRepository.existsByEventId(eventId)) {
            log.info("Float draw transaction already recorded for event {}, skipping", eventId);
            return;
        }

        BigDecimal balanceBefore = account.getDrawnAmount();
        account.setDrawnAmount(account.getDrawnAmount().add(amount));
        floatAccountRepository.save(account);

        FloatTransaction tx = buildTransaction(account, FloatTransactionType.DRAW,
                amount, balanceBefore, account.getDrawnAmount(),
                loanId.toString(), "LOAN_DISBURSEMENT",
                "Float draw for loan " + loanId, eventId);
        floatTransactionRepository.save(tx);

        FloatAllocation allocation = new FloatAllocation();
        allocation.setTenantId(tenantId);
        allocation.setFloatAccountId(account.getId());
        allocation.setLoanId(loanId);
        allocation.setAllocatedAmount(amount);
        allocation.setRepaidAmount(BigDecimal.ZERO);
        allocation.setStatus(FloatAllocationStatus.ACTIVE);
        floatAllocationRepository.save(allocation);

        floatEventPublisher.publishFloatDrawn(account.getId(), amount, loanId, tenantId);
        log.info("Float drawn {} for loan {} from account {} tenant {}", amount, loanId, account.getId(), tenantId);
    }

    /**
     * Called by listener when account.credit.received event is received. Tops up the float.
     */
    public void processTopUp(String referenceAccountId, BigDecimal amount, String tenantId) {
        List<FloatAccount> accounts = floatAccountRepository.findByTenantId(tenantId);
        FloatAccount account = accounts.stream()
                .filter(a -> a.getStatus() == FloatAccountStatus.ACTIVE)
                .findFirst()
                .orElse(null);

        if (account == null) {
            log.warn("No active float account found for tenant {} — cannot process top-up", tenantId);
            return;
        }

        // Top-up by reducing drawnAmount (repayment of float)
        BigDecimal balanceBefore = account.getDrawnAmount();
        BigDecimal newDrawn = account.getDrawnAmount().subtract(amount);
        if (newDrawn.compareTo(BigDecimal.ZERO) < 0) {
            newDrawn = BigDecimal.ZERO;
        }
        account.setDrawnAmount(newDrawn);
        floatAccountRepository.save(account);

        FloatTransaction tx = buildTransaction(account, FloatTransactionType.TOP_UP,
                amount, balanceBefore, account.getDrawnAmount(),
                referenceAccountId, "ACCOUNT_CREDIT",
                "Float top-up from account credit", null);
        floatTransactionRepository.save(tx);

        floatEventPublisher.publishFloatRepaid(account.getId(), amount, null, tenantId);
        log.info("Float top-up {} processed for tenant {} account {}", amount, tenantId, account.getId());
    }

    @Transactional(readOnly = true)
    public FloatSummaryResponse getSummary(String tenantId) {
        List<FloatAccount> accounts = floatAccountRepository.findByTenantId(tenantId);
        List<FloatAllocation> allocations = floatAllocationRepository.findByTenantId(tenantId);

        BigDecimal totalLimit = accounts.stream()
                .map(FloatAccount::getFloatLimit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalDrawn = accounts.stream()
                .map(FloatAccount::getDrawnAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long activeAccounts = accounts.stream()
                .filter(a -> a.getStatus() == FloatAccountStatus.ACTIVE)
                .count();

        long activeAllocations = allocations.stream()
                .filter(a -> a.getStatus() == FloatAllocationStatus.ACTIVE)
                .count();

        FloatSummaryResponse summary = new FloatSummaryResponse();
        summary.setTenantId(tenantId);
        summary.setTotalLimit(totalLimit);
        summary.setTotalDrawn(totalDrawn);
        summary.setTotalAvailable(totalLimit.subtract(totalDrawn));
        summary.setActiveAccounts((int) activeAccounts);
        summary.setActiveAllocations((int) activeAllocations);
        return summary;
    }

    @Transactional(readOnly = true)
    public PageResponse<FloatTransactionResponse> getTransactions(UUID accountId, String tenantId, Pageable pageable) {
        // Verify account belongs to tenant
        floatAccountRepository.findByTenantIdAndId(tenantId, accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Float account not found: " + accountId));

        Page<FloatTransaction> page = floatTransactionRepository
                .findByFloatAccountIdAndTenantIdOrderByCreatedAtDesc(accountId, tenantId, pageable);

        return PageResponse.from(page.map(this::toTransactionResponse));
    }

    // ---- Private helpers ----

    private FloatTransaction buildTransaction(FloatAccount account, FloatTransactionType type,
                                               BigDecimal amount, BigDecimal balanceBefore,
                                               BigDecimal balanceAfter, String referenceId,
                                               String referenceType, String narration, String eventId) {
        FloatTransaction tx = new FloatTransaction();
        tx.setTenantId(account.getTenantId());
        tx.setFloatAccountId(account.getId());
        tx.setTransactionType(type);
        tx.setAmount(amount);
        tx.setBalanceBefore(balanceBefore);
        tx.setBalanceAfter(balanceAfter);
        tx.setReferenceId(referenceId);
        tx.setReferenceType(referenceType);
        tx.setNarration(narration);
        tx.setEventId(eventId);
        return tx;
    }

    private FloatAccountResponse toAccountResponse(FloatAccount account) {
        FloatAccountResponse r = new FloatAccountResponse();
        r.setId(account.getId());
        r.setTenantId(account.getTenantId());
        r.setAccountName(account.getAccountName());
        r.setAccountCode(account.getAccountCode());
        r.setCurrency(account.getCurrency());
        r.setFloatLimit(account.getFloatLimit());
        r.setDrawnAmount(account.getDrawnAmount());
        r.setAvailable(account.getAvailable());
        r.setStatus(account.getStatus());
        r.setDescription(account.getDescription());
        r.setCreatedAt(account.getCreatedAt());
        r.setUpdatedAt(account.getUpdatedAt());
        return r;
    }

    private FloatTransactionResponse toTransactionResponse(FloatTransaction tx) {
        FloatTransactionResponse r = new FloatTransactionResponse();
        r.setId(tx.getId());
        r.setFloatAccountId(tx.getFloatAccountId());
        r.setTransactionType(tx.getTransactionType());
        r.setAmount(tx.getAmount());
        r.setBalanceBefore(tx.getBalanceBefore());
        r.setBalanceAfter(tx.getBalanceAfter());
        r.setReferenceId(tx.getReferenceId());
        r.setReferenceType(tx.getReferenceType());
        r.setNarration(tx.getNarration());
        r.setCreatedAt(tx.getCreatedAt());
        return r;
    }
}
