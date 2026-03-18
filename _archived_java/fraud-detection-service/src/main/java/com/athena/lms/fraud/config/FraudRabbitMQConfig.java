package com.athena.lms.fraud.config;

import com.athena.lms.common.config.LmsRabbitMQConfig;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FraudRabbitMQConfig {

    public static final String FRAUD_QUEUE = "athena.lms.fraud.queue";

    @Bean
    public Queue fraudQueue() {
        return new Queue(FRAUD_QUEUE, true);
    }

    @Bean
    public Binding fraudWildcardBinding(Queue fraudQueue, TopicExchange lmsExchange) {
        return BindingBuilder.bind(fraudQueue).to(lmsExchange).with(LmsRabbitMQConfig.WILDCARD_PATTERN);
    }
}
