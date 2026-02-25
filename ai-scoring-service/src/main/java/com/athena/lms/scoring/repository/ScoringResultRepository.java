package com.athena.lms.scoring.repository;

import com.athena.lms.scoring.entity.ScoringResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ScoringResultRepository extends JpaRepository<ScoringResult, UUID> {

    Optional<ScoringResult> findTopByLoanApplicationIdOrderByCreatedAtDesc(UUID loanApplicationId);

    Optional<ScoringResult> findByRequestId(UUID requestId);

    Optional<ScoringResult> findTopByCustomerIdOrderByCreatedAtDesc(Long customerId);
}
