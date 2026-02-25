package com.athena.lms.scoring.event;

import com.athena.lms.common.event.DomainEvent;
import com.athena.lms.common.config.LmsRabbitMQConfig;
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
public class ScoringEventPublisher {

    private final RabbitTemplate lmsRabbitTemplate;

    public void publishCreditAssessed(UUID loanApplicationId, Long customerId,
                                       BigDecimal finalScore, String scoreBand,
                                       BigDecimal pdProbability, String tenantId) {
        try {
            Map<String, Object> payload = Map.of(
                    "loanApplicationId", loanApplicationId.toString(),
                    "customerId", customerId,
                    "finalScore", finalScore,
                    "scoreBand", scoreBand,
                    "pdProbability", pdProbability
            );
            DomainEvent<Map<String, Object>> event = DomainEvent.of(
                    EventTypes.LOAN_CREDIT_ASSESSED,
                    "ai-scoring-service",
                    tenantId,
                    payload
            );
            lmsRabbitTemplate.convertAndSend(
                    LmsRabbitMQConfig.LMS_EXCHANGE,
                    EventTypes.LOAN_CREDIT_ASSESSED,
                    event
            );
            log.info("Published LOAN_CREDIT_ASSESSED for loanApplicationId={} customerId={}",
                    loanApplicationId, customerId);
        } catch (Exception e) {
            log.error("Failed to publish LOAN_CREDIT_ASSESSED for loanApplicationId={}: {}",
                    loanApplicationId, e.getMessage(), e);
        }
    }
}
