package com.athena.lms.account.repository;

import com.athena.lms.account.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    Page<Customer> findByTenantId(String tenantId, Pageable pageable);

    Optional<Customer> findByIdAndTenantId(UUID id, String tenantId);

    Optional<Customer> findByCustomerIdAndTenantId(String customerId, String tenantId);

    boolean existsByCustomerIdAndTenantId(String customerId, String tenantId);

    @Query(value = """
        SELECT * FROM customers
        WHERE tenant_id = :tenantId
          AND (first_name || ' ' || last_name ILIKE '%' || :q || '%'
               OR phone ILIKE '%' || :q || '%'
               OR email ILIKE '%' || :q || '%'
               OR customer_id ILIKE '%' || :q || '%')
        LIMIT 20
        """, nativeQuery = true)
    List<Customer> searchByTenantAndQuery(@Param("tenantId") String tenantId, @Param("q") String q);
}
