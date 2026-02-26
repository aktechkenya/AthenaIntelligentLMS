package com.athena.lms.common.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LMS RabbitMQ topology — TopicExchange for pub/sub domain events.
 * Extends existing athena.exchange (DirectExchange) with new athena.lms.exchange.
 */
@Configuration
public class LmsRabbitMQConfig {

    // ─── Exchange ──────────────────────────────────────────────────────────────
    public static final String LMS_EXCHANGE = "athena.lms.exchange";

    // ─── Queues ────────────────────────────────────────────────────────────────
    public static final String ACCOUNTING_QUEUE   = "athena.lms.accounting.queue";
    public static final String COLLECTIONS_QUEUE  = "athena.lms.collections.queue";
    public static final String COMPLIANCE_QUEUE   = "athena.lms.compliance.queue";
    public static final String NOTIFICATION_QUEUE = "athena.lms.notification.queue";
    public static final String LOAN_MGMT_QUEUE    = "athena.lms.loan.mgmt.queue";
    public static final String REPORTING_QUEUE    = "athena.lms.reporting.queue";
    public static final String FLOAT_QUEUE        = "athena.lms.float.queue";
    public static final String ACCOUNT_MOBILE_QUEUE  = "athena.lms.account.mobile.queue";
    public static final String OVERDRAFT_MOBILE_QUEUE = "athena.lms.overdraft.mobile.queue";

    // ─── Routing key patterns ──────────────────────────────────────────────────
    public static final String LOAN_ROUTING_PATTERN        = "loan.#";
    public static final String PAYMENT_ROUTING_PATTERN     = "payment.#";
    public static final String FLOAT_ROUTING_PATTERN       = "float.#";
    public static final String ACCOUNT_ROUTING_PATTERN     = "account.#";
    public static final String DPD_ROUTING_PATTERN         = "loan.dpd.#";
    public static final String STAGE_ROUTING_PATTERN       = "loan.stage.#";
    public static final String AML_ROUTING_PATTERN         = "aml.#";
    public static final String KYC_ROUTING_PATTERN         = "customer.kyc.#";
    public static final String WILDCARD_PATTERN            = "#";
    public static final String PAYMENT_COMPLETED_KEY       = "payment.completed";
    public static final String PAYMENT_REVERSED_KEY        = "payment.reversed";
    public static final String LOAN_DISBURSED_KEY          = "loan.disbursed";
    public static final String LOAN_SUBMITTED_KEY          = "loan.application.submitted";
    public static final String ACCOUNT_CREDIT_KEY          = "account.credit.received";
    public static final String TRANSFER_ROUTING_PATTERN    = "transfer.#";
    public static final String CUSTOMER_ROUTING_PATTERN    = "customer.#";

    // ─── Mobile wallet routing patterns ────────────────────────────────────────
    public static final String MOBILE_ROUTING_PATTERN      = "mobile.#";
    public static final String BILL_ROUTING_PATTERN        = "bill.#";
    public static final String SAVINGS_ROUTING_PATTERN     = "savings.#";
    public static final String SHOP_ROUTING_PATTERN        = "shop.#";

    @Bean
    public TopicExchange lmsExchange() {
        return new TopicExchange(LMS_EXCHANGE, true, false);
    }

    // ─── Queue declarations ────────────────────────────────────────────────────
    @Bean public Queue accountingQueue()   { return new Queue(ACCOUNTING_QUEUE, true); }
    @Bean public Queue collectionsQueue()  { return new Queue(COLLECTIONS_QUEUE, true); }
    @Bean public Queue complianceQueue()   { return new Queue(COMPLIANCE_QUEUE, true); }
    @Bean public Queue lmsNotificationQueue() { return new Queue(NOTIFICATION_QUEUE, true); }
    @Bean public Queue loanMgmtQueue()     { return new Queue(LOAN_MGMT_QUEUE, true); }
    @Bean public Queue reportingQueue()    { return new Queue(REPORTING_QUEUE, true); }
    @Bean public Queue floatQueue()        { return new Queue(FLOAT_QUEUE, true); }
    @Bean public Queue accountMobileQueue()  { return new Queue(ACCOUNT_MOBILE_QUEUE, true); }
    @Bean public Queue overdraftMobileQueue() { return new Queue(OVERDRAFT_MOBILE_QUEUE, true); }

    // ─── Bindings ──────────────────────────────────────────────────────────────
    @Bean
    public Binding accountingLoanBinding(Queue accountingQueue, TopicExchange lmsExchange) {
        return BindingBuilder.bind(accountingQueue).to(lmsExchange).with(LOAN_ROUTING_PATTERN);
    }
    @Bean
    public Binding accountingPaymentBinding(Queue accountingQueue, TopicExchange lmsExchange) {
        return BindingBuilder.bind(accountingQueue).to(lmsExchange).with(PAYMENT_ROUTING_PATTERN);
    }
    @Bean
    public Binding accountingFloatBinding(Queue accountingQueue, TopicExchange lmsExchange) {
        return BindingBuilder.bind(accountingQueue).to(lmsExchange).with(FLOAT_ROUTING_PATTERN);
    }
    @Bean
    public Binding accountingAccountBinding(Queue accountingQueue, TopicExchange lmsExchange) {
        return BindingBuilder.bind(accountingQueue).to(lmsExchange).with(ACCOUNT_ROUTING_PATTERN);
    }
    @Bean
    public Binding collectionsDpdBinding(Queue collectionsQueue, TopicExchange lmsExchange) {
        return BindingBuilder.bind(collectionsQueue).to(lmsExchange).with(DPD_ROUTING_PATTERN);
    }
    @Bean
    public Binding collectionsStageBinding(Queue collectionsQueue, TopicExchange lmsExchange) {
        return BindingBuilder.bind(collectionsQueue).to(lmsExchange).with(STAGE_ROUTING_PATTERN);
    }
    @Bean
    public Binding complianceAmlBinding(Queue complianceQueue, TopicExchange lmsExchange) {
        return BindingBuilder.bind(complianceQueue).to(lmsExchange).with(AML_ROUTING_PATTERN);
    }
    @Bean
    public Binding complianceKycBinding(Queue complianceQueue, TopicExchange lmsExchange) {
        return BindingBuilder.bind(complianceQueue).to(lmsExchange).with(KYC_ROUTING_PATTERN);
    }
    @Bean
    public Binding notificationWildcardBinding(Queue lmsNotificationQueue, TopicExchange lmsExchange) {
        return BindingBuilder.bind(lmsNotificationQueue).to(lmsExchange).with(WILDCARD_PATTERN);
    }
    @Bean
    public Binding loanMgmtPaymentCompletedBinding(Queue loanMgmtQueue, TopicExchange lmsExchange) {
        return BindingBuilder.bind(loanMgmtQueue).to(lmsExchange).with(PAYMENT_COMPLETED_KEY);
    }
    @Bean
    public Binding loanMgmtPaymentReversedBinding(Queue loanMgmtQueue, TopicExchange lmsExchange) {
        return BindingBuilder.bind(loanMgmtQueue).to(lmsExchange).with(PAYMENT_REVERSED_KEY);
    }
    @Bean
    public Binding loanMgmtLoanDisbursedBinding(Queue loanMgmtQueue, TopicExchange lmsExchange) {
        return BindingBuilder.bind(loanMgmtQueue).to(lmsExchange).with(LOAN_DISBURSED_KEY);
    }

    @Bean
    public Binding accountingTransferBinding(Queue accountingQueue, TopicExchange lmsExchange) {
        return BindingBuilder.bind(accountingQueue).to(lmsExchange).with(TRANSFER_ROUTING_PATTERN);
    }
    @Bean
    public Binding complianceCustomerBinding(Queue complianceQueue, TopicExchange lmsExchange) {
        return BindingBuilder.bind(complianceQueue).to(lmsExchange).with(CUSTOMER_ROUTING_PATTERN);
    }

    @Bean
    public Binding reportingWildcardBinding(Queue reportingQueue, TopicExchange lmsExchange) {
        return BindingBuilder.bind(reportingQueue).to(lmsExchange).with(WILDCARD_PATTERN);
    }
    @Bean
    public Binding floatAccountCreditBinding(Queue floatQueue, TopicExchange lmsExchange) {
        return BindingBuilder.bind(floatQueue).to(lmsExchange).with(ACCOUNT_CREDIT_KEY);
    }
    @Bean
    public Binding accountMobileBinding(Queue accountMobileQueue, TopicExchange lmsExchange) {
        return BindingBuilder.bind(accountMobileQueue).to(lmsExchange).with(MOBILE_ROUTING_PATTERN);
    }
    @Bean
    public Binding overdraftMobileBinding(Queue overdraftMobileQueue, TopicExchange lmsExchange) {
        return BindingBuilder.bind(overdraftMobileQueue).to(lmsExchange).with(MOBILE_ROUTING_PATTERN);
    }

    // ─── Converters ────────────────────────────────────────────────────────────
    @Bean
    public MessageConverter lmsMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate lmsRabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(lmsMessageConverter());
        return template;
    }
}
