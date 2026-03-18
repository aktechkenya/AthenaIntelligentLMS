package com.athena.lms.fraud.scheduler;

import com.athena.lms.fraud.entity.FraudCase;
import com.athena.lms.fraud.entity.SarReport;
import com.athena.lms.fraud.entity.WatchlistEntry;
import com.athena.lms.fraud.enums.AlertSeverity;
import com.athena.lms.fraud.enums.CaseStatus;
import com.athena.lms.fraud.enums.SarStatus;
import com.athena.lms.fraud.enums.WatchlistType;
import com.athena.lms.fraud.repository.FraudCaseRepository;
import com.athena.lms.fraud.repository.SarReportRepository;
import com.athena.lms.fraud.repository.WatchlistRepository;
import com.athena.lms.fraud.service.CaseManagementService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScheduledFraudTasksTest {

    @Mock
    WatchlistRepository watchlistRepository;

    @Mock
    SarReportRepository sarReportRepository;

    @Mock
    FraudCaseRepository fraudCaseRepository;

    @Mock
    CaseManagementService caseManagementService;

    @InjectMocks
    ScheduledFraudTasks tasks;

    @Test
    @DisplayName("deactivates expired watchlist entries")
    void deactivateExpired() {
        WatchlistEntry e1 = WatchlistEntry.builder()
                .id(UUID.randomUUID())
                .tenantId("t1")
                .name("Test")
                .active(true)
                .listType(WatchlistType.INTERNAL_BLACKLIST)
                .entryType("INDIVIDUAL")
                .expiresAt(OffsetDateTime.now().minusDays(1))
                .build();
        when(watchlistRepository.findExpiredEntries(any())).thenReturn(List.of(e1));
        when(watchlistRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        tasks.deactivateExpiredWatchlistEntries();

        verify(watchlistRepository).save(argThat(w -> !w.getActive()));
    }

    @Test
    @DisplayName("audits overdue SAR deadlines")
    void overdueSar() {
        SarReport r = SarReport.builder()
                .id(UUID.randomUUID())
                .tenantId("t1")
                .reportNumber("SAR-00001")
                .status(SarStatus.DRAFT)
                .filingDeadline(OffsetDateTime.now().minusDays(1))
                .build();
        when(sarReportRepository.findOverdueReports(any())).thenReturn(List.of(r));

        tasks.checkOverdueSarDeadlines();

        verify(caseManagementService).audit(eq("t1"), eq("SAR_OVERDUE"), eq("SAR_REPORT"),
                eq(r.getId()), eq("system"), argThat(s -> s.contains("SAR-00001")), isNull());
    }

    @Test
    @DisplayName("marks overdue cases as SLA breached")
    void checkOverdueCaseSLA() {
        FraudCase fraudCase = FraudCase.builder()
                .id(UUID.randomUUID())
                .tenantId("t1")
                .caseNumber("FRD-00001")
                .title("Test case")
                .status(CaseStatus.OPEN)
                .priority(AlertSeverity.HIGH)
                .slaDeadline(OffsetDateTime.now().minusHours(1))
                .slaBreached(false)
                .build();

        when(fraudCaseRepository.findOverdueCases(any())).thenReturn(List.of(fraudCase));
        when(fraudCaseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        tasks.checkOverdueCaseSLA();

        verify(fraudCaseRepository).save(argThat(c -> c.getSlaBreached()));
        verify(caseManagementService).audit(eq("t1"), eq("SLA_BREACHED"), eq("CASE"),
                eq(fraudCase.getId()), eq("system"),
                argThat(s -> s.contains("FRD-00001")), isNull());
    }
}
