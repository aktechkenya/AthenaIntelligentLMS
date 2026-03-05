package com.athena.lms.overdraft.service;

import com.athena.lms.common.exception.BusinessException;
import com.athena.lms.common.exception.ResourceNotFoundException;
import com.athena.lms.overdraft.client.ScoringClient;
import com.athena.lms.overdraft.dto.response.InterestChargeResponse;
import com.athena.lms.overdraft.dto.response.OverdraftFacilityResponse;
import com.athena.lms.overdraft.dto.response.OverdraftSummaryResponse;
import com.athena.lms.overdraft.entity.*;
import com.athena.lms.overdraft.event.OverdraftEventPublisher;
import com.athena.lms.overdraft.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class OverdraftFacilityService {

    private static final Map<String, BigDecimal> BAND_LIMITS = Map.of(
        "A", new BigDecimal("100000"),
        "B", new BigDecimal("50000"),
        "C", new BigDecimal("20000"),
        "D", new BigDecimal("5000")
    );

    private static final Map<String, BigDecimal> BAND_RATES = Map.of(
        "A", new BigDecimal("0.1500"),
        "B", new BigDecimal("0.2000"),
        "C", new BigDecimal("0.2500"),
        "D", new BigDecimal("0.3000")
    );

    private final CustomerWalletRepository walletRepo;
    private final OverdraftFacilityRepository facilityRepo;
    private final OverdraftInterestChargeRepository chargeRepo;
    private final CreditBandConfigRepository bandConfigRepo;
    private final OverdraftFeeRepository feeRepo;
    private final ScoringClient scoringClient;
    private final OverdraftEventPublisher eventPublisher;
    private final AuditService auditService;

    public OverdraftFacilityResponse applyForOverdraft(UUID walletId, String tenantId) {
        CustomerWallet wallet = walletRepo.findByTenantIdAndId(tenantId, walletId)
            .orElseThrow(() -> new ResourceNotFoundException("Wallet not found: " + walletId));

        ScoringClient.CreditScoreResult scoreResult = scoringClient.getLatestScore(wallet.getCustomerId());
        String band = scoreResult.band();
        int score = scoreResult.score();

        // Look up configurable band config, fallback to hardcoded
        CreditBandConfig bandConfig = resolveBandConfig(tenantId, band);
        BigDecimal limit = bandConfig != null ? bandConfig.getApprovedLimit() : BAND_LIMITS.getOrDefault(band, new BigDecimal("5000"));
        BigDecimal rate = bandConfig != null ? bandConfig.getInterestRate() : BAND_RATES.getOrDefault(band, new BigDecimal("0.3000"));
        BigDecimal arrangementFee = bandConfig != null ? bandConfig.getArrangementFee() : BigDecimal.ZERO;

        OverdraftFacility facility = facilityRepo.findTopByWalletIdOrderByCreatedAtDesc(walletId)
            .orElse(new OverdraftFacility());

        facility.setTenantId(tenantId);
        facility.setWalletId(walletId);
        facility.setCustomerId(wallet.getCustomerId());
        facility.setCreditScore(score);
        facility.setCreditBand(band);
        facility.setApprovedLimit(limit);
        facility.setInterestRate(rate);
        facility.setStatus("ACTIVE");
        facility.setNextBillingDate(LocalDate.now().plusMonths(1).withDayOfMonth(1));
        if (facility.getDrawnAmount() == null) facility.setDrawnAmount(BigDecimal.ZERO);
        if (facility.getDrawnPrincipal() == null) facility.setDrawnPrincipal(BigDecimal.ZERO);
        if (facility.getAccruedInterest() == null) facility.setAccruedInterest(BigDecimal.ZERO);

        OverdraftFacility saved = facilityRepo.save(facility);

        BigDecimal overdraftHeadroom = limit.subtract(saved.getDrawnAmount());
        wallet.setAvailableBalance(wallet.getCurrentBalance().add(overdraftHeadroom));
        walletRepo.save(wallet);

        // Charge arrangement fee if applicable
        if (arrangementFee.compareTo(BigDecimal.ZERO) > 0) {
            chargeArrangementFee(saved, wallet, arrangementFee);
        }

        eventPublisher.publishOverdraftApplied(walletId, wallet.getCustomerId(), band, limit, tenantId);

        auditService.audit(tenantId, "FACILITY", saved.getId(), "OVERDRAFT_APPLIED",
            null,
            Map.of("band", band, "limit", limit, "rate", rate, "score", score),
            Map.of("creditScore", score, "creditBand", band));

        log.info("Overdraft applied for wallet {} customer {} band {} limit {}", walletId, wallet.getCustomerId(), band, limit);
        return toFacilityResponse(saved);
    }

    private void chargeArrangementFee(OverdraftFacility facility, CustomerWallet wallet, BigDecimal feeAmount) {
        String ref = "FEE-ARR-" + facility.getId().toString().substring(0, 8);

        OverdraftFee fee = new OverdraftFee();
        fee.setTenantId(facility.getTenantId());
        fee.setFacilityId(facility.getId());
        fee.setFeeType("ARRANGEMENT");
        fee.setAmount(feeAmount);
        fee.setReference(ref);
        fee.setStatus("CHARGED");
        fee.setChargedAt(OffsetDateTime.now());
        feeRepo.save(fee);

        // Debit fee from wallet
        wallet.setCurrentBalance(wallet.getCurrentBalance().subtract(feeAmount));
        wallet.setAvailableBalance(wallet.getAvailableBalance().subtract(feeAmount));
        walletRepo.save(wallet);

        eventPublisher.publishFeeCharged(facility.getWalletId(), facility.getCustomerId(),
            "ARRANGEMENT", feeAmount, ref, facility.getTenantId());

        auditService.audit(facility.getTenantId(), "FACILITY", facility.getId(), "FEE_CHARGED",
            null, Map.of("feeType", "ARRANGEMENT", "amount", feeAmount, "reference", ref), null);

        log.info("Charged arrangement fee {} for facility {}", feeAmount, facility.getId());
    }

    private CreditBandConfig resolveBandConfig(String tenantId, String band) {
        // Try tenant-specific first, then system-level
        return bandConfigRepo.findByTenantIdAndBandAndStatus(tenantId, band, "ACTIVE")
            .or(() -> bandConfigRepo.findByTenantIdAndBandAndStatus("system", band, "ACTIVE"))
            .orElse(null);
    }

    @Transactional(readOnly = true)
    public OverdraftFacilityResponse getFacility(UUID walletId, String tenantId) {
        walletRepo.findByTenantIdAndId(tenantId, walletId)
            .orElseThrow(() -> new ResourceNotFoundException("Wallet not found: " + walletId));
        OverdraftFacility facility = facilityRepo.findTopByWalletIdOrderByCreatedAtDesc(walletId)
            .orElseThrow(() -> new ResourceNotFoundException("No overdraft facility for wallet: " + walletId));
        return toFacilityResponse(facility);
    }

    public OverdraftFacilityResponse suspendFacility(UUID walletId, String tenantId) {
        CustomerWallet wallet = walletRepo.findByTenantIdAndId(tenantId, walletId)
            .orElseThrow(() -> new ResourceNotFoundException("Wallet not found: " + walletId));
        OverdraftFacility facility = facilityRepo.findTopByWalletIdOrderByCreatedAtDesc(walletId)
            .orElseThrow(() -> new ResourceNotFoundException("No overdraft facility for wallet: " + walletId));
        if (!"ACTIVE".equals(facility.getStatus())) {
            throw new BusinessException("Facility is not active");
        }

        String previousStatus = facility.getStatus();
        facility.setStatus("SUSPENDED");
        facilityRepo.save(facility);

        // Recalculate available balance — remove overdraft headroom
        wallet.setAvailableBalance(wallet.getCurrentBalance());
        walletRepo.save(wallet);

        eventPublisher.publishOverdraftSuspended(walletId, wallet.getCustomerId(), tenantId);

        auditService.audit(tenantId, "FACILITY", facility.getId(), "SUSPENDED",
            Map.of("status", previousStatus),
            Map.of("status", "SUSPENDED"),
            null);

        return toFacilityResponse(facility);
    }

    @Transactional(readOnly = true)
    public List<InterestChargeResponse> getCharges(UUID walletId, String tenantId) {
        walletRepo.findByTenantIdAndId(tenantId, walletId)
            .orElseThrow(() -> new ResourceNotFoundException("Wallet not found: " + walletId));
        OverdraftFacility facility = facilityRepo.findTopByWalletIdOrderByCreatedAtDesc(walletId)
            .orElseThrow(() -> new ResourceNotFoundException("No overdraft facility for wallet: " + walletId));
        return chargeRepo.findByFacilityIdOrderByChargeDateDesc(facility.getId()).stream()
            .map(c -> {
                InterestChargeResponse r = new InterestChargeResponse();
                r.setId(c.getId());
                r.setFacilityId(c.getFacilityId());
                r.setChargeDate(c.getChargeDate());
                r.setDrawnAmount(c.getDrawnAmount());
                r.setDailyRate(c.getDailyRate());
                r.setInterestCharged(c.getInterestCharged());
                r.setReference(c.getReference());
                r.setCreatedAt(c.getCreatedAt());
                return r;
            })
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public OverdraftSummaryResponse getSummary(String tenantId) {
        List<OverdraftFacility> facilities = facilityRepo.findByTenantId(tenantId);

        long total = facilities.size();
        long active = facilities.stream().filter(f -> "ACTIVE".equals(f.getStatus())).count();
        BigDecimal totalLimit = facilities.stream().map(OverdraftFacility::getApprovedLimit)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalDrawn = facilities.stream().map(OverdraftFacility::getDrawnAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Long> byBand = facilities.stream()
            .collect(Collectors.groupingBy(OverdraftFacility::getCreditBand, Collectors.counting()));
        Map<String, BigDecimal> drawnByBand = facilities.stream()
            .collect(Collectors.groupingBy(OverdraftFacility::getCreditBand,
                Collectors.reducing(BigDecimal.ZERO, OverdraftFacility::getDrawnAmount, BigDecimal::add)));

        OverdraftSummaryResponse summary = new OverdraftSummaryResponse();
        summary.setTotalFacilities(total);
        summary.setActiveFacilities(active);
        summary.setTotalApprovedLimit(totalLimit);
        summary.setTotalDrawnAmount(totalDrawn);
        summary.setTotalAvailableOverdraft(totalLimit.subtract(totalDrawn));
        summary.setFacilitiesByBand(byBand);
        summary.setDrawnByBand(drawnByBand);
        return summary;
    }

    private OverdraftFacilityResponse toFacilityResponse(OverdraftFacility f) {
        OverdraftFacilityResponse r = new OverdraftFacilityResponse();
        r.setId(f.getId());
        r.setTenantId(f.getTenantId());
        r.setWalletId(f.getWalletId());
        r.setCustomerId(f.getCustomerId());
        r.setCreditScore(f.getCreditScore());
        r.setCreditBand(f.getCreditBand());
        r.setApprovedLimit(f.getApprovedLimit());
        r.setDrawnAmount(f.getDrawnAmount());
        r.setAvailableOverdraft(f.getApprovedLimit().subtract(f.getDrawnAmount()));
        r.setDrawnPrincipal(f.getDrawnPrincipal());
        r.setAccruedInterest(f.getAccruedInterest());
        r.setInterestRate(f.getInterestRate());
        r.setStatus(f.getStatus());
        r.setDpd(f.getDpd());
        r.setNplStage(f.getNplStage());
        r.setAppliedAt(f.getAppliedAt());
        r.setApprovedAt(f.getApprovedAt());
        r.setCreatedAt(f.getCreatedAt());
        return r;
    }
}
