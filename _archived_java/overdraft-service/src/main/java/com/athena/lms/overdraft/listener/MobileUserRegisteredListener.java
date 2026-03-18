package com.athena.lms.overdraft.listener;

import com.athena.lms.common.config.LmsRabbitMQConfig;
import com.athena.lms.overdraft.dto.request.CreateWalletRequest;
import com.athena.lms.overdraft.repository.CustomerWalletRepository;
import com.athena.lms.overdraft.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Consumes mobile.user.registered events from athena.lms.overdraft.mobile.queue.
 * Auto-provisions a wallet for newly registered mobile users.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MobileUserRegisteredListener {

    private final WalletService walletService;
    private final CustomerWalletRepository walletRepository;

    @RabbitListener(queues = LmsRabbitMQConfig.OVERDRAFT_MOBILE_QUEUE)
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

            if (customerId == null) {
                log.warn("mobile.user.registered event missing customerId, skipping");
                return;
            }

            log.info("Processing mobile.user.registered for customer [{}] tenant [{}]", customerId, tenantId);

            // Check if wallet already exists for this customer
            if (walletRepository.existsByTenantIdAndCustomerId(tenantId, customerId)) {
                log.info("Wallet already exists for customer [{}] in tenant [{}], skipping", customerId, tenantId);
                return;
            }

            CreateWalletRequest req = new CreateWalletRequest();
            req.setCustomerId(customerId);
            req.setCurrency("KES");

            walletService.createWallet(req, tenantId);
            log.info("Auto-provisioned wallet for mobile customer [{}] in tenant [{}]", customerId, tenantId);

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
