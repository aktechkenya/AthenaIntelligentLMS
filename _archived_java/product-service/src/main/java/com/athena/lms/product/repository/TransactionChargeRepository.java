package com.athena.lms.product.repository;

import com.athena.lms.product.entity.TransactionCharge;
import com.athena.lms.product.enums.ChargeTransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionChargeRepository extends JpaRepository<TransactionCharge, UUID> {

    Page<TransactionCharge> findByTenantId(String tenantId, Pageable pageable);

    Optional<TransactionCharge> findByIdAndTenantId(UUID id, String tenantId);

    List<TransactionCharge> findByTenantIdAndTransactionTypeAndIsActiveTrue(
            String tenantId, ChargeTransactionType transactionType);

    boolean existsByChargeCodeAndTenantId(String chargeCode, String tenantId);
}
