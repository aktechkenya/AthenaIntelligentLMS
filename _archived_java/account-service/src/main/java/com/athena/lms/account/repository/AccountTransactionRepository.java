package com.athena.lms.account.repository;

import com.athena.lms.account.entity.AccountTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountTransactionRepository extends JpaRepository<AccountTransaction, UUID> {

    Page<AccountTransaction> findByAccountIdOrderByCreatedAtDesc(UUID accountId, Pageable pageable);

    List<AccountTransaction> findTop10ByAccountIdOrderByCreatedAtDesc(UUID accountId);

    @Query("SELECT t FROM AccountTransaction t WHERE t.accountId = :accountId ORDER BY t.createdAt DESC")
    List<AccountTransaction> findTopNByAccountId(@Param("accountId") UUID accountId, Pageable pageable);

    Optional<AccountTransaction> findByIdempotencyKey(String idempotencyKey);

    @Query("""
        SELECT COALESCE(SUM(t.amount), 0) FROM AccountTransaction t
        WHERE t.accountId = :accountId
          AND t.transactionType = 'DEBIT'
          AND CAST(t.createdAt AS DATE) = CURRENT_DATE
        """)
    BigDecimal sumDailyDebits(@Param("accountId") UUID accountId);

    @Query("""
        SELECT COALESCE(SUM(t.amount), 0) FROM AccountTransaction t
        WHERE t.accountId = :accountId
          AND t.transactionType = 'DEBIT'
          AND t.createdAt >= DATE_TRUNC('month', CURRENT_TIMESTAMP)
        """)
    BigDecimal sumMonthlyDebits(@Param("accountId") UUID accountId);

    @Query("""
        SELECT t FROM AccountTransaction t
        WHERE t.accountId = :accountId
          AND t.createdAt >= :fromDate
          AND t.createdAt < :toDate
        ORDER BY t.createdAt ASC
        """)
    Page<AccountTransaction> findByAccountIdAndPeriod(
            @Param("accountId") UUID accountId,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            Pageable pageable);

    @Query("""
        SELECT COALESCE(
            SUM(CASE WHEN t.transactionType = 'CREDIT' THEN t.amount ELSE -t.amount END), 0)
        FROM AccountTransaction t
        WHERE t.accountId = :accountId
          AND t.createdAt < :before
        """)
    BigDecimal sumNetBalanceChangeBefore(
            @Param("accountId") UUID accountId,
            @Param("before") LocalDateTime before);
}
