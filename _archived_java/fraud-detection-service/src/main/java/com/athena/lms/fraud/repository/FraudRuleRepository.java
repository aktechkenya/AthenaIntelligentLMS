package com.athena.lms.fraud.repository;

import com.athena.lms.fraud.entity.FraudRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FraudRuleRepository extends JpaRepository<FraudRule, UUID> {

    @Query("SELECT r FROM FraudRule r WHERE (r.tenantId = :tenantId OR r.tenantId = '*') " +
           "AND r.enabled = true ORDER BY r.category, r.ruleCode")
    List<FraudRule> findActiveRules(String tenantId);

    Optional<FraudRule> findByTenantIdAndRuleCode(String tenantId, String ruleCode);

    List<FraudRule> findByTenantIdOrTenantId(String tenantId, String global);

    @Query("SELECT r FROM FraudRule r WHERE r.tenantId = :tenantId OR r.tenantId = '*' ORDER BY r.category, r.ruleCode")
    List<FraudRule> findByTenantIdOrGlobal(String tenantId);
}
