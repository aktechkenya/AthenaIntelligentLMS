package com.athena.lms.accounting.repository;

import com.athena.lms.accounting.entity.ChartOfAccount;
import com.athena.lms.accounting.enums.AccountType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChartOfAccountRepository extends JpaRepository<ChartOfAccount, UUID> {
    Optional<ChartOfAccount> findByTenantIdAndCode(String tenantId, String code);
    List<ChartOfAccount> findByTenantIdAndIsActiveTrue(String tenantId);
    List<ChartOfAccount> findByTenantIdAndAccountTypeAndIsActiveTrue(String tenantId, AccountType type);
    Optional<ChartOfAccount> findByIdAndTenantId(UUID id, String tenantId);
    // Fall back to system accounts if tenant doesn't have own CoA
    Optional<ChartOfAccount> findByCodeAndTenantIdIn(String code, List<String> tenantIds);
    Optional<ChartOfAccount> findByIdAndTenantIdIn(UUID id, List<String> tenantIds);
}
