package com.athena.lms.fraud.service;

import com.athena.lms.fraud.entity.FraudAlert;
import com.athena.lms.fraud.entity.NetworkLink;
import com.athena.lms.fraud.enums.AlertSeverity;
import com.athena.lms.fraud.enums.AlertType;
import com.athena.lms.fraud.event.FraudEventPublisher;
import com.athena.lms.fraud.repository.FraudAlertRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AutoActionServiceTest {

    @Mock private FraudAlertRepository alertRepository;
    @Mock private CaseManagementService caseManagementService;
    @Mock private NetworkAnalysisService networkAnalysisService;
    @Mock private FraudEventPublisher eventPublisher;

    @InjectMocks private AutoActionService service;

    private static final String TENANT = "test-tenant";

    @Nested
    @DisplayName("Auto-Block")
    class AutoBlockTests {

        @Test
        @DisplayName("triggers auto-block for CRITICAL watchlist alert")
        void autoBlockCriticalWatchlist() {
            FraudAlert alert = FraudAlert.builder()
                    .id(UUID.randomUUID()).tenantId(TENANT).customerId("CUST-1")
                    .severity(AlertSeverity.CRITICAL).alertType(AlertType.WATCHLIST_MATCH)
                    .subjectType("CUSTOMER").subjectId("CUST-1").description("Watchlist match")
                    .build();

            when(alertRepository.countOpenAlertsByCustomer(TENANT, "CUST-1")).thenReturn(1L);

            service.processAutoActions(TENANT, List.of(alert), Map.of());

            verify(caseManagementService).audit(eq(TENANT), eq("AUTO_BLOCK"), eq("ALERT"),
                    eq(alert.getId()), eq("system"), argThat(s -> s.contains("auto-blocked")), isNull());
        }

        @Test
        @DisplayName("triggers auto-block for CRITICAL structuring alert")
        void autoBlockCriticalStructuring() {
            assertThat(service.shouldAutoBlock(FraudAlert.builder()
                    .severity(AlertSeverity.CRITICAL).alertType(AlertType.STRUCTURING).build())).isTrue();
        }

        @Test
        @DisplayName("does not auto-block for HIGH severity")
        void noAutoBlockHigh() {
            assertThat(service.shouldAutoBlock(FraudAlert.builder()
                    .severity(AlertSeverity.HIGH).alertType(AlertType.WATCHLIST_MATCH).build())).isFalse();
        }

        @Test
        @DisplayName("does not auto-block for CRITICAL with non-watchlist type")
        void noAutoBlockWrongType() {
            assertThat(service.shouldAutoBlock(FraudAlert.builder()
                    .severity(AlertSeverity.CRITICAL).alertType(AlertType.LARGE_TRANSACTION).build())).isFalse();
        }
    }

    @Nested
    @DisplayName("Auto-Case Creation")
    class AutoCaseTests {

        @Test
        @DisplayName("creates case when customer has 3+ open alerts")
        void autoCreateCase() {
            FraudAlert alert = FraudAlert.builder()
                    .id(UUID.randomUUID()).tenantId(TENANT).customerId("CUST-1")
                    .severity(AlertSeverity.MEDIUM).alertType(AlertType.LARGE_TRANSACTION)
                    .subjectType("PAYMENT").subjectId("PAY-1").description("Large txn")
                    .build();

            when(alertRepository.countOpenAlertsByCustomer(TENANT, "CUST-1")).thenReturn(3L);
            when(caseManagementService.createCase(any(), eq(TENANT))).thenReturn(null);

            service.processAutoActions(TENANT, List.of(alert), Map.of());

            verify(caseManagementService).createCase(argThat(req ->
                    req.getCustomerId().equals("CUST-1") && req.getPriority().equals("HIGH")
            ), eq(TENANT));
        }

        @Test
        @DisplayName("does not create case when under threshold")
        void noAutoCreateCase() {
            FraudAlert alert = FraudAlert.builder()
                    .id(UUID.randomUUID()).tenantId(TENANT).customerId("CUST-1")
                    .severity(AlertSeverity.MEDIUM).alertType(AlertType.LARGE_TRANSACTION)
                    .subjectType("PAYMENT").subjectId("PAY-1").description("Large txn")
                    .build();

            when(alertRepository.countOpenAlertsByCustomer(TENANT, "CUST-1")).thenReturn(2L);

            service.processAutoActions(TENANT, List.of(alert), Map.of());

            verify(caseManagementService, never()).createCase(any(), any());
        }
    }

    @Nested
    @DisplayName("Network Link Detection")
    class NetworkLinkTests {

        @Test
        @DisplayName("detects shared phone between customers")
        void detectSharedPhone() {
            FraudAlert alert = FraudAlert.builder()
                    .id(UUID.randomUUID()).tenantId(TENANT).customerId("CUST-1")
                    .severity(AlertSeverity.MEDIUM).alertType(AlertType.LARGE_TRANSACTION)
                    .subjectType("PAYMENT").subjectId("PAY-1").description("test")
                    .build();

            NetworkLink existingLink = NetworkLink.builder()
                    .tenantId(TENANT).customerIdA("CUST-2").customerIdB("CUST-3")
                    .linkType("SHARED_PHONE").linkValue("+254700111222")
                    .build();

            when(networkAnalysisService.findByLinkValue(TENANT, "SHARED_PHONE", "+254700111222"))
                    .thenReturn(List.of(existingLink));

            Map<String, Object> eventData = Map.of("phone", "+254700111222");
            service.detectNetworkLinks(TENANT, List.of(alert), eventData);

            // Should create links to both CUST-2 and CUST-3
            verify(networkAnalysisService).recordLink(TENANT, "CUST-1", "CUST-2", "SHARED_PHONE", "+254700111222");
            verify(networkAnalysisService).recordLink(TENANT, "CUST-1", "CUST-3", "SHARED_PHONE", "+254700111222");
        }

        @Test
        @DisplayName("handles null event data gracefully")
        void nullEventData() {
            FraudAlert alert = FraudAlert.builder()
                    .id(UUID.randomUUID()).tenantId(TENANT).customerId("CUST-1")
                    .severity(AlertSeverity.MEDIUM).alertType(AlertType.LARGE_TRANSACTION)
                    .subjectType("PAYMENT").subjectId("PAY-1").description("test")
                    .build();

            service.detectNetworkLinks(TENANT, List.of(alert), null);

            verify(networkAnalysisService, never()).findByLinkValue(any(), any(), any());
        }

        @Test
        @DisplayName("handles multiple link attributes in single event")
        void multipleAttributes() {
            FraudAlert alert = FraudAlert.builder()
                    .id(UUID.randomUUID()).tenantId(TENANT).customerId("CUST-1")
                    .severity(AlertSeverity.MEDIUM).alertType(AlertType.LARGE_TRANSACTION)
                    .subjectType("PAYMENT").subjectId("PAY-1").description("test")
                    .build();

            when(networkAnalysisService.findByLinkValue(eq(TENANT), anyString(), anyString()))
                    .thenReturn(List.of());

            Map<String, Object> eventData = Map.of(
                    "phone", "+254700111222",
                    "deviceId", "device-abc",
                    "ipAddress", "10.0.0.1"
            );

            service.detectNetworkLinks(TENANT, List.of(alert), eventData);

            // Should check all three, but no recordLink since no existing links found
            verify(networkAnalysisService).findByLinkValue(TENANT, "SHARED_PHONE", "+254700111222");
            verify(networkAnalysisService).findByLinkValue(TENANT, "SHARED_DEVICE", "device-abc");
            verify(networkAnalysisService).findByLinkValue(TENANT, "SHARED_IP", "10.0.0.1");
            verify(networkAnalysisService, never()).recordLink(any(), any(), any(), any(), any());
        }
    }
}
