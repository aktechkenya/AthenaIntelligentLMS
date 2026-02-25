package com.athena.lms.account.config;

import com.athena.lms.common.config.LmsRabbitMQConfig;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import(LmsRabbitMQConfig.class)
public class RabbitMQConfig {
}
