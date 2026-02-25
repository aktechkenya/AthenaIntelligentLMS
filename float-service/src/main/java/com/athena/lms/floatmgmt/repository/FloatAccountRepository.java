package com.athena.lms.floatmgmt.repository;

import com.athena.lms.floatmgmt.entity.FloatAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FloatAccountRepository extends JpaRepository<FloatAccount, UUID> {

    List<FloatAccount> findByTenantId(String tenantId);

    Optional<FloatAccount> findByTenantIdAndId(String tenantId, UUID id);

    boolean existsByTenantIdAndAccountCode(String tenantId, String accountCode);
}
