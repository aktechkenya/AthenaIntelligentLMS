package com.athena.lms.collections.event;

import com.athena.lms.collections.enums.ActionType;
import com.athena.lms.collections.enums.CollectionStage;
import com.athena.lms.common.event.DomainEvent;
import com.athena.lms.common.config.LmsRabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class CollectionsEventPublisher {

    private final RabbitTemplate lmsRabbitTemplate;

    public void publishCaseCreated(UUID caseId, UUID loanId, String tenantId) {
        DomainEvent<Map<String, Object>> event = DomainEvent.of(
                "collection.case.created",
                "collections-service",
                tenantId,
                Map.of("caseId", caseId.toString(), "loanId", loanId.toString())
        );
        lmsRabbitTemplate.convertAndSend(LmsRabbitMQConfig.LMS_EXCHANGE, "collection.case.created", event);
        log.info("Published collection.case.created for case {} loan {}", caseId, loanId);
    }

    public void publishCaseEscalated(UUID caseId, UUID loanId, CollectionStage newStage, String tenantId) {
        DomainEvent<Map<String, Object>> event = DomainEvent.of(
                "collection.case.escalated",
                "collections-service",
                tenantId,
                Map.of("caseId", caseId.toString(), "loanId", loanId.toString(), "newStage", newStage.name())
        );
        lmsRabbitTemplate.convertAndSend(LmsRabbitMQConfig.LMS_EXCHANGE, "collection.case.escalated", event);
        log.info("Published collection.case.escalated for case {} to stage {}", caseId, newStage);
    }

    public void publishCaseClosed(UUID caseId, UUID loanId, String tenantId) {
        DomainEvent<Map<String, Object>> event = DomainEvent.of(
                "collection.case.closed",
                "collections-service",
                tenantId,
                Map.of("caseId", caseId.toString(), "loanId", loanId.toString())
        );
        lmsRabbitTemplate.convertAndSend(LmsRabbitMQConfig.LMS_EXCHANGE, "collection.case.closed", event);
        log.info("Published collection.case.closed for case {} loan {}", caseId, loanId);
    }

    public void publishActionTaken(UUID caseId, ActionType actionType, String tenantId) {
        DomainEvent<Map<String, Object>> event = DomainEvent.of(
                "collection.action.taken",
                "collections-service",
                tenantId,
                Map.of("caseId", caseId.toString(), "actionType", actionType.name())
        );
        lmsRabbitTemplate.convertAndSend(LmsRabbitMQConfig.LMS_EXCHANGE, "collection.action.taken", event);
        log.debug("Published collection.action.taken for case {} type {}", caseId, actionType);
    }
}
