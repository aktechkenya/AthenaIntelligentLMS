package com.athena.lms.payment.repository;

import com.athena.lms.payment.entity.PaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, UUID> {
    List<PaymentMethod> findByTenantIdAndCustomerIdAndIsActiveTrue(String tenantId, String customerId);
}
