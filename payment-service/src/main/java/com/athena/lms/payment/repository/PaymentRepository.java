package com.athena.lms.payment.repository;

import com.athena.lms.payment.entity.Payment;
import com.athena.lms.payment.enums.PaymentStatus;
import com.athena.lms.payment.enums.PaymentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByIdAndTenantId(UUID id, String tenantId);
    Page<Payment> findByTenantId(String tenantId, Pageable pageable);
    Page<Payment> findByTenantIdAndStatus(String tenantId, PaymentStatus status, Pageable pageable);
    Page<Payment> findByTenantIdAndPaymentType(String tenantId, PaymentType type, Pageable pageable);
    List<Payment> findByTenantIdAndCustomerId(String tenantId, String customerId);
    Optional<Payment> findByInternalReference(String internalReference);
    Optional<Payment> findByExternalReference(String externalReference);
    List<Payment> findByLoanId(UUID loanId);
}
