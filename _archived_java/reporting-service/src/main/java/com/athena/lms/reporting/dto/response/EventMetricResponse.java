package com.athena.lms.reporting.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class EventMetricResponse {
    private LocalDate metricDate;
    private String eventType;
    private Long eventCount;
    private BigDecimal totalAmount;
}
