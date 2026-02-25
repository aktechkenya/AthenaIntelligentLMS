package com.athena.lms.management.listener;

import com.athena.lms.management.config.RabbitMQConfig;
import com.athena.lms.management.service.LoanManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoanDisbursedEventListener {

    private final LoanManagementService loanManagementService;

    @RabbitListener(queues = RabbitMQConfig.LOAN_MGMT_QUEUE)
    public void onLoanDisbursed(Map<String, Object> event) {
        try {
            String eventType = (String) event.get("eventType");
            if (!"loan.disbursed".equals(eventType)) {
                log.debug("Ignoring event type: {}", eventType);
                return;
            }

            log.info("Received loan.disbursed event: {}", event.get("applicationId"));

            UUID applicationId = UUID.fromString((String) event.get("applicationId").toString());
            String customerId  = event.get("customerId") != null ? event.get("customerId").toString() : "";
            UUID productId     = UUID.fromString(event.get("productId").toString());
            String tenantId    = (String) event.get("tenantId");
            BigDecimal amount  = new BigDecimal(event.get("amount").toString());
            BigDecimal rate    = event.get("interestRate") != null
                ? new BigDecimal(event.get("interestRate").toString())
                : BigDecimal.ZERO;
            Integer tenorMonths = event.get("tenorMonths") != null
                ? Integer.parseInt(event.get("tenorMonths").toString())
                : 12;
            String disbursementAccount = (String) event.get("disbursementAccount");

            loanManagementService.activateLoan(
                applicationId, customerId, productId, tenantId,
                amount, rate, tenorMonths
            );
        } catch (Exception e) {
            log.error("Failed to process loan.disbursed event: {}", e.getMessage(), e);
            // Don't re-throw — let the message be acknowledged to avoid infinite retry
        }
    }
}
