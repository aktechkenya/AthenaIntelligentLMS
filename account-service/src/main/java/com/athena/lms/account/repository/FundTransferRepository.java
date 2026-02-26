package com.athena.lms.account.repository;

import com.athena.lms.account.entity.FundTransfer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface FundTransferRepository extends JpaRepository<FundTransfer, UUID> {

    Optional<FundTransfer> findByIdAndTenantId(UUID id, String tenantId);

    Optional<FundTransfer> findByReference(String reference);

    @Query("""
        SELECT t FROM FundTransfer t
        WHERE t.tenantId = :tenantId
          AND (t.sourceAccountId = :accountId OR t.destinationAccountId = :accountId)
        ORDER BY t.initiatedAt DESC
        """)
    Page<FundTransfer> findByAccountId(
            @Param("tenantId") String tenantId,
            @Param("accountId") UUID accountId,
            Pageable pageable);
}
