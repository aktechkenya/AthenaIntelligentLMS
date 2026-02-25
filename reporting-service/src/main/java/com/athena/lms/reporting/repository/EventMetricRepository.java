package com.athena.lms.reporting.repository;

import com.athena.lms.reporting.entity.EventMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EventMetricRepository extends JpaRepository<EventMetric, UUID> {

    List<EventMetric> findByTenantIdAndMetricDate(String tenantId, LocalDate date);

    List<EventMetric> findByTenantIdAndMetricDateBetween(String tenantId, LocalDate from, LocalDate to);

    Optional<EventMetric> findByTenantIdAndMetricDateAndEventType(String tenantId, LocalDate date, String eventType);
}
