package com.athena.lms.fraud.service;

import com.athena.lms.fraud.dto.response.BatchScreeningResult;
import com.athena.lms.fraud.entity.CustomerRiskProfile;
import com.athena.lms.fraud.entity.WatchlistEntry;
import com.athena.lms.fraud.enums.WatchlistType;
import com.athena.lms.fraud.repository.CustomerRiskProfileRepository;
import com.athena.lms.fraud.repository.FraudAlertRepository;
import com.athena.lms.fraud.repository.WatchlistRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BatchScreeningServiceTest {

    @Mock
    WatchlistRepository watchlistRepository;

    @Mock
    CustomerRiskProfileRepository customerRiskProfileRepository;

    @Mock
    FraudAlertRepository fraudAlertRepository;

    @Mock
    CaseManagementService caseManagementService;

    @InjectMocks
    BatchScreeningService service;

    private static final String TENANT = "test-tenant";

    @Test
    @DisplayName("screenAllCustomers with matches creates alerts")
    void screenAllCustomers_withMatches_createsAlerts() {
        WatchlistEntry entry = WatchlistEntry.builder()
                .id(UUID.randomUUID())
                .tenantId(TENANT)
                .name("John Doe")
                .nationalId("ID-123")
                .listType(WatchlistType.INTERNAL_BLACKLIST)
                .entryType("INDIVIDUAL")
                .reason("Suspected fraud")
                .active(true)
                .build();

        CustomerRiskProfile profile = CustomerRiskProfile.builder()
                .id(UUID.randomUUID())
                .tenantId(TENANT)
                .customerId("John Doe") // matches watchlist name
                .build();

        when(watchlistRepository.findAllByTenantIdAndActive(TENANT, true)).thenReturn(List.of(entry));
        when(customerRiskProfileRepository.findAllByTenantId(TENANT)).thenReturn(List.of(profile));
        when(fraudAlertRepository.countOpenAlertsByCustomer(eq(TENANT), anyString())).thenReturn(0L);
        when(fraudAlertRepository.countRecentAlertsByRule(eq(TENANT), anyString(), eq("WATCHLIST_SCREEN"), any()))
                .thenReturn(0L);
        when(fraudAlertRepository.save(any())).thenAnswer(inv -> {
            var alert = inv.getArgument(0);
            return alert;
        });

        BatchScreeningResult result = service.screenAllCustomers(TENANT);

        assertThat(result.getCustomersScreened()).isEqualTo(1);
        assertThat(result.getMatchesFound()).isEqualTo(1);
        assertThat(result.getAlertsCreated()).isEqualTo(1);
        assertThat(result.getMatchedCustomerIds()).contains("John Doe");

        verify(fraudAlertRepository).save(any());
        verify(caseManagementService).audit(eq(TENANT), eq("WATCHLIST_MATCH_FOUND"),
                eq("ALERT"), any(), eq("system"), argThat(s -> s.contains("John Doe")), isNull());
    }

    @Test
    @DisplayName("screenAllCustomers with no matches creates zero alerts")
    void screenAllCustomers_noMatches_zeroAlerts() {
        WatchlistEntry entry = WatchlistEntry.builder()
                .id(UUID.randomUUID())
                .tenantId(TENANT)
                .name("Bad Actor")
                .listType(WatchlistType.INTERNAL_BLACKLIST)
                .entryType("INDIVIDUAL")
                .active(true)
                .build();

        CustomerRiskProfile profile = CustomerRiskProfile.builder()
                .id(UUID.randomUUID())
                .tenantId(TENANT)
                .customerId("Good Customer")
                .build();

        when(watchlistRepository.findAllByTenantIdAndActive(TENANT, true)).thenReturn(List.of(entry));
        when(customerRiskProfileRepository.findAllByTenantId(TENANT)).thenReturn(List.of(profile));

        BatchScreeningResult result = service.screenAllCustomers(TENANT);

        assertThat(result.getCustomersScreened()).isEqualTo(1);
        assertThat(result.getMatchesFound()).isZero();
        assertThat(result.getAlertsCreated()).isZero();
        assertThat(result.getMatchedCustomerIds()).isEmpty();

        verify(fraudAlertRepository, never()).save(any());
    }

    @Test
    @DisplayName("screenCustomer returns matching watchlist entries")
    void screenCustomer_returnsMatches() {
        WatchlistEntry match = WatchlistEntry.builder()
                .id(UUID.randomUUID())
                .tenantId(TENANT)
                .name("Jane Doe")
                .nationalId("ID-456")
                .listType(WatchlistType.SANCTIONS)
                .entryType("INDIVIDUAL")
                .active(true)
                .build();

        when(watchlistRepository.findMatches(eq(TENANT), eq("ID-456"), eq("Jane Doe"), eq("")))
                .thenReturn(List.of(match));

        List<WatchlistEntry> results = service.screenCustomer(TENANT, "CUST-1", "Jane Doe", "ID-456", null);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getName()).isEqualTo("Jane Doe");
    }

    @Test
    @DisplayName("screenAllCustomers skips alert creation when recent alert already exists")
    void screenAllCustomers_skipsExistingAlerts() {
        WatchlistEntry entry = WatchlistEntry.builder()
                .id(UUID.randomUUID())
                .tenantId(TENANT)
                .name("John Doe")
                .listType(WatchlistType.INTERNAL_BLACKLIST)
                .entryType("INDIVIDUAL")
                .active(true)
                .build();

        CustomerRiskProfile profile = CustomerRiskProfile.builder()
                .id(UUID.randomUUID())
                .tenantId(TENANT)
                .customerId("John Doe")
                .build();

        when(watchlistRepository.findAllByTenantIdAndActive(TENANT, true)).thenReturn(List.of(entry));
        when(customerRiskProfileRepository.findAllByTenantId(TENANT)).thenReturn(List.of(profile));
        when(fraudAlertRepository.countRecentAlertsByRule(eq(TENANT), anyString(), eq("WATCHLIST_SCREEN"), any()))
                .thenReturn(1L); // Already has a recent alert

        BatchScreeningResult result = service.screenAllCustomers(TENANT);

        assertThat(result.getMatchesFound()).isEqualTo(1);
        assertThat(result.getAlertsCreated()).isZero();

        verify(fraudAlertRepository, never()).save(any());
    }
}
