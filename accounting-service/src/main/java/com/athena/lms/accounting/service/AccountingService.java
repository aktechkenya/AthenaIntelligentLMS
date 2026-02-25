package com.athena.lms.accounting.service;

import com.athena.lms.common.dto.PageResponse;
import com.athena.lms.common.exception.BusinessException;
import com.athena.lms.common.exception.ResourceNotFoundException;
import com.athena.lms.accounting.dto.request.*;
import com.athena.lms.accounting.dto.response.*;
import com.athena.lms.accounting.entity.*;
import com.athena.lms.accounting.enums.*;
import com.athena.lms.accounting.event.AccountingEventPublisher;
import com.athena.lms.accounting.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountingService {

    private final ChartOfAccountRepository coaRepo;
    private final JournalEntryRepository entryRepo;
    private final JournalLineRepository lineRepo;
    private final AccountBalanceRepository balanceRepo;
    private final AccountingEventPublisher eventPublisher;

    // ─── Chart of Accounts ───────────────────────────────────────────────────────

    @Transactional
    public AccountResponse createAccount(CreateAccountRequest req, String tenantId) {
        if (coaRepo.findByTenantIdAndCode(tenantId, req.getCode()).isPresent()) {
            throw new BusinessException("Account code already exists: " + req.getCode());
        }
        ChartOfAccount account = ChartOfAccount.builder()
            .tenantId(tenantId)
            .code(req.getCode())
            .name(req.getName())
            .accountType(req.getAccountType())
            .balanceType(req.getBalanceType())
            .parentId(req.getParentId())
            .description(req.getDescription())
            .isActive(true)
            .build();
        return toAccountResponse(coaRepo.save(account));
    }

    public List<AccountResponse> listAccounts(String tenantId, AccountType type) {
        List<ChartOfAccount> accounts = type != null
            ? coaRepo.findByTenantIdAndAccountTypeAndIsActiveTrue(tenantId, type)
            : coaRepo.findByTenantIdAndIsActiveTrue(tenantId);
        // If tenant has no accounts, fall back to system accounts
        if (accounts.isEmpty()) {
            accounts = type != null
                ? coaRepo.findByTenantIdAndAccountTypeAndIsActiveTrue("system", type)
                : coaRepo.findByTenantIdAndIsActiveTrue("system");
        }
        return accounts.stream().map(this::toAccountResponse).collect(Collectors.toList());
    }

    public AccountResponse getAccount(UUID id, String tenantId) {
        return toAccountResponse(coaRepo.findByIdAndTenantIdIn(id, List.of(tenantId, "system"))
            .orElseThrow(() -> new ResourceNotFoundException("Account", id.toString())));
    }

    public AccountResponse getAccountByCode(String code, String tenantId) {
        return coaRepo.findByCodeAndTenantIdIn(code, List.of(tenantId, "system"))
            .map(this::toAccountResponse)
            .orElseThrow(() -> new ResourceNotFoundException("Account", code));
    }

    // ─── Journal Entries ──────────────────────────────────────────────────────────

    @Transactional
    public JournalEntryResponse postEntry(PostJournalEntryRequest req, String tenantId, String userId) {
        // Validate balanced entry
        BigDecimal totalDebit  = req.getLines().stream().map(JournalLineRequest::getDebitAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredit = req.getLines().stream().map(JournalLineRequest::getCreditAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalDebit.compareTo(totalCredit) != 0) {
            throw new BusinessException("Journal entry is not balanced: debits=" + totalDebit + " credits=" + totalCredit);
        }

        JournalEntry entry = JournalEntry.builder()
            .tenantId(tenantId)
            .reference(req.getReference())
            .description(req.getDescription())
            .entryDate(req.getEntryDate() != null ? req.getEntryDate() : LocalDate.now())
            .status(EntryStatus.POSTED)
            .totalDebit(totalDebit)
            .totalCredit(totalCredit)
            .postedBy(userId)
            .build();

        int lineNo = 1;
        for (JournalLineRequest lr : req.getLines()) {
            JournalLine line = JournalLine.builder()
                .entry(entry)
                .tenantId(tenantId)
                .accountId(lr.getAccountId())
                .lineNo(lineNo++)
                .description(lr.getDescription())
                .debitAmount(lr.getDebitAmount())
                .creditAmount(lr.getCreditAmount())
                .currency(lr.getCurrency() != null ? lr.getCurrency() : "KES")
                .build();
            entry.getLines().add(line);
        }

        entry = entryRepo.save(entry);
        updateAccountBalances(entry);
        eventPublisher.publishJournalPosted(entry);
        return toEntryResponse(entry);
    }

    public PageResponse<JournalEntryResponse> listEntries(String tenantId, LocalDate from, LocalDate to, Pageable pageable) {
        Page<JournalEntry> page = (from != null && to != null)
            ? entryRepo.findByTenantIdAndEntryDateBetween(tenantId, from, to, pageable)
            : entryRepo.findByTenantId(tenantId, pageable);
        return PageResponse.from(page.map(this::toEntryResponse));
    }

    public JournalEntryResponse getEntry(UUID id, String tenantId) {
        JournalEntry entry = entryRepo.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("JournalEntry", id.toString()));
        return toEntryResponse(entry);
    }

    // ─── Balances & Reporting ─────────────────────────────────────────────────────

    public BalanceResponse getBalance(UUID accountId, String tenantId, int year, int month) {
        ChartOfAccount account = coaRepo.findByIdAndTenantIdIn(accountId, List.of(tenantId, "system"))
            .orElseThrow(() -> new ResourceNotFoundException("Account", accountId.toString()));

        BigDecimal net = lineRepo.getNetBalance(accountId, tenantId);
        // For CREDIT-normal accounts, flip sign for display
        if (account.getBalanceType() == BalanceType.CREDIT) net = net.negate();

        return BalanceResponse.builder()
            .accountId(accountId)
            .accountCode(account.getCode())
            .accountName(account.getName())
            .accountType(account.getAccountType().name())
            .balanceType(account.getBalanceType().name())
            .balance(net)
            .currency("KES")
            .periodYear(year)
            .periodMonth(month)
            .build();
    }

    public List<JournalLineResponse> getLedger(UUID accountId, String tenantId) {
        coaRepo.findByIdAndTenantIdIn(accountId, List.of(tenantId, "system"))
            .orElseThrow(() -> new ResourceNotFoundException("Account", accountId.toString()));
        return lineRepo.findByAccountId(accountId).stream()
            .map(l -> toLineResponse(l, null))
            .collect(Collectors.toList());
    }

    public TrialBalanceResponse getTrialBalance(String tenantId, int year, int month) {
        List<ChartOfAccount> accounts = coaRepo.findByTenantIdAndIsActiveTrue(tenantId);
        if (accounts.isEmpty()) accounts = coaRepo.findByTenantIdAndIsActiveTrue("system");

        List<BalanceResponse> rows = new ArrayList<>();
        BigDecimal totalDr = BigDecimal.ZERO;
        BigDecimal totalCr = BigDecimal.ZERO;

        for (ChartOfAccount acc : accounts) {
            BigDecimal net = lineRepo.getNetBalance(acc.getId(), tenantId);
            BalanceResponse row = BalanceResponse.builder()
                .accountId(acc.getId())
                .accountCode(acc.getCode())
                .accountName(acc.getName())
                .accountType(acc.getAccountType().name())
                .balanceType(acc.getBalanceType().name())
                .balance(net.abs())
                .currency("KES")
                .periodYear(year)
                .periodMonth(month)
                .build();
            rows.add(row);
            if (net.compareTo(BigDecimal.ZERO) >= 0) totalDr = totalDr.add(net.abs());
            else totalCr = totalCr.add(net.abs());
        }

        return TrialBalanceResponse.builder()
            .periodYear(year)
            .periodMonth(month)
            .accounts(rows)
            .totalDebits(totalDr)
            .totalCredits(totalCr)
            .balanced(totalDr.compareTo(totalCr) == 0)
            .build();
    }

    // ─── Event-driven journal posting ─────────────────────────────────────────────

    public boolean entryExists(String sourceEvent, String sourceId) {
        return sourceId != null && entryRepo.existsBySourceEventAndSourceId(sourceEvent, sourceId);
    }

    @Transactional
    public void postLoanDisbursement(String tenantId, String applicationId, BigDecimal amount) {
        // DR Loans Receivable (1100) / CR Cash (1000)
        UUID drAccount = resolveAccountId(tenantId, "1100");
        UUID crAccount = resolveAccountId(tenantId, "1000");

        JournalEntry entry = buildSystemEntry(tenantId, "DISB-" + applicationId,
            "Loan disbursement for application " + applicationId,
            "loan.disbursed", applicationId,
            drAccount, crAccount, amount);
        entryRepo.save(entry);
        updateAccountBalances(entry);
        eventPublisher.publishJournalPosted(entry);
        log.info("Posted disbursement journal for application [{}] amount [{}]", applicationId, amount);
    }

    @Transactional
    public void postRepayment(String tenantId, String paymentId, BigDecimal amount, Map<String, Object> payload) {
        // DR Cash (1000) / CR Loans Receivable (1100) for principal portion
        UUID drAccount = resolveAccountId(tenantId, "1000");
        UUID crAccount = resolveAccountId(tenantId, "1100");

        JournalEntry entry = buildSystemEntry(tenantId, "RPMT-" + paymentId,
            "Loan repayment payment " + paymentId,
            "payment.completed", paymentId,
            drAccount, crAccount, amount);
        entryRepo.save(entry);
        updateAccountBalances(entry);
        eventPublisher.publishJournalPosted(entry);
        log.info("Posted repayment journal for payment [{}] amount [{}]", paymentId, amount);
    }

    @Transactional
    public void postPaymentReversal(String tenantId, String paymentId, BigDecimal amount) {
        // Reverse: DR Loans Receivable (1100) / CR Cash (1000)
        UUID drAccount = resolveAccountId(tenantId, "1100");
        UUID crAccount = resolveAccountId(tenantId, "1000");

        JournalEntry entry = buildSystemEntry(tenantId, "REV-" + paymentId,
            "Payment reversal for " + paymentId,
            "payment.reversed", paymentId,
            drAccount, crAccount, amount);
        entryRepo.save(entry);
        updateAccountBalances(entry);
        eventPublisher.publishJournalPosted(entry);
    }

    // ─── Private helpers ──────────────────────────────────────────────────────────

    private UUID resolveAccountId(String tenantId, String code) {
        return coaRepo.findByCodeAndTenantIdIn(code, List.of(tenantId, "system"))
            .map(ChartOfAccount::getId)
            .orElseThrow(() -> new BusinessException("GL account not found: " + code));
    }

    private JournalEntry buildSystemEntry(String tenantId, String reference, String description,
                                           String sourceEvent, String sourceId,
                                           UUID drAccountId, UUID crAccountId, BigDecimal amount) {
        JournalEntry entry = JournalEntry.builder()
            .tenantId(tenantId)
            .reference(reference)
            .description(description)
            .entryDate(LocalDate.now())
            .status(EntryStatus.POSTED)
            .sourceEvent(sourceEvent)
            .sourceId(sourceId)
            .totalDebit(amount)
            .totalCredit(amount)
            .postedBy("system")
            .build();

        JournalLine debitLine = JournalLine.builder()
            .entry(entry).tenantId(tenantId).accountId(drAccountId)
            .lineNo(1).debitAmount(amount).creditAmount(BigDecimal.ZERO).currency("KES")
            .build();
        JournalLine creditLine = JournalLine.builder()
            .entry(entry).tenantId(tenantId).accountId(crAccountId)
            .lineNo(2).debitAmount(BigDecimal.ZERO).creditAmount(amount).currency("KES")
            .build();

        entry.getLines().add(debitLine);
        entry.getLines().add(creditLine);
        return entry;
    }

    private void updateAccountBalances(JournalEntry entry) {
        LocalDate date = entry.getEntryDate();
        int year = date.getYear();
        int month = date.getMonthValue();

        for (JournalLine line : entry.getLines()) {
            AccountBalance balance = balanceRepo
                .findByTenantIdAndAccountIdAndPeriodYearAndPeriodMonth(
                    entry.getTenantId(), line.getAccountId(), year, month)
                .orElseGet(() -> AccountBalance.builder()
                    .tenantId(entry.getTenantId())
                    .accountId(line.getAccountId())
                    .periodYear(year).periodMonth(month)
                    .openingBalance(BigDecimal.ZERO)
                    .totalDebits(BigDecimal.ZERO).totalCredits(BigDecimal.ZERO)
                    .closingBalance(BigDecimal.ZERO)
                    .currency("KES")
                    .build());

            balance.setTotalDebits(balance.getTotalDebits().add(line.getDebitAmount()));
            balance.setTotalCredits(balance.getTotalCredits().add(line.getCreditAmount()));
            balance.setClosingBalance(balance.getOpeningBalance()
                .add(balance.getTotalDebits()).subtract(balance.getTotalCredits()));
            balanceRepo.save(balance);
        }
    }

    // ─── Mappers ──────────────────────────────────────────────────────────────────

    private AccountResponse toAccountResponse(ChartOfAccount a) {
        return AccountResponse.builder()
            .id(a.getId()).tenantId(a.getTenantId()).code(a.getCode()).name(a.getName())
            .accountType(a.getAccountType()).balanceType(a.getBalanceType())
            .parentId(a.getParentId()).description(a.getDescription())
            .isActive(a.getIsActive()).createdAt(a.getCreatedAt())
            .build();
    }

    private JournalEntryResponse toEntryResponse(JournalEntry e) {
        List<JournalLineResponse> lines = e.getLines().stream()
            .map(l -> toLineResponse(l, null)).collect(Collectors.toList());
        return JournalEntryResponse.builder()
            .id(e.getId()).tenantId(e.getTenantId()).reference(e.getReference())
            .description(e.getDescription()).entryDate(e.getEntryDate()).status(e.getStatus())
            .sourceEvent(e.getSourceEvent()).sourceId(e.getSourceId())
            .totalDebit(e.getTotalDebit()).totalCredit(e.getTotalCredit())
            .postedBy(e.getPostedBy()).createdAt(e.getCreatedAt())
            .lines(lines)
            .build();
    }

    private JournalLineResponse toLineResponse(JournalLine l, ChartOfAccount account) {
        return JournalLineResponse.builder()
            .id(l.getId()).accountId(l.getAccountId()).lineNo(l.getLineNo())
            .description(l.getDescription()).debitAmount(l.getDebitAmount())
            .creditAmount(l.getCreditAmount()).currency(l.getCurrency())
            .build();
    }
}
