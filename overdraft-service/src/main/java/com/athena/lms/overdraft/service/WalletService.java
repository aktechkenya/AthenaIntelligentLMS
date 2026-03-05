package com.athena.lms.overdraft.service;

import com.athena.lms.common.dto.PageResponse;
import com.athena.lms.common.exception.BusinessException;
import com.athena.lms.common.exception.ResourceNotFoundException;
import com.athena.lms.overdraft.dto.request.CreateWalletRequest;
import com.athena.lms.overdraft.dto.request.WalletTransactionRequest;
import com.athena.lms.overdraft.dto.response.WalletResponse;
import com.athena.lms.overdraft.dto.response.WalletTransactionResponse;
import com.athena.lms.overdraft.entity.CustomerWallet;
import com.athena.lms.overdraft.entity.OverdraftFacility;
import com.athena.lms.overdraft.entity.OverdraftFee;
import com.athena.lms.overdraft.entity.WalletTransaction;
import com.athena.lms.overdraft.event.OverdraftEventPublisher;
import com.athena.lms.overdraft.repository.CustomerWalletRepository;
import com.athena.lms.overdraft.repository.OverdraftFacilityRepository;
import com.athena.lms.overdraft.repository.OverdraftFeeRepository;
import com.athena.lms.overdraft.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class WalletService {

    private final CustomerWalletRepository walletRepo;
    private final WalletTransactionRepository txRepo;
    private final OverdraftFacilityRepository facilityRepo;
    private final OverdraftFeeRepository feeRepo;
    private final OverdraftEventPublisher eventPublisher;
    private final AuditService auditService;

    public WalletResponse createWallet(CreateWalletRequest req, String tenantId) {
        if (walletRepo.existsByTenantIdAndCustomerId(tenantId, req.getCustomerId())) {
            throw new BusinessException("Wallet already exists for customer: " + req.getCustomerId());
        }
        CustomerWallet wallet = new CustomerWallet();
        wallet.setTenantId(tenantId);
        wallet.setCustomerId(req.getCustomerId());
        wallet.setAccountNumber(generateAccountNumber(req.getCustomerId()));
        wallet.setCurrency(req.getCurrency() != null ? req.getCurrency() : "KES");
        wallet.setCurrentBalance(BigDecimal.ZERO);
        wallet.setAvailableBalance(BigDecimal.ZERO);
        wallet.setStatus("ACTIVE");
        CustomerWallet saved = walletRepo.save(wallet);

        auditService.audit(tenantId, "WALLET", saved.getId(), "CREATED",
            null, Map.of("customerId", req.getCustomerId(), "accountNumber", saved.getAccountNumber()), null);

        log.info("Created wallet {} for customer {} tenant {}", saved.getId(), req.getCustomerId(), tenantId);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public WalletResponse getWalletByCustomer(String customerId, String tenantId) {
        CustomerWallet wallet = walletRepo.findByTenantIdAndCustomerId(tenantId, customerId)
            .orElseThrow(() -> new ResourceNotFoundException("Wallet not found for customer: " + customerId));
        return toResponse(wallet);
    }

    @Transactional(readOnly = true)
    public WalletResponse getWallet(UUID walletId, String tenantId) {
        CustomerWallet wallet = walletRepo.findByTenantIdAndId(tenantId, walletId)
            .orElseThrow(() -> new ResourceNotFoundException("Wallet not found: " + walletId));
        return toResponse(wallet);
    }

    @Transactional(readOnly = true)
    public List<WalletResponse> listWallets(String tenantId) {
        return walletRepo.findByTenantId(tenantId).stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    public WalletTransactionResponse deposit(UUID walletId, WalletTransactionRequest req, String tenantId) {
        CustomerWallet wallet = walletRepo.findByTenantIdAndId(tenantId, walletId)
            .orElseThrow(() -> new ResourceNotFoundException("Wallet not found: " + walletId));

        BigDecimal balanceBefore = wallet.getCurrentBalance();
        BigDecimal balanceAfter = balanceBefore.add(req.getAmount());
        wallet.setCurrentBalance(balanceAfter);

        BigDecimal interestRepaid = BigDecimal.ZERO;
        BigDecimal principalRepaid = BigDecimal.ZERO;
        BigDecimal feesRepaid = BigDecimal.ZERO;

        Optional<OverdraftFacility> facilityOpt = facilityRepo.findTopByWalletIdOrderByCreatedAtDesc(walletId);
        if (facilityOpt.isPresent()) {
            OverdraftFacility facility = facilityOpt.get();
            if ("ACTIVE".equals(facility.getStatus()) && facility.getDrawnAmount().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal remaining = req.getAmount().min(facility.getDrawnAmount());

                // Waterfall: 1) Fees → 2) Accrued Interest → 3) Drawn Principal
                // 1) Repay pending fees
                List<OverdraftFee> pendingFees = feeRepo.findByFacilityIdAndStatus(facility.getId(), "PENDING");
                for (OverdraftFee fee : pendingFees) {
                    if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
                    BigDecimal feePayment = remaining.min(fee.getAmount());
                    feesRepaid = feesRepaid.add(feePayment);
                    remaining = remaining.subtract(feePayment);
                    fee.setStatus("CHARGED");
                    feeRepo.save(fee);
                }

                // 2) Repay accrued interest
                if (remaining.compareTo(BigDecimal.ZERO) > 0 && facility.getAccruedInterest().compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal intPayment = remaining.min(facility.getAccruedInterest());
                    interestRepaid = intPayment;
                    facility.setAccruedInterest(facility.getAccruedInterest().subtract(intPayment));
                    remaining = remaining.subtract(intPayment);
                }

                // 3) Repay drawn principal
                if (remaining.compareTo(BigDecimal.ZERO) > 0 && facility.getDrawnPrincipal().compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal princPayment = remaining.min(facility.getDrawnPrincipal());
                    principalRepaid = princPayment;
                    facility.setDrawnPrincipal(facility.getDrawnPrincipal().subtract(princPayment));
                    remaining = remaining.subtract(princPayment);
                }

                facility.recalculateDrawnAmount();
                facilityRepo.save(facility);

                BigDecimal totalRepaid = feesRepaid.add(interestRepaid).add(principalRepaid);
                if (totalRepaid.compareTo(BigDecimal.ZERO) > 0) {
                    eventPublisher.publishOverdraftRepaidDetailed(walletId, wallet.getCustomerId(),
                        totalRepaid, interestRepaid, principalRepaid, feesRepaid, tenantId);
                }
            }
            if ("ACTIVE".equals(facility.getStatus())) {
                BigDecimal overdraftHeadroom = facility.getApprovedLimit().subtract(facility.getDrawnAmount());
                wallet.setAvailableBalance(balanceAfter.add(overdraftHeadroom));
            } else {
                wallet.setAvailableBalance(balanceAfter);
            }
        } else {
            wallet.setAvailableBalance(balanceAfter);
        }

        walletRepo.save(wallet);
        WalletTransaction tx = buildTx(wallet, "DEPOSIT", req.getAmount(), balanceBefore, balanceAfter,
            req.getReference(), req.getDescription());
        WalletTransaction savedTx = txRepo.save(tx);

        auditService.audit(tenantId, "WALLET", walletId, "DEPOSIT",
            Map.of("balance", balanceBefore),
            Map.of("balance", balanceAfter, "interestRepaid", interestRepaid, "principalRepaid", principalRepaid),
            Map.of("amount", req.getAmount(), "reference", req.getReference()));

        return toTxResponse(savedTx);
    }

    public WalletTransactionResponse withdraw(UUID walletId, WalletTransactionRequest req, String tenantId) {
        CustomerWallet wallet = walletRepo.findByTenantIdAndId(tenantId, walletId)
            .orElseThrow(() -> new ResourceNotFoundException("Wallet not found: " + walletId));

        if (wallet.getAvailableBalance().compareTo(req.getAmount()) < 0) {
            throw new BusinessException("Insufficient balance. Available: " + wallet.getAvailableBalance()
                + ", Requested: " + req.getAmount());
        }

        BigDecimal balanceBefore = wallet.getCurrentBalance();
        BigDecimal balanceAfter = balanceBefore.subtract(req.getAmount());
        wallet.setCurrentBalance(balanceAfter);

        String txType = "WITHDRAWAL";
        boolean overdraftDrawn = false;

        if (balanceAfter.compareTo(BigDecimal.ZERO) < 0) {
            txType = "OVERDRAFT_DRAW";
            overdraftDrawn = true;
        }

        Optional<OverdraftFacility> facilityOpt = facilityRepo.findTopByWalletIdOrderByCreatedAtDesc(walletId);
        if (facilityOpt.isPresent()) {
            OverdraftFacility facility = facilityOpt.get();
            if ("ACTIVE".equals(facility.getStatus())) {
                if (overdraftDrawn) {
                    BigDecimal previousOverdraft = balanceBefore.negate().max(BigDecimal.ZERO);
                    BigDecimal newOverdraft = balanceAfter.negate();
                    BigDecimal additionalDraw = newOverdraft.subtract(previousOverdraft);
                    facility.setDrawnPrincipal(facility.getDrawnPrincipal().add(additionalDraw));
                    facility.recalculateDrawnAmount();
                    facilityRepo.save(facility);
                }
                BigDecimal overdraftHeadroom = facility.getApprovedLimit().subtract(facility.getDrawnAmount());
                wallet.setAvailableBalance(balanceAfter.add(overdraftHeadroom));
            } else {
                wallet.setAvailableBalance(balanceAfter);
            }
        } else {
            wallet.setAvailableBalance(balanceAfter);
        }

        walletRepo.save(wallet);
        WalletTransaction tx = buildTx(wallet, txType, req.getAmount(), balanceBefore, balanceAfter,
            req.getReference(), req.getDescription());
        WalletTransaction saved = txRepo.save(tx);

        if (overdraftDrawn) {
            BigDecimal previousOverdraft = balanceBefore.negate().max(BigDecimal.ZERO);
            BigDecimal actualDraw = balanceAfter.negate().subtract(previousOverdraft);
            eventPublisher.publishOverdraftDrawn(walletId, wallet.getCustomerId(), actualDraw, tenantId);
        }

        auditService.audit(tenantId, "WALLET", walletId, "WITHDRAWAL",
            Map.of("balance", balanceBefore),
            Map.of("balance", balanceAfter),
            Map.of("amount", req.getAmount(), "reference", req.getReference(), "overdraftDrawn", overdraftDrawn));

        return toTxResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<WalletTransactionResponse> getTransactions(UUID walletId, String tenantId, Pageable pageable) {
        walletRepo.findByTenantIdAndId(tenantId, walletId)
            .orElseThrow(() -> new ResourceNotFoundException("Wallet not found: " + walletId));
        Page<WalletTransaction> page = txRepo.findByWalletIdAndTenantIdOrderByCreatedAtDesc(walletId, tenantId, pageable);
        return PageResponse.from(page.map(this::toTxResponse));
    }

    private String generateAccountNumber(String customerId) {
        String cleaned = customerId.toUpperCase().replaceAll("[^A-Z0-9]", "");
        String prefix = cleaned.length() > 6 ? cleaned.substring(0, 6) : cleaned;
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return "WLT-" + prefix + "-" + uniqueSuffix;
    }

    private WalletTransaction buildTx(CustomerWallet wallet, String type, BigDecimal amount,
                                       BigDecimal before, BigDecimal after, String reference, String description) {
        WalletTransaction tx = new WalletTransaction();
        tx.setTenantId(wallet.getTenantId());
        tx.setWalletId(wallet.getId());
        tx.setTransactionType(type);
        tx.setAmount(amount);
        tx.setBalanceBefore(before);
        tx.setBalanceAfter(after);
        tx.setReference(reference);
        tx.setDescription(description);
        return tx;
    }

    public WalletResponse toResponse(CustomerWallet w) {
        WalletResponse r = new WalletResponse();
        r.setId(w.getId());
        r.setTenantId(w.getTenantId());
        r.setCustomerId(w.getCustomerId());
        r.setAccountNumber(w.getAccountNumber());
        r.setCurrency(w.getCurrency());
        r.setCurrentBalance(w.getCurrentBalance());
        r.setAvailableBalance(w.getAvailableBalance());
        r.setStatus(w.getStatus());
        r.setCreatedAt(w.getCreatedAt());
        r.setUpdatedAt(w.getUpdatedAt());
        return r;
    }

    private WalletTransactionResponse toTxResponse(WalletTransaction tx) {
        WalletTransactionResponse r = new WalletTransactionResponse();
        r.setId(tx.getId());
        r.setWalletId(tx.getWalletId());
        r.setTransactionType(tx.getTransactionType());
        r.setAmount(tx.getAmount());
        r.setBalanceBefore(tx.getBalanceBefore());
        r.setBalanceAfter(tx.getBalanceAfter());
        r.setReference(tx.getReference());
        r.setDescription(tx.getDescription());
        r.setCreatedAt(tx.getCreatedAt());
        return r;
    }
}
