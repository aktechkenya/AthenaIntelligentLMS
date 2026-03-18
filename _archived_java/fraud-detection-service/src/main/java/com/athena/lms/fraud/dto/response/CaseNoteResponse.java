package com.athena.lms.fraud.dto.response;

import lombok.Data;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
public class CaseNoteResponse {
    private UUID id;
    private String author;
    private String content;
    private String noteType;
    private OffsetDateTime createdAt;
}
