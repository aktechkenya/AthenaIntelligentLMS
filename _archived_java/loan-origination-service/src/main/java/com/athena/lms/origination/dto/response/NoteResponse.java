package com.athena.lms.origination.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data @Builder
public class NoteResponse {
    private UUID id;
    private String noteType;
    private String content;
    private String authorId;
    private OffsetDateTime createdAt;
}
