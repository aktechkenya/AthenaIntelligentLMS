package com.athena.lms.compliance.repository;

import com.athena.lms.compliance.entity.ComplianceEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ComplianceEventRepository extends JpaRepository<ComplianceEvent, UUID> {

    Page<ComplianceEvent> findByTenantIdOrderByCreatedAtDesc(String tenantId, Pageable pageable);

    Page<ComplianceEvent> findByTenantIdAndEventType(String tenantId, String eventType, Pageable pageable);
}
