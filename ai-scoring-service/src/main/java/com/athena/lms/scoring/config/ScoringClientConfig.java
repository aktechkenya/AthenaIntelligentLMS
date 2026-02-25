package com.athena.lms.scoring.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class ScoringClientConfig {

    @Value("${athena.scoring.url}")
    private String scoringUrl;

    @Value("${athena.scoring.connect-timeout-ms:5000}")
    private int connectTimeoutMs;

    @Value("${athena.scoring.read-timeout-ms:30000}")
    private int readTimeoutMs;

    @Bean
    public RestTemplate scoringRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        return new RestTemplate(factory);
    }

    @Bean
    public String scoringBaseUrl() {
        return scoringUrl;
    }
}
