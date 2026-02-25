package com.athena.lms.product.repository;

import com.athena.lms.product.entity.ProductTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductTemplateRepository extends JpaRepository<ProductTemplate, UUID> {
    Optional<ProductTemplate> findByTemplateCode(String templateCode);
    List<ProductTemplate> findByIsActiveTrue();
}
