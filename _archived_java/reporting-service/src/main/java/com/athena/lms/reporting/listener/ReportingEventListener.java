package com.athena.lms.reporting.listener;

import com.athena.lms.reporting.service.ReportingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReportingEventListener {

    private final ReportingService reportingService;

    @RabbitListener(queues = "#{@reportingQueue}")
    public void handleEvent(Map<String, Object> payload) {
        try {
            String eventType = resolveEventType(payload);

            String tenantId = "default";
            Object tenantIdObj = payload.get("tenantId");
            if (tenantIdObj instanceof String t && t != null && !t.isBlank()) {
                tenantId = t;
            }

            reportingService.recordEvent(eventType, payload, tenantId);
        } catch (Exception e) {
            log.error("Error processing reporting event from queue: {}", e.getMessage(), e);
        }
    }

    private String resolveEventType(Map<String, Object> payload) {
        Object type = payload.get("type");
        if (type instanceof String s && s != null && !s.isBlank()) {
            return s;
        }
        Object eventType = payload.get("eventType");
        if (eventType instanceof String s && s != null && !s.isBlank()) {
            return s;
        }
        return "UNKNOWN";
    }
}
