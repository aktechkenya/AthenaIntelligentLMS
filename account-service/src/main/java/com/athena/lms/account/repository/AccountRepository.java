package com.athena.lms.account.repository;

import com.athena.lms.account.entity.Account;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByAccountNumber(String accountNumber);

    Optional<Account> findByIdAndTenantId(UUID id, String tenantId);

    List<Account> findByCustomerIdAndTenantId(String customerId, String tenantId);

    Page<Account> findByTenantId(String tenantId, Pageable pageable);

    @Query(value = """
        SELECT * FROM accounts
        WHERE tenant_id = :tenantId
          AND (account_number ILIKE '%' || :q || '%'
               OR account_name ILIKE '%' || :q || '%')
        LIMIT 20
        """, nativeQuery = true)
    List<Account> searchByTenantAndQuery(@Param("tenantId") String tenantId, @Param("q") String q);
}
