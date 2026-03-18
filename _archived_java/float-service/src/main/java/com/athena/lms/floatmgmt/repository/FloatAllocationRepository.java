package com.athena.lms.floatmgmt.repository;

import com.athena.lms.floatmgmt.entity.FloatAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FloatAllocationRepository extends JpaRepository<FloatAllocation, UUID> {

    Optional<FloatAllocation> findByLoanId(UUID loanId);

    List<FloatAllocation> findByTenantId(String tenantId);

    List<FloatAllocation> findByFloatAccountIdAndTenantId(UUID floatAccountId, String tenantId);
}
