package com.athena.lms.fraud.repository;

import com.athena.lms.fraud.entity.ScoringHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ScoringHistoryRepository extends JpaRepository<ScoringHistory, UUID> {

    Page<ScoringHistory> findByTenantIdAndCustomerId(String tenantId, String customerId, Pageable pageable);

    long countByTenantIdAndRiskLevel(String tenantId, String riskLevel);

    @Query("SELECT sh.riskLevel, AVG(sh.mlScore) FROM ScoringHistory sh " +
           "WHERE sh.tenantId = :tenantId GROUP BY sh.riskLevel")
    List<Object[]> averageScoreByRiskLevel(@Param("tenantId") String tenantId);

    @Query(value = "SELECT CAST(created_at AS DATE) AS day, COUNT(*) AS volume " +
                   "FROM scoring_history WHERE tenant_id = :tenantId " +
                   "AND created_at >= CURRENT_DATE - CAST(:days || ' days' AS INTERVAL) " +
                   "GROUP BY CAST(created_at AS DATE) ORDER BY day",
           nativeQuery = true)
    List<Object[]> scoringVolumePerDay(@Param("tenantId") String tenantId, @Param("days") int days);
}
