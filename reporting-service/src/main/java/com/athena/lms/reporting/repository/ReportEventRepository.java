package com.athena.lms.reporting.repository;

import com.athena.lms.reporting.entity.ReportEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface ReportEventRepository extends JpaRepository<ReportEvent, UUID> {

    Page<ReportEvent> findByTenantIdOrderByOccurredAtDesc(String tenantId, Pageable pageable);

    Page<ReportEvent> findByTenantIdAndEventTypeOrderByOccurredAtDesc(String tenantId, String eventType, Pageable pageable);

    Page<ReportEvent> findByTenantIdAndOccurredAtBetweenOrderByOccurredAtDesc(String tenantId, Instant from, Instant to, Pageable pageable);
}
