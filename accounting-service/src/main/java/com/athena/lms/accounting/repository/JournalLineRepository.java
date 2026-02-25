package com.athena.lms.accounting.repository;

import com.athena.lms.accounting.entity.JournalLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface JournalLineRepository extends JpaRepository<JournalLine, UUID> {
    List<JournalLine> findByEntryId(UUID entryId);
    List<JournalLine> findByAccountId(UUID accountId);

    @Query("SELECT COALESCE(SUM(l.debitAmount), 0) - COALESCE(SUM(l.creditAmount), 0) FROM JournalLine l WHERE l.accountId = :accountId AND l.tenantId = :tenantId")
    BigDecimal getNetBalance(@Param("accountId") UUID accountId, @Param("tenantId") String tenantId);
}
