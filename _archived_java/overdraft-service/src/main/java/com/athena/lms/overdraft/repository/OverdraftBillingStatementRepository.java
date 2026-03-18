package com.athena.lms.overdraft.repository;

import com.athena.lms.overdraft.entity.OverdraftBillingStatement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface OverdraftBillingStatementRepository extends JpaRepository<OverdraftBillingStatement, UUID> {
    List<OverdraftBillingStatement> findByFacilityIdOrderByBillingDateDesc(UUID facilityId);
    List<OverdraftBillingStatement> findByStatusIn(List<String> statuses);
    List<OverdraftBillingStatement> findByFacilityIdAndStatusAndDueDateBefore(UUID facilityId, String status, LocalDate date);
    boolean existsByFacilityIdAndBillingDate(UUID facilityId, LocalDate billingDate);
}
