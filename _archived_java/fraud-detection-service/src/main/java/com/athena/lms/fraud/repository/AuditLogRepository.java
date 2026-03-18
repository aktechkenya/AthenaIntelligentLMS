package com.athena.lms.fraud.repository;

import com.athena.lms.fraud.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    Page<AuditLog> findByTenantIdOrderByCreatedAtDesc(String tenantId, Pageable pageable);
    Page<AuditLog> findByTenantIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(
        String tenantId, String entityType, UUID entityId, Pageable pageable);
    Page<AuditLog> findByTenantIdAndPerformedByOrderByCreatedAtDesc(
        String tenantId, String performedBy, Pageable pageable);

    List<AuditLog> findByTenantIdAndEntityTypeAndEntityIdOrderByCreatedAtAsc(
        String tenantId, String entityType, UUID entityId);
}
