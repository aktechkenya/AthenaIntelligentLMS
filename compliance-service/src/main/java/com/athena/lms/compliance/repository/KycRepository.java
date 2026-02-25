package com.athena.lms.compliance.repository;

import com.athena.lms.compliance.entity.KycRecord;
import com.athena.lms.compliance.enums.KycStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface KycRepository extends JpaRepository<KycRecord, UUID> {

    Optional<KycRecord> findByTenantIdAndCustomerId(String tenantId, String customerId);

    Page<KycRecord> findByTenantId(String tenantId, Pageable pageable);

    long countByTenantIdAndStatus(String tenantId, KycStatus status);
}
