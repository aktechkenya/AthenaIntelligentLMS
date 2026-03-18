package com.athena.lms.account.dto;

import lombok.Data;

@Data
public class OrgSettingsRequest {
    private String currency;
    private String orgName;
    private String countryCode;
    private String timezone;
}
