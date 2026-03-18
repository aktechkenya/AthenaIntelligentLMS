package com.athena.lms.account.controller;

import com.athena.lms.account.dto.request.TransferRequest;
import com.athena.lms.account.dto.response.TransferResponse;
import com.athena.lms.account.service.TransferService;
import com.athena.lms.common.auth.TenantContextHolder;
import com.athena.lms.common.dto.PageResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transfers")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransferResponse initiateTransfer(
            @Valid @RequestBody TransferRequest req,
            HttpServletRequest httpRequest) {
        String userId = (String) httpRequest.getAttribute("userId");
        return transferService.initiateTransfer(req, getTenantId(httpRequest),
                userId != null ? userId : "system");
    }

    @GetMapping("/{id}")
    public TransferResponse getTransfer(@PathVariable UUID id, HttpServletRequest httpRequest) {
        return transferService.getTransfer(id, getTenantId(httpRequest));
    }

    @GetMapping("/account/{accountId}")
    public PageResponse<TransferResponse> getTransfersByAccount(
            @PathVariable UUID accountId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest httpRequest) {
        return transferService.getTransfersByAccount(accountId, getTenantId(httpRequest),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "initiatedAt")));
    }

    private String getTenantId(HttpServletRequest req) {
        String tid = (String) req.getAttribute("tenantId");
        return tid != null ? tid : TenantContextHolder.getTenantIdOrDefault();
    }
}
