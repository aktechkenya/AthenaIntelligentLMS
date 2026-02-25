package com.athena.lms.account.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;

@Entity
@Table(name = "tenant_settings")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TenantSettings {
    @Id
    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "KES";

    @Column(name = "org_name", length = 200)
    private String orgName;

    @Column(name = "country_code", length = 3)
    private String countryCode;

    @Column(name = "timezone", length = 50)
    @Builder.Default
    private String timezone = "Africa/Nairobi";

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
