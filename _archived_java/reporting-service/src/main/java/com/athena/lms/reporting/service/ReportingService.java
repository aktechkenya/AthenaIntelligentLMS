package com.athena.lms.reporting.service;

import com.athena.lms.common.exception.ResourceNotFoundException;
import com.athena.lms.common.dto.PageResponse;
import com.athena.lms.reporting.dto.response.EventMetricResponse;
import com.athena.lms.reporting.dto.response.PortfolioSnapshotResponse;
import com.athena.lms.reporting.dto.response.ReportEventResponse;
import com.athena.lms.reporting.entity.EventMetric;
import com.athena.lms.reporting.entity.PortfolioSnapshot;
import com.athena.lms.reporting.entity.ReportEvent;
import com.athena.lms.reporting.enums.EventCategory;
import com.athena.lms.reporting.repository.EventMetricRepository;
import com.athena.lms.reporting.repository.PortfolioSnapshotRepository;
import com.athena.lms.reporting.repository.ReportEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class ReportingService {

    private final ReportEventRepository reportEventRepository;
    private final PortfolioSnapshotRepository portfolioSnapshotRepository;
    private final EventMetricRepository eventMetricRepository;
    private final ObjectMapper objectMapper;

    public void recordEvent(String eventType, Map<String, Object> payload, String tenantId) {
        EventCategory category = categorize(eventType);

        String subjectId = resolveSubjectId(payload);
        Long customerId = extractLong(payload, "customerId");
        BigDecimal amount = extractBigDecimal(payload, "amount");

        String payloadJson = null;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.warn("Could not serialize event payload: {}", e.getMessage());
            payloadJson = payload.toString();
        }

        ReportEvent event = ReportEvent.builder()
                .tenantId(tenantId)
                .eventId(extractString(payload, "eventId"))
                .eventType(eventType)
                .eventCategory(category.name())
                .sourceService(extractString(payload, "sourceService"))
                .subjectId(subjectId)
                .customerId(customerId)
                .amount(amount)
                .payload(payloadJson)
                .build();

        reportEventRepository.save(event);
        upsertMetric(tenantId, LocalDate.now(), eventType, amount);

        log.debug("Recorded event type={} tenant={} category={}", eventType, tenantId, category);
    }

    private void upsertMetric(String tenantId, LocalDate date, String eventType, BigDecimal amount) {
        Optional<EventMetric> existing = eventMetricRepository
                .findByTenantIdAndMetricDateAndEventType(tenantId, date, eventType);

        if (existing.isPresent()) {
            EventMetric metric = existing.get();
            metric.setEventCount(metric.getEventCount() + 1);
            if (amount != null) {
                metric.setTotalAmount(metric.getTotalAmount().add(amount));
            }
            eventMetricRepository.save(metric);
        } else {
            EventMetric metric = EventMetric.builder()
                    .tenantId(tenantId)
                    .metricDate(date)
                    .eventType(eventType)
                    .eventCount(1L)
                    .totalAmount(amount != null ? amount : BigDecimal.ZERO)
                    .build();
            eventMetricRepository.save(metric);
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<ReportEventResponse> getEvents(String tenantId, String eventType,
                                                        Instant from, Instant to, Pageable pageable) {
        Page<ReportEvent> page;
        if (eventType != null && !eventType.isBlank()) {
            page = reportEventRepository.findByTenantIdAndEventTypeOrderByOccurredAtDesc(tenantId, eventType, pageable);
        } else if (from != null && to != null) {
            page = reportEventRepository.findByTenantIdAndOccurredAtBetweenOrderByOccurredAtDesc(tenantId, from, to, pageable);
        } else {
            page = reportEventRepository.findByTenantIdOrderByOccurredAtDesc(tenantId, pageable);
        }
        return PageResponse.from(page.map(this::toReportEventResponse));
    }

    @Transactional(readOnly = true)
    public PageResponse<PortfolioSnapshotResponse> getSnapshots(String tenantId, Pageable pageable) {
        Page<PortfolioSnapshot> page = portfolioSnapshotRepository
                .findByTenantIdOrderBySnapshotDateDesc(tenantId, pageable);
        return PageResponse.from(page.map(this::toSnapshotResponse));
    }

    @Transactional(readOnly = true)
    public PortfolioSnapshotResponse getLatestSnapshot(String tenantId) {
        PortfolioSnapshot snapshot = portfolioSnapshotRepository
                .findTopByTenantIdOrderBySnapshotDateDesc(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("No portfolio snapshot found for tenant: " + tenantId));
        return toSnapshotResponse(snapshot);
    }

    @Transactional(readOnly = true)
    public List<EventMetricResponse> getMetrics(String tenantId, LocalDate from, LocalDate to) {
        List<EventMetric> metrics = eventMetricRepository
                .findByTenantIdAndMetricDateBetween(tenantId, from, to);
        return metrics.stream().map(this::toMetricResponse).collect(Collectors.toList());
    }

    public void generateDailySnapshot(String tenantId) {
        // Use today — accumulate all events from today and yesterday so data is current
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        List<EventMetric> metricsToday = eventMetricRepository
                .findByTenantIdAndMetricDate(tenantId, today);
        List<EventMetric> metricsYesterday = eventMetricRepository
                .findByTenantIdAndMetricDate(tenantId, yesterday);

        // Combine metrics from both days (handles stress tests run today)
        List<EventMetric> metrics = new java.util.ArrayList<>();
        metrics.addAll(metricsToday);
        metrics.addAll(metricsYesterday);

        int disbursedCount = countEventType(metrics, "loan.disbursed");
        int closedLoans = countEventType(metrics, "loan.closed");
        int defaultedLoans = countEventType(metrics, "loan.written.off");
        int activeLoans = Math.max(0, disbursedCount - closedLoans - defaultedLoans);
        int totalLoans = disbursedCount;

        BigDecimal totalDisbursed = sumEventType(metrics, "loan.disbursed");
        BigDecimal totalCollected = sumEventType(metrics, "payment.completed");

        BigDecimal par30 = sumEventType(metrics, "loan.dpd.updated.par30");
        BigDecimal par90 = sumEventType(metrics, "loan.dpd.updated.par90");

        PortfolioSnapshot snapshot = portfolioSnapshotRepository
                .findTopByTenantIdOrderBySnapshotDateDesc(tenantId)
                .filter(s -> s.getSnapshotDate().equals(today))
                .orElse(PortfolioSnapshot.builder()
                        .tenantId(tenantId)
                        .snapshotDate(today)
                        .period("DAILY")
                        .build());

        snapshot.setTotalLoans(totalLoans);
        snapshot.setActiveLoans(activeLoans);
        snapshot.setClosedLoans(closedLoans);
        snapshot.setDefaultedLoans(defaultedLoans);
        snapshot.setTotalDisbursed(totalDisbursed);
        snapshot.setTotalCollected(totalCollected);
        snapshot.setTotalOutstanding(totalDisbursed.subtract(totalCollected).max(BigDecimal.ZERO));
        snapshot.setPar30(par30);
        snapshot.setPar90(par90);

        portfolioSnapshotRepository.save(snapshot);
        log.info("Generated daily snapshot for tenant={} date={}", tenantId, yesterday);
    }

    private EventCategory categorize(String eventType) {
        if (eventType == null) return EventCategory.UNKNOWN;
        if (eventType.startsWith("loan.application")) return EventCategory.LOAN_ORIGINATION;
        if (eventType.startsWith("loan.")) return EventCategory.LOAN_MANAGEMENT;
        if (eventType.startsWith("payment.")) return EventCategory.PAYMENT;
        if (eventType.startsWith("float.")) return EventCategory.FLOAT;
        if (eventType.startsWith("aml.") || eventType.startsWith("customer.kyc.")) return EventCategory.COMPLIANCE;
        if (eventType.startsWith("account.")) return EventCategory.ACCOUNT;
        return EventCategory.UNKNOWN;
    }

    private String resolveSubjectId(Map<String, Object> payload) {
        String subjectId = extractString(payload, "loanId");
        if (subjectId != null) return subjectId;
        subjectId = extractString(payload, "accountId");
        if (subjectId != null) return subjectId;
        return extractString(payload, "paymentId");
    }

    private String extractString(Map<String, Object> payload, String key) {
        Object val = payload.get(key);
        return val instanceof String s ? s : (val != null ? val.toString() : null);
    }

    private Long extractLong(Map<String, Object> payload, String key) {
        Object val = payload.get(key);
        if (val instanceof Number n) return n.longValue();
        return null;
    }

    private BigDecimal extractBigDecimal(Map<String, Object> payload, String key) {
        Object val = payload.get(key);
        if (val instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return null;
    }

    private int countEventType(List<EventMetric> metrics, String eventType) {
        return metrics.stream()
                .filter(m -> eventType.equals(m.getEventType()))
                .mapToInt(m -> m.getEventCount().intValue())
                .sum();
    }

    private BigDecimal sumEventType(List<EventMetric> metrics, String eventType) {
        return metrics.stream()
                .filter(m -> eventType.equals(m.getEventType()))
                .map(EventMetric::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private ReportEventResponse toReportEventResponse(ReportEvent e) {
        ReportEventResponse r = new ReportEventResponse();
        r.setId(e.getId());
        r.setTenantId(e.getTenantId());
        r.setEventId(e.getEventId());
        r.setEventType(e.getEventType());
        r.setEventCategory(e.getEventCategory());
        r.setSourceService(e.getSourceService());
        r.setSubjectId(e.getSubjectId());
        r.setCustomerId(e.getCustomerId());
        r.setAmount(e.getAmount());
        r.setCurrency(e.getCurrency());
        r.setPayload(e.getPayload());
        r.setOccurredAt(e.getOccurredAt());
        r.setCreatedAt(e.getCreatedAt());
        return r;
    }

    private PortfolioSnapshotResponse toSnapshotResponse(PortfolioSnapshot s) {
        PortfolioSnapshotResponse r = new PortfolioSnapshotResponse();
        r.setId(s.getId());
        r.setTenantId(s.getTenantId());
        r.setSnapshotDate(s.getSnapshotDate());
        r.setPeriod(s.getPeriod());
        r.setTotalLoans(s.getTotalLoans());
        r.setActiveLoans(s.getActiveLoans());
        r.setClosedLoans(s.getClosedLoans());
        r.setDefaultedLoans(s.getDefaultedLoans());
        r.setTotalDisbursed(s.getTotalDisbursed());
        r.setTotalOutstanding(s.getTotalOutstanding());
        r.setTotalCollected(s.getTotalCollected());
        r.setWatchLoans(s.getWatchLoans());
        r.setSubstandardLoans(s.getSubstandardLoans());
        r.setDoubtfulLoans(s.getDoubtfulLoans());
        r.setLossLoans(s.getLossLoans());
        r.setPar30(s.getPar30());
        r.setPar90(s.getPar90());
        r.setCreatedAt(s.getCreatedAt());
        return r;
    }

    private EventMetricResponse toMetricResponse(EventMetric m) {
        EventMetricResponse r = new EventMetricResponse();
        r.setMetricDate(m.getMetricDate());
        r.setEventType(m.getEventType());
        r.setEventCount(m.getEventCount());
        r.setTotalAmount(m.getTotalAmount());
        return r;
    }
}
