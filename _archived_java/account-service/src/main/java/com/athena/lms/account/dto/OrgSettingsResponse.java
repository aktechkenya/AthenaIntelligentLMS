package com.athena.lms.account.dto;

import lombok.Builder;
import lombok.Data;

@Data @Builder
public class OrgSettingsResponse {
    private String tenantId;
    private String currency;
    private String orgName;
    private String countryCode;
    private String timezone;
}
