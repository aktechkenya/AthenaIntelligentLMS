package com.athena.lms.floatmgmt.repository;

import com.athena.lms.floatmgmt.entity.FloatTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface FloatTransactionRepository extends JpaRepository<FloatTransaction, UUID> {

    Page<FloatTransaction> findByFloatAccountIdAndTenantIdOrderByCreatedAtDesc(
            UUID floatAccountId, String tenantId, Pageable pageable);

    boolean existsByEventId(String eventId);
}
