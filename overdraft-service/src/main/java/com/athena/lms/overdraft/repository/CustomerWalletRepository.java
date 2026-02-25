package com.athena.lms.overdraft.repository;

import com.athena.lms.overdraft.entity.CustomerWallet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerWalletRepository extends JpaRepository<CustomerWallet, UUID> {
    Optional<CustomerWallet> findByTenantIdAndCustomerId(String tenantId, String customerId);
    Optional<CustomerWallet> findByTenantIdAndId(String tenantId, UUID id);
    List<CustomerWallet> findByTenantId(String tenantId);
    boolean existsByTenantIdAndCustomerId(String tenantId, String customerId);
}
