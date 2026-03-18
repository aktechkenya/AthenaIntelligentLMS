package com.athena.lms.overdraft.repository;

import com.athena.lms.overdraft.entity.OverdraftAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OverdraftAuditLogRepository extends JpaRepository<OverdraftAuditLog, UUID> {
    Page<OverdraftAuditLog> findByTenantIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(
        String tenantId, String entityType, UUID entityId, Pageable pageable);
    Page<OverdraftAuditLog> findByTenantIdOrderByCreatedAtDesc(String tenantId, Pageable pageable);
    Page<OverdraftAuditLog> findByTenantIdAndEntityTypeOrderByCreatedAtDesc(
        String tenantId, String entityType, Pageable pageable);
}
