package com.athena.lms.payment.listener;

import com.athena.lms.payment.config.RabbitMQConfig;
import com.athena.lms.payment.enums.PaymentChannel;
import com.athena.lms.payment.enums.PaymentStatus;
import com.athena.lms.payment.enums.PaymentType;
import com.athena.lms.payment.entity.Payment;
import com.athena.lms.payment.event.PaymentEventPublisher;
import com.athena.lms.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Listens for loan.disbursed events and creates a LOAN_DISBURSEMENT payment record.
 * The event is published by loan-origination-service as a raw Map.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoanDisbursedPaymentListener {

    private final PaymentRepository paymentRepository;
    private final PaymentEventPublisher eventPublisher;

    @RabbitListener(queues = RabbitMQConfig.PAYMENT_INBOUND_QUEUE)
    @Transactional
    public void onLoanDisbursed(Map<String, Object> event) {
        try {
            // Raw map payload from loan-origination-service
            Object rawType = event.get("eventType");
            if (rawType == null || !"loan.disbursed".equals(rawType.toString())) {
                log.debug("Ignoring non-disbursement event: {}", rawType);
                return;
            }

            String tenantId    = (String) event.get("tenantId");
            UUID applicationId = UUID.fromString(event.get("applicationId").toString());
            UUID customerId    = UUID.fromString(event.get("customerId").toString());
            BigDecimal amount  = new BigDecimal(event.get("amount").toString());
            String currency    = event.containsKey("currency") ? (String) event.get("currency") : "KES";

            Payment payment = Payment.builder()
                .tenantId(tenantId)
                .customerId(customerId)
                .applicationId(applicationId)
                .paymentType(PaymentType.LOAN_DISBURSEMENT)
                .paymentChannel(PaymentChannel.INTERNAL)
                .status(PaymentStatus.COMPLETED)
                .amount(amount)
                .currency(currency)
                .internalReference("DISB-" + applicationId.toString())
                .description("Loan disbursement for application " + applicationId)
                .initiatedAt(OffsetDateTime.now())
                .completedAt(OffsetDateTime.now())
                .createdBy("system")
                .build();

            paymentRepository.save(payment);
            eventPublisher.publishCompleted(payment);

            log.info("Disbursement payment record created for application [{}]", applicationId);
        } catch (Exception e) {
            log.error("Failed to process loan.disbursed for payment record: {}", e.getMessage(), e);
        }
    }
}
