package com.athena.lms.product.controller;

import com.athena.lms.common.auth.TenantContextHolder;
import com.athena.lms.common.dto.PageResponse;
import com.athena.lms.product.dto.request.CreateChargeRequest;
import com.athena.lms.product.dto.response.ChargeCalculationResponse;
import com.athena.lms.product.dto.response.TransactionChargeResponse;
import com.athena.lms.product.service.ChargeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/charges")
@RequiredArgsConstructor
public class ChargeController {

    private final ChargeService chargeService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionChargeResponse createCharge(
            @Valid @RequestBody CreateChargeRequest req,
            HttpServletRequest httpRequest) {
        return chargeService.createCharge(req, getTenantId(httpRequest));
    }

    @GetMapping
    public PageResponse<TransactionChargeResponse> listCharges(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest httpRequest) {
        return chargeService.listCharges(getTenantId(httpRequest),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @GetMapping("/{id}")
    public TransactionChargeResponse getCharge(@PathVariable UUID id, HttpServletRequest httpRequest) {
        return chargeService.getCharge(id, getTenantId(httpRequest));
    }

    @PutMapping("/{id}")
    public TransactionChargeResponse updateCharge(
            @PathVariable UUID id,
            @Valid @RequestBody CreateChargeRequest req,
            HttpServletRequest httpRequest) {
        return chargeService.updateCharge(id, req, getTenantId(httpRequest));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCharge(@PathVariable UUID id, HttpServletRequest httpRequest) {
        chargeService.deleteCharge(id, getTenantId(httpRequest));
    }

    @GetMapping("/calculate")
    public ChargeCalculationResponse calculateCharge(
            @RequestParam String transactionType,
            @RequestParam BigDecimal amount,
            HttpServletRequest httpRequest) {
        return chargeService.calculateCharge(transactionType, amount, getTenantId(httpRequest));
    }

    private String getTenantId(HttpServletRequest req) {
        String tid = (String) req.getAttribute("tenantId");
        return tid != null ? tid : TenantContextHolder.getTenantIdOrDefault();
    }
}
