package com.athena.lms.fraud.service;

import com.athena.lms.fraud.entity.VelocityCounter;
import com.athena.lms.fraud.repository.VelocityCounterRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VelocityServiceTest {

    @Mock private VelocityCounterRepository counterRepository;
    @InjectMocks private VelocityService velocityService;

    private static final String TENANT = "test-tenant";
    private static final String CUSTOMER = "CUST-1";

    @Test
    @DisplayName("increment creates new counter when none exists")
    void incrementCreatesNewCounter() {
        when(counterRepository.findByTenantIdAndCustomerIdAndCounterTypeAndWindowStart(
            anyString(), anyString(), anyString(), any()
        )).thenReturn(Optional.empty());
        when(counterRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        velocityService.increment(TENANT, CUSTOMER, "TXN_COUNT", new BigDecimal("50000"), 60);

        ArgumentCaptor<VelocityCounter> captor = ArgumentCaptor.forClass(VelocityCounter.class);
        verify(counterRepository).save(captor.capture());

        VelocityCounter saved = captor.getValue();
        assertThat(saved.getTenantId()).isEqualTo(TENANT);
        assertThat(saved.getCustomerId()).isEqualTo(CUSTOMER);
        assertThat(saved.getCounterType()).isEqualTo("TXN_COUNT");
        assertThat(saved.getCount()).isEqualTo(1);
        assertThat(saved.getTotalAmount()).isEqualByComparingTo(new BigDecimal("50000"));
    }

    @Test
    @DisplayName("increment updates existing counter in same window")
    void incrementUpdatesExisting() {
        VelocityCounter existing = VelocityCounter.builder()
            .tenantId(TENANT).customerId(CUSTOMER).counterType("TXN_COUNT")
            .windowStart(OffsetDateTime.now().minusMinutes(30))
            .windowEnd(OffsetDateTime.now().plusMinutes(30))
            .count(3).totalAmount(new BigDecimal("100000"))
            .build();

        when(counterRepository.findByTenantIdAndCustomerIdAndCounterTypeAndWindowStart(
            anyString(), anyString(), anyString(), any()
        )).thenReturn(Optional.of(existing));
        when(counterRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        velocityService.increment(TENANT, CUSTOMER, "TXN_COUNT", new BigDecimal("25000"), 60);

        verify(counterRepository).save(argThat(c ->
            c.getCount() == 4 &&
            c.getTotalAmount().compareTo(new BigDecimal("125000")) == 0
        ));
    }

    @Test
    @DisplayName("getCount returns sum of counts from matching windows")
    void getCountReturnsSummedCount() {
        when(counterRepository.sumCountSince(eq(TENANT), eq(CUSTOMER), eq("TXN_COUNT"), any()))
            .thenReturn(15);

        int count = velocityService.getCount(TENANT, CUSTOMER, "TXN_COUNT", 60);

        assertThat(count).isEqualTo(15);
    }

    @Test
    @DisplayName("getCount returns 0 when no counters exist")
    void getCountReturnsZero() {
        when(counterRepository.sumCountSince(eq(TENANT), eq(CUSTOMER), eq("TXN_COUNT"), any()))
            .thenReturn(0);

        int count = velocityService.getCount(TENANT, CUSTOMER, "TXN_COUNT", 60);

        assertThat(count).isEqualTo(0);
    }

    @Test
    @DisplayName("getTotalAmount returns summed amount for window")
    void getTotalAmountWorks() {
        when(counterRepository.sumAmountSince(eq(TENANT), eq(CUSTOMER), eq("TXN_AMOUNT"), any()))
            .thenReturn(new BigDecimal("500000"));

        BigDecimal total = velocityService.getTotalAmount(TENANT, CUSTOMER, "TXN_AMOUNT", 1440);

        assertThat(total).isEqualByComparingTo(new BigDecimal("500000"));
    }

    @Test
    @DisplayName("handles zero amount gracefully")
    void zeroAmountHandled() {
        when(counterRepository.findByTenantIdAndCustomerIdAndCounterTypeAndWindowStart(
            anyString(), anyString(), anyString(), any()
        )).thenReturn(Optional.empty());
        when(counterRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        velocityService.increment(TENANT, CUSTOMER, "TXN_COUNT", BigDecimal.ZERO, 60);

        verify(counterRepository).save(argThat(c ->
            c.getCount() == 1 && c.getTotalAmount().compareTo(BigDecimal.ZERO) == 0
        ));
    }
}
