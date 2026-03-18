package com.athena.lms.management.repository;

import com.athena.lms.management.entity.LoanSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LoanScheduleRepository extends JpaRepository<LoanSchedule, UUID> {
    List<LoanSchedule> findByLoanIdOrderByInstallmentNo(UUID loanId);
    Optional<LoanSchedule> findByLoanIdAndInstallmentNo(UUID loanId, Integer installmentNo);
    List<LoanSchedule> findByLoanIdAndStatus(UUID loanId, String status);
    void deleteByLoanId(UUID loanId);
}
