package com.athena.lms.compliance.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "compliance_events")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 50)
    private String tenantId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "source_service", length = 100)
    private String sourceService;

    @Column(name = "subject_id", length = 100)
    private String subjectId;

    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
