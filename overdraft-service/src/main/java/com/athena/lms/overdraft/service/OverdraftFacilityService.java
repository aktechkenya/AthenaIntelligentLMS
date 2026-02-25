package com.athena.lms.overdraft.service;

import com.athena.lms.common.exception.BusinessException;
import com.athena.lms.common.exception.ResourceNotFoundException;
import com.athena.lms.overdraft.client.ScoringClient;
import com.athena.lms.overdraft.dto.response.InterestChargeResponse;
import com.athena.lms.overdraft.dto.response.OverdraftFacilityResponse;
import com.athena.lms.overdraft.dto.response.OverdraftSummaryResponse;
import com.athena.lms.overdraft.entity.CustomerWallet;
import com.athena.lms.overdraft.entity.OverdraftFacility;
import com.athena.lms.overdraft.event.OverdraftEventPublisher;
import com.athena.lms.overdraft.repository.CustomerWalletRepository;
import com.athena.lms.overdraft.repository.OverdraftFacilityRepository;
import com.athena.lms.overdraft.repository.OverdraftInterestChargeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
    private final ScoringClient scoringClient;
    private final OverdraftEventPublisher eventPublisher;

    public OverdraftFacilityResponse applyForOverdraft(UUID walletId, String tenantId) {
        CustomerWallet wallet = walletRepo.findByTenantIdAndId(tenantId, walletId)
            .orElseThrow(() -> new ResourceNotFoundException("Wallet not found: " + walletId));

        ScoringClient.CreditScoreResult scoreResult = scoringClient.getLatestScore(wallet.getCustomerId());
        String band = scoreResult.band();
        int score = scoreResult.score();

        BigDecimal limit = BAND_LIMITS.getOrDefault(band, new BigDecimal("5000"));
        BigDecimal rate = BAND_RATES.getOrDefault(band, new BigDecimal("0.3000"));

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
        if (facility.getDrawnAmount() == null) facility.setDrawnAmount(BigDecimal.ZERO);

        OverdraftFacility saved = facilityRepo.save(facility);

        BigDecimal overdraftHeadroom = limit.subtract(saved.getDrawnAmount());
        wallet.setAvailableBalance(wallet.getCurrentBalance().add(overdraftHeadroom));
        walletRepo.save(wallet);

        eventPublisher.publishOverdraftApplied(walletId, wallet.getCustomerId(), band, limit, tenantId);
        log.info("Overdraft applied for wallet {} customer {} band {} limit {}", walletId, wallet.getCustomerId(), band, limit);
        return toFacilityResponse(saved);
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
        walletRepo.findByTenantIdAndId(tenantId, walletId)
            .orElseThrow(() -> new ResourceNotFoundException("Wallet not found: " + walletId));
        OverdraftFacility facility = facilityRepo.findTopByWalletIdOrderByCreatedAtDesc(walletId)
            .orElseThrow(() -> new ResourceNotFoundException("No overdraft facility for wallet: " + walletId));
        if (!"ACTIVE".equals(facility.getStatus())) {
            throw new BusinessException("Facility is not active");
        }
        facility.setStatus("SUSPENDED");
        facilityRepo.save(facility);
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
        r.setInterestRate(f.getInterestRate());
        r.setStatus(f.getStatus());
        r.setAppliedAt(f.getAppliedAt());
        r.setApprovedAt(f.getApprovedAt());
        r.setCreatedAt(f.getCreatedAt());
        return r;
    }
}
