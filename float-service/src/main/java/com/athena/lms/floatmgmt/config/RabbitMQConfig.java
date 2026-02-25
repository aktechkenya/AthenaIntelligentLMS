package com.athena.lms.floatmgmt.config;

import com.athena.lms.common.config.LmsRabbitMQConfig;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import(LmsRabbitMQConfig.class)
public class RabbitMQConfig {

    // Local inbound queue for loan.disbursed events (float draw trigger)
    public static final String FLOAT_INBOUND_QUEUE = "athena.lms.float.inbound.queue";

    @Bean
    public Queue floatInboundQueue() {
        return QueueBuilder.durable(FLOAT_INBOUND_QUEUE).build();
    }

    @Bean
    public Binding floatLoanDisbursedBinding(Queue floatInboundQueue, TopicExchange lmsExchange) {
        return BindingBuilder.bind(floatInboundQueue).to(lmsExchange).with("loan.disbursed");
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory, MessageConverter lmsMessageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(lmsMessageConverter);
        return factory;
    }
}
