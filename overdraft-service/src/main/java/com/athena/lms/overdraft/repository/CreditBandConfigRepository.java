package com.athena.lms.overdraft.repository;

import com.athena.lms.overdraft.entity.CreditBandConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CreditBandConfigRepository extends JpaRepository<CreditBandConfig, UUID> {
    List<CreditBandConfig> findByTenantIdAndStatusOrderByMinScoreDesc(String tenantId, String status);
    Optional<CreditBandConfig> findByTenantIdAndBandAndStatus(String tenantId, String band, String status);
    List<CreditBandConfig> findByTenantIdOrderByMinScoreDesc(String tenantId);
}
