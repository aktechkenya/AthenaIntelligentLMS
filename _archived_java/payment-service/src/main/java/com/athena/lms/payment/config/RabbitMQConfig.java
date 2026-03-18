package com.athena.lms.payment.config;

import com.athena.lms.common.config.LmsRabbitMQConfig;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Payment-service RabbitMQ config.
 * Imports shared topology; adds a dedicated inbound queue for loan.disbursed
 * so payment-service can create disbursement payment records.
 */
@Configuration
@Import(LmsRabbitMQConfig.class)
public class RabbitMQConfig {

    public static final String PAYMENT_INBOUND_QUEUE = "athena.lms.payment.inbound.queue";

    @Bean
    public Queue paymentInboundQueue() {
        return QueueBuilder.durable(PAYMENT_INBOUND_QUEUE).build();
    }

    @Bean
    public Binding paymentDisbursedBinding(Queue paymentInboundQueue, TopicExchange lmsExchange) {
        return BindingBuilder.bind(paymentInboundQueue).to(lmsExchange).with("loan.disbursed");
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter lmsMessageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(lmsMessageConverter);
        return factory;
    }
}
