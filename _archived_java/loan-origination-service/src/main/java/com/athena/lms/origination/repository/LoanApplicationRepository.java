package com.athena.lms.origination.repository;

import com.athena.lms.origination.entity.LoanApplication;
import com.athena.lms.origination.enums.ApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LoanApplicationRepository extends JpaRepository<LoanApplication, UUID> {

    Optional<LoanApplication> findByIdAndTenantId(UUID id, String tenantId);

    Page<LoanApplication> findByTenantId(String tenantId, Pageable pageable);

    Page<LoanApplication> findByTenantIdAndStatus(String tenantId, ApplicationStatus status, Pageable pageable);

    List<LoanApplication> findByTenantIdAndCustomerId(String tenantId, String customerId);

    @Query("SELECT la FROM LoanApplication la LEFT JOIN FETCH la.collaterals LEFT JOIN FETCH la.notes LEFT JOIN FETCH la.statusHistory WHERE la.id = :id AND la.tenantId = :tenantId")
    Optional<LoanApplication> findByIdWithDetails(@Param("id") UUID id, @Param("tenantId") String tenantId);
}
