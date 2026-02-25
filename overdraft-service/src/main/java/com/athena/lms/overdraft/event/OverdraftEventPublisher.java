package com.athena.lms.overdraft.event;

import com.athena.lms.common.config.LmsRabbitMQConfig;
import com.athena.lms.common.event.DomainEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class OverdraftEventPublisher {

    private final RabbitTemplate lmsRabbitTemplate;

    public void publishOverdraftApplied(UUID walletId, String customerId, String band, BigDecimal limit, String tenantId) {
        String type = "overdraft.applied";
        Map<String, Object> payload = Map.of(
            "walletId", walletId.toString(),
            "customerId", customerId,
            "creditBand", band,
            "approvedLimit", limit,
            "tenantId", tenantId
        );
        DomainEvent<Map<String, Object>> event = DomainEvent.of(type, "overdraft-service", tenantId, payload);
        lmsRabbitTemplate.convertAndSend(LmsRabbitMQConfig.LMS_EXCHANGE, type, event);
        log.info("Published overdraft.applied for customer {} band {} limit {}", customerId, band, limit);
    }

    public void publishOverdraftDrawn(UUID walletId, String customerId, BigDecimal amount, String tenantId) {
        String type = "overdraft.drawn";
        Map<String, Object> payload = Map.of(
            "walletId", walletId.toString(),
            "customerId", customerId,
            "amount", amount,
            "tenantId", tenantId
        );
        DomainEvent<Map<String, Object>> event = DomainEvent.of(type, "overdraft-service", tenantId, payload);
        lmsRabbitTemplate.convertAndSend(LmsRabbitMQConfig.LMS_EXCHANGE, type, event);
    }

    public void publishOverdraftRepaid(UUID walletId, String customerId, BigDecimal amount, String tenantId) {
        String type = "overdraft.repaid";
        Map<String, Object> payload = Map.of(
            "walletId", walletId.toString(),
            "customerId", customerId,
            "amount", amount,
            "tenantId", tenantId
        );
        DomainEvent<Map<String, Object>> event = DomainEvent.of(type, "overdraft-service", tenantId, payload);
        lmsRabbitTemplate.convertAndSend(LmsRabbitMQConfig.LMS_EXCHANGE, type, event);
    }

    public void publishInterestCharged(UUID walletId, String customerId, BigDecimal interest, String tenantId) {
        String type = "overdraft.interest.charged";
        Map<String, Object> payload = Map.of(
            "walletId", walletId.toString(),
            "customerId", customerId,
            "interestCharged", interest,
            "tenantId", tenantId
        );
        DomainEvent<Map<String, Object>> event = DomainEvent.of(type, "overdraft-service", tenantId, payload);
        lmsRabbitTemplate.convertAndSend(LmsRabbitMQConfig.LMS_EXCHANGE, type, event);
    }
}
