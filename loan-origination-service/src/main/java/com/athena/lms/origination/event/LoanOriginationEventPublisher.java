package com.athena.lms.origination.event;

import com.athena.lms.origination.config.RabbitMQConfig;
import com.athena.lms.origination.entity.LoanApplication;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoanOriginationEventPublisher {

    private final RabbitTemplate lmsRabbitTemplate;

    public void publishSubmitted(LoanApplication app) {
        publish("loan.application.submitted", app, null);
    }

    public void publishApproved(LoanApplication app) {
        publish("loan.application.approved", app, null);
    }

    public void publishRejected(LoanApplication app, String reason) {
        Map<String, Object> extra = new HashMap<>();
        extra.put("reason", reason);
        publish("loan.application.rejected", app, extra);
    }

    public void publishDisbursed(LoanApplication app) {
        publish("loan.disbursed", app, null);
    }

    private void publish(String routingKey, LoanApplication app, Map<String, Object> extra) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventType", routingKey);
        payload.put("applicationId", app.getId());
        payload.put("tenantId", app.getTenantId());
        payload.put("customerId", app.getCustomerId());
        payload.put("productId", app.getProductId());
        payload.put("status", app.getStatus());
        payload.put("amount", app.getApprovedAmount() != null ? app.getApprovedAmount() : app.getRequestedAmount());
        payload.put("currency", app.getCurrency());
        payload.put("tenorMonths", app.getTenorMonths());
        payload.put("interestRate", app.getInterestRate());
        payload.put("disbursementAccount", app.getDisbursementAccount());
        payload.put("timestamp", OffsetDateTime.now().toString());
        if (extra != null) payload.putAll(extra);

        log.info("Publishing event [{}] for application [{}]", routingKey, app.getId());
        lmsRabbitTemplate.convertAndSend(RabbitMQConfig.LMS_EXCHANGE, routingKey, payload);
    }
}
