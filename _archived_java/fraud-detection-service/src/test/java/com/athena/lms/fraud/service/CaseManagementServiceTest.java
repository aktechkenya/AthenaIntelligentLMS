package com.athena.lms.fraud.service;

import com.athena.lms.fraud.dto.request.AddCaseNoteRequest;
import com.athena.lms.fraud.dto.request.CreateCaseRequest;
import com.athena.lms.fraud.dto.request.UpdateCaseRequest;
import com.athena.lms.fraud.dto.response.CaseNoteResponse;
import com.athena.lms.fraud.dto.response.CaseResponse;
import com.athena.lms.fraud.entity.AuditLog;
import com.athena.lms.fraud.entity.CaseNote;
import com.athena.lms.fraud.entity.FraudCase;
import com.athena.lms.fraud.enums.AlertSeverity;
import com.athena.lms.fraud.enums.CaseStatus;
import com.athena.lms.fraud.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CaseManagementServiceTest {

    @Mock private FraudCaseRepository caseRepository;
    @Mock private CaseNoteRepository noteRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private FraudAlertRepository alertRepository;

    @InjectMocks private CaseManagementService service;

    private static final String TENANT = "test-tenant";

    @Nested
    @DisplayName("Case Creation")
    class CaseCreationTests {

        @Test
        @DisplayName("creates case with auto-generated case number")
        void createCase() {
            when(caseRepository.findMaxCaseNumber(TENANT)).thenReturn(42);
            when(caseRepository.save(any())).thenAnswer(inv -> {
                FraudCase c = inv.getArgument(0);
                c.setId(UUID.randomUUID());
                return c;
            });
            when(noteRepository.findByCaseIdAndTenantIdOrderByCreatedAtDesc(any(), eq(TENANT)))
                .thenReturn(List.of());
            when(auditLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CreateCaseRequest req = new CreateCaseRequest();
            req.setTitle("Suspected structuring ring");
            req.setDescription("Multiple customers with similar patterns");
            req.setCustomerId("CUST-1");

            CaseResponse result = service.createCase(req, TENANT);

            assertThat(result.getCaseNumber()).isEqualTo("FRD-00043");
            assertThat(result.getTitle()).isEqualTo("Suspected structuring ring");
            assertThat(result.getStatus()).isEqualTo(CaseStatus.OPEN);
            assertThat(result.getPriority()).isEqualTo(AlertSeverity.MEDIUM);

            verify(caseRepository).save(any());
            verify(auditLogRepository).save(any());
        }

        @Test
        @DisplayName("creates case with custom priority and alert IDs")
        void createWithPriorityAndAlerts() {
            when(caseRepository.findMaxCaseNumber(TENANT)).thenReturn(0);
            when(caseRepository.save(any())).thenAnswer(inv -> {
                FraudCase c = inv.getArgument(0);
                c.setId(UUID.randomUUID());
                return c;
            });
            when(noteRepository.findByCaseIdAndTenantIdOrderByCreatedAtDesc(any(), eq(TENANT)))
                .thenReturn(List.of());
            when(auditLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            UUID alertId = UUID.randomUUID();
            CreateCaseRequest req = new CreateCaseRequest();
            req.setTitle("High-value fraud");
            req.setPriority("CRITICAL");
            req.setAlertIds(Set.of(alertId));
            req.setTotalExposure(new BigDecimal("5000000"));

            CaseResponse result = service.createCase(req, TENANT);

            assertThat(result.getCaseNumber()).isEqualTo("FRD-00001");
            assertThat(result.getPriority()).isEqualTo(AlertSeverity.CRITICAL);
            assertThat(result.getAlertIds()).contains(alertId);
        }
    }

    @Nested
    @DisplayName("Case Updates")
    class CaseUpdateTests {

        @Test
        @DisplayName("updates case status and records audit")
        void updateStatus() {
            UUID caseId = UUID.randomUUID();
            FraudCase existing = FraudCase.builder()
                .id(caseId).tenantId(TENANT).caseNumber("FRD-00001")
                .title("Test").status(CaseStatus.OPEN)
                .priority(AlertSeverity.MEDIUM).alertIds(new HashSet<>())
                .build();

            when(caseRepository.findById(caseId)).thenReturn(Optional.of(existing));
            when(caseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(noteRepository.findByCaseIdAndTenantIdOrderByCreatedAtDesc(caseId, TENANT))
                .thenReturn(List.of());
            when(auditLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            UpdateCaseRequest req = new UpdateCaseRequest();
            req.setStatus("INVESTIGATING");

            CaseResponse result = service.updateCase(caseId, req, TENANT);

            assertThat(result.getStatus()).isEqualTo(CaseStatus.INVESTIGATING);
            verify(auditLogRepository).save(argThat(a -> a.getAction().equals("CASE_UPDATED")));
        }

        @Test
        @DisplayName("closing case sets closedAt and outcome")
        void closeCase() {
            UUID caseId = UUID.randomUUID();
            FraudCase existing = FraudCase.builder()
                .id(caseId).tenantId(TENANT).caseNumber("FRD-00002")
                .title("Test").status(CaseStatus.INVESTIGATING)
                .priority(AlertSeverity.HIGH).alertIds(new HashSet<>())
                .build();

            when(caseRepository.findById(caseId)).thenReturn(Optional.of(existing));
            when(caseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(noteRepository.findByCaseIdAndTenantIdOrderByCreatedAtDesc(caseId, TENANT))
                .thenReturn(List.of());
            when(auditLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            UpdateCaseRequest req = new UpdateCaseRequest();
            req.setStatus("CLOSED_CONFIRMED");
            req.setClosedBy("analyst-1");
            req.setOutcome("Confirmed fraud ring");

            CaseResponse result = service.updateCase(caseId, req, TENANT);

            assertThat(result.getStatus()).isEqualTo(CaseStatus.CLOSED_CONFIRMED);
            assertThat(result.getClosedAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Case Notes")
    class CaseNotesTests {

        @Test
        @DisplayName("adds note to existing case")
        void addNote() {
            UUID caseId = UUID.randomUUID();
            FraudCase existing = FraudCase.builder()
                .id(caseId).tenantId(TENANT).caseNumber("FRD-00001")
                .title("Test").status(CaseStatus.OPEN)
                .priority(AlertSeverity.MEDIUM).alertIds(new HashSet<>())
                .build();

            when(caseRepository.findById(caseId)).thenReturn(Optional.of(existing));
            when(noteRepository.save(any())).thenAnswer(inv -> {
                CaseNote n = inv.getArgument(0);
                n.setId(UUID.randomUUID());
                return n;
            });
            when(auditLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            AddCaseNoteRequest req = new AddCaseNoteRequest();
            req.setContent("Identified additional suspicious transactions");
            req.setAuthor("analyst-1");

            CaseNoteResponse result = service.addNote(caseId, req, TENANT);

            assertThat(result.getContent()).isEqualTo("Identified additional suspicious transactions");
            assertThat(result.getAuthor()).isEqualTo("analyst-1");
            assertThat(result.getNoteType()).isEqualTo("COMMENT");
        }

        @Test
        @DisplayName("throws when adding note to non-existent case")
        void addNoteNotFound() {
            UUID caseId = UUID.randomUUID();
            when(caseRepository.findById(caseId)).thenReturn(Optional.empty());

            AddCaseNoteRequest req = new AddCaseNoteRequest();
            req.setContent("Test");
            req.setAuthor("analyst");

            assertThatThrownBy(() -> service.addNote(caseId, req, TENANT))
                .isInstanceOf(com.athena.lms.common.exception.ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Audit Trail")
    class AuditTests {

        @Test
        @DisplayName("records audit entry with changes")
        void auditWithChanges() {
            when(auditLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            UUID entityId = UUID.randomUUID();
            Map<String, Object> changes = Map.of("status", Map.of("from", "OPEN", "to", "INVESTIGATING"));

            service.audit(TENANT, "CASE_UPDATED", "CASE", entityId, "analyst-1", "Status changed", changes);

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(captor.capture());

            AuditLog saved = captor.getValue();
            assertThat(saved.getAction()).isEqualTo("CASE_UPDATED");
            assertThat(saved.getEntityType()).isEqualTo("CASE");
            assertThat(saved.getPerformedBy()).isEqualTo("analyst-1");
            assertThat(saved.getChanges()).containsKey("status");
        }
    }
}
