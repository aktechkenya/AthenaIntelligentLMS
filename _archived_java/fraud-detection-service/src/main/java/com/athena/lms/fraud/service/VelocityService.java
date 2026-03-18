package com.athena.lms.fraud.service;

import com.athena.lms.fraud.entity.VelocityCounter;
import com.athena.lms.fraud.repository.VelocityCounterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class VelocityService {

    private final VelocityCounterRepository counterRepository;

    @Transactional
    public void increment(String tenantId, String customerId, String counterType,
                          BigDecimal amount, int windowMinutes) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime start;
        if (windowMinutes < 60) {
            int minuteBucket = (now.getMinute() / windowMinutes) * windowMinutes;
            start = now.truncatedTo(ChronoUnit.HOURS).plusMinutes(minuteBucket);
        } else if (windowMinutes >= 1440) {
            start = now.truncatedTo(ChronoUnit.DAYS);
        } else {
            start = now.truncatedTo(ChronoUnit.HOURS);
        }
        final OffsetDateTime windowStart = start;
        final OffsetDateTime windowEnd = windowStart.plusMinutes(windowMinutes);

        VelocityCounter counter = counterRepository
                .findByTenantIdAndCustomerIdAndCounterTypeAndWindowStart(
                        tenantId, customerId, counterType, windowStart)
                .orElseGet(() -> VelocityCounter.builder()
                        .tenantId(tenantId)
                        .customerId(customerId)
                        .counterType(counterType)
                        .windowStart(windowStart)
                        .windowEnd(windowEnd)
                        .count(0)
                        .totalAmount(BigDecimal.ZERO)
                        .build());

        counter.setCount(counter.getCount() + 1);
        if (amount != null) {
            counter.setTotalAmount(counter.getTotalAmount().add(amount));
        }
        counterRepository.save(counter);
    }

    @Transactional(readOnly = true)
    public int getCount(String tenantId, String customerId, String counterType, int windowMinutes) {
        OffsetDateTime since = OffsetDateTime.now().minusMinutes(windowMinutes);
        return counterRepository.sumCountSince(tenantId, customerId, counterType, since);
    }

    @Transactional(readOnly = true)
    public BigDecimal getTotalAmount(String tenantId, String customerId, String counterType, int windowMinutes) {
        OffsetDateTime since = OffsetDateTime.now().minusMinutes(windowMinutes);
        return counterRepository.sumAmountSince(tenantId, customerId, counterType, since);
    }
}
