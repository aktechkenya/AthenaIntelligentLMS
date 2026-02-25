package com.athena.lms.floatmgmt.controller;

import com.athena.lms.common.auth.TenantContextHolder;
import com.athena.lms.common.dto.PageResponse;
import com.athena.lms.floatmgmt.dto.request.CreateFloatAccountRequest;
import com.athena.lms.floatmgmt.dto.request.FloatDrawRequest;
import com.athena.lms.floatmgmt.dto.request.FloatRepayRequest;
import com.athena.lms.floatmgmt.dto.response.FloatAccountResponse;
import com.athena.lms.floatmgmt.dto.response.FloatSummaryResponse;
import com.athena.lms.floatmgmt.dto.response.FloatTransactionResponse;
import com.athena.lms.floatmgmt.service.FloatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/float")
@RequiredArgsConstructor
public class FloatController {

    private final FloatService floatService;

    @PostMapping("/accounts")
    public ResponseEntity<FloatAccountResponse> createAccount(
            @Valid @RequestBody CreateFloatAccountRequest request) {
        String tenantId = TenantContextHolder.getTenantId();
        FloatAccountResponse response = floatService.createAccount(request, tenantId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/accounts")
    public ResponseEntity<List<FloatAccountResponse>> listAccounts() {
        String tenantId = TenantContextHolder.getTenantId();
        return ResponseEntity.ok(floatService.listAccounts(tenantId));
    }

    @GetMapping("/accounts/{id}")
    public ResponseEntity<FloatAccountResponse> getAccount(@PathVariable UUID id) {
        String tenantId = TenantContextHolder.getTenantId();
        return ResponseEntity.ok(floatService.getAccount(id, tenantId));
    }

    @PostMapping("/accounts/{id}/draw")
    public ResponseEntity<FloatTransactionResponse> draw(
            @PathVariable UUID id,
            @Valid @RequestBody FloatDrawRequest request) {
        String tenantId = TenantContextHolder.getTenantId();
        return ResponseEntity.ok(floatService.draw(id, request, tenantId));
    }

    @PostMapping("/accounts/{id}/repay")
    public ResponseEntity<FloatTransactionResponse> repay(
            @PathVariable UUID id,
            @Valid @RequestBody FloatRepayRequest request) {
        String tenantId = TenantContextHolder.getTenantId();
        return ResponseEntity.ok(floatService.repay(id, request, tenantId));
    }

    @GetMapping("/accounts/{id}/transactions")
    public ResponseEntity<PageResponse<FloatTransactionResponse>> getTransactions(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String tenantId = TenantContextHolder.getTenantId();
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(floatService.getTransactions(id, tenantId, pageable));
    }

    @GetMapping("/summary")
    public ResponseEntity<FloatSummaryResponse> getSummary() {
        String tenantId = TenantContextHolder.getTenantId();
        return ResponseEntity.ok(floatService.getSummary(tenantId));
    }
}
