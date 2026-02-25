package com.athena.lms.product.controller;

import com.athena.lms.common.auth.TenantContextHolder;
import com.athena.lms.common.dto.PageResponse;
import com.athena.lms.product.dto.request.CreateProductRequest;
import com.athena.lms.product.dto.request.SimulateScheduleRequest;
import com.athena.lms.product.dto.response.ProductResponse;
import com.athena.lms.product.dto.response.ScheduleResponse;
import com.athena.lms.product.service.ProductService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','LOAN_OFFICER','PRODUCT_MANAGER')")
    public ProductResponse createProduct(
            @Valid @RequestBody CreateProductRequest req,
            HttpServletRequest httpRequest) {
        return productService.createProduct(req, getTenantId(httpRequest), getUsername());
    }

    @GetMapping
    public PageResponse<ProductResponse> listProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest httpRequest) {
        return productService.listProducts(getTenantId(httpRequest),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @GetMapping("/{id}")
    public ProductResponse getProduct(@PathVariable UUID id, HttpServletRequest httpRequest) {
        return productService.getProduct(id, getTenantId(httpRequest));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','LOAN_OFFICER','PRODUCT_MANAGER')")
    public ProductResponse updateProduct(
            @PathVariable UUID id,
            @Valid @RequestBody CreateProductRequest req,
            @RequestParam(required = false, defaultValue = "Product update") String changeReason,
            HttpServletRequest httpRequest) {
        return productService.updateProduct(id, req, getTenantId(httpRequest), getUsername(), changeReason);
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAnyRole('ADMIN','PRODUCT_MANAGER')")
    public ProductResponse activateProduct(@PathVariable UUID id, HttpServletRequest httpRequest) {
        return productService.activateProduct(id, getTenantId(httpRequest), getUsername());
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAnyRole('ADMIN','PRODUCT_MANAGER')")
    public ProductResponse deactivateProduct(@PathVariable UUID id, HttpServletRequest httpRequest) {
        return productService.deactivateProduct(id, getTenantId(httpRequest));
    }

    @PostMapping("/{id}/pause")
    @PreAuthorize("hasAnyRole('ADMIN','PRODUCT_MANAGER')")
    public ProductResponse pauseProduct(@PathVariable UUID id, HttpServletRequest httpRequest) {
        return productService.pauseProduct(id, getTenantId(httpRequest), getUsername());
    }

    @PostMapping("/{id}/simulate")
    public ScheduleResponse simulateSchedule(
            @PathVariable UUID id,
            @Valid @RequestBody SimulateScheduleRequest req,
            HttpServletRequest httpRequest) {
        return productService.simulateSchedule(id, req, getTenantId(httpRequest));
    }

    @GetMapping("/{id}/versions")
    public List<?> getProductVersions(@PathVariable UUID id, HttpServletRequest httpRequest) {
        return productService.getProductVersions(id, getTenantId(httpRequest));
    }

    @PostMapping("/from-template/{code}")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','LOAN_OFFICER','PRODUCT_MANAGER')")
    public ProductResponse createFromTemplate(
            @PathVariable String code,
            HttpServletRequest httpRequest) {
        return productService.createFromTemplate(code, getTenantId(httpRequest), getUsername());
    }

    private String getTenantId(HttpServletRequest req) {
        String tid = (String) req.getAttribute("tenantId");
        return tid != null ? tid : TenantContextHolder.getTenantIdOrDefault();
    }

    private String getUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "system";
    }
}
