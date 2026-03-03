package com.athena.notificationservice.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Component
public class CustomerClient {

    private final RestTemplate restTemplate;

    @Value("${athena.account.url:http://lms-account-service:8086}")
    private String accountServiceUrl;

    @Value("${lms.internal.service-key:}")
    private String serviceKey;

    public CustomerClient() {
        this.restTemplate = new RestTemplate();
    }

    public String resolveEmail(String customerId, String tenantId) {
        if (customerId == null || customerId.isBlank()) {
            return "noreply@athena.lms";
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Service-Key", serviceKey);
            headers.set("X-Service-Tenant", tenantId != null ? tenantId : "default");
            headers.set("X-Service-User", "notification-service");

            ResponseEntity<Map> response = restTemplate.exchange(
                accountServiceUrl + "/api/v1/customers/by-customer-id/" + customerId,
                HttpMethod.GET, new HttpEntity<>(headers), Map.class);

            Map<?, ?> body = response.getBody();
            if (body != null && body.get("email") != null) {
                String email = body.get("email").toString();
                if (!email.isBlank()) return email;
            }
        } catch (Exception e) {
            log.warn("Could not resolve email for customer {}: {}", customerId, e.getMessage());
        }
        return "noreply@athena.lms";
    }
}
