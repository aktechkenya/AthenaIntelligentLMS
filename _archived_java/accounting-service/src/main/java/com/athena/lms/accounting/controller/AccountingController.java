package com.athena.lms.accounting.controller;

import com.athena.lms.common.auth.TenantContextHolder;
import com.athena.lms.common.dto.PageResponse;
import com.athena.lms.accounting.dto.request.*;
import com.athena.lms.accounting.dto.response.*;
import com.athena.lms.accounting.enums.AccountType;
import com.athena.lms.accounting.service.AccountingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounting")
@RequiredArgsConstructor
public class AccountingController {

    private final AccountingService service;

    // ─── Chart of Accounts ───────────────────────────────────────────────────────

    @PostMapping("/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse createAccount(@Valid @RequestBody CreateAccountRequest req,
                                          HttpServletRequest httpReq) {
        return service.createAccount(req, tenantId(httpReq));
    }

    @GetMapping("/accounts")
    public List<AccountResponse> listAccounts(
            @RequestParam(required = false) AccountType type,
            HttpServletRequest httpReq) {
        return service.listAccounts(tenantId(httpReq), type);
    }

    @GetMapping("/accounts/{id}")
    public AccountResponse getAccount(@PathVariable UUID id, HttpServletRequest httpReq) {
        return service.getAccount(id, tenantId(httpReq));
    }

    @GetMapping("/accounts/code/{code}")
    public AccountResponse getAccountByCode(@PathVariable String code, HttpServletRequest httpReq) {
        return service.getAccountByCode(code, tenantId(httpReq));
    }

    // ─── Journal Entries ──────────────────────────────────────────────────────────

    @PostMapping("/journal-entries")
    @ResponseStatus(HttpStatus.CREATED)
    public JournalEntryResponse postEntry(@Valid @RequestBody PostJournalEntryRequest req,
                                           Authentication auth, HttpServletRequest httpReq) {
        return service.postEntry(req, tenantId(httpReq), auth.getName());
    }

    @GetMapping("/journal-entries")
    public PageResponse<JournalEntryResponse> listEntries(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest httpReq) {
        return service.listEntries(tenantId(httpReq), from, to,
            PageRequest.of(page, size, Sort.by("entryDate").descending()));
    }

    @GetMapping("/journal-entries/{id}")
    public JournalEntryResponse getEntry(@PathVariable UUID id, HttpServletRequest httpReq) {
        return service.getEntry(id, tenantId(httpReq));
    }

    // ─── Balances ─────────────────────────────────────────────────────────────────

    @GetMapping("/accounts/{id}/balance")
    public BalanceResponse getBalance(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().getYear()}") int year,
            @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().getMonthValue()}") int month,
            HttpServletRequest httpReq) {
        return service.getBalance(id, tenantId(httpReq), year, month);
    }

    @GetMapping("/accounts/{id}/ledger")
    public List<JournalLineResponse> getLedger(@PathVariable UUID id, HttpServletRequest httpReq) {
        return service.getLedger(id, tenantId(httpReq));
    }

    // ─── Trial Balance ────────────────────────────────────────────────────────────

    @GetMapping("/trial-balance")
    public TrialBalanceResponse getTrialBalance(
            @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().getYear()}") int year,
            @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().getMonthValue()}") int month,
            HttpServletRequest httpReq) {
        return service.getTrialBalance(tenantId(httpReq), year, month);
    }

    private String tenantId(HttpServletRequest req) {
        String tid = (String) req.getAttribute("tenantId");
        return tid != null ? tid : TenantContextHolder.getTenantIdOrDefault();
    }
}
