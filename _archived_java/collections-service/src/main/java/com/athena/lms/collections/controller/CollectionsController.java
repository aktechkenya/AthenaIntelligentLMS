package com.athena.lms.collections.controller;

import com.athena.lms.collections.dto.request.AddActionRequest;
import com.athena.lms.collections.dto.request.AddPtpRequest;
import com.athena.lms.collections.dto.request.UpdateCaseRequest;
import com.athena.lms.collections.dto.response.*;
import com.athena.lms.collections.enums.CaseStatus;
import com.athena.lms.collections.service.CollectionsService;
import com.athena.lms.common.auth.TenantContextHolder;
import com.athena.lms.common.dto.PageResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/collections")
@RequiredArgsConstructor
public class CollectionsController {

    private final CollectionsService collectionsService;

    // -----------------------------------------------------------------------
    // Cases
    // -----------------------------------------------------------------------

    @GetMapping("/cases")
    public PageResponse<CollectionCaseResponse> listCases(
            @RequestParam(required = false) CaseStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest req) {
        Pageable pageable = PageRequest.of(page, size);
        return collectionsService.listCases(tenantId(req), status, pageable);
    }

    @GetMapping("/cases/{id}")
    public CollectionCaseResponse getCase(@PathVariable UUID id, HttpServletRequest req) {
        return collectionsService.getCase(id, tenantId(req));
    }

    @GetMapping("/cases/loan/{loanId}")
    public CollectionCaseResponse getCaseByLoan(@PathVariable UUID loanId, HttpServletRequest req) {
        return collectionsService.getCaseByLoan(loanId, tenantId(req));
    }

    @PutMapping("/cases/{id}")
    public CollectionCaseResponse updateCase(@PathVariable UUID id,
                                              @RequestBody UpdateCaseRequest request,
                                              HttpServletRequest req) {
        return collectionsService.updateCase(id, request, tenantId(req));
    }

    @PostMapping("/cases/{id}/close")
    public CollectionCaseResponse closeCase(@PathVariable UUID id, HttpServletRequest req) {
        return collectionsService.closeCase(id, tenantId(req));
    }

    // -----------------------------------------------------------------------
    // Actions
    // -----------------------------------------------------------------------

    @PostMapping("/cases/{id}/actions")
    @ResponseStatus(HttpStatus.CREATED)
    public CollectionActionResponse addAction(@PathVariable UUID id,
                                               @Valid @RequestBody AddActionRequest request,
                                               HttpServletRequest req) {
        return collectionsService.addAction(id, request, tenantId(req));
    }

    @GetMapping("/cases/{id}/actions")
    public List<CollectionActionResponse> listActions(@PathVariable UUID id, HttpServletRequest req) {
        return collectionsService.listActions(id, tenantId(req));
    }

    // -----------------------------------------------------------------------
    // PTPs
    // -----------------------------------------------------------------------

    @PostMapping("/cases/{id}/ptps")
    @ResponseStatus(HttpStatus.CREATED)
    public PtpResponse addPtp(@PathVariable UUID id,
                               @Valid @RequestBody AddPtpRequest request,
                               HttpServletRequest req) {
        return collectionsService.addPtp(id, request, tenantId(req));
    }

    @GetMapping("/cases/{id}/ptps")
    public List<PtpResponse> listPtps(@PathVariable UUID id, HttpServletRequest req) {
        return collectionsService.listPtps(id, tenantId(req));
    }

    // -----------------------------------------------------------------------
    // Summary
    // -----------------------------------------------------------------------

    @GetMapping("/summary")
    public CollectionSummaryResponse getSummary(HttpServletRequest req) {
        return collectionsService.getSummary(tenantId(req));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private String tenantId(HttpServletRequest req) {
        String tid = (String) req.getAttribute("tenantId");
        return tid != null ? tid : TenantContextHolder.getTenantIdOrDefault();
    }
}
