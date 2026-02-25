package com.athena.lms.scoring.config;

import com.athena.lms.common.config.LmsRabbitMQConfig;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import(LmsRabbitMQConfig.class)
public class RabbitMQConfig {

    public static final String SCORING_INBOUND_QUEUE = "athena.lms.scoring.inbound.queue";

    @Bean
    public Queue scoringInboundQueue() {
        return QueueBuilder.durable(SCORING_INBOUND_QUEUE).build();
    }

    @Bean
    public Binding scoringSubmittedBinding(Queue scoringInboundQueue, TopicExchange lmsExchange) {
        return BindingBuilder.bind(scoringInboundQueue).to(lmsExchange).with("loan.application.submitted");
    }

    @Bean
    public Binding scoringApprovedBinding(Queue scoringInboundQueue, TopicExchange lmsExchange) {
        return BindingBuilder.bind(scoringInboundQueue).to(lmsExchange).with("loan.application.approved");
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
