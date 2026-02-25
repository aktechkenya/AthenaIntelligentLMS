# AthenaLMS 100-Loan Stress Test Report

**Date**: 2026-02-25 05:34:57
**Duration**: ~300s (estimated, including 1.5s event wait per test)
**Environment**: Docker (localhost), Spring Boot 3.2.5, PostgreSQL 16, RabbitMQ

---

## Executive Summary

| Metric | Value |
|--------|-------|
| Total Tests | 100 |
| Passed | 100 |
| Failed | 0 |
| **Pass Rate** | **100%** |
| Products Tested | 10 |
| Schedule Accuracy | 100/100 (100%) |

---

## Products Configuration

| # | Code | Name | Type | Rate | Schedule | Min | Max | Tenor |
|---|------|------|------|------|----------|-----|-----|-------|
| 1 | NL-001 | Nano Loan | NANO_LOAN | 36.0% | FLAT_RATE | 1m | 3m | ID: ...303aacb5 |
| 2 | PL-STD | Personal Loan Standard | PERSONAL_LOAN | 18.0% | EMI | 3m | 24m | ID: ...7c531699 |
| 3 | PL-PREM | Personal Loan Premium | PERSONAL_LOAN | 14.5% | EMI | 6m | 60m | ID: ...6bea9b52 |
| 4 | SME-001 | SME Loan | SME_LOAN | 22.0% | EMI | 6m | 36m | ID: ...a0729186 |
| 5 | BNPL-001 | BNPL | BNPL | 0.0% | FLAT_RATE | 1m | 3m | ID: ...2c3397f6 |
| 6 | AGRI-001 | Agricultural Loan | PERSONAL_LOAN | 12.0% | EMI | 6m | 18m | ID: ...32d201c8 |
| 7 | STAFF-001 | Staff Loan | PERSONAL_LOAN | 8.0% | EMI | 12m | 60m | ID: ...1d642221 |
| 8 | EMRG-001 | Emergency Loan | NANO_LOAN | 24.0% | FLAT_RATE | 1m | 2m | ID: ...11f10c96 |
| 9 | AF-001 | Asset Finance | SME_LOAN | 17.5% | EMI | 12m | 84m | ID: ...be08b8cd |
| 10 | GRP-001 | Group Loan | PERSONAL_LOAN | 20.0% | EMI | 3m | 12m | ID: ...7b9586eb |

---

## Pass Rate by Product

| Product | Passed | Failed | Rate |
|---------|--------|--------|------|
| Nano Loan | 10 | 0 | 100% |
| Personal Loan Standard | 10 | 0 | 100% |
| Personal Loan Premium | 10 | 0 | 100% |
| SME Loan | 10 | 0 | 100% |
| BNPL | 10 | 0 | 100% |
| Agricultural Loan | 10 | 0 | 100% |
| Staff Loan | 10 | 0 | 100% |
| Emergency Loan | 10 | 0 | 100% |
| Asset Finance | 10 | 0 | 100% |
| Group Loan | 10 | 0 | 100% |

---

## Pass Rate by Step

| Step | Passed | Failed | Rate |
|------|--------|--------|------|
| a. Create Application | 100 | 0 | 100% |
| b. Submit Application | 100 | 0 | 100% |
| c. Start Review | 100 | 0 | 100% |
| d. Approve Application | 100 | 0 | 100% |
| e. Disburse Loan | 100 | 0 | 100% |
| f. Verify Loan ACTIVE | 100 | 0 | 100% |
| g. Get Schedule | 100 | 0 | 100% |
| h. Apply Repayment | 100 | 0 | 100% |
| i. Verify Repayment | 100 | 0 | 100% |
| j. Verify Outstanding | 100 | 0 | 100% |

---

## Schedule Installment Accuracy

| Metric | Value |
|--------|-------|
| Tests with schedule data | 100 |
| Correct installment count | 100 |
| Accuracy | 100% |

Note: Schedule count = tenor months for MONTHLY repayment frequency.
The `get_schedule` step counts as WARN (not FAIL) when installment count doesn't match.

### Schedule counts by test (sample):
All schedules had correct installment counts.

---

## API Response Timing (milliseconds)

| Step | Avg ms | Min ms | Max ms |
|------|--------|--------|--------|
| a. Create Application | 15 | 7 | 60 |
| b. Submit Application | 15 | 8 | 39 |
| c. Start Review | 12 | 7 | 29 |
| d. Approve Application | 13 | 8 | 37 |
| e. Disburse Loan | 15 | 8 | 37 |
| f. Verify Loan ACTIVE | 18 | 7 | 37 |
| g. Get Schedule | 15 | 7 | 34 |
| h. Apply Repayment | 18 | 10 | 35 |
| i. Verify Repayment | 12 | 6 | 26 |
| j. Verify Outstanding | 11 | 6 | 31 |

---

## Failure Analysis

No test failures in final run. All 100 tests passed.

---

## Test Journey Details (Full Lifecycle)

Each test ran the following steps:
1. **Create Application** — POST /api/v1/loan-applications
2. **Submit** — POST /api/v1/loan-applications/{id}/submit
3. **Start Review** — POST /api/v1/loan-applications/{id}/review/start
4. **Approve** — POST /api/v1/loan-applications/{id}/review/approve (with credit score + risk grade)
5. **Disburse** — POST /api/v1/loan-applications/{id}/disburse
6. **Wait 1.5s** for RabbitMQ event propagation (loan.disbursed -> loan-management-service)
7. **Verify ACTIVE** — GET /api/v1/loans/customer/{customerId} (match by applicationId)
8. **Get Schedule** — GET /api/v1/loans/{loanId}/schedule (verify installment count)
9. **Apply Repayment** — POST /api/v1/loans/{loanId}/repayments (first installment totalDue)
10. **Verify Repayment** — GET /api/v1/loans/{loanId}/repayments
11. **Verify Outstanding** — GET /api/v1/loans/{loanId} (outstanding < disbursed)

---

## Notes

- All loan amounts computed as: `min(product.minAmount + test_num * 1000, product.maxAmount)`
- Tenor computed as: `product.minTenor + ((test_num - 1) % (product.maxTenor - product.minTenor + 1))`
- Credit scores: `600 + (test_num % 300)` — range 600-899
- Risk grades: A (>=750), B (>=650), C (>=550), D (<550)
- Payment method: MOBILE_MONEY, reference: RPMT-STRESS-{NNN}
- Services: account-service:8086, product-service:8087, loan-origination-service:8088, loan-management-service:8089
