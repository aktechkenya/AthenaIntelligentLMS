package com.athena.lms.fraud.repository;

import com.athena.lms.fraud.entity.VelocityCounter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface VelocityCounterRepository extends JpaRepository<VelocityCounter, UUID> {

    Optional<VelocityCounter> findByTenantIdAndCustomerIdAndCounterTypeAndWindowStart(
            String tenantId, String customerId, String counterType, OffsetDateTime windowStart);

    @Query("SELECT COALESCE(SUM(v.count), 0) FROM VelocityCounter v " +
           "WHERE v.tenantId = :tenantId AND v.customerId = :customerId " +
           "AND v.counterType = :counterType AND v.windowEnd > :since")
    int sumCountSince(String tenantId, String customerId, String counterType, OffsetDateTime since);

    @Query("SELECT COALESCE(SUM(v.totalAmount), 0) FROM VelocityCounter v " +
           "WHERE v.tenantId = :tenantId AND v.customerId = :customerId " +
           "AND v.counterType = :counterType AND v.windowEnd > :since")
    BigDecimal sumAmountSince(String tenantId, String customerId, String counterType, OffsetDateTime since);
}
