package com.athena.lms.fraud.dto.request;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class CreateWatchlistEntryRequest {
    private String listType; // PEP, SANCTIONS, INTERNAL_BLACKLIST, ADVERSE_MEDIA
    private String entryType; // INDIVIDUAL, ENTITY
    private String name;
    private String nationalId;
    private String phone;
    private String reason;
    private String source;
    private OffsetDateTime expiresAt;
}
