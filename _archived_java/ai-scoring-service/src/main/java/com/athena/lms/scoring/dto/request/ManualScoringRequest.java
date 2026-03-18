package com.athena.lms.scoring.dto.request;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.IOException;
import java.util.UUID;

@Data
public class ManualScoringRequest {

    @NotNull
    private UUID loanApplicationId;

    /**
     * Customer ID. Accepts both numeric Long values and string identifiers (e.g. "CUST-001").
     * String values that are not parseable as Long are coerced via hash, consistent with
     * how the event listener handles mixed customerId formats.
     */
    @NotNull
    @JsonDeserialize(using = ManualScoringRequest.FlexibleCustomerIdDeserializer.class)
    private Long customerId;

    private String triggerEvent;

    static class FlexibleCustomerIdDeserializer extends StdDeserializer<Long> {

        FlexibleCustomerIdDeserializer() {
            super(Long.class);
        }

        @Override
        public Long deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
            String raw = p.getValueAsString();
            if (raw == null || raw.isBlank()) {
                return null;
            }
            // Try direct numeric parse first
            try {
                return Long.parseLong(raw);
            } catch (NumberFormatException ignored) {
                // Fall through — the value is a non-numeric string such as "CUST-001"
            }
            // Strip a leading alphabetic/dash prefix (e.g. "CUST-", "C-") and retry
            String stripped = raw.replaceAll("^[^0-9]+", "");
            if (!stripped.isEmpty()) {
                try {
                    return Long.parseLong(stripped);
                } catch (NumberFormatException ignored) {
                    // Fall through to hash-based coercion
                }
            }
            // Last resort: stable positive hash, mirrors LoanApplicationEventListener behaviour
            return (long) Math.abs(raw.hashCode());
        }
    }
}
