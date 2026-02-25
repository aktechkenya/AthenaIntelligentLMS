package com.athena.lms.account.controller;

import com.athena.lms.account.dto.request.CreateAccountRequest;
import com.athena.lms.account.dto.request.TransactionRequest;
import com.athena.lms.account.dto.response.AccountResponse;
import com.athena.lms.account.dto.response.BalanceResponse;
import com.athena.lms.account.dto.response.TransactionResponse;
import com.athena.lms.account.service.AccountService;
import com.athena.lms.common.auth.TenantContextHolder;
import com.athena.lms.common.dto.PageResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse createAccount(
            @Valid @RequestBody CreateAccountRequest req,
            HttpServletRequest httpRequest) {
        return accountService.createAccount(req, getTenantId(httpRequest));
    }

    @GetMapping
    public PageResponse<AccountResponse> listAccounts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest httpRequest) {
        return accountService.listAccounts(getTenantId(httpRequest),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
    }


    @GetMapping("/{id}")
    public AccountResponse getAccount(@PathVariable UUID id, HttpServletRequest httpRequest) {
        return accountService.getAccount(id, getTenantId(httpRequest));
    }

    @GetMapping("/{id}/balance")
    public BalanceResponse getBalance(@PathVariable UUID id, HttpServletRequest httpRequest) {
        return accountService.getBalance(id, getTenantId(httpRequest));
    }

    @PostMapping("/{id}/credit")
    public TransactionResponse credit(
            @PathVariable UUID id,
            @Valid @RequestBody TransactionRequest req,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest httpRequest) {
        if (idempotencyKey != null && req.getIdempotencyKey() == null) {
            req.setIdempotencyKey(idempotencyKey);
        }
        return accountService.credit(id, req, getTenantId(httpRequest));
    }

    @PostMapping("/{id}/debit")
    public TransactionResponse debit(
            @PathVariable UUID id,
            @Valid @RequestBody TransactionRequest req,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest httpRequest) {
        if (idempotencyKey != null && req.getIdempotencyKey() == null) {
            req.setIdempotencyKey(idempotencyKey);
        }
        return accountService.debit(id, req, getTenantId(httpRequest));
    }

    @GetMapping("/{id}/transactions")
    public PageResponse<TransactionResponse> getTransactionHistory(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest httpRequest) {
        return accountService.getTransactionHistory(id, getTenantId(httpRequest),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @GetMapping("/{id}/mini-statement")
    public List<TransactionResponse> getMiniStatement(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "10") int count,
            HttpServletRequest httpRequest) {
        return accountService.getMiniStatement(id, getTenantId(httpRequest), count);
    }

    @GetMapping("/search")
    public List<AccountResponse> searchAccounts(
            @RequestParam String q,
            HttpServletRequest httpRequest) {
        return accountService.searchAccounts(q, getTenantId(httpRequest));
    }

    @GetMapping("/customer/{customerId}")
    public List<AccountResponse> getByCustomerId(
            @PathVariable String customerId,
            HttpServletRequest httpRequest) {
        return accountService.getByCustomerId(customerId, getTenantId(httpRequest));
    }

    private String getTenantId(HttpServletRequest req) {
        String tid = (String) req.getAttribute("tenantId");
        return tid != null ? tid : TenantContextHolder.getTenantIdOrDefault();
    }
}
