package com.athena.lms.fraud.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "fraud_network_links")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class NetworkLink {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 50)
    private String tenantId;

    @Column(name = "customer_id_a", nullable = false, length = 100)
    private String customerIdA;

    @Column(name = "customer_id_b", nullable = false, length = 100)
    private String customerIdB;

    @Column(name = "link_type", nullable = false, length = 50)
    private String linkType; // SHARED_PHONE, SHARED_DEVICE, SHARED_IP, SHARED_EMPLOYER, SHARED_ADDRESS

    @Column(name = "link_value", nullable = false, length = 500)
    private String linkValue;

    @Column(name = "strength", nullable = false)
    @Builder.Default
    private Integer strength = 1;

    @Column(name = "flagged", nullable = false)
    @Builder.Default
    private Boolean flagged = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
