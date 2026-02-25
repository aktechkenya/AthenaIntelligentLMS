package com.athena.lms.payment.controller;

import com.athena.lms.common.auth.TenantContextHolder;
import com.athena.lms.common.dto.PageResponse;
import com.athena.lms.payment.dto.request.*;
import com.athena.lms.payment.dto.response.PaymentMethodResponse;
import com.athena.lms.payment.dto.response.PaymentResponse;
import com.athena.lms.payment.enums.PaymentStatus;
import com.athena.lms.payment.enums.PaymentType;
import com.athena.lms.payment.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse initiate(@Valid @RequestBody InitiatePaymentRequest req,
                                     Authentication auth, HttpServletRequest httpReq) {
        return service.initiate(req, tenantId(httpReq), auth.getName());
    }

    @GetMapping("/{id}")
    public PaymentResponse getById(@PathVariable UUID id, HttpServletRequest httpReq) {
        return service.getById(id, tenantId(httpReq));
    }

    @GetMapping
    public PageResponse<PaymentResponse> list(
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) PaymentType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest httpReq) {
        return service.list(tenantId(httpReq), status, type,
            PageRequest.of(page, size, Sort.by("createdAt").descending()));
    }

    @GetMapping("/customer/{customerId}")
    public List<PaymentResponse> listByCustomer(@PathVariable String customerId, HttpServletRequest httpReq) {
        return service.listByCustomer(customerId, tenantId(httpReq));
    }

    @GetMapping("/reference/{ref}")
    public PaymentResponse getByReference(@PathVariable String ref) {
        return service.getByReference(ref);
    }

    @PostMapping("/{id}/process")
    public PaymentResponse process(@PathVariable UUID id, HttpServletRequest httpReq) {
        return service.process(id, tenantId(httpReq));
    }

    @PostMapping("/{id}/complete")
    public PaymentResponse complete(@PathVariable UUID id,
                                     @RequestBody(required = false) CompletePaymentRequest req,
                                     HttpServletRequest httpReq) {
        return service.complete(id, req != null ? req : new CompletePaymentRequest(), tenantId(httpReq));
    }

    @PostMapping("/{id}/fail")
    public PaymentResponse fail(@PathVariable UUID id,
                                 @Valid @RequestBody FailPaymentRequest req,
                                 HttpServletRequest httpReq) {
        return service.fail(id, req, tenantId(httpReq));
    }

    @PostMapping("/{id}/reverse")
    public PaymentResponse reverse(@PathVariable UUID id,
                                    @Valid @RequestBody ReversePaymentRequest req,
                                    HttpServletRequest httpReq) {
        return service.reverse(id, req, tenantId(httpReq));
    }

    @PostMapping("/methods")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentMethodResponse addMethod(@Valid @RequestBody AddPaymentMethodRequest req,
                                            HttpServletRequest httpReq) {
        return service.addPaymentMethod(req, tenantId(httpReq));
    }

    @GetMapping("/methods/customer/{customerId}")
    public List<PaymentMethodResponse> getMethods(@PathVariable String customerId, HttpServletRequest httpReq) {
        return service.getPaymentMethods(customerId, tenantId(httpReq));
    }

    private String tenantId(HttpServletRequest req) {
        String tid = (String) req.getAttribute("tenantId");
        return tid != null ? tid : TenantContextHolder.getTenantIdOrDefault();
    }
}
