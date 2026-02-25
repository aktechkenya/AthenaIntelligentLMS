
## 2026-02-25: E2E Test Run Lessons

### @Builder + Field Initializer Anti-Pattern
**Problem:** Lombok `@Builder` ignores Java field initializers (e.g., `private OffsetDateTime createdAt = OffsetDateTime.now()`). The builder creates instances with `null` for those fields. At DB persist time, NOT NULL constraints blow up.
**Fix:** Use `@CreationTimestamp` / `@UpdateTimestamp` from Hibernate annotations for audit timestamps, OR annotate the field with `@Builder.Default`. Never use `= OffsetDateTime.now()` with `@Builder` without `@Builder.Default`.
**Impact:** Affects EVERY entity in the codebase — must fix all at once.

### RabbitMQ Wildcard Queue Accumulation
**Problem:** Using `#` wildcard binding on reporting/notification queues causes ALL events to accumulate. In development with many test runs, queues balloon to 878k+ messages.
**Fix:** Use specific routing patterns for notification/reporting, or implement auto-acknowledge with no persistence for high-fanout queues. Add queue depth monitoring alerts.

### Event Contract Type Mismatch
**Problem:** loan-origination-service publishes `customerId` as UUID string; ai-scoring-service listener expects Long. Services share no typed event DTOs.
**Fix:** Create shared event DTO classes in `athena-lms-common`. All event producers and consumers must import the same DTO.

### Missing RabbitMQ Binding for Core Service
**Problem:** `LOAN_MGMT_QUEUE` was missing the `loan.disbursed` binding in shared config. Loan lifecycle could never advance without it.
**Fix:** When adding a new event routing key, always update the shared `LmsRabbitMQConfig.java` binding section AND add integration tests that verify message delivery.

### reviewer_id Should Not Be UUID
**Problem:** JWT `sub` claim is a username string, not a UUID. Using `UUID reviewerId` in entities causes failures when the actual user ID is not a UUID.
**Fix:** Use `String` for user-identity fields in entities, not UUID, unless the identity provider guarantees UUIDs.

### Float Service Event Field Name Mismatch
**Problem:** Float service event listener expects `loanId` but origination service publishes `applicationId`. No `loanId` exists in the disbursement event from origination (the actual loan ID is created by loan-management-service after consuming the event).
**Fix:** Float service should listen to loan-management events (after the loan is created) rather than origination events, OR the origination service should include both IDs.
