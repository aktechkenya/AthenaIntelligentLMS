package com.athena.lms.overdraft.event;

import com.athena.lms.common.config.LmsRabbitMQConfig;
import com.athena.lms.common.event.DomainEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class OverdraftEventPublisher {

    private final RabbitTemplate lmsRabbitTemplate;

    public void publishOverdraftApplied(UUID walletId, String customerId, String band, BigDecimal limit, String tenantId) {
        publish("overdraft.applied", tenantId, Map.of(
            "walletId", walletId.toString(),
            "customerId", customerId,
            "creditBand", band,
            "approvedLimit", limit,
            "tenantId", tenantId
        ));
        log.info("Published overdraft.applied for customer {} band {} limit {}", customerId, band, limit);
    }

    public void publishOverdraftDrawn(UUID walletId, String customerId, BigDecimal amount, String tenantId) {
        publish("overdraft.drawn", tenantId, Map.of(
            "walletId", walletId.toString(),
            "customerId", customerId,
            "amount", amount,
            "tenantId", tenantId
        ));
    }

    public void publishOverdraftRepaid(UUID walletId, String customerId, BigDecimal amount, String tenantId) {
        publish("overdraft.repaid", tenantId, Map.of(
            "walletId", walletId.toString(),
            "customerId", customerId,
            "amount", amount,
            "tenantId", tenantId
        ));
    }

    public void publishOverdraftRepaidDetailed(UUID walletId, String customerId, BigDecimal totalAmount,
                                                BigDecimal interestRepaid, BigDecimal principalRepaid,
                                                BigDecimal feesRepaid, String tenantId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("walletId", walletId.toString());
        payload.put("customerId", customerId);
        payload.put("amount", totalAmount);
        payload.put("interestRepaid", interestRepaid);
        payload.put("principalRepaid", principalRepaid);
        payload.put("feesRepaid", feesRepaid);
        payload.put("tenantId", tenantId);
        publish("overdraft.repaid", tenantId, payload);
    }

    public void publishInterestCharged(UUID walletId, String customerId, BigDecimal interest, String tenantId) {
        publish("overdraft.interest.charged", tenantId, Map.of(
            "walletId", walletId.toString(),
            "customerId", customerId,
            "interestCharged", interest,
            "tenantId", tenantId
        ));
    }

    public void publishOverdraftSuspended(UUID walletId, String customerId, String tenantId) {
        publish("overdraft.suspended", tenantId, Map.of(
            "walletId", walletId.toString(),
            "customerId", customerId,
            "tenantId", tenantId
        ));
    }

    public void publishFeeCharged(UUID walletId, String customerId, String feeType, BigDecimal amount,
                                   String reference, String tenantId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("walletId", walletId.toString());
        payload.put("customerId", customerId);
        payload.put("feeType", feeType);
        payload.put("amount", amount);
        payload.put("reference", reference);
        payload.put("tenantId", tenantId);
        publish("overdraft.fee.charged", tenantId, payload);
    }

    public void publishBillingStatement(UUID walletId, String customerId, BigDecimal closingBalance,
                                         BigDecimal minimumPayment, LocalDate dueDate, String tenantId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("walletId", walletId.toString());
        payload.put("customerId", customerId);
        payload.put("closingBalance", closingBalance);
        payload.put("minimumPayment", minimumPayment);
        payload.put("dueDate", dueDate.toString());
        payload.put("tenantId", tenantId);
        publish("overdraft.billing.statement", tenantId, payload);
    }

    public void publishDpdUpdated(UUID walletId, String customerId, int dpd, String stage, String tenantId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("walletId", walletId.toString());
        payload.put("customerId", customerId);
        payload.put("dpd", dpd);
        payload.put("nplStage", stage);
        payload.put("tenantId", tenantId);
        publish("overdraft.dpd.updated", tenantId, payload);
    }

    public void publishStageChanged(UUID walletId, String customerId, String previousStage,
                                     String newStage, int dpd, String tenantId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("walletId", walletId.toString());
        payload.put("customerId", customerId);
        payload.put("previousStage", previousStage);
        payload.put("newStage", newStage);
        payload.put("dpd", dpd);
        payload.put("tenantId", tenantId);
        publish("overdraft.stage.changed", tenantId, payload);
    }

    private void publish(String type, String tenantId, Map<String, Object> payload) {
        DomainEvent<Map<String, Object>> event = DomainEvent.of(type, "overdraft-service", tenantId, payload);
        lmsRabbitTemplate.convertAndSend(LmsRabbitMQConfig.LMS_EXCHANGE, type, event);
    }
}
