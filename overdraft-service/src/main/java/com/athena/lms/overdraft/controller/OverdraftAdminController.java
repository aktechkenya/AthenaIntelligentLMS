package com.athena.lms.overdraft.controller;

import com.athena.lms.common.auth.TenantContextHolder;
import com.athena.lms.overdraft.dto.response.OverdraftSummaryResponse;
import com.athena.lms.overdraft.service.OverdraftFacilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/overdraft")
@RequiredArgsConstructor
public class OverdraftAdminController {

    private final OverdraftFacilityService overdraftFacilityService;

    @GetMapping("/summary")
    public ResponseEntity<OverdraftSummaryResponse> getSummary() {
        return ResponseEntity.ok(overdraftFacilityService.getSummary(TenantContextHolder.getTenantId()));
    }
}
