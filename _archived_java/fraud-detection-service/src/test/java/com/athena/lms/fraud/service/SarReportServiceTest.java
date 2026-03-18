package com.athena.lms.fraud.service;

import com.athena.lms.fraud.dto.request.CreateSarReportRequest;
import com.athena.lms.fraud.dto.request.UpdateSarReportRequest;
import com.athena.lms.fraud.dto.response.SarReportResponse;
import com.athena.lms.fraud.entity.FraudAlert;
import com.athena.lms.fraud.entity.FraudCase;
import com.athena.lms.fraud.entity.SarReport;
import com.athena.lms.fraud.enums.*;
import com.athena.lms.fraud.event.FraudEventPublisher;
import com.athena.lms.fraud.repository.FraudAlertRepository;
import com.athena.lms.fraud.repository.FraudCaseRepository;
import com.athena.lms.fraud.repository.SarReportRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SarReportServiceTest {

    @Mock private SarReportRepository sarReportRepository;
    @Mock private FraudCaseRepository caseRepository;
    @Mock private FraudAlertRepository alertRepository;
    @Mock private CaseManagementService caseManagementService;
    @Mock private FraudEventPublisher eventPublisher;

    @InjectMocks private SarReportService service;

    private static final String TENANT = "test-tenant";

    @Nested
    @DisplayName("SAR Report Creation")
    class CreationTests {

        @Test
        @DisplayName("creates SAR report with auto-generated number")
        void createSarReport() {
            when(sarReportRepository.findMaxReportNumber(TENANT)).thenReturn(5);
            when(sarReportRepository.save(any())).thenAnswer(inv -> {
                SarReport r = inv.getArgument(0);
                r.setId(UUID.randomUUID());
                return r;
            });

            CreateSarReportRequest req = new CreateSarReportRequest();
            req.setReportType("SAR");
            req.setSubjectCustomerId("CUST-100");
            req.setSubjectName("John Doe");
            req.setSubjectNationalId("ID-12345");
            req.setNarrative("Suspicious structuring activity detected");
            req.setSuspiciousAmount(new BigDecimal("500000"));
            req.setPreparedBy("analyst-1");

            SarReportResponse result = service.createReport(req, TENANT);

            assertThat(result.getReportNumber()).isEqualTo("SAR-00006");
            assertThat(result.getReportType()).isEqualTo(SarReportType.SAR);
            assertThat(result.getStatus()).isEqualTo(SarStatus.DRAFT);
            assertThat(result.getSubjectName()).isEqualTo("John Doe");
            assertThat(result.getSuspiciousAmount()).isEqualByComparingTo(new BigDecimal("500000"));
            assertThat(result.getFilingDeadline()).isNotNull();
            assertThat(result.getRegulator()).isEqualTo("FRC Kenya");

            verify(sarReportRepository).save(any());
            verify(caseManagementService).audit(eq(TENANT), eq("SAR_CREATED"), eq("SAR_REPORT"),
                    any(), eq("analyst-1"), contains("SAR-00006"), isNull());
        }
    }

    @Nested
    @DisplayName("SAR Report Updates")
    class UpdateTests {

        @Test
        @DisplayName("updates SAR status to FILED with filing details")
        void updateToFiled() {
            UUID reportId = UUID.randomUUID();
            SarReport existing = SarReport.builder()
                    .id(reportId).tenantId(TENANT).reportNumber("SAR-00001")
                    .reportType(SarReportType.SAR).status(SarStatus.APPROVED)
                    .subjectName("John Doe").alertIds(new HashSet<>())
                    .build();

            when(sarReportRepository.findById(reportId)).thenReturn(Optional.of(existing));
            when(sarReportRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            UpdateSarReportRequest req = new UpdateSarReportRequest();
            req.setStatus("FILED");
            req.setFiledBy("compliance-officer");
            req.setFilingReference("FRC-2026-001234");

            SarReportResponse result = service.updateReport(reportId, req, TENANT);

            assertThat(result.getStatus()).isEqualTo(SarStatus.FILED);
            assertThat(result.getFiledBy()).isEqualTo("compliance-officer");
            assertThat(result.getFilingReference()).isEqualTo("FRC-2026-001234");
            assertThat(result.getFiledAt()).isNotNull();

            verify(caseManagementService).audit(eq(TENANT), eq("SAR_UPDATED"), eq("SAR_REPORT"),
                    eq(reportId), eq("compliance-officer"), anyString(), any());
        }

        @Test
        @DisplayName("rejects update to already-FILED report")
        void rejectUpdateToFiledReport() {
            UUID reportId = UUID.randomUUID();
            SarReport existing = SarReport.builder()
                    .id(reportId).tenantId(TENANT).reportNumber("SAR-00002")
                    .reportType(SarReportType.SAR).status(SarStatus.FILED)
                    .filedAt(OffsetDateTime.now()).filedBy("officer")
                    .alertIds(new HashSet<>())
                    .build();

            when(sarReportRepository.findById(reportId)).thenReturn(Optional.of(existing));

            UpdateSarReportRequest req = new UpdateSarReportRequest();
            req.setNarrative("Trying to change narrative");

            assertThatThrownBy(() -> service.updateReport(reportId, req, TENANT))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("FILED");
        }
    }

    @Nested
    @DisplayName("Generate SAR from Case")
    class GenerateFromCaseTests {

        @Test
        @DisplayName("generates SAR from existing case with linked alerts")
        void generateFromCase() {
            UUID caseId = UUID.randomUUID();
            UUID alertId1 = UUID.randomUUID();
            UUID alertId2 = UUID.randomUUID();

            FraudCase fraudCase = FraudCase.builder()
                    .id(caseId).tenantId(TENANT).caseNumber("FRD-00010")
                    .title("Structuring ring").description("Multiple small deposits")
                    .customerId("CUST-200")
                    .status(CaseStatus.INVESTIGATING)
                    .priority(AlertSeverity.HIGH)
                    .alertIds(Set.of(alertId1, alertId2))
                    .build();

            FraudAlert alert1 = FraudAlert.builder()
                    .id(alertId1).tenantId(TENANT).alertType(AlertType.STRUCTURING)
                    .description("Structuring detected").triggerAmount(new BigDecimal("50000"))
                    .subjectType("CUSTOMER").subjectId("CUST-200")
                    .createdAt(OffsetDateTime.now().minusDays(5))
                    .build();

            FraudAlert alert2 = FraudAlert.builder()
                    .id(alertId2).tenantId(TENANT).alertType(AlertType.LARGE_TRANSACTION)
                    .description("Large transaction").triggerAmount(new BigDecimal("200000"))
                    .subjectType("CUSTOMER").subjectId("CUST-200")
                    .createdAt(OffsetDateTime.now().minusDays(2))
                    .build();

            when(caseRepository.findById(caseId)).thenReturn(Optional.of(fraudCase));
            when(alertRepository.findById(alertId1)).thenReturn(Optional.of(alert1));
            when(alertRepository.findById(alertId2)).thenReturn(Optional.of(alert2));
            when(sarReportRepository.findMaxReportNumber(TENANT)).thenReturn(0);
            when(sarReportRepository.save(any())).thenAnswer(inv -> {
                SarReport r = inv.getArgument(0);
                r.setId(UUID.randomUUID());
                return r;
            });

            SarReportResponse result = service.generateFromCase(caseId, TENANT);

            assertThat(result.getReportNumber()).isEqualTo("SAR-00001");
            assertThat(result.getReportType()).isEqualTo(SarReportType.SAR);
            assertThat(result.getSubjectCustomerId()).isEqualTo("CUST-200");
            assertThat(result.getCaseId()).isEqualTo(caseId);
            assertThat(result.getSuspiciousAmount()).isEqualByComparingTo(new BigDecimal("250000"));
            assertThat(result.getNarrative()).contains("FRD-00010");
            assertThat(result.getNarrative()).contains("Structuring ring");
            assertThat(result.getAlertIds()).containsExactlyInAnyOrder(alertId1, alertId2);
            assertThat(result.getFilingDeadline()).isNotNull();

            verify(caseManagementService).audit(eq(TENANT), eq("SAR_GENERATED_FROM_CASE"),
                    eq("SAR_REPORT"), any(), eq("system"), contains("FRD-00010"), isNull());
        }
    }

    @Nested
    @DisplayName("List Reports")
    class ListTests {

        @Test
        @DisplayName("lists reports by status")
        void listByStatus() {
            SarReport report = SarReport.builder()
                    .id(UUID.randomUUID()).tenantId(TENANT).reportNumber("SAR-00001")
                    .reportType(SarReportType.SAR).status(SarStatus.DRAFT)
                    .alertIds(new HashSet<>())
                    .build();

            Pageable pageable = PageRequest.of(0, 20);
            Page<SarReport> page = new PageImpl<>(List.of(report), pageable, 1);
            when(sarReportRepository.findByTenantIdAndStatus(TENANT, SarStatus.DRAFT, pageable))
                    .thenReturn(page);

            var result = service.listReports(TENANT, SarStatus.DRAFT, null, pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getReportNumber()).isEqualTo("SAR-00001");
            assertThat(result.getContent().get(0).getStatus()).isEqualTo(SarStatus.DRAFT);
        }
    }
}
