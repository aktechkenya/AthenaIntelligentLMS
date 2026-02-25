package com.athena.lms.account.service;

import com.athena.lms.account.dto.OrgSettingsRequest;
import com.athena.lms.account.dto.OrgSettingsResponse;
import com.athena.lms.account.entity.TenantSettings;
import com.athena.lms.account.repository.TenantSettingsRepository;
import com.athena.lms.common.auth.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrgSettingsService {

    private final TenantSettingsRepository settingsRepository;

    public OrgSettingsResponse getSettings() {
        String tenantId = TenantContextHolder.getTenantId();
        TenantSettings settings = settingsRepository.findById(tenantId)
            .orElseGet(() -> TenantSettings.builder().tenantId(tenantId).build());
        return toResponse(settings);
    }

    @Transactional
    public OrgSettingsResponse updateSettings(OrgSettingsRequest req) {
        String tenantId = TenantContextHolder.getTenantId();
        TenantSettings settings = settingsRepository.findById(tenantId)
            .orElseGet(() -> TenantSettings.builder().tenantId(tenantId).build());
        if (req.getCurrency() != null && !req.getCurrency().isBlank()) {
            settings.setCurrency(req.getCurrency().toUpperCase());
        }
        if (req.getOrgName() != null) settings.setOrgName(req.getOrgName());
        if (req.getCountryCode() != null) settings.setCountryCode(req.getCountryCode());
        if (req.getTimezone() != null) settings.setTimezone(req.getTimezone());
        return toResponse(settingsRepository.save(settings));
    }

    private OrgSettingsResponse toResponse(TenantSettings s) {
        return OrgSettingsResponse.builder()
            .tenantId(s.getTenantId())
            .currency(s.getCurrency())
            .orgName(s.getOrgName())
            .countryCode(s.getCountryCode())
            .timezone(s.getTimezone())
            .build();
    }
}
