package com.athena.lms.management.config;

import com.athena.lms.common.config.LmsRabbitMQConfig;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import(LmsRabbitMQConfig.class)
public class RabbitMQConfig {

    // Constant aliases for backward compatibility with listener + publisher
    public static final String LMS_EXCHANGE    = LmsRabbitMQConfig.LMS_EXCHANGE;
    public static final String LOAN_MGMT_QUEUE = LmsRabbitMQConfig.LOAN_MGMT_QUEUE;

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory, MessageConverter lmsMessageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(lmsMessageConverter);
        return factory;
    }
}
