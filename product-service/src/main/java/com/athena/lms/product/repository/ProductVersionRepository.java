package com.athena.lms.product.repository;

import com.athena.lms.product.entity.ProductVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProductVersionRepository extends JpaRepository<ProductVersion, UUID> {
    List<ProductVersion> findByProductIdOrderByVersionNumberDesc(UUID productId);
}
