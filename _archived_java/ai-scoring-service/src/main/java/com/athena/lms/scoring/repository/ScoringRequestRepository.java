package com.athena.lms.scoring.repository;

import com.athena.lms.scoring.entity.ScoringRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ScoringRequestRepository extends JpaRepository<ScoringRequest, UUID> {

    Optional<ScoringRequest> findTopByLoanApplicationIdOrderByCreatedAtDesc(UUID loanApplicationId);

    Page<ScoringRequest> findByCustomerIdOrderByCreatedAtDesc(Long customerId, Pageable pageable);

    Page<ScoringRequest> findByTenantId(String tenantId, Pageable pageable);
}
