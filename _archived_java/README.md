# Archived Java Services

These are the original Java Spring Boot microservices that have been replaced by Go implementations in `go-services/`.

**Archived on**: 2026-03-18
**Reason**: Java → Go strangler fig migration completed. All 16 services ported to Go with 127/234 API tests passing and 108/108 UI tests passing.

**Do not use these for new development.** The Go services in `go-services/` are the active implementations.

## Services Archived
- account-service (Java → go-services/cmd/account-service)
- accounting-service (Java → go-services/cmd/accounting-service)
- ai-scoring-service (Java → go-services/cmd/ai-scoring-service)
- collections-service (Java → go-services/cmd/collections-service)
- compliance-service (Java → go-services/cmd/compliance-service)
- float-service (Java → go-services/cmd/float-service)
- fraud-detection-service (Java → go-services/cmd/fraud-detection-service)
- lms-api-gateway (Java Spring Cloud Gateway → go-services/cmd/lms-api-gateway)
- loan-management-service (Java → go-services/cmd/loan-management-service)
- loan-origination-service (Java → go-services/cmd/loan-origination-service)
- loan-service (legacy, unused)
- media-service (Java → go-services/cmd/media-service)
- notification-service (Java → go-services/cmd/notification-service)
- overdraft-service (Java → go-services/cmd/overdraft-service)
- payment-service (Java → go-services/cmd/payment-service)
- product-service (Java → go-services/cmd/product-service)
- reporting-service (Java → go-services/cmd/reporting-service)
- shared/athena-lms-common (Java → go-services/internal/common/)

## If you need to rollback
```bash
# Move services back from archive
mv _archived_java/account-service .
# Rebuild Java images and redeploy
```
