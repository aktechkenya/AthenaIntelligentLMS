package com.athena.lms.account.controller;

import com.athena.lms.account.dto.request.CreateCustomerRequest;
import com.athena.lms.account.dto.request.UpdateCustomerRequest;
import com.athena.lms.account.dto.response.CustomerResponse;
import com.athena.lms.account.service.CustomerService;
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
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponse createCustomer(
            @Valid @RequestBody CreateCustomerRequest req,
            HttpServletRequest httpRequest) {
        return customerService.createCustomer(req, getTenantId(httpRequest));
    }

    @GetMapping
    public PageResponse<CustomerResponse> listCustomers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest httpRequest) {
        return customerService.listCustomers(getTenantId(httpRequest),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @GetMapping("/{id}")
    public CustomerResponse getCustomer(@PathVariable UUID id, HttpServletRequest httpRequest) {
        return customerService.getCustomer(id, getTenantId(httpRequest));
    }

    @PutMapping("/{id}")
    public CustomerResponse updateCustomer(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCustomerRequest req,
            HttpServletRequest httpRequest) {
        return customerService.updateCustomer(id, req, getTenantId(httpRequest));
    }

    @PatchMapping("/{id}/status")
    public CustomerResponse updateStatus(
            @PathVariable UUID id,
            @RequestParam String status,
            HttpServletRequest httpRequest) {
        return customerService.updateStatus(id, status, getTenantId(httpRequest));
    }

    @GetMapping("/search")
    public List<CustomerResponse> searchCustomers(
            @RequestParam String q,
            HttpServletRequest httpRequest) {
        return customerService.searchCustomers(q, getTenantId(httpRequest));
    }

    private String getTenantId(HttpServletRequest req) {
        String tid = (String) req.getAttribute("tenantId");
        return tid != null ? tid : TenantContextHolder.getTenantIdOrDefault();
    }
}
