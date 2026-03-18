package com.athena.lms.accounting.repository;

import com.athena.lms.accounting.entity.AccountBalance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountBalanceRepository extends JpaRepository<AccountBalance, UUID> {
    Optional<AccountBalance> findByTenantIdAndAccountIdAndPeriodYearAndPeriodMonth(
        String tenantId, UUID accountId, int year, int month);
    List<AccountBalance> findByTenantIdAndPeriodYearAndPeriodMonth(
        String tenantId, int year, int month);
}
