package com.athena.lms.product.entity;

import com.athena.lms.product.enums.ProductType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "product_templates")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "template_code", nullable = false, unique = true, length = 50)
    private String templateCode;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false, length = 30)
    private ProductType productType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "configuration", nullable = false, columnDefinition = "JSONB")
    private String configuration;

    @Column(name = "is_active")
    @Builder.Default
    private boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
