package com.athena.lms.accounting.dto.response;

import com.athena.lms.accounting.enums.EntryStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data @Builder
public class JournalEntryResponse {
    private UUID id;
    private String tenantId;
    private String reference;
    private String description;
    private LocalDate entryDate;
    private EntryStatus status;
    private String sourceEvent;
    private String sourceId;
    private BigDecimal totalDebit;
    private BigDecimal totalCredit;
    private String postedBy;
    private OffsetDateTime createdAt;
    private List<JournalLineResponse> lines;
}
