package com.athena.lms.fraud.repository;

import com.athena.lms.fraud.entity.FraudAlert;
import com.athena.lms.fraud.enums.AlertSeverity;
import com.athena.lms.fraud.enums.AlertStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface FraudAlertRepository extends JpaRepository<FraudAlert, UUID> {

    Page<FraudAlert> findByTenantId(String tenantId, Pageable pageable);

    Page<FraudAlert> findByTenantIdAndStatus(String tenantId, AlertStatus status, Pageable pageable);

    Page<FraudAlert> findByTenantIdAndCustomerId(String tenantId, String customerId, Pageable pageable);

    List<FraudAlert> findByTenantIdAndCustomerIdAndStatusIn(
            String tenantId, String customerId, List<AlertStatus> statuses);

    long countByTenantIdAndStatus(String tenantId, AlertStatus status);

    long countByTenantIdAndSeverityAndStatus(String tenantId, AlertSeverity severity, AlertStatus status);

    @Query("SELECT COUNT(a) FROM FraudAlert a WHERE a.tenantId = :tenantId AND a.customerId = :customerId AND a.status = 'OPEN'")
    long countOpenAlertsByCustomer(String tenantId, String customerId);

    @Query("SELECT COUNT(a) FROM FraudAlert a WHERE a.tenantId = :tenantId AND a.customerId = :customerId " +
           "AND a.ruleCode = :ruleCode AND a.createdAt > :since")
    long countRecentAlertsByRule(String tenantId, String customerId, String ruleCode, OffsetDateTime since);

    @Query("SELECT a.ruleCode, COUNT(a) FROM FraudAlert a WHERE a.tenantId = :tenantId AND a.ruleCode IS NOT NULL GROUP BY a.ruleCode ORDER BY COUNT(a) DESC")
    List<Object[]> countByRule(String tenantId);

    @Query("SELECT a.ruleCode, COUNT(a) FROM FraudAlert a WHERE a.tenantId = :tenantId AND a.status = 'CONFIRMED_FRAUD' AND a.ruleCode IS NOT NULL GROUP BY a.ruleCode")
    List<Object[]> countConfirmedByRule(String tenantId);

    @Query("SELECT a.ruleCode, COUNT(a) FROM FraudAlert a WHERE a.tenantId = :tenantId AND a.status = 'FALSE_POSITIVE' AND a.ruleCode IS NOT NULL GROUP BY a.ruleCode")
    List<Object[]> countFalsePositiveByRule(String tenantId);

    @Query("SELECT CAST(a.createdAt AS date), COUNT(a) FROM FraudAlert a WHERE a.tenantId = :tenantId AND a.createdAt > :since GROUP BY CAST(a.createdAt AS date) ORDER BY CAST(a.createdAt AS date)")
    List<Object[]> countByDay(String tenantId, OffsetDateTime since);

    @Query("SELECT a.alertType, COUNT(a) FROM FraudAlert a WHERE a.tenantId = :tenantId AND a.createdAt > :since GROUP BY a.alertType ORDER BY COUNT(a) DESC")
    List<Object[]> countByAlertType(String tenantId, OffsetDateTime since);

    long countByTenantId(String tenantId);

    @Query("SELECT COUNT(a) FROM FraudAlert a WHERE a.tenantId = :tenantId AND a.status IN ('CONFIRMED_FRAUD', 'FALSE_POSITIVE')")
    long countResolved(String tenantId);
}
