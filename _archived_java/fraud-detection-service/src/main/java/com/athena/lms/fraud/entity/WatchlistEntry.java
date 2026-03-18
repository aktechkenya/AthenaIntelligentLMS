package com.athena.lms.fraud.entity;

import com.athena.lms.fraud.enums.WatchlistType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "watchlist_entries")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WatchlistEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 50)
    private String tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "list_type", nullable = false, length = 50)
    private WatchlistType listType;

    @Column(name = "entry_type", nullable = false, length = 50)
    private String entryType;

    @Column(name = "name", length = 300)
    private String name;

    @Column(name = "national_id", length = 50)
    private String nationalId;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "source", length = 200)
    private String source;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
