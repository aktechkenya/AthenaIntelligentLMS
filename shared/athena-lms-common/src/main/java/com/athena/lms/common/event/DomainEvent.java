package com.athena.lms.common.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Generic domain event envelope for all LMS services.
 * Published to athena.lms.exchange with routing key = event type (e.g. "loan.disbursed").
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DomainEvent<T> {

    /** Unique event ID (UUID). */
    @Builder.Default
    private String id = UUID.randomUUID().toString();

    /** Routing key / event type (e.g. "loan.disbursed", "payment.completed"). */
    private String type;

    /** Schema version for forward compatibility. */
    @Builder.Default
    private int version = 1;

    /** Originating service name. */
    private String source;

    /** Tenant identifier for multi-tenant routing. */
    private String tenantId;

    /** Correlation ID for distributed tracing. */
    private String correlationId;

    @Builder.Default
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant timestamp = Instant.now();

    /** Domain-specific payload. */
    private T payload;

    // ─── Convenience factory ──────────────────────────────────────────────────

    public static <T> DomainEvent<T> of(String type, String source, String tenantId, T payload) {
        return DomainEvent.<T>builder()
                .type(type)
                .source(source)
                .tenantId(tenantId)
                .payload(payload)
                .build();
    }
}
