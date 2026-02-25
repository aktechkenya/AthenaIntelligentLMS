package com.athena.lms.product.repository;

import com.athena.lms.product.entity.Product;
import com.athena.lms.product.enums.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    Optional<Product> findByIdAndTenantId(UUID id, String tenantId);

    boolean existsByProductCodeAndTenantId(String productCode, String tenantId);

    Page<Product> findByTenantIdAndStatus(String tenantId, ProductStatus status, Pageable pageable);

    Page<Product> findByTenantId(String tenantId, Pageable pageable);

    @Query(value = """
        SELECT * FROM products WHERE tenant_id = :tenantId
          AND (name ILIKE '%' || :q || '%' OR product_code ILIKE '%' || :q || '%')
        LIMIT 20
        """, nativeQuery = true)
    java.util.List<Product> searchByTenantAndQuery(@Param("tenantId") String tenantId, @Param("q") String q);
}
