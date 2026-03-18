package com.athena.lms.fraud.entity;

import com.athena.lms.fraud.enums.SarReportType;
import com.athena.lms.fraud.enums.SarStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

@Entity
@Table(name = "sar_reports")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SarReport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 50)
    private String tenantId;

    @Column(name = "report_number", nullable = false, unique = true, length = 30)
    private String reportNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false, length = 20)
    @Builder.Default
    private SarReportType reportType = SarReportType.SAR;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private SarStatus status = SarStatus.DRAFT;

    @Column(name = "subject_customer_id", length = 100)
    private String subjectCustomerId;

    @Column(name = "subject_name", length = 300)
    private String subjectName;

    @Column(name = "subject_national_id", length = 50)
    private String subjectNationalId;

    @Column(name = "narrative", columnDefinition = "TEXT")
    private String narrative;

    @Column(name = "suspicious_amount", precision = 19, scale = 4)
    private BigDecimal suspiciousAmount;

    @Column(name = "activity_start_date")
    private OffsetDateTime activityStartDate;

    @Column(name = "activity_end_date")
    private OffsetDateTime activityEndDate;

    @ElementCollection
    @CollectionTable(name = "sar_report_alert_ids", joinColumns = @JoinColumn(name = "report_id"))
    @Column(name = "alert_id")
    private Set<UUID> alertIds;

    @Column(name = "case_id")
    private UUID caseId;

    @Column(name = "prepared_by", length = 100)
    private String preparedBy;

    @Column(name = "reviewed_by", length = 100)
    private String reviewedBy;

    @Column(name = "filed_by", length = 100)
    private String filedBy;

    @Column(name = "filed_at")
    private OffsetDateTime filedAt;

    @Column(name = "filing_reference", length = 100)
    private String filingReference;

    @Column(name = "regulator", length = 100)
    @Builder.Default
    private String regulator = "FRC Kenya";

    @Column(name = "filing_deadline")
    private OffsetDateTime filingDeadline;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
