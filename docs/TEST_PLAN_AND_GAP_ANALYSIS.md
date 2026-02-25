# AthenaLMS API Test Plan & PRD Gap Analysis

**Document Version**: 1.0  
**Date**: 2026-02-25  
**Environment**: Local development (localhost)  
**Auth Service**: user-service on port 8081 (AthenaCreditScore stack)

---

## 1. Service Status Summary

Smoke tests were executed on 2026-02-25. All 11 LMS services are registered with Eureka and returning expected HTTP responses.

| Service | Port | Database | Health | Unauthenticated Response | Key Base Path |
|---|---|---|---|---|---|
| account-service | 8086 | athena_accounts | UP (DB+RabbitMQ+Eureka OK) | 403 | /api/v1/accounts |
| product-service | 8087 | athena_products | UP | 403 | /api/v1/products |
| loan-origination-service | 8088 | athena_loans | UP | 403 | /api/v1/loan-applications |
| loan-management-service | 8089 | athena_loans (shared) | UP | 403 | /api/v1/loans |
| payment-service | 8090 | athena_payments | UP | 403 | /api/v1/payments |
| accounting-service | 8091 | athena_accounting | UP | 403 | /api/v1/accounting |
| float-service | 8092 | athena_float | UP | 403 | /api/v1/float |
| collections-service | 8093 | athena_collections | UP | 403 | /api/v1/collections |
| compliance-service | 8094 | athena_compliance | UP | 403 | /api/v1/compliance |
| reporting-service | 8095 | athena_reporting | UP | 403 | /api/v1/reporting |
| ai-scoring-service | 8096 | athena_scoring | UP | 403 | /api/v1/scoring |

**Notes**:
- `403` on unauthenticated requests confirms Spring Security is active and JWT filter is enforcing auth.
- `401` would be expected from a pure 401-returning security config; `403` indicates filter rejects before challenge. Both indicate auth is working.
- `/actuator/health` is publicly accessible (returns `200 UP` on port 8086 confirmed).

---

## 2. API Test Plan (Per Service)

### Authentication Setup

```bash
# Obtain a JWT from the existing user-service (port 8081)
JWT_RESPONSE=$(curl -s -X POST http://localhost:8081/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "admin", "password": "admin123"}')

TOKEN=$(echo $JWT_RESPONSE | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")
JWT="Bearer $TOKEN"

# Verify token works
curl -s http://localhost:8086/api/v1/accounts/search?q=test \
  -H "Authorization: $JWT"
```

> All curl commands below assume `$JWT` is set as above. Replace `{id}`, `{customerId}` etc. with actual UUIDs from your test data.

---

### 2.1 account-service (Port 8086)

**Overview**: Manages deposit/savings/current accounts, balances, and ledger transactions. Multi-tenant. Supports idempotent credit/debit via `Idempotency-Key` header.

**Key Entities**: Account, AccountTransaction

**Database**: athena_accounts

```bash
# ── Health ────────────────────────────────────────────────────────────────────

curl -s http://localhost:8086/actuator/health | python3 -m json.tool

# ── Auth Required: expect 403 without token ───────────────────────────────────

curl -s -o /dev/null -w "Status: %{http_code}\n" http://localhost:8086/api/v1/accounts

# ── Create Account (201) ──────────────────────────────────────────────────────

curl -s -X POST http://localhost:8086/api/v1/accounts \
  -H "Authorization: $JWT" \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": 1,
    "accountType": "SAVINGS",
    "currency": "KES",
    "productCode": "SAVINGS-BASIC"
  }' | python3 -m json.tool

# ── Get Account by ID (200 / 404) ────────────────────────────────────────────

ACCT_ID="<uuid-from-create>"
curl -s "http://localhost:8086/api/v1/accounts/$ACCT_ID" \
  -H "Authorization: $JWT" | python3 -m json.tool

# ── Get Balance (200) ─────────────────────────────────────────────────────────

curl -s "http://localhost:8086/api/v1/accounts/$ACCT_ID/balance" \
  -H "Authorization: $JWT" | python3 -m json.tool

# ── Credit Account (200, idempotent) ──────────────────────────────────────────

curl -s -X POST "http://localhost:8086/api/v1/accounts/$ACCT_ID/credit" \
  -H "Authorization: $JWT" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: idem-key-001" \
  -d '{
    "amount": 50000.00,
    "currency": "KES",
    "reference": "DEPOSIT-001",
    "description": "Initial deposit"
  }' | python3 -m json.tool

# ── Debit Account (200 / 422 on insufficient funds) ───────────────────────────

curl -s -X POST "http://localhost:8086/api/v1/accounts/$ACCT_ID/debit" \
  -H "Authorization: $JWT" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: idem-key-002" \
  -d '{
    "amount": 1000.00,
    "currency": "KES",
    "reference": "WITHDRAW-001",
    "description": "Test debit"
  }' | python3 -m json.tool

# ── Transaction History (paginated) ───────────────────────────────────────────

curl -s "http://localhost:8086/api/v1/accounts/$ACCT_ID/transactions?page=0&size=10" \
  -H "Authorization: $JWT" | python3 -m json.tool

# ── Mini Statement (last N transactions) ──────────────────────────────────────

curl -s "http://localhost:8086/api/v1/accounts/$ACCT_ID/mini-statement?count=5" \
  -H "Authorization: $JWT" | python3 -m json.tool

# ── Search Accounts ───────────────────────────────────────────────────────────

curl -s "http://localhost:8086/api/v1/accounts/search?q=John" \
  -H "Authorization: $JWT" | python3 -m json.tool

# ── Get by Customer ID ────────────────────────────────────────────────────────

curl -s "http://localhost:8086/api/v1/accounts/customer/1" \
  -H "Authorization: $JWT" | python3 -m json.tool

# ── Validation Error Test (expect 400) ───────────────────────────────────────

curl -s -X POST http://localhost:8086/api/v1/accounts \
  -H "Authorization: $JWT" \
  -H "Content-Type: application/json" \
  -d '{"customerId": null}' | python3 -m json.tool
```

**Expected Response Codes**:

| Endpoint | No Auth | Valid Auth | Notes |
|---|---|---|---|
| GET /actuator/health | 200 | 200 | Public |
| POST /api/v1/accounts | 403 | 201 | Returns AccountResponse |
| GET /api/v1/accounts/{id} | 403 | 200/404 | 404 if not found |
| GET /api/v1/accounts/{id}/balance | 403 | 200 | |
| POST /api/v1/accounts/{id}/credit | 403 | 200 | Idempotent |
| POST /api/v1/accounts/{id}/debit | 403 | 200/422 | 422 on insufficient funds |
| GET /api/v1/accounts/{id}/transactions | 403 | 200 | Paginated |
| GET /api/v1/accounts/{id}/mini-statement | 403 | 200 | |
| GET /api/v1/accounts/search | 403 | 200 | |
| GET /api/v1/accounts/customer/{id} | 403 | 200 | |

---

### 2.2 product-service (Port 8087)

**Overview**: Loan product catalog with versioning, templates, and schedule simulation. Role-protected write operations.

**Key Entities**: LoanProduct, ProductVersion, ProductTemplate

**Database**: athena_products

```bash
# ── List Product Templates (200) ─────────────────────────────────────────────

curl -s http://localhost:8087/api/v1/product-templates \
  -H "Authorization: $JWT" | python3 -m json.tool

curl -s http://localhost:8087/api/v1/product-templates/PERSONAL-LOAN-KES \
  -H "Authorization: $JWT" | python3 -m json.tool

# ── Create Product from Template (201, requires ADMIN/LOAN_OFFICER/PRODUCT_MANAGER) ──

curl -s -X POST http://localhost:8087/api/v1/products/from-template/PERSONAL-LOAN-KES \
  -H "Authorization: $JWT" | python3 -m json.tool

# ── Create Product Manually (201) ─────────────────────────────────────────────

curl -s -X POST http://localhost:8087/api/v1/products \
  -H "Authorization: $JWT" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Test Personal Loan",
    "code": "TEST-PL-001",
    "productType": "PERSONAL_LOAN",
    "currency": "KES",
    "minAmount": 10000,
    "maxAmount": 500000,
    "minTenorMonths": 3,
    "maxTenorMonths": 36,
    "interestRate": 18.0,
    "interestMethod": "REDUCING_BALANCE",
    "repaymentFrequency": "MONTHLY",
    "processingFeeRate": 2.5,
    "gracePeriodDays": 3
  }' | python3 -m json.tool

# ── List Products (paginated, 200) ────────────────────────────────────────────

curl -s "http://localhost:8087/api/v1/products?page=0&size=20" \
  -H "Authorization: $JWT" | python3 -m json.tool

# ── Get Product by ID (200/404) ───────────────────────────────────────────────

PROD_ID="<uuid-from-create>"
curl -s "http://localhost:8087/api/v1/products/$PROD_ID" \
  -H "Authorization: $JWT" | python3 -m json.tool

# ── Simulate Repayment Schedule (200) ────────────────────────────────────────

curl -s -X POST "http://localhost:8087/api/v1/products/$PROD_ID/simulate" \
  -H "Authorization: $JWT" \
  -H "Content-Type: application/json" \
  -d '{
    "amount": 100000,
    "tenorMonths": 12,
    "disbursementDate": "2026-03-01"
  }' | python3 -m json.tool

# ── Activate Product (requires ADMIN/PRODUCT_MANAGER) ────────────────────────

curl -s -X POST "http://localhost:8087/api/v1/products/$PROD_ID/activate" \
  -H "Authorization: $JWT" | python3 -m json.tool

# ── Deactivate Product ────────────────────────────────────────────────────────

curl -s -X POST "http://localhost:8087/api/v1/products/$PROD_ID/deactivate" \
  -H "Authorization: $JWT" | python3 -m json.tool

# ── Product Version History (200) ─────────────────────────────────────────────

curl -s "http://localhost:8087/api/v1/products/$PROD_ID/versions" \
  -H "Authorization: $JWT" | python3 -m json.tool
```

**Expected Response Codes**:

| Endpoint | No Auth | Valid Auth | Notes |
|---|---|---|---|
| GET /api/v1/product-templates | 403 | 200 | Public list |
| POST /api/v1/products | 403 | 201/403 | 403 if wrong role |
| GET /api/v1/products | 403 | 200 | Paginated |
| GET /api/v1/products/{id} | 403 | 200/404 | |
| POST /api/v1/products/{id}/simulate | 403 | 200 | No role restriction |
| POST /api/v1/products/{id}/activate | 403 | 200/403 | Requires PRODUCT_MANAGER |
| GET /api/v1/products/{id}/versions | 403 | 200 | |

---

### 2.3 loan-origination-service (Port 8088)

**Overview**: Full loan application lifecycle with state machine: DRAFT → SUBMITTED → UNDER_REVIEW → APPROVED/REJECTED → DISBURSED/CANCELLED. Publishes RabbitMQ events on state transitions.

**Key Entities**: LoanApplication, ApplicationCollateral, ApplicationNote, ApplicationStatusHistory

**Database**: athena_loans

```bash
# ── Create Application (201, DRAFT state) ─────────────────────────────────────

APP_RESP=$(curl -s -X POST http://localhost:8088/api/v1/loan-applications \
  -H "Authorization: $JWT" \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "11111111-1111-1111-1111-111111111111",
    "productId": "'$PROD_ID'",
    "requestedAmount": 100000.00,
    "requestedTenorMonths": 12,
    "purpose": "Business expansion",
    "currency": "KES"
  }')
echo $APP_RESP | python3 -m json.tool
APP_ID=$(echo $APP_RESP | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")

# ── Get Application (200) ─────────────────────────────────────────────────────

curl -s "http://localhost:8088/api/v1/loan-applications/$APP_ID" \
  -H "Authorization: $JWT" | python3 -m json.tool

# ── Add Collateral (201) ──────────────────────────────────────────────────────

curl -s -X POST "http://localhost:8088/api/v1/loan-applications/$APP_ID/collaterals" \
  -H "Authorization: $JWT" \
  -H "Content-Type: application/json" \
  -d '{
    "collateralType": "VEHICLE",
    "description": "Toyota Land Cruiser 2020",
    "estimatedValue": 4500000.00,
    "currency": "KES"
  }' | python3 -m json.tool

# ── Add Note (201) ────────────────────────────────────────────────────────────

curl -s -X POST "http://localhost:8088/api/v1/loan-applications/$APP_ID/notes" \
  -H "Authorization: $JWT" \
  -H "Content-Type: application/json" \
  -d '{
    "noteText": "Customer has good repayment history. Recommend for fast-track review.",
    "noteType": "UNDERWRITER"
  }' | python3 -m json.tool

# ── Submit Application (200, DRAFT → SUBMITTED; triggers AI scoring event) ────

curl -s -X POST "http://localhost:8088/api/v1/loan-applications/$APP_ID/submit" \
  -H "Authorization: $JWT" | python3 -m json.tool

# ── Start Review (200, SUBMITTED → UNDER_REVIEW) ─────────────────────────────

curl -s -X POST "http://localhost:8088/api/v1/loan-applications/$APP_ID/review/start" \
  -H "Authorization: $JWT" | python3 -m json.tool

# ── Approve (200, UNDER_REVIEW → APPROVED) ────────────────────────────────────

curl -s -X POST "http://localhost:8088/api/v1/loan-applications/$APP_ID/review/approve" \
  -H "Authorization: $JWT" \
  -H "Content-Type: application/json" \
  -d '{
    "approvedAmount": 100000.00,
    "approvedTenorMonths": 12,
    "interestRate": 18.0,
    "conditions": "Maintain collateral insurance throughout tenure"
  }' | python3 -m json.tool

# ── Reject (alternative to approve; UNDER_REVIEW → REJECTED) ─────────────────

curl -s -X POST "http://localhost:8088/api/v1/loan-applications/$APP_ID/review/reject" \
  -H "Authorization: $JWT" \
  -H "Content-Type: application/json" \
  -d '{
    "rejectionReason": "Insufficient collateral coverage",
    "rejectionCode": "COLLATERAL_INSUFFICIENT"
  }' | python3 -m json.tool

# ── Disburse (200, APPROVED → DISBURSED; triggers loan-management + accounting events) ─

curl -s -X POST "http://localhost:8088/api/v1/loan-applications/$APP_ID/disburse" \
  -H "Authorization: $JWT" \
  -H "Content-Type: application/json" \
  -d '{
    "disbursementDate": "2026-03-01",
    "disbursementAccount": "'$ACCT_ID'",
    "reference": "DISB-001"
  }' | python3 -m json.tool

# ── Cancel Application ────────────────────────────────────────────────────────

curl -s -X POST "http://localhost:8088/api/v1/loan-applications/$APP_ID/cancel?reason=Customer+request" \
  -H "Authorization: $JWT" | python3 -m json.tool

# ── List Applications (paginated + status filter) ─────────────────────────────

curl -s "http://localhost:8088/api/v1/loan-applications?status=SUBMITTED&page=0&size=20" \
  -H "Authorization: $JWT" | python3 -m json.tool

# ── List by Customer ──────────────────────────────────────────────────────────

curl -s "http://localhost:8088/api/v1/loan-applications/customer/11111111-1111-1111-1111-111111111111" \
  -H "Authorization: $JWT" | python3 -m json.tool

# ── State Machine Violation Test (expect 422/400) ────────────────────────────

# Try to approve a DRAFT application (should fail)
NEW_APP_ID=$(curl -s -X POST http://localhost:8088/api/v1/loan-applications \
  -H "Authorization: $JWT" -H "Content-Type: application/json" \
  -d '{"customerId":"11111111-1111-1111-1111-111111111111","productId":"'$PROD_ID'","requestedAmount":50000,"requestedTenorMonths":6,"purpose":"test","currency":"KES"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")
curl -s -X POST "http://localhost:8088/api/v1/loan-applications/$NEW_APP_ID/review/approve" \
  -H "Authorization: $JWT" -H "Content-Type: application/json" \
  -d '{"approvedAmount":50000,"approvedTenorMonths":6,"interestRate":18}' | python3 -m json.tool
```

**Expected Response Codes**:

| Endpoint | No Auth | Valid Auth | Invalid State |
|---|---|---|---|
| POST /api/v1/loan-applications | 403 | 201 | N/A |
| GET /api/v1/loan-applications/{id} | 403 | 200/404 | N/A |
| POST /{id}/submit | 403 | 200 | 422 if not DRAFT |
| POST /{id}/review/start | 403 | 200 | 422 if not SUBMITTED |
| POST /{id}/review/approve | 403 | 200 | 422 if not UNDER_REVIEW |
| POST /{id}/review/reject | 403 | 200 | 422 if not UNDER_REVIEW |
| POST /{id}/disburse | 403 | 200 | 422 if not APPROVED |
| POST /{id}/cancel | 403 | 200 | 422 if already terminal |

---

### 2.4 loan-management-service (Port 8089)

**Overview**: Manages active loans post-disbursement. Handles repayment schedule, DPD calculation, staging, and restructuring. Consumes `loan.disbursed` events from origination.

**Key Entities**: Loan, LoanSchedule, LoanRepayment, LoanDpdHistory

**Database**: athena_loans (shared with origination, separate Flyway history table)

```bash
# ── List Loans (paginated) ────────────────────────────────────────────────────

curl -s "http://localhost:8089/api/v1/loans?page=0&size=20" \
  -H "Authorization: $JWT" | python3 -m json.tool

# ── Filter by Status ──────────────────────────────────────────────────────────

curl -s "http://localhost:8089/api/v1/loans?status=ACTIVE" \
  -H "Authorization: $JWT" | python3 -m json.tool

# ── Get Loan by ID (200/404) ──────────────────────────────────────────────────

LOAN_ID="<uuid-created-after-disbursement>"
curl -s "http://localhost:8089/api/v1/loans/$LOAN_ID" \
  -H "Authorization: $JWT" | python3 -m json.tool

# ── Get Repayment Schedule ────────────────────────────────────────────────────

curl -s "http://localhost:8089/api/v1/loans/$LOAN_ID/schedule" \
  -H "Authorization: $JWT" | python3 -m json.tool

# ── Get Single Installment ────────────────────────────────────────────────────

curl -s "http://localhost:8089/api/v1/loans/$LOAN_ID/schedule/1" \
  -H "Authorization: $JWT" | python3 -m json.tool

# ── Apply Repayment (201) ─────────────────────────────────────────────────────

curl -s -X POST "http://localhost:8089/api/v1/loans/$LOAN_ID/repayments" \
  -H "Authorization: $JWT" \
  -H "Content-Type: application/json" \
  -d '{
    "amount": 9500.00,
    "currency": "KES",
    "paymentDate": "2026-03-01",
    "reference": "REPAY-001",
    "paymentChannel": "MPESA"
  }' | python3 -m json.tool

# ── Get Repayment History ─────────────────────────────────────────────────────

curl -s "http://localhost:8089/api/v1/loans/$LOAN_ID/repayments" \
  -H "Authorization: $JWT" | python3 -m json.tool

# ── Get DPD (Days Past Due) ───────────────────────────────────────────────────

curl -s "http://localhost:8089/api/v1/loans/$LOAN_ID/dpd" \
  -H "Authorization: $JWT" | python3 -m json.tool

# ── Restructure Loan (200) ────────────────────────────────────────────────────

curl -s -X POST "http://localhost:8089/api/v1/loans/$LOAN_ID/restructure" \
  -H "Authorization: $JWT" \
  -H "Content-Type: application/json" \
  -d '{
    "newTenorMonths": 18,
    "restructureReason": "Financial hardship",
    "effectiveDate": "2026-04-01"
  }' | python3 -m json.tool

# ── Get Loans by Customer ─────────────────────────────────────────────────────

curl -s "http://localhost:8089/api/v1/loans/customer/11111111-1111-1111-1111-111111111111" \
  -H "Authorization: $JWT" | python3 -m json.tool
```

**Expected Response Codes**:

| Endpoint | No Auth | Valid Auth | Notes |
|---|---|---|---|
| GET /api/v1/loans | 403 | 200 | Paginated |
| GET /api/v1/loans/{id} | 403 | 200/404 | Created by event consumer |
| GET /api/v1/loans/{id}/schedule | 403 | 200 | Array of installments |
| GET /api/v1/loans/{id}/dpd | 403 | 200 | DPD + stage + PAR bucket |
| POST /api/v1/loans/{id}/repayments | 403 | 201 | Waterfall allocation |
| POST /api/v1/loans/{id}/restructure | 403 | 200 | Regenerates schedule |

---

### 2.5 payment-service (Port 8090)

**Overview**: Initiates and tracks payments (disbursements, repayments, fees). Supports multiple payment channels. Manages payment methods per customer.

**Key Entities**: Payment, PaymentMethod

**Database**: athena_payments

```bash
# ── Initiate Payment (201) ────────────────────────────────────────────────────

PAY_RESP=$(curl -s -X POST http://localhost:8090/api/v1/payments \
  -H "Authorization: $JWT" \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "11111111-1111-1111-1111-111111111111",
    "amount": 100000.00,
    "currency": "KES",
    "paymentType": "DISBURSEMENT",
    "channel": "BANK_TRANSFER",
    "reference": "PAY-REF-001",
    "description": "Loan disbursement",
    "loanId": "'$LOAN_ID'"
  }')
echo $PAY_RESP | python3 -m json.tool
PAY_ID=$(echo $PAY_RESP | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")

# ── Get Payment by ID ─────────────────────────────────────────────────────────

curl -s "http://localhost:8090/api/v1/payments/$PAY_ID" \
  -H "Authorization: $JWT" | python3 -m json.tool

# ── Process Payment (PENDING → PROCESSING) ────────────────────────────────────

curl -s -X POST "http://localhost:8090/api/v1/payments/$PAY_ID/process" \
  -H "Authorization: $JWT" | python3 -m json.tool

# ── Complete Payment (PROCESSING → COMPLETED) ─────────────────────────────────

curl -s -X POST "http://localhost:8090/api/v1/payments/$PAY_ID/complete" \
  -H "Authorization: $JWT" \
  -H "Content-Type: application/json" \
  -d '{
    "externalReference": "BANK-TXN-12345",
    "completedAt": "2026-03-01T10:00:00Z"
  }' | python3 -m json.tool

# ── Fail Payment ──────────────────────────────────────────────────────────────

curl -s -X POST "http://localhost:8090/api/v1/payments/$PAY_ID/fail" \
  -H "Authorization: $JWT" \
  -H "Content-Type: application/json" \
  -d '{
    "failureReason": "Insufficient funds in recipient account",
    "failureCode": "INSUF_FUNDS"
  }' | python3 -m json.tool

# ── Reverse Payment ───────────────────────────────────────────────────────────

curl -s -X POST "http://localhost:8090/api/v1/payments/$PAY_ID/reverse" \
  -H "Authorization: $JWT" \
  -H "Content-Type: application/json" \
  -d '{
    "reversalReason": "Customer request",
    "reversalReference": "REV-001"
  }' | python3 -m json.tool

# ── Get by Reference ──────────────────────────────────────────────────────────

curl -s "http://localhost:8090/api/v1/payments/reference/PAY-REF-001" \
  -H "Authorization: $JWT" | python3 -m json.tool

# ── List Payments (with filters) ──────────────────────────────────────────────

curl -s "http://localhost:8090/api/v1/payments?status=COMPLETED&type=DISBURSEMENT&page=0&size=20" \
  -H "Authorization: $JWT" | python3 -m json.tool

# ── List by Customer ──────────────────────────────────────────────────────────

curl -s "http://localhost:8090/api/v1/payments/customer/11111111-1111-1111-1111-111111111111" \
  -H "Authorization: $JWT" | python3 -m json.tool

# ── Add Payment Method ────────────────────────────────────────────────────────

curl -s -X POST http://localhost:8090/api/v1/payments/methods \
  -H "Authorization: $JWT" \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "11111111-1111-1111-1111-111111111111",
    "methodType": "MPESA",
    "identifier": "0722000001",
    "displayName": "John Doe M-Pesa"
  }' | python3 -m json.tool

# ── Get Customer Payment Methods ─────────────────────────────────────────────

curl -s "http://localhost:8090/api/v1/payments/methods/customer/11111111-1111-1111-1111-111111111111" \
  -H "Authorization: $JWT" | python3 -m json.tool
```

---

### 2.6 accounting-service (Port 8091)

**Overview**: Double-entry bookkeeping. Chart of Accounts (COA), journal entries, balance ledger, and trial balance. Base path is `/api/v1/accounting` (not `/api/v1/accounts`).

**Key Entities**: LedgerAccount (COA), JournalEntry, JournalLine, AccountBalance

**Database**: athena_accounting

```bash
# ── Create GL Account (Chart of Accounts) ─────────────────────────────────────

curl -s -X POST http://localhost:8091/api/v1/accounting/accounts \
  -H "Authorization: $JWT" \
  -H "Content-Type: application/json" \
  -d '{
    "code": "1001",
    "name": "Cash and Bank",
    "accountType": "ASSET",
    "currency": "KES",
    "description": "Primary cash and bank account"
  }' | python3 -m json.tool

# ── List All GL Accounts ──────────────────────────────────────────────────────

curl -s "http://localhost:8091/api/v1/accounting/accounts" \
  -H "Authorization: $JWT" | python3 -m json.tool

# ── Filter by Account Type ────────────────────────────────────────────────────

curl -s "http://localhost:8091/api/v1/accounting/accounts?type=ASSET" \
  -H "Authorization: $JWT" | python3 -m json.tool

# ── Get GL Account by Code ────────────────────────────────────────────────────

GL_ID="<uuid-from-create>"
curl -s "http://localhost:8091/api/v1/accounting/accounts/code/1001" \
  -H "Authorization: $JWT" | python3 -m json.tool

# ── Post Journal Entry ────────────────────────────────────────────────────────

curl -s -X POST http://localhost:8091/api/v1/accounting/journal-entries \
  -H "Authorization: $JWT" \
  -H "Content-Type: application/json" \
  -d '{
    "entryDate": "2026-03-01",
    "reference": "JE-001",
    "description": "Loan disbursement entry",
    "lines": [
      {"accountCode": "1200", "debit": 100000.00, "credit": 0, "description": "Loan receivable DR"},
      {"accountCode": "1001", "debit": 0, "credit": 100000.00, "description": "Cash CR"}
    ]
  }' | python3 -m json.tool

# ── List Journal Entries (date filter) ───────────────────────────────────────

curl -s "http://localhost:8091/api/v1/accounting/journal-entries?from=2026-03-01&to=2026-03-31&page=0&size=20" \
  -H "Authorization: $JWT" | python3 -m json.tool

# ── Get Account Balance ───────────────────────────────────────────────────────

curl -s "http://localhost:8091/api/v1/accounting/accounts/$GL_ID/balance?year=2026&month=3" \
  -H "Authorization: $JWT" | python3 -m json.tool

# ── Get Account Ledger ────────────────────────────────────────────────────────

curl -s "http://localhost:8091/api/v1/accounting/accounts/$GL_ID/ledger" \
  -H "Authorization: $JWT" | python3 -m json.tool

# ── Trial Balance ─────────────────────────────────────────────────────────────

curl -s "http://localhost:8091/api/v1/accounting/trial-balance?year=2026&month=3" \
  -H "Authorization: $JWT" | python3 -m json.tool

# ── Unbalanced Journal Entry (expect 400/422) ─────────────────────────────────

curl -s -X POST http://localhost:8091/api/v1/accounting/journal-entries \
  -H "Authorization: $JWT" \
  -H "Content-Type: application/json" \
  -d '{
    "entryDate": "2026-03-01",
    "reference": "BAD-JE-001",
    "description": "Unbalanced entry",
    "lines": [
      {"accountCode": "1200", "debit": 100000.00, "credit": 0, "description": "DR only"}
    ]
  }' | python3 -m json.tool
```

---

### 2.7 float-service (Port 8092)

**Overview**: Manages float (liquidity pool) accounts for the MFI. Tracks draws (for loan disbursements) and repayments back into float. Provides float summary for treasury management.

**Key Entities**: FloatAccount, FloatTransaction

**Database**: athena_float

```bash
# ── Create Float Account (201) ────────────────────────────────────────────────

FLOAT_RESP=$(curl -s -X POST http://localhost:8092/api/v1/float/accounts \
  -H "Authorization: $JWT" \
  -H "Content-Type: application/json" \
  -d '{
    "accountName": "Main Float Account",
    "currency": "KES",
    "initialBalance": 10000000.00,
    "description": "Primary lending float pool"
  }')
echo $FLOAT_RESP | python3 -m json.tool
FLOAT_ID=$(echo $FLOAT_RESP | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")

# ── List Float Accounts ───────────────────────────────────────────────────────

curl -s http://localhost:8092/api/v1/float/accounts \
  -H "Authorization: $JWT" | python3 -m json.tool

# ── Get Float Account ─────────────────────────────────────────────────────────

curl -s "http://localhost:8092/api/v1/float/accounts/$FLOAT_ID" \
  -H "Authorization: $JWT" | python3 -m json.tool

# ── Draw from Float (loan disbursement reduces float) ─────────────────────────

curl -s -X POST "http://localhost:8092/api/v1/float/accounts/$FLOAT_ID/draw" \
  -H "Authorization: $JWT" \
  -H "Content-Type: application/json" \
  -d '{
    "amount": 100000.00,
    "currency": "KES",
    "reference": "DRAW-001",
    "description": "Disbursement for loan APP-001",
    "loanId": "'$LOAN_ID'"
  }' | python3 -m json.tool

# ── Repay to Float (repayment increases float) ────────────────────────────────

curl -s -X POST "http://localhost:8092/api/v1/float/accounts/$FLOAT_ID/repay" \
  -H "Authorization: $JWT" \
  -H "Content-Type: application/json" \
  -d '{
    "amount": 9500.00,
    "currency": "KES",
    "reference": "REPAY-TO-FLOAT-001",
    "description": "Repayment received",
    "loanId": "'$LOAN_ID'"
  }' | python3 -m json.tool

# ── Transaction History ────────────────────────────────────────────────────────

curl -s "http://localhost:8092/api/v1/float/accounts/$FLOAT_ID/transactions?page=0&size=20" \
  -H "Authorization: $JWT" | python3 -m json.tool

# ── Float Summary (treasury view) ─────────────────────────────────────────────

curl -s http://localhost:8092/api/v1/float/summary \
  -H "Authorization: $JWT" | python3 -m json.tool

# ── Overdraw Test (expect 422) ────────────────────────────────────────────────

curl -s -X POST "http://localhost:8092/api/v1/float/accounts/$FLOAT_ID/draw" \
  -H "Authorization: $JWT" \
  -H "Content-Type: application/json" \
  -d '{"amount": 999999999.00, "currency": "KES", "reference": "OVERDRAW-TEST"}' \
  | python3 -m json.tool
```

---

### 2.8 collections-service (Port 8093)

**Overview**: Manages delinquent loan cases. Collection cases are auto-created when DPD exceeds threshold (via RabbitMQ event from loan-management). Supports agent actions and promise-to-pay (PTP) tracking.

**Key Entities**: CollectionCase, CollectionAction, PromiseToPay

**Database**: athena_collections

```bash
# ── List Cases ────────────────────────────────────────────────────────────────

curl -s "http://localhost:8093/api/v1/collections/cases?page=0&size=20" \
  -H "Authorization: $JWT" | python3 -m json.tool

# ── Filter by Status ──────────────────────────────────────────────────────────

curl -s "http://localhost:8093/api/v1/collections/cases?status=OPEN" \
  -H "Authorization: $JWT" | python3 -m json.tool

# ── Get Case by ID ────────────────────────────────────────────────────────────

CASE_ID="<uuid-auto-created-by-event>"
curl -s "http://localhost:8093/api/v1/collections/cases/$CASE_ID" \
  -H "Authorization: $JWT" | python3 -m json.tool

# ── Get Case by Loan ID ───────────────────────────────────────────────────────

curl -s "http://localhost:8093/api/v1/collections/cases/loan/$LOAN_ID" \
  -H "Authorization: $JWT" | python3 -m json.tool

# ── Update Case ───────────────────────────────────────────────────────────────

curl -s -X PUT "http://localhost:8093/api/v1/collections/cases/$CASE_ID" \
  -H "Authorization: $JWT" \
  -H "Content-Type: application/json" \
  -d '{
    "assignedAgent": "agent001",
    "priority": "HIGH",
    "notes": "Escalated due to no response"
  }' | python3 -m json.tool

# ── Add Collection Action ─────────────────────────────────────────────────────

curl -s -X POST "http://localhost:8093/api/v1/collections/cases/$CASE_ID/actions" \
  -H "Authorization: $JWT" \
  -H "Content-Type: application/json" \
  -d '{
    "actionType": "PHONE_CALL",
    "outcome": "CUSTOMER_CONTACTED",
    "notes": "Customer promised to pay on 2026-03-05",
    "agentId": "agent001"
  }' | python3 -m json.tool

# ── List Actions for Case ─────────────────────────────────────────────────────

curl -s "http://localhost:8093/api/v1/collections/cases/$CASE_ID/actions" \
  -H "Authorization: $JWT" | python3 -m json.tool

# ── Add Promise-to-Pay ────────────────────────────────────────────────────────

curl -s -X POST "http://localhost:8093/api/v1/collections/cases/$CASE_ID/ptps" \
  -H "Authorization: $JWT" \
  -H "Content-Type: application/json" \
  -d '{
    "promisedAmount": 9500.00,
    "promisedDate": "2026-03-05",
    "notes": "Verbal commitment via phone"
  }' | python3 -m json.tool

# ── List PTPs for Case ────────────────────────────────────────────────────────

curl -s "http://localhost:8093/api/v1/collections/cases/$CASE_ID/ptps" \
  -H "Authorization: $JWT" | python3 -m json.tool

# ── Close Case ────────────────────────────────────────────────────────────────

curl -s -X POST "http://localhost:8093/api/v1/collections/cases/$CASE_ID/close" \
  -H "Authorization: $JWT" | python3 -m json.tool

# ── Collections Summary ───────────────────────────────────────────────────────

curl -s http://localhost:8093/api/v1/collections/summary \
  -H "Authorization: $JWT" | python3 -m json.tool
```

---

### 2.9 compliance-service (Port 8094)

**Overview**: AML/KYC compliance management. Handles suspicious activity alerts, SAR (Suspicious Activity Report) filing, and KYC verification state per customer. Also maintains a compliance event audit log.

**Key Entities**: AmlAlert, SarFiling, KycRecord, ComplianceEvent

**Database**: athena_compliance

```bash
# ── Create AML Alert (201) ────────────────────────────────────────────────────

ALERT_RESP=$(curl -s -X POST http://localhost:8094/api/v1/compliance/alerts \
  -H "Authorization: $JWT" \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": 1,
    "alertType": "LARGE_TRANSACTION",
    "severity": "HIGH",
    "description": "Single transaction exceeds KES 1,000,000 threshold",
    "transactionReference": "PAY-REF-001",
    "amount": 1500000.00,
    "currency": "KES"
  }')
echo $ALERT_RESP | python3 -m json.tool
ALERT_ID=$(echo $ALERT_RESP | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")

# ── List Alerts ───────────────────────────────────────────────────────────────

curl -s "http://localhost:8094/api/v1/compliance/alerts?status=OPEN&page=0&size=20" \
  -H "Authorization: $JWT" | python3 -m json.tool

# ── Get Alert ─────────────────────────────────────────────────────────────────

curl -s "http://localhost:8094/api/v1/compliance/alerts/$ALERT_ID" \
  -H "Authorization: $JWT" | python3 -m json.tool

# ── Resolve Alert ─────────────────────────────────────────────────────────────

curl -s -X POST "http://localhost:8094/api/v1/compliance/alerts/$ALERT_ID/resolve" \
  -H "Authorization: $JWT" \
  -H "Content-Type: application/json" \
  -d '{
    "resolutionNotes": "Verified as legitimate salary payment from employer",
    "resolvedBy": "compliance-officer-001"
  }' | python3 -m json.tool

# ── File SAR (201) ────────────────────────────────────────────────────────────

curl -s -X POST "http://localhost:8094/api/v1/compliance/alerts/$ALERT_ID/sar" \
  -H "Authorization: $JWT" \
  -H "Content-Type: application/json" \
  -d '{
    "regulator": "FRC_KENYA",
    "notes": "Pattern of structuring transactions below reporting threshold",
    "submittedBy": "compliance-officer-001"
  }' | python3 -m json.tool

# ── Get SAR for Alert ─────────────────────────────────────────────────────────

curl -s "http://localhost:8094/api/v1/compliance/alerts/$ALERT_ID/sar" \
  -H "Authorization: $JWT" | python3 -m json.tool

# ── Create/Update KYC Record ──────────────────────────────────────────────────

curl -s -X POST http://localhost:8094/api/v1/compliance/kyc \
  -H "Authorization: $JWT" \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": 1,
    "documentType": "NATIONAL_ID",
    "documentNumber": "12345678",
    "documentExpiryDate": "2030-12-31",
    "verificationMethod": "MANUAL"
  }' | python3 -m json.tool

# ── Get KYC Status ────────────────────────────────────────────────────────────

curl -s http://localhost:8094/api/v1/compliance/kyc/1 \
  -H "Authorization: $JWT" | python3 -m json.tool

# ── Pass KYC ─────────────────────────────────────────────────────────────────

curl -s -X POST http://localhost:8094/api/v1/compliance/kyc/1/pass \
  -H "Authorization: $JWT" | python3 -m json.tool

# ── Fail KYC ─────────────────────────────────────────────────────────────────

curl -s -X POST http://localhost:8094/api/v1/compliance/kyc/1/fail \
  -H "Authorization: $JWT" \
  -H "Content-Type: application/json" \
  -d '{"resolutionNotes": "Document appears tampered"}' | python3 -m json.tool

# ── List Compliance Events ────────────────────────────────────────────────────

curl -s "http://localhost:8094/api/v1/compliance/events?page=0&size=20" \
  -H "Authorization: $JWT" | python3 -m json.tool

# ── Compliance Summary ────────────────────────────────────────────────────────

curl -s http://localhost:8094/api/v1/compliance/summary \
  -H "Authorization: $JWT" | python3 -m json.tool
```

---

### 2.10 reporting-service (Port 8095)

**Overview**: Portfolio analytics and reporting. Consumes all LMS events via RabbitMQ, stores event stream, and generates portfolio snapshots (PAR buckets, loan counts, disbursed/outstanding amounts).

**Key Entities**: ReportEvent, PortfolioSnapshot, EventMetric

**Database**: athena_reporting

> Note: The reporting service uses `/api/v1/reporting` (not `/api/v1/reports`).

```bash
# ── Get Events (full audit log) ───────────────────────────────────────────────

curl -s "http://localhost:8095/api/v1/reporting/events?page=0&size=20" \
  -H "Authorization: $JWT" | python3 -m json.tool

# ── Filter Events by Type and Date ───────────────────────────────────────────

curl -s "http://localhost:8095/api/v1/reporting/events?eventType=loan.disbursed&from=2026-03-01T00:00:00Z&to=2026-03-31T23:59:59Z" \
  -H "Authorization: $JWT" | python3 -m json.tool

# ── Get Portfolio Snapshots ───────────────────────────────────────────────────

curl -s "http://localhost:8095/api/v1/reporting/snapshots?page=0&size=30" \
  -H "Authorization: $JWT" | python3 -m json.tool

# ── Get Latest Snapshot ───────────────────────────────────────────────────────

curl -s http://localhost:8095/api/v1/reporting/snapshots/latest \
  -H "Authorization: $JWT" | python3 -m json.tool

# ── Get Metrics (requires date range) ────────────────────────────────────────

curl -s "http://localhost:8095/api/v1/reporting/metrics?from=2026-03-01&to=2026-03-31" \
  -H "Authorization: $JWT" | python3 -m json.tool

# ── Portfolio Summary (aggregated view) ──────────────────────────────────────

curl -s http://localhost:8095/api/v1/reporting/summary \
  -H "Authorization: $JWT" | python3 -m json.tool

# ── Trigger Manual Snapshot Generation ───────────────────────────────────────

curl -s -X POST http://localhost:8095/api/v1/reporting/snapshots/generate \
  -H "Authorization: $JWT" | python3 -m json.tool
```

**Note on Smoke Test Result**: The smoke test against `/api/v1/reports` returned 403 (path not found behind security filter). The actual base path is `/api/v1/reporting`. This is a documentation discrepancy — not a service defect.

---

### 2.11 ai-scoring-service (Port 8096)

**Overview**: Spring Boot adapter that proxies AI credit scoring to the existing Python service (port 8001). Receives `loan.application.submitted` events via RabbitMQ, runs scoring, stores results. Supports manual scoring triggers.

**Key Entities**: ScoringRequest, ScoringResult

**Database**: athena_scoring

```bash
# ── Manual Score Trigger (201) ────────────────────────────────────────────────

SCORE_RESP=$(curl -s -X POST http://localhost:8096/api/v1/scoring/requests \
  -H "Authorization: $JWT" \
  -H "Content-Type: application/json" \
  -d '{
    "applicationId": "'$APP_ID'",
    "customerId": 1,
    "requestedAmount": 100000.00,
    "requestedTenorMonths": 12
  }')
echo $SCORE_RESP | python3 -m json.tool
SCORE_REQ_ID=$(echo $SCORE_RESP | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])" 2>/dev/null)

# ── List Scoring Requests ─────────────────────────────────────────────────────

curl -s "http://localhost:8096/api/v1/scoring/requests?page=0&size=20" \
  -H "Authorization: $JWT" | python3 -m json.tool

# ── Get Scoring Request by ID ─────────────────────────────────────────────────

curl -s "http://localhost:8096/api/v1/scoring/requests/$SCORE_REQ_ID" \
  -H "Authorization: $JWT" | python3 -m json.tool

# ── Get Scoring Request by Application ───────────────────────────────────────

curl -s "http://localhost:8096/api/v1/scoring/applications/$APP_ID/request" \
  -H "Authorization: $JWT" | python3 -m json.tool

# ── Get Scoring Result by Application ────────────────────────────────────────

curl -s "http://localhost:8096/api/v1/scoring/applications/$APP_ID/result" \
  -H "Authorization: $JWT" | python3 -m json.tool

# ── Get Latest Score for Customer ────────────────────────────────────────────

curl -s http://localhost:8096/api/v1/scoring/customers/1/latest \
  -H "Authorization: $JWT" | python3 -m json.tool
```

---

## 3. End-to-End Test Scenarios

### Scenario 1: Complete Loan Journey

```bash
BASE_8087="http://localhost:8087"
BASE_8088="http://localhost:8088"
BASE_8089="http://localhost:8089"
BASE_8090="http://localhost:8090"
BASE_8096="http://localhost:8096"
H_AUTH="Authorization: $JWT"
H_JSON="Content-Type: application/json"
CUSTOMER_ID="11111111-1111-1111-1111-111111111111"

# Step 1 — Browse available active products
curl -s "$BASE_8087/api/v1/products?page=0&size=10" -H "$H_AUTH"

# Step 2 — Simulate schedule before applying
curl -s -X POST "$BASE_8087/api/v1/products/$PROD_ID/simulate" \
  -H "$H_AUTH" -H "$H_JSON" \
  -d '{"amount":100000,"tenorMonths":12,"disbursementDate":"2026-03-01"}'

# Step 3 — Create loan application (DRAFT)
APP_ID=$(curl -s -X POST "$BASE_8088/api/v1/loan-applications" \
  -H "$H_AUTH" -H "$H_JSON" \
  -d '{"customerId":"'$CUSTOMER_ID'","productId":"'$PROD_ID'","requestedAmount":100000,"requestedTenorMonths":12,"purpose":"Working capital","currency":"KES"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")

# Step 4 — Submit (DRAFT → SUBMITTED; triggers ai-scoring event automatically)
curl -s -X POST "$BASE_8088/api/v1/loan-applications/$APP_ID/submit" -H "$H_AUTH"

# Step 5 — Check AI score arrived (allow ~2s for async processing)
sleep 2
curl -s "$BASE_8096/api/v1/scoring/applications/$APP_ID/result" -H "$H_AUTH"

# Step 6 — Start review (SUBMITTED → UNDER_REVIEW)
curl -s -X POST "$BASE_8088/api/v1/loan-applications/$APP_ID/review/start" -H "$H_AUTH"

# Step 7 — Approve (UNDER_REVIEW → APPROVED)
curl -s -X POST "$BASE_8088/api/v1/loan-applications/$APP_ID/review/approve" \
  -H "$H_AUTH" -H "$H_JSON" \
  -d '{"approvedAmount":100000,"approvedTenorMonths":12,"interestRate":18.0,"conditions":"Standard conditions apply"}'

# Step 8 — Disburse (APPROVED → DISBURSED; events fire to loan-mgmt + accounting + float)
curl -s -X POST "$BASE_8088/api/v1/loan-applications/$APP_ID/disburse" \
  -H "$H_AUTH" -H "$H_JSON" \
  -d '{"disbursementDate":"2026-03-01","disbursementAccount":"'$ACCT_ID'","reference":"DISB-LOAN-001"}'

# Step 9 — Confirm loan created in loan-management (allow ~2s for event)
sleep 2
LOAN_ID=$(curl -s "$BASE_8089/api/v1/loans/customer/$CUSTOMER_ID" -H "$H_AUTH" \
  | python3 -c "import sys,json; data=json.load(sys.stdin); print(data[0]['id']) if data else print('NOT_FOUND')")

# Step 10 — View repayment schedule
curl -s "$BASE_8089/api/v1/loans/$LOAN_ID/schedule" -H "$H_AUTH"

# Step 11 — Make first repayment
curl -s -X POST "$BASE_8089/api/v1/loans/$LOAN_ID/repayments" \
  -H "$H_AUTH" -H "$H_JSON" \
  -d '{"amount":9500,"currency":"KES","paymentDate":"2026-04-01","reference":"REPAY-001","paymentChannel":"MPESA"}'

# Step 12 — Check DPD (should be 0 if paid on time)
curl -s "$BASE_8089/api/v1/loans/$LOAN_ID/dpd" -H "$H_AUTH"
```

**Expected**: Application transitions through all states. Loan record appears in loan-management within seconds. Schedule generated. Repayment reduces outstanding balance. DPD = 0.

---

### Scenario 2: Float Draw Workflow

```bash
BASE_8092="http://localhost:8092"

# Step 1 — Create float account (treasury setup — done once)
FLOAT_ID=$(curl -s -X POST "$BASE_8092/api/v1/float/accounts" \
  -H "$H_AUTH" -H "$H_JSON" \
  -d '{"accountName":"Primary Float","currency":"KES","initialBalance":10000000,"description":"Main pool"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")

# Step 2 — Check float before draw
curl -s "$BASE_8092/api/v1/float/summary" -H "$H_AUTH"

# Step 3 — Draw for loan disbursement
curl -s -X POST "$BASE_8092/api/v1/float/accounts/$FLOAT_ID/draw" \
  -H "$H_AUTH" -H "$H_JSON" \
  -d '{"amount":100000,"currency":"KES","reference":"DRAW-'$LOAN_ID'","description":"Loan disbursement","loanId":"'$LOAN_ID'"}'

# Step 4 — Confirm reduced float balance
curl -s "$BASE_8092/api/v1/float/accounts/$FLOAT_ID" -H "$H_AUTH"

# Step 5 — Repay into float after loan repayment received
curl -s -X POST "$BASE_8092/api/v1/float/accounts/$FLOAT_ID/repay" \
  -H "$H_AUTH" -H "$H_JSON" \
  -d '{"amount":9500,"currency":"KES","reference":"FLOAT-REPAY-001","loanId":"'$LOAN_ID'"}'

# Step 6 — View transaction history
curl -s "$BASE_8092/api/v1/float/accounts/$FLOAT_ID/transactions" -H "$H_AUTH"
```

---

### Scenario 3: Collections Workflow

```bash
BASE_8093="http://localhost:8093"

# Step 1 — Simulate overdue loan (advance date or create a loan with past-due installment)
# In practice: loan-management DPD job fires and publishes loan.dpd.updated event
# Collections service auto-creates a case on receipt

# Step 2 — Verify case was auto-created
curl -s "$BASE_8093/api/v1/collections/cases?status=OPEN" -H "$H_AUTH"
CASE_ID=$(curl -s "$BASE_8093/api/v1/collections/cases/loan/$LOAN_ID" -H "$H_AUTH" \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])" 2>/dev/null)

# Step 3 — Assign to agent
curl -s -X PUT "$BASE_8093/api/v1/collections/cases/$CASE_ID" \
  -H "$H_AUTH" -H "$H_JSON" \
  -d '{"assignedAgent":"agent-001","priority":"MEDIUM"}'

# Step 4 — Log contact attempt
curl -s -X POST "$BASE_8093/api/v1/collections/cases/$CASE_ID/actions" \
  -H "$H_AUTH" -H "$H_JSON" \
  -d '{"actionType":"PHONE_CALL","outcome":"NO_ANSWER","notes":"3 ring no response","agentId":"agent-001"}'

# Step 5 — Record promise to pay
curl -s -X POST "$BASE_8093/api/v1/collections/cases/$CASE_ID/ptps" \
  -H "$H_AUTH" -H "$H_JSON" \
  -d '{"promisedAmount":9500,"promisedDate":"2026-04-10","notes":"WhatsApp confirmation"}'

# Step 6 — After payment confirmed, close case
curl -s -X POST "$BASE_8093/api/v1/collections/cases/$CASE_ID/close" -H "$H_AUTH"

# Step 7 — Summary
curl -s "$BASE_8093/api/v1/collections/summary" -H "$H_AUTH"
```

---

### Scenario 4: Compliance AML Alert Workflow

```bash
BASE_8094="http://localhost:8094"

# Step 1 — Register KYC for customer before loan approval
curl -s -X POST "$BASE_8094/api/v1/compliance/kyc" \
  -H "$H_AUTH" -H "$H_JSON" \
  -d '{"customerId":1,"documentType":"NATIONAL_ID","documentNumber":"12345678","documentExpiryDate":"2030-01-01","verificationMethod":"MANUAL"}'

curl -s -X POST "$BASE_8094/api/v1/compliance/kyc/1/pass" -H "$H_AUTH"

# Step 2 — AML monitoring flags a large repayment
curl -s -X POST "$BASE_8094/api/v1/compliance/alerts" \
  -H "$H_AUTH" -H "$H_JSON" \
  -d '{"customerId":1,"alertType":"LARGE_TRANSACTION","severity":"MEDIUM","description":"Repayment significantly exceeds scheduled amount","transactionReference":"REPAY-001","amount":9500000,"currency":"KES"}'

# Step 3 — Compliance officer reviews
ALERT_ID=$(curl -s "$BASE_8094/api/v1/compliance/alerts?status=OPEN" -H "$H_AUTH" \
  | python3 -c "import sys,json; data=json.load(sys.stdin); print(data['content'][0]['id']) if data.get('content') else print('NONE')")

# Step 4a — Resolve as false positive
curl -s -X POST "$BASE_8094/api/v1/compliance/alerts/$ALERT_ID/resolve" \
  -H "$H_AUTH" -H "$H_JSON" \
  -d '{"resolutionNotes":"Verified as legitimate early repayment. Customer provided source of funds.","resolvedBy":"co-001"}'

# Step 4b — Or: escalate by filing SAR
curl -s -X POST "$BASE_8094/api/v1/compliance/alerts/$ALERT_ID/sar" \
  -H "$H_AUTH" -H "$H_JSON" \
  -d '{"regulator":"FRC_KENYA","notes":"Suspicious source of funds","submittedBy":"co-001"}'

# Step 5 — Check compliance summary
curl -s "$BASE_8094/api/v1/compliance/summary" -H "$H_AUTH"
```

---

## 4. PRD Gap Analysis

This section cross-references the AthenaLMS PRD against the current implementation across all 11 services.

| PRD Section | Feature | Status | Notes |
|---|---|---|---|
| **3.1 Deposit & Wallet Products** | | | |
| 3.1.1 Current/Savings Accounts | Account creation, credit, debit | IMPLEMENTED | account-service; accountType field supports SAVINGS, CURRENT |
| 3.1.2 Interest Calculation | Interest accrual on savings | NOT IMPLEMENTED | No interest accrual job in account-service |
| 3.1.3 Account Statements | Mini-statement + paginated history | IMPLEMENTED | /mini-statement and /transactions endpoints |
| 3.1.4 Account Status Mgmt | Freeze, unfreeze, close | NOT IMPLEMENTED | No status transitions; only balance and tx endpoints |
| **3.2 Loan Products** | | | |
| 3.2.1 Personal Loan | Full lifecycle state machine | IMPLEMENTED | loan-origination-service covers DRAFT→DISBURSED |
| 3.2.2 Business Loan | Same lifecycle | PARTIALLY IMPLEMENTED | product-service supports BUSINESS_LOAN type; no distinct fields |
| 3.2.3 Asset Finance | Collateral-backed | PARTIALLY IMPLEMENTED | Collateral entity exists; no asset-specific product logic |
| 3.2.4 BNPL / Revolving Credit | Revolving credit line | NOT IMPLEMENTED | No revolving/line-of-credit product type |
| **3.3 Product Configuration** | | | |
| 3.3.1 Interest Methods | Reducing balance, flat | IMPLEMENTED | interestMethod enum in product-service |
| 3.3.2 Repayment Frequency | Monthly, weekly, biweekly | IMPLEMENTED | repaymentFrequency field |
| 3.3.3 Fee Structure | Processing fee, late fee, penalty | PARTIALLY IMPLEMENTED | processingFeeRate exists; penalty calculation in loan-management |
| 3.3.4 Product Versioning | Immutable versions with change log | IMPLEMENTED | ProductVersion entity + /versions endpoint |
| 3.3.5 Product Templates | Standard templates (personal, group) | IMPLEMENTED | ProductTemplate entity + seeded templates |
| **3.4 Loan Origination** | | | |
| 3.4.1 Application Submission | Create + submit | IMPLEMENTED | |
| 3.4.2 Document Management | Attach supporting docs | NOT IMPLEMENTED | No document endpoints; media-service integration not wired |
| 3.4.3 Credit Bureau Check | Integration with CRB | NOT IMPLEMENTED | ai-scoring proxies existing ML model; no CRB API |
| 3.4.4 AI Credit Scoring | ML score triggers on submission | IMPLEMENTED | ai-scoring-service auto-triggered via RabbitMQ |
| 3.4.5 Underwriter Workflow | Review, approve, reject with notes | IMPLEMENTED | review/start, review/approve, review/reject + notes |
| 3.4.6 Approval Conditions | Conditions text on approval | PARTIALLY IMPLEMENTED | conditions field in ApproveApplicationRequest; no enforcement workflow |
| 3.4.7 Multi-level Approval | Maker-checker for large amounts | NOT IMPLEMENTED | Single-approver only |
| **3.5 Loan Disbursement** | | | |
| 3.5.1 Disbursement Trigger | Manual trigger on approved app | IMPLEMENTED | /disburse endpoint |
| 3.5.2 Payment Channel | Bank transfer, M-Pesa, cash | IMPLEMENTED | payment-service with channel enum |
| 3.5.3 Disbursement Notification | Notify customer on disbursement | PARTIALLY IMPLEMENTED | Event published; notification-service consumes but not verified |
| 3.5.4 GL Posting | Double-entry on disbursement | IMPLEMENTED | accounting-service consumes loan.disbursed event |
| **3.6 Loan Management** | | | |
| 3.6.1 Repayment Schedule | Amortization schedule generation | IMPLEMENTED | Generated on loan activation |
| 3.6.2 Repayment Allocation | Waterfall: fees → interest → principal | IMPLEMENTED | LoanManagementService waterfall logic |
| 3.6.3 Early Repayment | Full prepayment, partial payment | PARTIALLY IMPLEMENTED | Repayment endpoint accepts any amount; no early settlement fee calc |
| 3.6.4 Late Fees & Penalties | Auto-apply penalties on overdue | PARTIALLY IMPLEMENTED | Penalty fields in schema; auto-accrual job not confirmed |
| 3.6.5 Loan Restructuring | Extend tenor, reduce installment | IMPLEMENTED | /restructure endpoint |
| 3.6.6 Loan Write-off | Write off non-recoverable loans | NOT IMPLEMENTED | No write-off endpoint or status |
| **3.7 DPD & PAR Buckets** | | | |
| 3.7.1 DPD Calculation | Days past due per loan | IMPLEMENTED | /dpd endpoint returns current DPD |
| 3.7.2 Loan Stage Classification | PAR1, PAR30, PAR60, PAR90, Loss | IMPLEMENTED | DpdResponse includes stage |
| 3.7.3 Portfolio PAR Reporting | PAR30/PAR60/PAR90 at portfolio level | IMPLEMENTED | reporting-service snapshot includes PAR buckets |
| 3.7.4 DPD History | Historical DPD log | IMPLEMENTED | loan_dpd_history table |
| **3.8 Collections** | | | |
| 3.8.1 Auto Case Creation | Case created when DPD threshold hit | IMPLEMENTED | collections-service consumes loan.dpd.updated |
| 3.8.2 Agent Assignment | Assign case to collection agent | IMPLEMENTED | assignedAgent field on case |
| 3.8.3 Action Logging | Log call, SMS, visit outcomes | IMPLEMENTED | CollectionAction entity with actionType |
| 3.8.4 Promise-to-Pay | PTP recording and tracking | IMPLEMENTED | PTP entity with status tracking |
| 3.8.5 Automated Follow-up | Auto-SMS/call scheduling | NOT IMPLEMENTED | No scheduler; manual actions only |
| 3.8.6 Legal Escalation | Legal action tracking | NOT IMPLEMENTED | No legal case entity or workflow |
| **3.9 Payments** | | | |
| 3.9.1 M-Pesa Integration | Real M-Pesa STK push / C2B | NOT IMPLEMENTED | Channel enum exists; no Safaricom Daraja API integration |
| 3.9.2 Bank Transfer | Bank ACH/RTGS | NOT IMPLEMENTED | Channel enum; no real bank API |
| 3.9.3 Cash Payment | Manual cash entry | PARTIALLY IMPLEMENTED | Payment can be created with CASH channel |
| 3.9.4 Payment Reversal | Reverse erroneous payments | IMPLEMENTED | /reverse endpoint |
| 3.9.5 Idempotency | Prevent duplicate payments | PARTIALLY IMPLEMENTED | Idempotency-Key in account-service; not in payment-service |
| 3.9.6 Payment Reconciliation | Reconcile external references | NOT IMPLEMENTED | No reconciliation workflow |
| **3.10 Accounting** | | | |
| 3.10.1 Chart of Accounts | COA management | IMPLEMENTED | accounting-service with full COA CRUD |
| 3.10.2 Double-Entry Ledger | Balanced journal entries | IMPLEMENTED | Validation in AccountingService |
| 3.10.3 Trial Balance | Monthly trial balance | IMPLEMENTED | /trial-balance endpoint |
| 3.10.4 GL Auto-Posting | Auto-post on loan/payment events | IMPLEMENTED | accounting-service consumes RabbitMQ events |
| 3.10.5 Financial Statements | P&L, Balance Sheet | NOT IMPLEMENTED | No income statement or balance sheet report |
| 3.10.6 Intercompany / Multi-entity | Multi-tenant GL segregation | IMPLEMENTED | All queries tenant-scoped |
| **3.11 Float Management** | | | |
| 3.11.1 Float Account | Liquidity pool account | IMPLEMENTED | float-service with FloatAccount entity |
| 3.11.2 Draw & Repay | Float draw on disbursement | IMPLEMENTED | /draw and /repay endpoints |
| 3.11.3 Float Threshold Alerts | Alert when float below threshold | NOT IMPLEMENTED | No threshold configuration or alert mechanism |
| 3.11.4 Float Summary | Portfolio-level float view | IMPLEMENTED | /summary endpoint |
| **3.12 Compliance** | | | |
| 3.12.1 KYC Management | KYC record per customer | IMPLEMENTED | /kyc endpoints with pass/fail workflow |
| 3.12.2 AML Monitoring | AML alert creation and management | IMPLEMENTED | Alert + SAR filing endpoints |
| 3.12.3 SAR Filing | File SAR with regulator details | IMPLEMENTED | /sar endpoints |
| 3.12.4 Regulatory Reporting | Auto-submission to CBK/FRC | NOT IMPLEMENTED | SAR stored but no external submission integration |
| 3.12.5 CDD / EDD | Enhanced due diligence workflow | NOT IMPLEMENTED | No CDD entity or workflow beyond KYC |
| 3.12.6 Sanctions Screening | OFAC/UN sanctions check | NOT IMPLEMENTED | No sanctions list integration |
| **3.13 Reporting & Analytics** | | | |
| 3.13.1 Portfolio Snapshot | Daily portfolio snapshot | IMPLEMENTED | /snapshots and /generate endpoints |
| 3.13.2 Event Stream | Full event audit trail | IMPLEMENTED | All events stored in reporting DB |
| 3.13.3 Metrics API | Event metrics by date range | IMPLEMENTED | /metrics endpoint |
| 3.13.4 Executive Dashboard | Pre-built dashboard | NOT IMPLEMENTED | API only; no UI or BI tool integration |
| 3.13.5 CBK Prudential Reports | Regulatory report templates | NOT IMPLEMENTED | No regulatory report format generation |
| 3.13.6 Custom Report Builder | Configurable report templates | NOT IMPLEMENTED | Only fixed endpoints |
| **3.14 Multi-Tenancy** | | | |
| 3.14.1 Tenant Isolation | Row-level tenant filtering | IMPLEMENTED | All 11 services enforce tenant_id |
| 3.14.2 Tenant Provisioning | Create new tenant | NOT IMPLEMENTED | No tenant management API |
| 3.14.3 Tenant Configuration | Per-tenant product/fee config | PARTIALLY IMPLEMENTED | Product config is per-tenant; no tenant settings API |
| **3.15 API Gateway** | | | |
| 3.15.1 Kong Gateway | Route all services via Kong | PARTIALLY IMPLEMENTED | Kong in docker-compose; routing not fully configured |
| 3.15.2 Rate Limiting | Per-client rate limits | NOT IMPLEMENTED | Not configured in Kong |
| 3.15.3 API Versioning | /api/v1/ prefix | IMPLEMENTED | All services use /api/v1/ |
| **3.16 Service Discovery** | | | |
| 3.16.1 Eureka | Service registration | IMPLEMENTED | All 11 services registered (confirmed in health check) |
| 3.16.2 Load Balancing | Client-side load balancing | PARTIALLY IMPLEMENTED | Eureka registered; no multiple instances configured |
| **3.17 Security** | | | |
| 3.17.1 JWT Authentication | HS256 JWT for all endpoints | IMPLEMENTED | Shared LmsJwtAuthenticationFilter in common lib |
| 3.17.2 RBAC | Role-based endpoint protection | PARTIALLY IMPLEMENTED | product-service has @PreAuthorize; others lack role checks |
| 3.17.3 HTTPS / TLS | TLS termination | NOT IMPLEMENTED | HTTP only in local dev; Kong TLS not configured |
| 3.17.4 Audit Trail | All mutations logged | PARTIALLY IMPLEMENTED | Status history in origination; no global audit log |
| **3.18 Notifications** | | | |
| 3.18.1 SMS Notifications | Customer SMS on key events | PARTIALLY IMPLEMENTED | Events published to notification queue; notification-service integration not tested |
| 3.18.2 Email Notifications | Email on approval/rejection | PARTIALLY IMPLEMENTED | Same as above |
| 3.18.3 Push Notifications | Mobile push | NOT IMPLEMENTED | |
| **3.19 Customer Management** | | | |
| 3.19.1 Customer Profile | Full customer record | IMPLEMENTED | customer-service (port 8082, existing) |
| 3.19.2 Customer Search | Name/phone/ID search | IMPLEMENTED | customer-service + account-service /search |
| **3.20 AI/ML Scoring** | | | |
| 3.20.1 Automated Scoring | Score on application submit | IMPLEMENTED | ai-scoring-service + python-service (8001) |
| 3.20.2 Score Explanation | Risk factors breakdown | PARTIALLY IMPLEMENTED | ScoringResult stored; factor explanation depends on Python model |
| 3.20.3 Model Management | A/B test models, versioning | NOT IMPLEMENTED | Single model; no model versioning |
| **3.21 Infrastructure** | | | |
| 3.21.1 Containerization | Docker + docker-compose | IMPLEMENTED | docker-compose.lms.yml |
| 3.21.2 Database Migrations | Flyway versioned migrations | IMPLEMENTED | All 11 services use Flyway |
| 3.21.3 Health Endpoints | /actuator/health | IMPLEMENTED | All services expose health |
| 3.21.4 Metrics | /actuator/prometheus | IMPLEMENTED | All services expose Prometheus metrics |
| 3.21.5 Centralized Logging | ELK / Loki log aggregation | NOT IMPLEMENTED | No log aggregation stack configured |
| 3.21.6 Distributed Tracing | Zipkin / Jaeger | NOT IMPLEMENTED | No tracing configured |

**Status Legend**:
- **IMPLEMENTED**: Feature exists, endpoints work, business logic confirmed in code review
- **PARTIALLY IMPLEMENTED**: Core entity/endpoint exists but missing sub-features
- **NOT IMPLEMENTED**: Feature not present in any service
- **OUT OF SCOPE**: Not planned for current development phase

---

## 5. Priority Implementation Gaps

Ranked by business impact for an MFI operating in a regulated market.

| Priority | Gap | Impact | Effort | Service Affected |
|---|---|---|---|---|
| 1 | **M-Pesa / Mobile Money Integration** | Critical — primary payment channel for East African MFIs. Without it, disbursements and repayments cannot be automated. | High | payment-service |
| 2 | **RBAC Enforcement Across All Services** | High — loan-management, payment, accounting, float, collections all lack `@PreAuthorize` guards. Any authenticated user can perform destructive operations. | Low | All services |
| 3 | **Document Management on Loan Applications** | High — regulatory requirement to attach ID, payslips, and bank statements to loan files. media-service exists but not wired. | Medium | loan-origination-service |
| 4 | **Account Status Management (Freeze/Close)** | High — required for AML compliance. Compliance team must be able to freeze accounts instantly. | Low | account-service |
| 5 | **Multi-level Approval (Maker-Checker)** | High — standard control requirement for loans above a threshold. Single-approver is a compliance risk. | Medium | loan-origination-service |
| 6 | **Loan Write-off Workflow** | High — PAR90+ loans need write-off for accurate P&L and regulatory reporting. | Low | loan-management-service |
| 7 | **Float Threshold Alerts** | Medium — treasury team needs early warning before float is exhausted. | Low | float-service |
| 8 | **Financial Statements (P&L, Balance Sheet)** | Medium — required for board reporting and CBK submission. Trial balance data exists; need aggregation layer. | Medium | accounting-service |
| 9 | **Idempotency in payment-service** | Medium — prevents duplicate disbursements if client retries. Critical for mobile money callbacks. | Low | payment-service |
| 10 | **Regulatory Reporting (CBK Prudential Reports)** | Medium — statutory obligation. Data exists in reporting-service; needs formatted output (PDF/Excel). | High | reporting-service |

---

## 6. Test Automation Recommendations

### 6.1 Postman Collection

Create a Postman collection organized as follows:

```
AthenaLMS API
├── 00 - Auth
│   └── POST Login → save token to collection variable
├── 01 - account-service
│   ├── Create Account
│   ├── Credit Account
│   └── ...
├── 02 - product-service
├── 03 - loan-origination-service
│   └── Full Loan Lifecycle (chained requests using Postman scripts)
...
└── E2E - Complete Loan Journey (folder with pre/post scripts)
```

Export as `postman_collection_v2.1.json` and commit to `docs/postman/`.

### 6.2 Spring Boot Integration Tests

Add integration tests using `@SpringBootTest` + `TestContainers`:

```java
// Example structure per service
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class LoanOriginationIntegrationTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");
    
    // Test full state machine transitions
    @Test void testFullLoanLifecycle() { ... }
    @Test void testIllegalStateTransitionIsRejected() { ... }
    @Test void testConcurrentSubmissionIdempotency() { ... }
}
```

Priority order for test implementation: loan-origination → loan-management → payment → accounting.

### 6.3 Contract Tests (Pact)

Use Pact for consumer-driven contract tests between services that communicate via RabbitMQ events:
- **Producer**: loan-origination-service (publishes `loan.disbursed`)
- **Consumer**: loan-management-service (consumes `loan.disbursed`)

This ensures the event schema never breaks silently.

### 6.4 Load Testing

Use Gatling or k6 for load testing critical paths:

```javascript
// k6 loan application scenario
export default function () {
  // 1. POST /api/v1/loan-applications
  // 2. POST /{id}/submit
  // Target: 100 concurrent applications, p95 < 500ms
}
```

### 6.5 CI Pipeline Recommendations

```yaml
# Suggested GitHub Actions stages
1. Unit Tests (per service)        → mvn test
2. Integration Tests (TestContainers) → mvn verify -Pintegration
3. Contract Tests (Pact)           → pact verify
4. Smoke Test Suite (curl scripts) → docs/scripts/smoke-test.sh
5. Load Test (k6, nightly)        → k6 run docs/load-tests/loan-journey.js
```

---

## Appendix A: Service Health Check URLs

```bash
for port in 8086 8087 8088 8089 8090 8091 8092 8093 8094 8095 8096; do
  STATUS=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:$port/actuator/health)
  echo "Port $port: HTTP $STATUS"
done
```

## Appendix B: RabbitMQ Event Topology

| Event Key | Publisher | Consumers |
|---|---|---|
| `loan.application.submitted` | loan-origination | ai-scoring, notification |
| `loan.application.approved` | loan-origination | accounting, notification |
| `loan.application.rejected` | loan-origination | notification |
| `loan.disbursed` | loan-origination | loan-management, accounting, float, notification |
| `loan.stage.changed` | loan-management | collections, compliance |
| `loan.dpd.updated` | loan-management | collections, accounting |
| `loan.closed` | loan-management | accounting, reporting |
| `payment.completed` | payment | loan-management, accounting |
| `payment.reversed` | payment | loan-management, accounting |
| `float.draw` | float | accounting |
| `aml.alert.created` | compliance | notification |
| `customer.kyc.passed` | compliance | loan-origination |

## Appendix C: Common Request Headers

| Header | Required | Example | Notes |
|---|---|---|---|
| `Authorization` | Yes (all except health/swagger) | `Bearer eyJhbGc...` | JWT from user-service |
| `Content-Type` | Yes (POST/PUT) | `application/json` | |
| `X-Tenant-Id` | Optional | `tenant-abc` | Overrides JWT tenant claim |
| `Idempotency-Key` | Recommended | `uuid-v4` | account-service credit/debit |

---

*Generated: 2026-02-25 | Stack: Java 17, Spring Boot 3.2.5, PostgreSQL 16, RabbitMQ 3.13.7*
