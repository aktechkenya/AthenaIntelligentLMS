package com.athena.lms.fraud.repository;

import com.athena.lms.fraud.entity.SarReport;
import com.athena.lms.fraud.enums.SarReportType;
import com.athena.lms.fraud.enums.SarStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface SarReportRepository extends JpaRepository<SarReport, UUID> {

    @Query("SELECT s FROM SarReport s WHERE s.status IN ('DRAFT', 'PENDING_REVIEW', 'APPROVED') AND s.filingDeadline IS NOT NULL AND s.filingDeadline < :now")
    List<SarReport> findOverdueReports(OffsetDateTime now);


    Page<SarReport> findByTenantId(String tenantId, Pageable pageable);

    Page<SarReport> findByTenantIdAndStatus(String tenantId, SarStatus status, Pageable pageable);

    Page<SarReport> findByTenantIdAndReportType(String tenantId, SarReportType type, Pageable pageable);

    long countByTenantIdAndStatus(String tenantId, SarStatus status);

    @Query("SELECT COALESCE(MAX(CAST(SUBSTRING(s.reportNumber, 5) AS int)), 0) FROM SarReport s WHERE s.tenantId = :tenantId")
    int findMaxReportNumber(String tenantId);
}
