package com.athena.lms.payment.event;

import com.athena.lms.common.config.LmsRabbitMQConfig;
import com.athena.lms.common.event.DomainEvent;
import com.athena.lms.common.event.EventTypes;
import com.athena.lms.payment.entity.Payment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventPublisher {

    private final RabbitTemplate lmsRabbitTemplate;

    public void publishInitiated(Payment payment) {
        publish(EventTypes.PAYMENT_INITIATED, payment, null);
    }

    public void publishCompleted(Payment payment) {
        publish(EventTypes.PAYMENT_COMPLETED, payment, null);
    }

    public void publishFailed(Payment payment) {
        publish(EventTypes.PAYMENT_FAILED, payment,
            Map.of("reason", payment.getFailureReason() != null ? payment.getFailureReason() : "unknown"));
    }

    public void publishReversed(Payment payment) {
        publish(EventTypes.PAYMENT_REVERSED, payment,
            Map.of("reason", payment.getReversalReason() != null ? payment.getReversalReason() : ""));
    }

    private void publish(String eventType, Payment payment, Map<String, Object> extra) {
        java.util.Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("paymentId", payment.getId());
        payload.put("customerId", payment.getCustomerId());
        payload.put("loanId", payment.getLoanId());
        payload.put("applicationId", payment.getApplicationId());
        payload.put("paymentType", payment.getPaymentType().name());
        payload.put("paymentChannel", payment.getPaymentChannel().name());
        payload.put("amount", payment.getAmount());
        payload.put("currency", payment.getCurrency());
        payload.put("internalReference", payment.getInternalReference());
        payload.put("externalReference", payment.getExternalReference());
        if (extra != null) payload.putAll(extra);

        DomainEvent<Map<String, Object>> event = DomainEvent.of(
            eventType, "payment-service", payment.getTenantId(), payload);

        log.info("Publishing event [{}] for payment [{}]", eventType, payment.getId());
        lmsRabbitTemplate.convertAndSend(LmsRabbitMQConfig.LMS_EXCHANGE, eventType, event);
    }
}
