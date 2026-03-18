package com.athena.lms.management.repository;

import com.athena.lms.management.entity.LoanRepayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LoanRepaymentRepository extends JpaRepository<LoanRepayment, UUID> {
    List<LoanRepayment> findByLoanIdOrderByPaymentDateDesc(UUID loanId);
}
