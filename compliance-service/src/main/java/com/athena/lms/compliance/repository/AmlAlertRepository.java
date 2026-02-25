package com.athena.lms.compliance.repository;

import com.athena.lms.compliance.entity.AmlAlert;
import com.athena.lms.compliance.enums.AlertSeverity;
import com.athena.lms.compliance.enums.AlertStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AmlAlertRepository extends JpaRepository<AmlAlert, UUID> {

    Page<AmlAlert> findByTenantId(String tenantId, Pageable pageable);

    Page<AmlAlert> findByTenantIdAndStatus(String tenantId, AlertStatus status, Pageable pageable);

    List<AmlAlert> findByTenantIdAndCustomerId(String tenantId, String customerId);

    long countByTenantIdAndStatus(String tenantId, AlertStatus status);

    long countByTenantIdAndSeverityAndStatus(String tenantId, AlertSeverity severity, AlertStatus status);
}
