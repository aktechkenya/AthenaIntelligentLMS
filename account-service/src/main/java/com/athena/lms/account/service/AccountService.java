package com.athena.lms.account.service;

import com.athena.lms.account.dto.request.CreateAccountRequest;
import com.athena.lms.account.dto.request.TransactionRequest;
import com.athena.lms.account.dto.response.AccountResponse;
import com.athena.lms.account.dto.response.BalanceResponse;
import com.athena.lms.account.dto.response.TransactionResponse;
import com.athena.lms.account.entity.Account;
import com.athena.lms.account.entity.AccountBalance;
import com.athena.lms.account.entity.AccountTransaction;
import com.athena.lms.account.event.AccountEventPublisher;
import com.athena.lms.account.repository.AccountBalanceRepository;
import com.athena.lms.account.repository.AccountRepository;
import com.athena.lms.account.repository.AccountTransactionRepository;
import com.athena.lms.common.dto.PageResponse;
import com.athena.lms.common.exception.BusinessException;
import com.athena.lms.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    // KYC tier daily/monthly limits in KES
    private static final BigDecimal TIER_0_DAILY_LIMIT   = new BigDecimal("2600");    // ~$20
    private static final BigDecimal TIER_1_MONTHLY_LIMIT = new BigDecimal("65000");   // ~$500
    private static final BigDecimal TIER_2_MONTHLY_LIMIT = new BigDecimal("650000");  // ~$5000

    private final AccountRepository accountRepository;
    private final AccountBalanceRepository accountBalanceRepository;
    private final AccountTransactionRepository transactionRepository;
    private final AccountEventPublisher eventPublisher;
    private final SecureRandom random = new SecureRandom();

    @Transactional
    public AccountResponse createAccount(CreateAccountRequest req, String tenantId) {
        Account.AccountType type;
        try {
            type = Account.AccountType.valueOf(req.getAccountType().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw BusinessException.badRequest("Invalid account type: " + req.getAccountType());
        }

        String accountNumber = generateAccountNumber(tenantId);
        Account account = Account.builder()
                .tenantId(tenantId)
                .accountNumber(accountNumber)
                .customerId(req.getCustomerId())
                .accountType(type)
                .currency(req.getCurrency() != null ? req.getCurrency() : "KES")
                .kycTier(req.getKycTier())
                .accountName(req.getAccountName())
                .build();

        // Set KYC limits based on tier
        applyKycLimits(account, req.getKycTier());
        account = accountRepository.save(account);

        // Create zero balance record
        AccountBalance balance = AccountBalance.builder()
                .accountId(account.getId())
                .availableBalance(BigDecimal.ZERO)
                .currentBalance(BigDecimal.ZERO)
                .ledgerBalance(BigDecimal.ZERO)
                .build();
        accountBalanceRepository.save(balance);

        // Populate @Transient balance for response
        account.setBalance(balance);
        eventPublisher.publishCreated(account.getId(), accountNumber, req.getCustomerId(), tenantId);
        log.info("Created account {} for customer {} in tenant {}", accountNumber, req.getCustomerId(), tenantId);
        return AccountResponse.from(account);
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccount(UUID id, String tenantId) {
        Account account = accountRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", id));
        // Populate @Transient balance field for the response
        accountBalanceRepository.findByAccountId(id).ifPresent(account::setBalance);
        return AccountResponse.from(account);
    }

    @Transactional(readOnly = true)
    public PageResponse<AccountResponse> listAccounts(String tenantId, Pageable pageable) {
        return PageResponse.from(accountRepository.findByTenantId(tenantId, pageable)
                .map(AccountResponse::from));
    }


    @Transactional(readOnly = true)
    public BalanceResponse getBalance(UUID accountId, String tenantId) {
        accountRepository.findByIdAndTenantId(accountId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
        AccountBalance bal = accountBalanceRepository.findByAccountId(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Balance for account", accountId));
        return BalanceResponse.builder()
                .availableBalance(bal.getAvailableBalance())
                .currentBalance(bal.getCurrentBalance())
                .ledgerBalance(bal.getLedgerBalance())
                .updatedAt(bal.getUpdatedAt())
                .build();
    }

    @Transactional
    public TransactionResponse credit(UUID accountId, TransactionRequest req, String tenantId) {
        // Idempotency check
        if (req.getIdempotencyKey() != null) {
            var existing = transactionRepository.findByIdempotencyKey(req.getIdempotencyKey());
            if (existing.isPresent()) {
                return TransactionResponse.from(existing.get());
            }
        }

        Account account = accountRepository.findByIdAndTenantId(accountId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));

        if (account.getStatus() != Account.AccountStatus.ACTIVE) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Account is " + account.getStatus() + " — cannot credit");
        }

        AccountBalance balance = accountBalanceRepository.findByAccountIdForUpdate(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Balance for account", accountId));

        BigDecimal newBalance = balance.getAvailableBalance().add(req.getAmount());
        balance.setAvailableBalance(newBalance);
        balance.setCurrentBalance(balance.getCurrentBalance().add(req.getAmount()));
        balance.setLedgerBalance(balance.getLedgerBalance().add(req.getAmount()));
        accountBalanceRepository.save(balance);

        AccountTransaction txn = AccountTransaction.builder()
                .tenantId(tenantId)
                .accountId(accountId)
                .transactionType(AccountTransaction.TransactionType.CREDIT)
                .amount(req.getAmount())
                .balanceAfter(newBalance)
                .reference(req.getReference())
                .description(req.getDescription())
                .channel(req.getChannel() != null ? req.getChannel() : "SYSTEM")
                .idempotencyKey(req.getIdempotencyKey())
                .build();
        txn = transactionRepository.save(txn);

        eventPublisher.publishCreditReceived(accountId, req.getAmount(), tenantId);
        return TransactionResponse.from(txn);
    }

    @Transactional
    public TransactionResponse debit(UUID accountId, TransactionRequest req, String tenantId) {
        // Idempotency check
        if (req.getIdempotencyKey() != null) {
            var existing = transactionRepository.findByIdempotencyKey(req.getIdempotencyKey());
            if (existing.isPresent()) {
                return TransactionResponse.from(existing.get());
            }
        }

        Account account = accountRepository.findByIdAndTenantId(accountId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));

        if (account.getStatus() != Account.AccountStatus.ACTIVE) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Account is " + account.getStatus() + " — cannot debit");
        }

        AccountBalance balance = accountBalanceRepository.findByAccountIdForUpdate(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Balance for account", accountId));

        // Sufficient funds check
        if (balance.getAvailableBalance().compareTo(req.getAmount()) < 0) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "Insufficient funds");
        }

        // KYC limit enforcement
        enforceKycLimits(account, req.getAmount(), accountId);

        BigDecimal newBalance = balance.getAvailableBalance().subtract(req.getAmount());
        balance.setAvailableBalance(newBalance);
        balance.setCurrentBalance(balance.getCurrentBalance().subtract(req.getAmount()));
        balance.setLedgerBalance(balance.getLedgerBalance().subtract(req.getAmount()));
        accountBalanceRepository.save(balance);

        AccountTransaction txn = AccountTransaction.builder()
                .tenantId(tenantId)
                .accountId(accountId)
                .transactionType(AccountTransaction.TransactionType.DEBIT)
                .amount(req.getAmount())
                .balanceAfter(newBalance)
                .reference(req.getReference())
                .description(req.getDescription())
                .channel(req.getChannel() != null ? req.getChannel() : "SYSTEM")
                .idempotencyKey(req.getIdempotencyKey())
                .build();
        txn = transactionRepository.save(txn);

        eventPublisher.publishDebitProcessed(accountId, req.getAmount(), tenantId);
        return TransactionResponse.from(txn);
    }

    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> getTransactionHistory(UUID accountId, String tenantId, Pageable pageable) {
        accountRepository.findByIdAndTenantId(accountId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
        Page<AccountTransaction> page = transactionRepository.findByAccountIdOrderByCreatedAtDesc(accountId, pageable);
        return PageResponse.from(page.map(TransactionResponse::from));
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> getMiniStatement(UUID accountId, String tenantId, int count) {
        accountRepository.findByIdAndTenantId(accountId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
        int limit = Math.min(count, 50);
        Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        return transactionRepository.findTopNByAccountId(accountId, pageable)
                .stream().map(TransactionResponse::from).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> searchAccounts(String q, String tenantId) {
        return accountRepository.searchByTenantAndQuery(tenantId, q)
                .stream().map(AccountResponse::from).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> getByCustomerId(Long customerId, String tenantId) {
        return accountRepository.findByCustomerIdAndTenantId(customerId, tenantId)
                .stream().map(AccountResponse::from).collect(Collectors.toList());
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private String generateAccountNumber(String tenantId) {
        String prefix = tenantId.length() >= 3
                ? tenantId.substring(0, 3).toUpperCase()
                : tenantId.toUpperCase();
        String suffix = String.format("%08d", random.nextInt(100_000_000));
        String candidate = "ACC-" + prefix + "-" + suffix;
        // Retry on collision (extremely unlikely)
        while (accountRepository.findByAccountNumber(candidate).isPresent()) {
            suffix = String.format("%08d", random.nextInt(100_000_000));
            candidate = "ACC-" + prefix + "-" + suffix;
        }
        return candidate;
    }

    private void applyKycLimits(Account account, int kycTier) {
        switch (kycTier) {
            case 0 -> account.setDailyTransactionLimit(TIER_0_DAILY_LIMIT);
            case 1 -> account.setMonthlyTransactionLimit(TIER_1_MONTHLY_LIMIT);
            case 2 -> account.setMonthlyTransactionLimit(TIER_2_MONTHLY_LIMIT);
            case 3 -> { /* unlimited */ }
            default -> account.setDailyTransactionLimit(TIER_0_DAILY_LIMIT);
        }
    }

    private void enforceKycLimits(Account account, BigDecimal amount, UUID accountId) {
        int tier = account.getKycTier();
        if (tier == 3) return; // unlimited

        if (tier == 0 && account.getDailyTransactionLimit() != null) {
            BigDecimal dailyUsed = transactionRepository.sumDailyDebits(accountId);
            if (dailyUsed.add(amount).compareTo(account.getDailyTransactionLimit()) > 0) {
                throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "KYC Tier 0 daily limit exceeded. Limit: " + account.getDailyTransactionLimit() + " KES");
            }
        }

        if ((tier == 1 || tier == 2) && account.getMonthlyTransactionLimit() != null) {
            BigDecimal monthlyUsed = transactionRepository.sumMonthlyDebits(accountId);
            if (monthlyUsed.add(amount).compareTo(account.getMonthlyTransactionLimit()) > 0) {
                throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "KYC Tier " + tier + " monthly limit exceeded. Limit: " + account.getMonthlyTransactionLimit() + " KES");
            }
        }
    }
}
