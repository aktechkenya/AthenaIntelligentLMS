package com.athena.lms.overdraft.controller;

import com.athena.lms.common.auth.TenantContextHolder;
import com.athena.lms.common.dto.PageResponse;
import com.athena.lms.overdraft.dto.request.CreateWalletRequest;
import com.athena.lms.overdraft.dto.request.WalletTransactionRequest;
import com.athena.lms.overdraft.dto.response.InterestChargeResponse;
import com.athena.lms.overdraft.dto.response.OverdraftFacilityResponse;
import com.athena.lms.overdraft.dto.response.WalletResponse;
import com.athena.lms.overdraft.dto.response.WalletTransactionResponse;
import com.athena.lms.overdraft.service.OverdraftFacilityService;
import com.athena.lms.overdraft.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;
    private final OverdraftFacilityService overdraftFacilityService;

    @PostMapping
    public ResponseEntity<WalletResponse> createWallet(@Valid @RequestBody CreateWalletRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(walletService.createWallet(req, TenantContextHolder.getTenantId()));
    }

    @GetMapping
    public ResponseEntity<List<WalletResponse>> listWallets() {
        return ResponseEntity.ok(walletService.listWallets(TenantContextHolder.getTenantId()));
    }

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<WalletResponse> getWalletByCustomer(@PathVariable String customerId) {
        return ResponseEntity.ok(walletService.getWalletByCustomer(customerId, TenantContextHolder.getTenantId()));
    }

    @GetMapping("/{walletId}")
    public ResponseEntity<WalletResponse> getWallet(@PathVariable UUID walletId) {
        return ResponseEntity.ok(walletService.getWallet(walletId, TenantContextHolder.getTenantId()));
    }

    @PostMapping("/{walletId}/deposit")
    public ResponseEntity<WalletTransactionResponse> deposit(
            @PathVariable UUID walletId,
            @Valid @RequestBody WalletTransactionRequest req) {
        return ResponseEntity.ok(walletService.deposit(walletId, req, TenantContextHolder.getTenantId()));
    }

    @PostMapping("/{walletId}/withdraw")
    public ResponseEntity<WalletTransactionResponse> withdraw(
            @PathVariable UUID walletId,
            @Valid @RequestBody WalletTransactionRequest req) {
        return ResponseEntity.ok(walletService.withdraw(walletId, req, TenantContextHolder.getTenantId()));
    }

    @GetMapping("/{walletId}/transactions")
    public ResponseEntity<PageResponse<WalletTransactionResponse>> getTransactions(
            @PathVariable UUID walletId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(walletService.getTransactions(
            walletId, TenantContextHolder.getTenantId(), PageRequest.of(page, size)));
    }

    @PostMapping("/{walletId}/overdraft/apply")
    public ResponseEntity<OverdraftFacilityResponse> applyOverdraft(@PathVariable UUID walletId) {
        return ResponseEntity.ok(overdraftFacilityService.applyForOverdraft(walletId, TenantContextHolder.getTenantId()));
    }

    @GetMapping("/{walletId}/overdraft")
    public ResponseEntity<OverdraftFacilityResponse> getFacility(@PathVariable UUID walletId) {
        return ResponseEntity.ok(overdraftFacilityService.getFacility(walletId, TenantContextHolder.getTenantId()));
    }

    @PostMapping("/{walletId}/overdraft/suspend")
    public ResponseEntity<OverdraftFacilityResponse> suspendFacility(@PathVariable UUID walletId) {
        return ResponseEntity.ok(overdraftFacilityService.suspendFacility(walletId, TenantContextHolder.getTenantId()));
    }

    @GetMapping("/{walletId}/overdraft/charges")
    public ResponseEntity<List<InterestChargeResponse>> getCharges(@PathVariable UUID walletId) {
        return ResponseEntity.ok(overdraftFacilityService.getCharges(walletId, TenantContextHolder.getTenantId()));
    }
}
