package com.athena.lms.account.service;

import com.athena.lms.account.dto.request.TransferRequest;
import com.athena.lms.account.dto.response.TransferResponse;
import com.athena.lms.account.entity.Account;
import com.athena.lms.account.entity.AccountBalance;
import com.athena.lms.account.entity.AccountTransaction;
import com.athena.lms.account.entity.FundTransfer;
import com.athena.lms.account.event.AccountEventPublisher;
import com.athena.lms.account.repository.AccountBalanceRepository;
import com.athena.lms.account.repository.AccountRepository;
import com.athena.lms.account.repository.AccountTransactionRepository;
import com.athena.lms.account.repository.FundTransferRepository;
import com.athena.lms.common.dto.PageResponse;
import com.athena.lms.common.exception.BusinessException;
import com.athena.lms.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransferService {

    private final FundTransferRepository transferRepository;
    private final AccountRepository accountRepository;
    private final AccountBalanceRepository balanceRepository;
    private final AccountTransactionRepository transactionRepository;
    private final AccountEventPublisher eventPublisher;
    private final RestTemplate restTemplate;

    @Value("${lms.product-service.url:http://lms-product-service:8087}")
    private String productServiceUrl;

    @Value("${lms.internal.service-key:}")
    private String serviceKey;

    @Transactional
    public TransferResponse initiateTransfer(TransferRequest req, String tenantId, String initiatedBy) {
        // Idempotency check
        if (req.getIdempotencyKey() != null) {
            var existing = transferRepository.findByReference(req.getIdempotencyKey());
            if (existing.isPresent()) {
                FundTransfer t = existing.get();
                return TransferResponse.from(t,
                        accountRepository.findById(t.getSourceAccountId()).map(Account::getAccountNumber).orElse(null),
                        accountRepository.findById(t.getDestinationAccountId()).map(Account::getAccountNumber).orElse(null));
            }
        }

        // Parse transfer type
        FundTransfer.TransferType transferType;
        try {
            transferType = FundTransfer.TransferType.valueOf(req.getTransferType().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw BusinessException.badRequest("Invalid transfer type: " + req.getTransferType());
        }

        // Resolve source account
        Account sourceAccount = accountRepository.findByIdAndTenantId(req.getSourceAccountId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Source account", req.getSourceAccountId()));
        if (sourceAccount.getStatus() != Account.AccountStatus.ACTIVE) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "Source account is " + sourceAccount.getStatus());
        }

        // Resolve destination account
        Account destAccount;
        if (req.getDestinationAccountId() != null) {
            destAccount = accountRepository.findById(req.getDestinationAccountId())
                    .orElseThrow(() -> new ResourceNotFoundException("Destination account", req.getDestinationAccountId()));
        } else if (req.getDestinationAccountNumber() != null) {
            destAccount = accountRepository.findByAccountNumber(req.getDestinationAccountNumber())
                    .orElseThrow(() -> BusinessException.badRequest("Destination account not found: " + req.getDestinationAccountNumber()));
        } else {
            throw BusinessException.badRequest("Either destinationAccountId or destinationAccountNumber is required");
        }

        if (destAccount.getStatus() != Account.AccountStatus.ACTIVE) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "Destination account is " + destAccount.getStatus());
        }

        // Same account check
        if (sourceAccount.getId().equals(destAccount.getId())) {
            throw BusinessException.badRequest("Cannot transfer to the same account");
        }

        // Currency check
        if (!sourceAccount.getCurrency().equals(destAccount.getCurrency())) {
            throw BusinessException.badRequest("Currency mismatch: " + sourceAccount.getCurrency() + " vs " + destAccount.getCurrency());
        }

        // For INTERNAL transfers, verify same customer
        if (transferType == FundTransfer.TransferType.INTERNAL
                && !sourceAccount.getCustomerId().equals(destAccount.getCustomerId())) {
            throw BusinessException.badRequest("INTERNAL transfers require same customer; use THIRD_PARTY for different customers");
        }

        // Calculate charge (fail-open: 0 if product-service unreachable)
        BigDecimal chargeAmount = calculateCharge(transferType.name(), req.getAmount(), tenantId);

        BigDecimal totalDebit = req.getAmount().add(chargeAmount);

        // Lock balances in UUID order to prevent deadlocks
        UUID first = sourceAccount.getId().compareTo(destAccount.getId()) < 0
                ? sourceAccount.getId() : destAccount.getId();
        UUID second = first.equals(sourceAccount.getId()) ? destAccount.getId() : sourceAccount.getId();

        AccountBalance firstBal = balanceRepository.findByAccountIdForUpdate(first)
                .orElseThrow(() -> new ResourceNotFoundException("Balance", first));
        AccountBalance secondBal = balanceRepository.findByAccountIdForUpdate(second)
                .orElseThrow(() -> new ResourceNotFoundException("Balance", second));

        AccountBalance sourceBal = first.equals(sourceAccount.getId()) ? firstBal : secondBal;
        AccountBalance destBal = first.equals(destAccount.getId()) ? firstBal : secondBal;

        // Sufficient funds check
        if (sourceBal.getAvailableBalance().compareTo(totalDebit) < 0) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Insufficient funds. Required: " + totalDebit + " " + sourceAccount.getCurrency()
                    + " (transfer: " + req.getAmount() + " + charge: " + chargeAmount + ")");
        }

        // Generate reference
        String reference = req.getIdempotencyKey() != null
                ? req.getIdempotencyKey()
                : "TXF-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();

        // Debit source
        sourceBal.setAvailableBalance(sourceBal.getAvailableBalance().subtract(totalDebit));
        sourceBal.setCurrentBalance(sourceBal.getCurrentBalance().subtract(totalDebit));
        sourceBal.setLedgerBalance(sourceBal.getLedgerBalance().subtract(totalDebit));
        balanceRepository.save(sourceBal);

        // Credit destination
        destBal.setAvailableBalance(destBal.getAvailableBalance().add(req.getAmount()));
        destBal.setCurrentBalance(destBal.getCurrentBalance().add(req.getAmount()));
        destBal.setLedgerBalance(destBal.getLedgerBalance().add(req.getAmount()));
        balanceRepository.save(destBal);

        // Create transaction records
        AccountTransaction debitTxn = AccountTransaction.builder()
                .tenantId(tenantId)
                .accountId(sourceAccount.getId())
                .transactionType(AccountTransaction.TransactionType.DEBIT)
                .amount(totalDebit)
                .balanceAfter(sourceBal.getAvailableBalance())
                .reference(reference)
                .description("Transfer to " + destAccount.getAccountNumber()
                        + (req.getNarration() != null ? " — " + req.getNarration() : ""))
                .channel("TRANSFER")
                .build();
        transactionRepository.save(debitTxn);

        AccountTransaction creditTxn = AccountTransaction.builder()
                .tenantId(tenantId)
                .accountId(destAccount.getId())
                .transactionType(AccountTransaction.TransactionType.CREDIT)
                .amount(req.getAmount())
                .balanceAfter(destBal.getAvailableBalance())
                .reference(reference)
                .description("Transfer from " + sourceAccount.getAccountNumber()
                        + (req.getNarration() != null ? " — " + req.getNarration() : ""))
                .channel("TRANSFER")
                .build();
        transactionRepository.save(creditTxn);

        // Save transfer record
        FundTransfer transfer = FundTransfer.builder()
                .tenantId(tenantId)
                .sourceAccountId(sourceAccount.getId())
                .destinationAccountId(destAccount.getId())
                .amount(req.getAmount())
                .currency(sourceAccount.getCurrency())
                .transferType(transferType)
                .status(FundTransfer.TransferStatus.COMPLETED)
                .reference(reference)
                .narration(req.getNarration())
                .chargeAmount(chargeAmount)
                .initiatedBy(initiatedBy)
                .completedAt(LocalDateTime.now())
                .build();
        transfer = transferRepository.save(transfer);

        eventPublisher.publishTransferCompleted(transfer.getId(),
                sourceAccount.getId(), destAccount.getId(), req.getAmount(), tenantId);

        log.info("Transfer {} completed: {} {} from {} to {} (charge: {})",
                reference, req.getAmount(), sourceAccount.getCurrency(),
                sourceAccount.getAccountNumber(), destAccount.getAccountNumber(), chargeAmount);

        return TransferResponse.from(transfer,
                sourceAccount.getAccountNumber(), destAccount.getAccountNumber());
    }

    @Transactional(readOnly = true)
    public TransferResponse getTransfer(UUID id, String tenantId) {
        FundTransfer t = transferRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer", id));
        String srcNum = accountRepository.findById(t.getSourceAccountId())
                .map(Account::getAccountNumber).orElse(null);
        String destNum = accountRepository.findById(t.getDestinationAccountId())
                .map(Account::getAccountNumber).orElse(null);
        return TransferResponse.from(t, srcNum, destNum);
    }

    @Transactional(readOnly = true)
    public PageResponse<TransferResponse> getTransfersByAccount(UUID accountId, String tenantId, Pageable pageable) {
        return PageResponse.from(transferRepository.findByAccountId(tenantId, accountId, pageable)
                .map(TransferResponse::from));
    }

    private BigDecimal calculateCharge(String transferType, BigDecimal amount, String tenantId) {
        try {
            String chargeType = "TRANSFER_" + transferType;
            String url = productServiceUrl + "/api/v1/charges/calculate?transactionType="
                    + chargeType + "&amount=" + amount;

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Service-Key", serviceKey);
            headers.set("X-Service-Tenant", tenantId);
            headers.set("X-Service-User", "account-service");
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            @SuppressWarnings("unchecked")
            ResponseEntity<Map> resp = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                Object charge = resp.getBody().get("chargeAmount");
                if (charge != null) {
                    return new BigDecimal(charge.toString());
                }
            }
        } catch (Exception e) {
            log.warn("Could not fetch charge from product-service: {}. Using 0 charge.", e.getMessage());
        }
        return BigDecimal.ZERO;
    }
}
