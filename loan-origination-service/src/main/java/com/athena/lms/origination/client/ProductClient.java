package com.athena.lms.origination.client;

import com.athena.lms.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;
import java.util.UUID;

/**
 * Calls product-service to validate a product exists and is ACTIVE before a
 * loan application is created. Fails open (logs a warning) if product-service
 * is unreachable so that origination is not blocked by an infra issue.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductClient {

    private final RestTemplate restTemplate;

    @Value("${athena.product.url:http://lms-product-service:8087}")
    private String productServiceUrl;

    public void validateProductActiveAndExists(UUID productId) {
        if (productId == null) {
            throw new BusinessException("productId must not be null");
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            String authHeader = currentAuthHeader();
            if (authHeader != null) {
                headers.set("Authorization", authHeader);
            }

            ResponseEntity<Map> response = restTemplate.exchange(
                productServiceUrl + "/api/v1/products/" + productId,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class
            );

            Map<?, ?> body = response.getBody();
            if (body == null) {
                throw new BusinessException("Product not found: " + productId);
            }
            String status = (String) body.get("status");
            if (!"ACTIVE".equals(status)) {
                throw new BusinessException(
                    "Product " + productId + " is not available for new applications (status=" + status + ")");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (HttpClientErrorException.NotFound e) {
            throw new BusinessException("Product not found: " + productId);
        } catch (HttpClientErrorException.Forbidden | HttpClientErrorException.Unauthorized e) {
            // product-service auth issue — log and fail-open so ops can investigate
            log.warn("Product service auth error for productId={}, proceeding without validation: {}", productId, e.getMessage());
        } catch (Exception e) {
            // product-service is unreachable — fail-open, don't block origination
            log.warn("Product service unavailable, skipping validation for productId={}: {}", productId, e.getMessage());
        }
    }

    private String currentAuthHeader() {
        try {
            ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            return attrs.getRequest().getHeader("Authorization");
        } catch (Exception e) {
            return null;
        }
    }
}
