package com.athena.lms.fraud.dto.response;

import com.athena.lms.fraud.enums.WatchlistType;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
public class WatchlistEntryResponse {
    private UUID id;
    private String tenantId;
    private WatchlistType listType;
    private String entryType;
    private String name;
    private String nationalId;
    private String phone;
    private String reason;
    private String source;
    private Boolean active;
    private OffsetDateTime expiresAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
