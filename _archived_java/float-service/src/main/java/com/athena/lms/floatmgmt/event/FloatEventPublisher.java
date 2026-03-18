package com.athena.lms.floatmgmt.event;

import com.athena.lms.common.config.LmsRabbitMQConfig;
import com.athena.lms.common.event.DomainEvent;
import com.athena.lms.common.event.EventTypes;
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
public class FloatEventPublisher {

    private final RabbitTemplate lmsRabbitTemplate;

    public void publishFloatDrawn(UUID accountId, BigDecimal amount, UUID loanId, String tenantId) {
        String type = EventTypes.FLOAT_DRAWN;
        Map<String, Object> payload = Map.of(
                "floatAccountId", accountId.toString(),
                "amount", amount,
                "loanId", loanId != null ? loanId.toString() : "",
                "tenantId", tenantId
        );
        DomainEvent<Map<String, Object>> event = DomainEvent.of(type, "float-service", tenantId, payload);
        lmsRabbitTemplate.convertAndSend(LmsRabbitMQConfig.LMS_EXCHANGE, type, event);
        log.info("Published {} event for account {} amount {}", type, accountId, amount);
    }

    public void publishFloatRepaid(UUID accountId, BigDecimal amount, UUID loanId, String tenantId) {
        String type = EventTypes.FLOAT_REPAID;
        Map<String, Object> payload = Map.of(
                "floatAccountId", accountId.toString(),
                "amount", amount,
                "loanId", loanId != null ? loanId.toString() : "",
                "tenantId", tenantId
        );
        DomainEvent<Map<String, Object>> event = DomainEvent.of(type, "float-service", tenantId, payload);
        lmsRabbitTemplate.convertAndSend(LmsRabbitMQConfig.LMS_EXCHANGE, type, event);
        log.info("Published {} event for account {} amount {}", type, accountId, amount);
    }

    public void publishFeeCharged(UUID accountId, BigDecimal fee, String tenantId) {
        String type = EventTypes.FLOAT_FEE_CHARGED;
        Map<String, Object> payload = Map.of(
                "floatAccountId", accountId.toString(),
                "fee", fee,
                "tenantId", tenantId
        );
        DomainEvent<Map<String, Object>> event = DomainEvent.of(type, "float-service", tenantId, payload);
        lmsRabbitTemplate.convertAndSend(LmsRabbitMQConfig.LMS_EXCHANGE, type, event);
        log.info("Published {} event for account {} fee {}", type, accountId, fee);
    }

    public void publishLimitChanged(UUID accountId, BigDecimal oldLimit, BigDecimal newLimit, String tenantId) {
        String type = EventTypes.FLOAT_LIMIT_CHANGED;
        Map<String, Object> payload = Map.of(
                "floatAccountId", accountId.toString(),
                "oldLimit", oldLimit,
                "newLimit", newLimit,
                "tenantId", tenantId
        );
        DomainEvent<Map<String, Object>> event = DomainEvent.of(type, "float-service", tenantId, payload);
        lmsRabbitTemplate.convertAndSend(LmsRabbitMQConfig.LMS_EXCHANGE, type, event);
        log.info("Published {} event for account {} limit {} -> {}", type, accountId, oldLimit, newLimit);
    }
}
