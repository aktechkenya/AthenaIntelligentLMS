package com.athena.lms.account.controller;

import com.athena.lms.account.dto.OrgSettingsRequest;
import com.athena.lms.account.dto.OrgSettingsResponse;
import com.athena.lms.account.service.OrgSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/organization")
@RequiredArgsConstructor
public class OrgSettingsController {

    private final OrgSettingsService settingsService;

    @GetMapping("/settings")
    public ResponseEntity<OrgSettingsResponse> getSettings() {
        return ResponseEntity.ok(settingsService.getSettings());
    }

    @PutMapping("/settings")
    public ResponseEntity<OrgSettingsResponse> updateSettings(@RequestBody OrgSettingsRequest req) {
        return ResponseEntity.ok(settingsService.updateSettings(req));
    }
}
