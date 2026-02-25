package com.athena.lms.overdraft.repository;

import com.athena.lms.overdraft.entity.WalletTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, UUID> {
    Page<WalletTransaction> findByWalletIdAndTenantIdOrderByCreatedAtDesc(UUID walletId, String tenantId, Pageable pageable);
}
