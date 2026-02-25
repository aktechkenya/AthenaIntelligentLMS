package com.athena.lms.reporting.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "report_events")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 50)
    private String tenantId;

    @Column(name = "event_id", length = 100)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "event_category", length = 50)
    private String eventCategory;

    @Column(name = "source_service", length = 100)
    private String sourceService;

    @Column(name = "subject_id", length = 100)
    private String subjectId;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "amount", precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", length = 3)
    @Builder.Default
    private String currency = "KES";

    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    @Column(name = "occurred_at", nullable = false)
    @Builder.Default
    private Instant occurredAt = Instant.now();

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @PrePersist
    protected void prePersist() {
        if (occurredAt == null) occurredAt = Instant.now();
        if (createdAt == null) createdAt = Instant.now();
        if (currency == null) currency = "KES";
    }
}
