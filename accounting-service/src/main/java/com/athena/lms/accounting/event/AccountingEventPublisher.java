package com.athena.lms.accounting.event;

import com.athena.lms.common.config.LmsRabbitMQConfig;
import com.athena.lms.common.event.DomainEvent;
import com.athena.lms.accounting.entity.JournalEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountingEventPublisher {

    private final RabbitTemplate lmsRabbitTemplate;

    public void publishJournalPosted(JournalEntry entry) {
        Map<String, Object> payload = Map.of(
            "entryId",      entry.getId(),
            "reference",    entry.getReference(),
            "entryDate",    entry.getEntryDate().toString(),
            "sourceEvent",  entry.getSourceEvent() != null ? entry.getSourceEvent() : "",
            "sourceId",     entry.getSourceId() != null ? entry.getSourceId() : "",
            "totalDebit",   entry.getTotalDebit(),
            "totalCredit",  entry.getTotalCredit()
        );

        DomainEvent<Map<String, Object>> event = DomainEvent.of(
            "accounting.posted", "accounting-service", entry.getTenantId(), payload);

        log.info("Publishing accounting.posted for entry [{}]", entry.getId());
        lmsRabbitTemplate.convertAndSend(LmsRabbitMQConfig.LMS_EXCHANGE, "accounting.posted", event);
    }
}
