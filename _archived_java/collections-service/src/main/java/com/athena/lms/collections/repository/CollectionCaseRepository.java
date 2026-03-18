package com.athena.lms.collections.repository;

import com.athena.lms.collections.entity.CollectionCase;
import com.athena.lms.collections.enums.CasePriority;
import com.athena.lms.collections.enums.CaseStatus;
import com.athena.lms.collections.enums.CollectionStage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CollectionCaseRepository extends JpaRepository<CollectionCase, UUID> {

    Page<CollectionCase> findByTenantId(String tenantId, Pageable pageable);

    Optional<CollectionCase> findByLoanId(UUID loanId);

    Optional<CollectionCase> findByTenantIdAndId(String tenantId, UUID id);

    Page<CollectionCase> findByTenantIdAndStatus(String tenantId, CaseStatus status, Pageable pageable);

    long countByTenantIdAndCurrentStage(String tenantId, CollectionStage stage);

    long countByTenantIdAndStatus(String tenantId, CaseStatus status);

    long countByTenantIdAndPriority(String tenantId, CasePriority priority);
}
