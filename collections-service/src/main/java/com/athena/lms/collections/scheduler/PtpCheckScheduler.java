package com.athena.lms.collections.scheduler;

import com.athena.lms.collections.entity.PromiseToPay;
import com.athena.lms.collections.enums.PtpStatus;
import com.athena.lms.collections.repository.PtpRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class PtpCheckScheduler {

    private final PtpRepository ptpRepository;

    @Scheduled(cron = "0 0 6 * * *")
    @Transactional
    public void markExpiredPtpsAsBroken() {
        LocalDate today = LocalDate.now();
        List<PromiseToPay> expiredPtps = ptpRepository.findByStatusAndPromiseDateBefore(PtpStatus.PENDING, today);

        if (expiredPtps.isEmpty()) {
            log.debug("PTP check: no expired promises found");
            return;
        }

        OffsetDateTime now = OffsetDateTime.now();
        for (PromiseToPay ptp : expiredPtps) {
            ptp.setStatus(PtpStatus.BROKEN);
            ptp.setBrokenAt(now);
        }
        ptpRepository.saveAll(expiredPtps);
        log.info("PTP check: marked {} expired promises as BROKEN", expiredPtps.size());
    }
}
