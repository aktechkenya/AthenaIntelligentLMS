package com.athena.lms.management.repository;

import com.athena.lms.management.entity.LoanDpdHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LoanDpdHistoryRepository extends JpaRepository<LoanDpdHistory, UUID> {
    List<LoanDpdHistory> findByLoanIdOrderBySnapshotDateDesc(UUID loanId);
}
