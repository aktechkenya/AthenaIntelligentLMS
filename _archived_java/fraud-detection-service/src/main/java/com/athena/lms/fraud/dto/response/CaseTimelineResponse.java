package com.athena.lms.fraud.dto.response;

import lombok.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaseTimelineResponse {
    private UUID caseId;
    private String caseNumber;
    private List<TimelineEvent> events;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimelineEvent {
        private String action;
        private String description;
        private String performedBy;
        private OffsetDateTime timestamp;
    }
}
