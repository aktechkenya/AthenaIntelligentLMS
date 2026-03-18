package com.athena.lms.fraud.repository;

import com.athena.lms.fraud.entity.FraudEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FraudEventRepository extends JpaRepository<FraudEvent, UUID> {

    Page<FraudEvent> findByTenantIdOrderByProcessedAtDesc(String tenantId, Pageable pageable);

    Page<FraudEvent> findByTenantIdAndCustomerIdOrderByProcessedAtDesc(
            String tenantId, String customerId, Pageable pageable);

    Page<FraudEvent> findByTenantId(String tenantId, Pageable pageable);
}
