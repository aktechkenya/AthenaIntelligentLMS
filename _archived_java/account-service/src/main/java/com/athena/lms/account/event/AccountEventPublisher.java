package com.athena.lms.account.event;

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
public class AccountEventPublisher {

    private final RabbitTemplate lmsRabbitTemplate;

    public void publishCreated(UUID accountId, String accountNumber, String customerId, String tenantId) {
        publish(EventTypes.ACCOUNT_CREATED, tenantId,
                Map.of("accountId", accountId.toString(),
                       "accountNumber", accountNumber,
                       "customerId", customerId));
    }

    public void publishCreditReceived(UUID accountId, BigDecimal amount, String tenantId) {
        publish(EventTypes.ACCOUNT_CREDIT_RECEIVED, tenantId,
                Map.of("accountId", accountId.toString(),
                       "amount", amount,
                       "tenantId", tenantId));
    }

    public void publishCustomerCreated(UUID id, String customerId, String tenantId) {
        publish(EventTypes.CUSTOMER_CREATED, tenantId,
                Map.of("id", id.toString(), "customerId", customerId));
    }

    public void publishCustomerUpdated(UUID id, String customerId, String tenantId) {
        publish(EventTypes.CUSTOMER_UPDATED, tenantId,
                Map.of("id", id.toString(), "customerId", customerId));
    }

    public void publishTransferCompleted(UUID transferId, UUID sourceAccountId,
            UUID destAccountId, BigDecimal amount, String tenantId) {
        publish(EventTypes.TRANSFER_COMPLETED, tenantId,
                Map.of("transferId", transferId.toString(),
                       "sourceAccountId", sourceAccountId.toString(),
                       "destinationAccountId", destAccountId.toString(),
                       "amount", amount));
    }

    public void publishTransferFailed(UUID transferId, String reason, String tenantId) {
        publish(EventTypes.TRANSFER_FAILED, tenantId,
                Map.of("transferId", transferId.toString(), "reason", reason));
    }

    public void publishDebitProcessed(UUID accountId, BigDecimal amount, String tenantId) {
        publish(EventTypes.ACCOUNT_DEBIT_PROCESSED, tenantId,
                Map.of("accountId", accountId.toString(),
                       "amount", amount));
    }

    private void publish(String type, String tenantId, Map<String, Object> payload) {
        try {
            DomainEvent<Map<String, Object>> event =
                    DomainEvent.of(type, "account-service", tenantId, payload);
            lmsRabbitTemplate.convertAndSend(LmsRabbitMQConfig.LMS_EXCHANGE, type, event);
            log.debug("Published event: {} for tenant: {}", type, tenantId);
        } catch (Exception e) {
            log.error("Failed to publish event {}: {}", type, e.getMessage());
        }
    }
}
