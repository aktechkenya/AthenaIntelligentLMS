package com.athena.lms.management.controller;

import com.athena.lms.common.dto.PageResponse;
import com.athena.lms.management.dto.request.RepaymentRequest;
import com.athena.lms.management.dto.request.RestructureRequest;
import com.athena.lms.management.dto.response.*;
import com.athena.lms.management.enums.LoanStatus;
import com.athena.lms.management.service.LoanManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/loans")
@RequiredArgsConstructor
public class LoanManagementController {

    private final LoanManagementService service;

    @GetMapping("/{id}")
    public ResponseEntity<LoanResponse> getById(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(service.getById(id, getTenantId(auth)));
    }

    @GetMapping("/{id}/schedule")
    public ResponseEntity<List<InstallmentResponse>> getSchedule(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(service.getSchedule(id, getTenantId(auth)));
    }

    @GetMapping("/{id}/schedule/{no}")
    public ResponseEntity<InstallmentResponse> getInstallment(@PathVariable UUID id,
                                                               @PathVariable Integer no,
                                                               Authentication auth) {
        return ResponseEntity.ok(service.getInstallment(id, no, getTenantId(auth)));
    }

    @GetMapping("/{id}/repayments")
    public ResponseEntity<List<RepaymentResponse>> getRepayments(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(service.getRepayments(id, getTenantId(auth)));
    }

    @GetMapping("/{id}/dpd")
    public ResponseEntity<DpdResponse> getDpd(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(service.getDpd(id, getTenantId(auth)));
    }

    @PostMapping("/{id}/repayments")
    public ResponseEntity<RepaymentResponse> applyRepayment(@PathVariable UUID id,
                                                             @Valid @RequestBody RepaymentRequest req,
                                                             Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(service.applyRepayment(id, req, getTenantId(auth), auth.getName()));
    }

    @PostMapping("/{id}/restructure")
    public ResponseEntity<LoanResponse> restructure(@PathVariable UUID id,
                                                     @Valid @RequestBody RestructureRequest req,
                                                     Authentication auth) {
        return ResponseEntity.ok(service.restructure(id, req, getTenantId(auth)));
    }

    @GetMapping
    public ResponseEntity<PageResponse<LoanResponse>> list(
            @RequestParam(required = false) LoanStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(service.list(getTenantId(auth), status, pageable));
    }

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<List<LoanResponse>> listByCustomer(@PathVariable String customerId,
                                                              Authentication auth) {
        return ResponseEntity.ok(service.listByCustomer(customerId, getTenantId(auth)));
    }

    private String getTenantId(Authentication auth) {
        if (auth.getCredentials() instanceof String tenantId && !tenantId.isBlank()) {
            return tenantId;
        }
        return auth.getName();
    }
}
