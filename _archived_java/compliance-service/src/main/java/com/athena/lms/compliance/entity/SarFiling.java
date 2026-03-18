package com.athena.lms.compliance.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "sar_filings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SarFiling {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 50)
    private String tenantId;

    @Column(name = "alert_id", nullable = false)
    private UUID alertId;

    @Column(name = "reference_number", nullable = false, length = 100)
    private String referenceNumber;

    @Column(name = "filing_date", nullable = false)
    @Builder.Default
    private LocalDate filingDate = LocalDate.now();

    @Column(name = "regulator", length = 100)
    @Builder.Default
    private String regulator = "FRC Kenya";

    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private String status = "FILED";

    @Column(name = "submitted_by", length = 100)
    private String submittedBy;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
