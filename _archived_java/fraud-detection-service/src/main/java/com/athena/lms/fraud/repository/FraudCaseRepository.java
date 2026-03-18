package com.athena.lms.fraud.repository;

import com.athena.lms.fraud.entity.FraudCase;
import com.athena.lms.fraud.enums.CaseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FraudCaseRepository extends JpaRepository<FraudCase, UUID> {
    Page<FraudCase> findByTenantId(String tenantId, Pageable pageable);
    Page<FraudCase> findByTenantIdAndStatus(String tenantId, CaseStatus status, Pageable pageable);
    Page<FraudCase> findByTenantIdAndCustomerId(String tenantId, String customerId, Pageable pageable);
    Page<FraudCase> findByTenantIdAndAssignedTo(String tenantId, String assignee, Pageable pageable);
    Optional<FraudCase> findByTenantIdAndCaseNumber(String tenantId, String caseNumber);
    long countByTenantIdAndStatus(String tenantId, CaseStatus status);

    @Query("SELECT COALESCE(MAX(CAST(SUBSTRING(c.caseNumber, 5) AS int)), 0) FROM FraudCase c WHERE c.tenantId = :tenantId")
    int findMaxCaseNumber(String tenantId);

    @Query("SELECT COUNT(c) FROM FraudCase c WHERE c.tenantId = :tenantId AND c.status NOT LIKE 'CLOSED%'")
    long countActiveCases(String tenantId);

    @Query("SELECT c FROM FraudCase c WHERE c.slaDeadline < :now AND c.slaBreached = false " +
           "AND c.status NOT IN (com.athena.lms.fraud.enums.CaseStatus.CLOSED_CONFIRMED, " +
           "com.athena.lms.fraud.enums.CaseStatus.CLOSED_FALSE_POSITIVE, " +
           "com.athena.lms.fraud.enums.CaseStatus.CLOSED_INCONCLUSIVE)")
    List<FraudCase> findOverdueCases(OffsetDateTime now);
}
