package com.athena.notificationservice.config;

import com.athena.lms.common.config.LmsRabbitMQConfig;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Notification service RabbitMQ config — imports LMS topology.
 * The NOTIFICATION_QUEUE is bound to "#" in LmsRabbitMQConfig, receiving all LMS events.
 * A second listener on the legacy athena.dispute.queue is kept for backward compat.
 */
@Configuration
@Import(LmsRabbitMQConfig.class)
public class AthenaRabbitMQConfig {

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
