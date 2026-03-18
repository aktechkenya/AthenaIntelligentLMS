package com.athena.lms.origination.controller;

import com.athena.lms.common.auth.LmsJwtAuthenticationFilter;
import com.athena.lms.common.dto.PageResponse;
import com.athena.lms.origination.dto.request.*;
import com.athena.lms.origination.dto.response.*;
import com.athena.lms.origination.enums.ApplicationStatus;
import com.athena.lms.origination.service.LoanOriginationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/loan-applications")
@RequiredArgsConstructor
public class LoanOriginationController {

    private final LoanOriginationService service;

    @PostMapping
    public ResponseEntity<ApplicationResponse> create(@Valid @RequestBody CreateApplicationRequest req,
                                                       Authentication auth) {
        String tenantId = getTenantId(auth);
        String userId = auth.getName();
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req, tenantId, userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApplicationResponse> getById(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(service.getById(id, getTenantId(auth)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApplicationResponse> update(@PathVariable UUID id,
                                                       @Valid @RequestBody CreateApplicationRequest req,
                                                       Authentication auth) {
        return ResponseEntity.ok(service.update(id, req, getTenantId(auth), auth.getName()));
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<ApplicationResponse> submit(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(service.submit(id, getTenantId(auth), auth.getName()));
    }

    @PostMapping("/{id}/review/start")
    public ResponseEntity<ApplicationResponse> startReview(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(service.startReview(id, getTenantId(auth), auth.getName()));
    }

    @PostMapping("/{id}/review/approve")
    public ResponseEntity<ApplicationResponse> approve(@PathVariable UUID id,
                                                        @Valid @RequestBody ApproveApplicationRequest req,
                                                        Authentication auth) {
        return ResponseEntity.ok(service.approve(id, req, getTenantId(auth), auth.getName()));
    }

    @PostMapping("/{id}/review/reject")
    public ResponseEntity<ApplicationResponse> reject(@PathVariable UUID id,
                                                       @Valid @RequestBody RejectApplicationRequest req,
                                                       Authentication auth) {
        return ResponseEntity.ok(service.reject(id, req, getTenantId(auth), auth.getName()));
    }

    @PostMapping("/{id}/disburse")
    public ResponseEntity<ApplicationResponse> disburse(@PathVariable UUID id,
                                                          @Valid @RequestBody DisburseRequest req,
                                                          Authentication auth) {
        return ResponseEntity.ok(service.disburse(id, req, getTenantId(auth), auth.getName()));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApplicationResponse> cancel(@PathVariable UUID id,
                                                       @RequestParam(required = false) String reason,
                                                       Authentication auth) {
        return ResponseEntity.ok(service.cancel(id, reason, getTenantId(auth), auth.getName()));
    }

    @PostMapping("/{id}/collaterals")
    public ResponseEntity<CollateralResponse> addCollateral(@PathVariable UUID id,
                                                             @Valid @RequestBody AddCollateralRequest req,
                                                             Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addCollateral(id, req, getTenantId(auth)));
    }

    @PostMapping("/{id}/notes")
    public ResponseEntity<NoteResponse> addNote(@PathVariable UUID id,
                                                 @Valid @RequestBody AddNoteRequest req,
                                                 Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addNote(id, req, getTenantId(auth), auth.getName()));
    }

    @GetMapping
    public ResponseEntity<PageResponse<ApplicationResponse>> list(
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(service.list(getTenantId(auth), status, pageable));
    }

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<List<ApplicationResponse>> listByCustomer(@PathVariable String customerId,
                                                                      Authentication auth) {
        return ResponseEntity.ok(service.listByCustomer(customerId, getTenantId(auth)));
    }

    private String getTenantId(Authentication auth) {
        Object details = auth.getDetails();
        if (details instanceof org.springframework.security.web.authentication.WebAuthenticationDetails) {
            // fall back to principal name as tenant — filter sets it
        }
        // TenantId is stored in the JWT claims by LmsJwtAuthenticationFilter
        if (auth.getCredentials() instanceof String tenantId && !tenantId.isBlank()) {
            return tenantId;
        }
        return auth.getName();
    }
}
