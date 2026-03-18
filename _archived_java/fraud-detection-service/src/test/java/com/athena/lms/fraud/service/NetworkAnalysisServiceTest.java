package com.athena.lms.fraud.service;

import com.athena.lms.fraud.dto.response.NetworkNodeResponse;
import com.athena.lms.fraud.entity.CustomerRiskProfile;
import com.athena.lms.fraud.entity.NetworkLink;
import com.athena.lms.fraud.enums.RiskLevel;
import com.athena.lms.fraud.repository.CustomerRiskProfileRepository;
import com.athena.lms.fraud.repository.NetworkLinkRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NetworkAnalysisServiceTest {

    @Mock private NetworkLinkRepository linkRepository;
    @Mock private CustomerRiskProfileRepository riskProfileRepository;

    @InjectMocks private NetworkAnalysisService service;

    private static final String TENANT = "test-tenant";

    @Nested
    @DisplayName("Record Links")
    class RecordLinkTests {

        @Test
        @DisplayName("creates new link between customers")
        void recordNewLink() {
            when(linkRepository.existsByTenantIdAndCustomerIdAAndCustomerIdBAndLinkType(
                anyString(), anyString(), anyString(), anyString())).thenReturn(false);
            when(linkRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.recordLink(TENANT, "CUST-2", "CUST-1", "SHARED_PHONE", "+254700111222");

            verify(linkRepository).save(argThat(link ->
                link.getCustomerIdA().equals("CUST-1") &&
                link.getCustomerIdB().equals("CUST-2") &&
                link.getLinkType().equals("SHARED_PHONE") &&
                link.getStrength() == 1
            ));
        }

        @Test
        @DisplayName("normalizes customer order (A < B)")
        void normalizesOrder() {
            when(linkRepository.existsByTenantIdAndCustomerIdAAndCustomerIdBAndLinkType(
                anyString(), anyString(), anyString(), anyString())).thenReturn(false);
            when(linkRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.recordLink(TENANT, "CUST-B", "CUST-A", "SHARED_IP", "192.168.1.1");

            verify(linkRepository).save(argThat(link ->
                link.getCustomerIdA().equals("CUST-A") &&
                link.getCustomerIdB().equals("CUST-B")
            ));
        }
    }

    @Nested
    @DisplayName("Customer Network")
    class NetworkQueryTests {

        @Test
        @DisplayName("returns network graph for customer")
        void getNetwork() {
            NetworkLink link1 = NetworkLink.builder()
                .tenantId(TENANT).customerIdA("CUST-1").customerIdB("CUST-2")
                .linkType("SHARED_PHONE").linkValue("+254700111222")
                .strength(3).flagged(false).build();
            NetworkLink link2 = NetworkLink.builder()
                .tenantId(TENANT).customerIdA("CUST-1").customerIdB("CUST-3")
                .linkType("SHARED_IP").linkValue("10.0.0.1")
                .strength(1).flagged(true).build();

            when(linkRepository.findByCustomer(TENANT, "CUST-1")).thenReturn(List.of(link1, link2));
            when(riskProfileRepository.findByTenantIdAndCustomerId(TENANT, "CUST-1"))
                .thenReturn(Optional.of(CustomerRiskProfile.builder()
                    .tenantId(TENANT).customerId("CUST-1")
                    .riskLevel(RiskLevel.HIGH).riskScore(new BigDecimal("0.65"))
                    .build()));

            NetworkNodeResponse result = service.getCustomerNetwork(TENANT, "CUST-1");

            assertThat(result.getCustomerId()).isEqualTo("CUST-1");
            assertThat(result.getRiskLevel()).isEqualTo("HIGH");
            assertThat(result.getLinkCount()).isEqualTo(2);
            assertThat(result.getLinks()).hasSize(2);
            assertThat(result.getLinks().get(0).getLinkedCustomerId()).isEqualTo("CUST-2");
            assertThat(result.getLinks().get(1).getLinkedCustomerId()).isEqualTo("CUST-3");
            assertThat(result.getLinks().get(1).isFlagged()).isTrue();
        }

        @Test
        @DisplayName("returns LOW risk for unknown customer")
        void unknownCustomerDefaultRisk() {
            when(linkRepository.findByCustomer(TENANT, "UNKNOWN")).thenReturn(List.of());
            when(riskProfileRepository.findByTenantIdAndCustomerId(TENANT, "UNKNOWN"))
                .thenReturn(Optional.empty());

            NetworkNodeResponse result = service.getCustomerNetwork(TENANT, "UNKNOWN");

            assertThat(result.getRiskLevel()).isEqualTo("LOW");
            assertThat(result.getLinkCount()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Flag Links")
    class FlagTests {

        @Test
        @DisplayName("flags suspicious link")
        void flagLink() {
            UUID linkId = UUID.randomUUID();
            NetworkLink link = NetworkLink.builder()
                .id(linkId).tenantId(TENANT)
                .customerIdA("CUST-1").customerIdB("CUST-2")
                .linkType("SHARED_DEVICE").linkValue("device-abc")
                .flagged(false).build();

            when(linkRepository.findById(linkId)).thenReturn(Optional.of(link));
            when(linkRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.flagLink(TENANT, linkId);

            verify(linkRepository).save(argThat(l -> l.getFlagged()));
        }
    }
}
