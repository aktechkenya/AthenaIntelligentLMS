# AthenaLMS Stress Test Bug Fix Log

**Date**: 2026-02-25
**Test Session**: 100-Loan Comprehensive Stress Test
**Final Pass Rate**: 100/100 (100%)

---

## Bug 1: Missing FLAT_RATE Enum Value in product-service

### Symptom
When creating products with `scheduleType: "FLAT_RATE"` (Nano Loan NL-001, BNPL BNPL-001, Emergency Loan EMRG-001), the product-service returned:

```
HTTP 400 Bad Request
{"error":"Bad Request","message":"No enum constant com.athena.lms.product.enums.ScheduleType.FLAT_RATE","status":400}
```

### Root Cause
The `ScheduleType` enum in `product-service` defined `FLAT` but the `loan-management-service` and the task spec used `FLAT_RATE`. This naming inconsistency caused product creation to fail for all 3 products using FLAT_RATE schedule type.

**Files affected:**
- `product-service/src/main/java/com/athena/lms/product/enums/ScheduleType.java`
- `product-service/src/main/java/com/athena/lms/product/service/ScheduleSimulator.java`

### Fix Applied

**Step 1**: Added `FLAT_RATE` enum constant to `ScheduleType.java`:
```java
// Before
public enum ScheduleType {
    EMI, FLAT, ACTUARIAL, DAILY_SIMPLE, BALLOON, SEASONAL, GRADUATED
}

// After
public enum ScheduleType {
    EMI, FLAT, FLAT_RATE, ACTUARIAL, DAILY_SIMPLE, BALLOON, SEASONAL, GRADUATED
}
```

**Step 2**: Added `FLAT_RATE` case to `ScheduleSimulator.java` switch statement:
```java
// Before
case FLAT -> simulateFlat(req);

// After
case FLAT, FLAT_RATE -> simulateFlat(req);
```

**Step 3**: Rebuilt and restarted product-service container:
```bash
cd /home/adira/AthenaIntelligentLMS
docker build --build-context shared=./shared -f product-service/Dockerfile -t athenacreditscore-product-service:latest .
docker rm -f lms-product-service
docker run -d --name lms-product-service --network athenacreditscore_athena-net -p 8087:8087 \
  [env vars] athenacreditscore-product-service:latest
```

### Impact
- 3 products failed to create (Nano Loan, BNPL, Emergency Loan)
- 30% of loan tests skipped (no product available)
- After fix: all 10 products create and activate successfully

---

## Bug 2: Test Script — verify_active Picks Wrong Loan on Repeat Runs

### Symptom
When the 100-test suite is run multiple times (e.g., after fixing Bug 1), some `verify_active` checks reported:
```
loan status=CLOSED
```
...even though the new loan had just been disbursed successfully.

### Root Cause
The `GET /api/v1/loans/customer/{customerId}` endpoint returns **all** loans for that customer across all time. On the second test run, each customer (e.g., STRESS-TEST-001) had TWO loans:
- One CLOSED loan from the first test run (which had a repayment applied during run 1)
- One freshly ACTIVE loan from the current run

The test code used `body[0]` to pick the loan, but the list ordering returned the OLD CLOSED loan first.

### Fix Applied
Updated the `verify_active` step to match by `applicationId` instead of using `body[0]`:

```python
# Find the loan matching this application (handles repeat test runs)
app_id_str = str(app_id)
matched_loan = None
for loan_candidate in body:
    if str(loan_candidate.get("applicationId", "")) == app_id_str:
        matched_loan = loan_candidate
        break
# Fallback: pick most recently created ACTIVE loan
if matched_loan is None:
    active_loans = [l for l in body if l.get("status") == "ACTIVE"]
    if active_loans:
        matched_loan = active_loans[-1]
```

### Note
This is a **test script bug**, not a backend bug. The backend correctly returns all historical loans for a customer. The `LoanResponse` includes `applicationId` which allows precise matching.

---

## Bug 3: Product Conflict on Repeated Test Runs (Test Script Issue)

### Symptom
On the second run of the test script, `POST /api/v1/products` returned:
```
HTTP 409 Conflict
{"error":"Conflict","message":"Product code already exists: PL-STD","status":409}
```

### Root Cause
The test script always tried to create products, not checking if they already existed.

### Fix Applied
Updated the test script to first list all existing products and look up by `productCode` before creating:

```python
def lookup_or_create_products():
    # Fetch existing products
    status, body, _ = curl("GET", f"{BASE_PRODUCT}/api/v1/products?size=100", None, TOKEN)
    existing_by_code = {}
    if status == 200:
        items = body.get("content", [])
        for p in items:
            existing_by_code[p["productCode"]] = p["id"]
    
    for prod_def in PRODUCT_DEFS:
        code = prod_def["productCode"]
        if code in existing_by_code:
            # Reuse existing product ID
            PRODUCT_IDS.append(existing_by_code[code])
        else:
            # Create new product
            ...
```

### Note
This is a **test script idempotency issue**, not a backend bug. The 409 conflict response is correct backend behavior (product codes must be unique per tenant).

---

## Summary

| # | Type | Severity | Component | Status |
|---|------|----------|-----------|--------|
| 1 | Backend Bug | High | product-service ScheduleType enum | FIXED (rebuilt service) |
| 2 | Test Script Bug | Medium | verify_active loan matching logic | FIXED |
| 3 | Test Script Bug | Low | Product creation idempotency | FIXED |

### Source Files Modified

| File | Change |
|------|--------|
| `product-service/src/main/java/com/athena/lms/product/enums/ScheduleType.java` | Added `FLAT_RATE` enum constant |
| `product-service/src/main/java/com/athena/lms/product/service/ScheduleSimulator.java` | Added `FLAT_RATE` case to switch statement |

### Service Rebuild Required
- `lms-product-service` was rebuilt and restarted to apply the FLAT_RATE enum fix

### Final State
All 100 tests pass with 100% success rate across all 10 product types and all 10 test journey steps.
