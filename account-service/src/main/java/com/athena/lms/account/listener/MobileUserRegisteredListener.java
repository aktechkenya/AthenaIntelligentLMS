package com.athena.lms.account.listener;

import com.athena.lms.account.dto.request.CreateAccountRequest;
import com.athena.lms.account.entity.Customer;
import com.athena.lms.account.event.AccountEventPublisher;
import com.athena.lms.account.repository.AccountRepository;
import com.athena.lms.account.repository.CustomerRepository;
import com.athena.lms.account.service.AccountService;
import com.athena.lms.common.config.LmsRabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Consumes mobile.user.registered events from athena.lms.account.mobile.queue.
 * Auto-provisions a WALLET account for newly registered mobile users.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MobileUserRegisteredListener {

    private final AccountService accountService;
    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;
    private final AccountEventPublisher eventPublisher;

    @RabbitListener(queues = LmsRabbitMQConfig.ACCOUNT_MOBILE_QUEUE)
    public void onEvent(Map<String, Object> message) {
        try {
            String eventType = resolveEventType(message);
            if (eventType == null) {
                log.debug("Could not resolve event type, skipping");
                return;
            }

            if (!"mobile.user.registered".equals(eventType)) {
                log.debug("Ignoring event type: {}", eventType);
                return;
            }

            String tenantId = resolveTenantId(message);
            Map<String, Object> payload = resolvePayload(message);

            String customerId = getStr(payload, "customerId");
            String phoneNumber = getStr(payload, "phoneNumber");

            if (customerId == null) {
                log.warn("mobile.user.registered event missing customerId, skipping");
                return;
            }

            log.info("Processing mobile.user.registered for customer [{}] tenant [{}]", customerId, tenantId);

            // Auto-create Customer record if not exists (REQ-003)
            if (!customerRepository.existsByCustomerIdAndTenantId(customerId, tenantId)) {
                String firstName = getStr(payload, "firstName");
                String lastName = getStr(payload, "lastName");
                Customer customer = Customer.builder()
                        .tenantId(tenantId)
                        .customerId(customerId)
                        .firstName(firstName != null ? firstName : "Mobile")
                        .lastName(lastName != null ? lastName : "User")
                        .phone(phoneNumber)
                        .source("MOBILE")
                        .customerType(Customer.CustomerType.INDIVIDUAL)
                        .status(Customer.CustomerStatus.ACTIVE)
                        .kycStatus("PENDING")
                        .build();
                Customer saved = customerRepository.save(customer);
                eventPublisher.publishCustomerCreated(saved.getId(), customerId, tenantId);
                log.info("Auto-created Customer record for mobile user [{}] in tenant [{}]", customerId, tenantId);
            }

            // Check if account already exists for this customer
            if (!accountRepository.findByCustomerIdAndTenantId(customerId, tenantId).isEmpty()) {
                log.info("Account already exists for customer [{}] in tenant [{}], skipping", customerId, tenantId);
                return;
            }

            CreateAccountRequest req = new CreateAccountRequest();
            req.setCustomerId(customerId);
            req.setAccountType("WALLET");
            req.setCurrency("KES");
            req.setAccountName("Mobile Wallet - " + phoneNumber);
            req.setKycTier(0);

            accountService.createAccount(req, tenantId);
            log.info("Auto-provisioned WALLET account for mobile customer [{}] in tenant [{}]", customerId, tenantId);

        } catch (Exception e) {
            log.error("Failed to process mobile.user.registered event: {}", e.getMessage(), e);
        }
    }

    // ─── helpers ─────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private String resolveEventType(Map<String, Object> message) {
        if (message.containsKey("eventType")) return getStr(message, "eventType");
        if (message.containsKey("type")) return getStr(message, "type");
        return null;
    }

    @SuppressWarnings("unchecked")
    private String resolveTenantId(Map<String, Object> message) {
        if (message.containsKey("payload")) {
            Object p = message.get("payload");
            if (p instanceof Map) return getStr((Map<String, Object>) p, "tenantId");
        }
        return getStr(message, "tenantId");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> resolvePayload(Map<String, Object> message) {
        if (message.containsKey("payload")) {
            Object p = message.get("payload");
            if (p instanceof Map) return (Map<String, Object>) p;
        }
        return message;
    }

    private String getStr(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v != null ? v.toString() : null;
    }
}
