package com.athena.lms.overdraft.service;

import com.athena.lms.overdraft.entity.CustomerWallet;
import com.athena.lms.overdraft.entity.OverdraftFacility;
import com.athena.lms.overdraft.entity.OverdraftInterestCharge;
import com.athena.lms.overdraft.entity.WalletTransaction;
import com.athena.lms.overdraft.event.OverdraftEventPublisher;
import com.athena.lms.overdraft.repository.CustomerWalletRepository;
import com.athena.lms.overdraft.repository.OverdraftFacilityRepository;
import com.athena.lms.overdraft.repository.OverdraftInterestChargeRepository;
import com.athena.lms.overdraft.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class InterestAccrualService {

    private static final BigDecimal DAYS_PER_YEAR = new BigDecimal("365");

    private final OverdraftFacilityRepository facilityRepo;
    private final OverdraftInterestChargeRepository chargeRepo;
    private final CustomerWalletRepository walletRepo;
    private final WalletTransactionRepository txRepo;
    private final OverdraftEventPublisher eventPublisher;

    @Scheduled(cron = "0 1 0 * * *")
    @Transactional
    public void accrueInterest() {
        LocalDate today = LocalDate.now();
        log.info("Starting overdraft interest accrual for {}", today);

        List<OverdraftFacility> activeFacilities = facilityRepo
            .findByStatusAndDrawnAmountGreaterThan("ACTIVE", BigDecimal.ZERO);

        int processed = 0;
        for (OverdraftFacility facility : activeFacilities) {
            try {
                processInterest(facility, today);
                processed++;
            } catch (Exception e) {
                log.error("Failed to accrue interest for facility {}: {}", facility.getId(), e.getMessage());
            }
        }
        log.info("Interest accrual complete: {} facilities processed for {}", processed, today);
    }

    private void processInterest(OverdraftFacility facility, LocalDate today) {
        if (chargeRepo.existsByFacilityIdAndChargeDate(facility.getId(), today)) {
            log.info("Interest already charged for facility {} on {}", facility.getId(), today);
            return;
        }

        BigDecimal dailyRate = facility.getInterestRate().divide(DAYS_PER_YEAR, 8, RoundingMode.HALF_UP);
        BigDecimal interest = facility.getDrawnAmount().multiply(dailyRate).setScale(4, RoundingMode.HALF_UP);

        if (interest.compareTo(BigDecimal.ZERO) <= 0) return;

        CustomerWallet wallet = walletRepo.findById(facility.getWalletId()).orElse(null);
        if (wallet == null) {
            log.warn("Wallet {} not found for facility {}", facility.getWalletId(), facility.getId());
            return;
        }

        BigDecimal balanceBefore = wallet.getCurrentBalance();
        BigDecimal balanceAfter = balanceBefore.subtract(interest);
        wallet.setCurrentBalance(balanceAfter);

        facility.setDrawnAmount(facility.getDrawnAmount().add(interest));
        facilityRepo.save(facility);

        BigDecimal overdraftHeadroom = facility.getApprovedLimit().subtract(facility.getDrawnAmount());
        wallet.setAvailableBalance(balanceAfter.add(overdraftHeadroom));
        walletRepo.save(wallet);

        String ref = "INT-" + facility.getId().toString().substring(0, 8) + "-" + today.toString().replace("-", "");

        WalletTransaction tx = new WalletTransaction();
        tx.setTenantId(facility.getTenantId());
        tx.setWalletId(facility.getWalletId());
        tx.setTransactionType("INTEREST_CHARGE");
        tx.setAmount(interest);
        tx.setBalanceBefore(balanceBefore);
        tx.setBalanceAfter(balanceAfter);
        tx.setReference(ref);
        tx.setDescription("Overdraft interest charge for " + today);
        txRepo.save(tx);

        OverdraftInterestCharge charge = new OverdraftInterestCharge();
        charge.setTenantId(facility.getTenantId());
        charge.setFacilityId(facility.getId());
        charge.setChargeDate(today);
        charge.setDrawnAmount(facility.getDrawnAmount());
        charge.setDailyRate(dailyRate);
        charge.setInterestCharged(interest);
        charge.setReference(ref);
        chargeRepo.save(charge);

        eventPublisher.publishInterestCharged(facility.getWalletId(), facility.getCustomerId(), interest, facility.getTenantId());
        log.info("Charged interest {} on facility {} wallet {}", interest, facility.getId(), facility.getWalletId());
    }
}
