package com.athena.lms.management.repository;

import com.athena.lms.management.entity.Loan;
import com.athena.lms.management.enums.LoanStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LoanRepository extends JpaRepository<Loan, UUID> {
    Optional<Loan> findByIdAndTenantId(UUID id, String tenantId);
    Page<Loan> findByTenantId(String tenantId, Pageable pageable);
    Page<Loan> findByTenantIdAndStatus(String tenantId, LoanStatus status, Pageable pageable);
    List<Loan> findByTenantIdAndCustomerId(String tenantId, String customerId);

    @Query("SELECT l FROM Loan l WHERE l.status = 'ACTIVE' AND l.tenantId = :tenantId")
    List<Loan> findActiveLoans(@Param("tenantId") String tenantId);

    // For DPD scheduler: all active loans across all tenants
    @Query("SELECT l FROM Loan l WHERE l.status = com.athena.lms.management.enums.LoanStatus.ACTIVE")
    List<Loan> findAllActiveLoans();
}
