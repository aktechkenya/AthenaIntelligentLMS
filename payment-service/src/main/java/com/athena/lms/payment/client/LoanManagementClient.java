package com.athena.lms.payment.client;

import com.athena.lms.common.exception.BusinessException;
import com.athena.lms.common.exception.ResourceNotFoundException;
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
 * Validates that a loanId references a real, active loan before accepting a payment.
 * Fails open on infrastructure errors so payments aren't blocked by loan-management downtime.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoanManagementClient {

    private final RestTemplate restTemplate;

    @Value("${athena.loan-management.url:http://lms-loan-management-service:8089}")
    private String loanManagementUrl;

    public void validateLoanExists(UUID loanId) {
        if (loanId == null) return; // loanId is optional on payments
        try {
            HttpHeaders headers = new HttpHeaders();
            String authHeader = currentAuthHeader();
            if (authHeader != null) headers.set("Authorization", authHeader);

            ResponseEntity<Map> response = restTemplate.exchange(
                loanManagementUrl + "/api/v1/loans/" + loanId,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class
            );

            Map<?, ?> body = response.getBody();
            if (body == null) {
                throw new ResourceNotFoundException("Loan", loanId.toString());
            }
            Object status = body.get("status");
            if ("CLOSED".equals(status) || "WRITTEN_OFF".equals(status)) {
                throw new BusinessException("Loan " + loanId + " is not eligible for payment (status=" + status + ")");
            }
        } catch (BusinessException | ResourceNotFoundException e) {
            throw e;
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResourceNotFoundException("Loan", loanId.toString());
        } catch (HttpClientErrorException.Forbidden | HttpClientErrorException.Unauthorized e) {
            log.warn("Loan management auth error for loanId={}, proceeding: {}", loanId, e.getMessage());
        } catch (Exception e) {
            log.warn("Loan management unavailable, skipping loanId validation for {}: {}", loanId, e.getMessage());
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
