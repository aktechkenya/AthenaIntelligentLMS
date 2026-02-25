package com.athena.lms.management.event;

import com.athena.lms.management.config.RabbitMQConfig;
import com.athena.lms.management.entity.Loan;
import com.athena.lms.management.entity.LoanRepayment;
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
public class LoanManagementEventPublisher {

    private final RabbitTemplate lmsRabbitTemplate;

    public void publishStageChanged(Loan loan, String previousStage) {
        Map<String, Object> payload = basePayload(loan);
        payload.put("previousStage", previousStage);
        payload.put("newStage", loan.getStage().name());
        publish("loan.stage.changed", payload);
    }

    public void publishDpdUpdated(Loan loan) {
        Map<String, Object> payload = basePayload(loan);
        payload.put("dpd", loan.getDpd());
        payload.put("stage", loan.getStage().name());
        publish("loan.dpd.updated", payload);
    }

    public void publishLoanClosed(Loan loan) {
        Map<String, Object> payload = basePayload(loan);
        payload.put("closedAt", loan.getClosedAt() != null ? loan.getClosedAt().toString() : null);
        publish("loan.closed", payload);
    }

    public void publishRepaymentCompleted(Loan loan, LoanRepayment repayment) {
        Map<String, Object> payload = basePayload(loan);
        payload.put("eventType", "payment.completed");
        payload.put("paymentId", repayment.getId() != null ? repayment.getId().toString() : null);
        payload.put("amount", repayment.getAmount());
        payload.put("currency", repayment.getCurrency());
        payload.put("principalApplied", repayment.getPrincipalApplied());
        payload.put("interestApplied", repayment.getInterestApplied());
        payload.put("feeApplied", repayment.getFeeApplied());
        payload.put("penaltyApplied", repayment.getPenaltyApplied());
        payload.put("paymentReference", repayment.getPaymentReference());
        payload.put("paymentMethod", repayment.getPaymentMethod());
        payload.put("paymentType", "LOAN_REPAYMENT");
        payload.put("loanId", loan.getId());
        publish("payment.completed", payload);
    }

    private void publish(String routingKey, Map<String, Object> payload) {
        log.info("Publishing event [{}] for loan [{}]", routingKey, payload.get("loanId"));
        lmsRabbitTemplate.convertAndSend(RabbitMQConfig.LMS_EXCHANGE, routingKey, payload);
    }

    private Map<String, Object> basePayload(Loan loan) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("loanId", loan.getId());
        payload.put("tenantId", loan.getTenantId());
        payload.put("customerId", loan.getCustomerId());
        payload.put("status", loan.getStatus().name());
        payload.put("stage", loan.getStage().name());
        payload.put("dpd", loan.getDpd());
        payload.put("timestamp", OffsetDateTime.now().toString());
        return payload;
    }
}
