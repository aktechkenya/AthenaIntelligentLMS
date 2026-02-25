package com.athena.lms.overdraft.repository;

import com.athena.lms.overdraft.entity.OverdraftFacility;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OverdraftFacilityRepository extends JpaRepository<OverdraftFacility, UUID> {
    Optional<OverdraftFacility> findTopByWalletIdOrderByCreatedAtDesc(UUID walletId);
    List<OverdraftFacility> findByTenantId(String tenantId);
    List<OverdraftFacility> findByStatusAndDrawnAmountGreaterThan(String status, java.math.BigDecimal amount);
}
