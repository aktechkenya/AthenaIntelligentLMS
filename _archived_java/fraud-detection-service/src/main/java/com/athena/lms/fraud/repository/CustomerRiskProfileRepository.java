package com.athena.lms.fraud.repository;

import com.athena.lms.fraud.entity.CustomerRiskProfile;
import com.athena.lms.fraud.enums.RiskLevel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerRiskProfileRepository extends JpaRepository<CustomerRiskProfile, UUID> {

    Optional<CustomerRiskProfile> findByTenantIdAndCustomerId(String tenantId, String customerId);

    Page<CustomerRiskProfile> findByTenantId(String tenantId, Pageable pageable);

    Page<CustomerRiskProfile> findByTenantIdAndRiskLevel(String tenantId, RiskLevel riskLevel, Pageable pageable);

    long countByTenantIdAndRiskLevel(String tenantId, RiskLevel riskLevel);

    List<CustomerRiskProfile> findAllByTenantId(String tenantId);
}
