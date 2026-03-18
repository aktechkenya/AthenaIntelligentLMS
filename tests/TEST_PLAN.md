# Athena LMS — Comprehensive Test Plan

## Overview

This document covers all automated testing for the Athena LMS system:
- **234 API tests** (Python pytest) covering 16 Go microservices
- **Playwright UI tests** covering the React portal
- **Test credentials, ports, and infrastructure** for reproducibility

---

## 1. Infrastructure

### Services (K3s namespace: `lms`)

| Service | Port | DB | Test File |
|---------|------|----|-----------|
| account-service | 18086 | athena_accounts | test_02-04, test_18 |
| product-service | 18087 | athena_products | test_05 |
| loan-origination-service | 18088 | athena_loans | test_06 |
| loan-management-service | 18089 | athena_loans | test_07 |
| payment-service | 18090 | athena_payments | test_08 |
| accounting-service | 18091 | athena_accounting | test_09 |
| float-service | 18092 | athena_float | test_10 |
| collections-service | 18093 | athena_collections | test_11 |
| compliance-service | 18094 | athena_compliance | test_12 |
| reporting-service | 18095 | athena_reporting | (test_01 health) |
| ai-scoring-service | 18096 | athena_scoring | test_13 |
| overdraft-service | 18097 | athena_overdraft | test_14 |
| media-service | 18098 | athena_media | test_15 |
| notification-service | 18099 | athena_notifications | test_16 |
| fraud-detection-service | 18100 | athena_fraud | test_23 |
| lms-api-gateway | 18105 | — | test_17 |
| lms-portal-ui | 3001 | — | Playwright |

### Shared Infrastructure (K3s namespace: `infra`)
- PostgreSQL: `postgres.infra.svc.cluster.local:5432` (user: admin, pass: password)
- RabbitMQ: `rabbitmq.infra.svc.cluster.local:5672` (user: guest, pass: guest)

### Credentials

| User | Password | Roles | Use |
|------|----------|-------|-----|
| admin | admin123 | ADMIN,USER | Full system access |
| manager | manager123 | MANAGER,USER | Branch operations |
| officer | officer123 | OFFICER,USER | Loan processing |
| teller@athena.com | teller123 | TELLER,USER | Teller operations |

**Service Key**: `1473bdcbf4d90d90833bb90cf042faa16d3f5729c258624de9118eb4519ffe17`

---

## 2. Running Tests

### Prerequisites
```bash
# Port-forward all services (run in background)
nohup bash /tmp/lms-portforward.sh &

# Or manually:
kubectl port-forward -n lms svc/account-service 18086:8086 --address 0.0.0.0 &
# ... repeat for each service
```

### API Tests (pytest)
```bash
cd tests/
pip install -r requirements.txt
LMS_BASE="http://localhost" python3 -m pytest -v --tb=short

# Run specific test file:
python3 -m pytest test_03_customers.py -v

# Run with HTML report:
python3 -m pytest --html=reports/report.html --self-contained-html

# Run only passing tests (skip Spring-specific):
python3 -m pytest -k "not actuator_info and not swagger and not api_docs"
```

### UI Tests (Playwright)
```bash
cd tests/ui/
npm install
npx playwright install chromium
npx playwright test

# Run with UI mode:
npx playwright test --ui

# Run specific test:
npx playwright test tests/auth.spec.ts
```

---

## 3. API Test Catalog

### test_01_health.py — Service Health (30 tests)
| Test | Method | Path | Expected |
|------|--------|------|----------|
| test_actuator_health[{service}] | GET | /actuator/health | 200, `{"status":"UP"}` |
| test_actuator_info[{service}] | GET | /actuator/info | ~~200~~ (Spring-only, Go returns 404) |

### test_02_auth.py — Authentication (12 tests)
| Test | What it validates |
|------|-------------------|
| test_admin_login | POST /api/auth/login with admin/admin123 → 200 + JWT |
| test_manager_login | POST /api/auth/login with manager/manager123 → 200 |
| test_officer_login | POST /api/auth/login with officer/officer123 → 200 |
| test_teller_login | POST /api/auth/login with teller@athena.com/teller123 → 200 |
| test_invalid_credentials | Wrong password → 401 |
| test_missing_password | Empty body → 400 |
| test_me_endpoint | GET /api/auth/me with Bearer token → 200 + username/roles |
| test_no_auth_rejected | GET /api/v1/accounts without token → 401 |
| test_bad_token_rejected | GET with invalid JWT → 401 |
| test_service_key_accepted | X-Service-Key header → 200 |
| test_wrong_service_key | Wrong key → 401 |
| test_no_key_no_jwt | Neither auth method → 401 |

### test_03_customers.py — Customer CRUD (8 tests)
| Test | What it validates |
|------|-------------------|
| test_create_customer | POST /api/v1/customers → 201 + customer data |
| test_list_customers | GET /api/v1/customers → 200 + paginated |
| test_get_customer | GET /api/v1/customers/{id} → 200 |
| test_update_customer | PUT /api/v1/customers/{id} → 200 + updated fields |
| test_search_customers | GET /api/v1/customers/search?q=... → results |
| test_get_by_customer_id | GET /api/v1/customers/by-customer-id/{cid} → 200 |
| test_update_status | PATCH /api/v1/customers/{id}/status → 200 |
| test_customer_not_found | GET /api/v1/customers/{bad-id} → 404 |

### test_04_accounts.py — Account Operations (13 tests)
| Test | What it validates |
|------|-------------------|
| test_create_account | POST /api/v1/accounts → 201 |
| test_list_accounts | GET /api/v1/accounts → 200 + paginated |
| test_get_account | GET /api/v1/accounts/{id} → 200 |
| test_get_balance | GET /api/v1/accounts/{id}/balance → 200 + amount |
| test_credit | POST /api/v1/accounts/{id}/credit → 200 + balance increased |
| test_debit | POST /api/v1/accounts/{id}/debit → 200 + balance decreased |
| test_transactions | GET /api/v1/accounts/{id}/transactions → 200 + history |
| test_mini_statement | GET /api/v1/accounts/{id}/mini-statement → last N txns |
| test_statement | GET /api/v1/accounts/{id}/statement?startDate&endDate → 200 |
| test_search | GET /api/v1/accounts/search?q=... → results |
| test_by_customer | GET /api/v1/accounts/customer/{cid} → accounts list |
| test_update_status | PUT /api/v1/accounts/{id}/status → 200 |
| test_not_found | GET /api/v1/accounts/{bad-id} → 404 |

### test_05_products.py — Product Catalog (8 tests)
| Test | What it validates |
|------|-------------------|
| test_create_product | POST /api/v1/products → 201 |
| test_list_products | GET /api/v1/products → 200 + paginated |
| test_get_product | GET /api/v1/products/{id} → 200 |
| test_simulate_schedule | POST /api/v1/products/{id}/simulate → amortization |
| test_product_versions | GET /api/v1/products/{id}/versions → history |
| test_list_templates | GET /api/v1/product-templates → templates |
| test_create_charge | POST /api/v1/charges → 201 |
| test_list_charges | GET /api/v1/charges → 200 |

### test_06_loan_origination.py — Loan Application Workflow (11 tests)
| Test | What it validates |
|------|-------------------|
| test_create_application | POST /api/v1/loan-applications → 201 |
| test_list_applications | GET /api/v1/loan-applications → 200 |
| test_get_application | GET /api/v1/loan-applications/{id} → 200 |
| test_by_customer | GET /api/v1/loan-applications/customer/{cid} → list |
| test_submit_application | POST /{id}/submit → status SUBMITTED |
| test_start_review | POST /{id}/review/start → UNDER_REVIEW |
| test_approve_application | POST /{id}/review/approve → APPROVED |
| test_reject_application | POST /{id}/review/reject → REJECTED |
| test_cancel_application | POST /{id}/cancel → CANCELLED |
| test_add_note | POST /{id}/notes → note added |
| test_add_collateral | POST /{id}/collaterals → collateral added |

### test_07_loan_management.py — Active Loans (7 tests)
| Test | What it validates |
|------|-------------------|
| test_list_loans | GET /api/v1/loans → 200 |
| test_get_loan | GET /api/v1/loans/{id} → 200 + schedule |
| test_get_schedule | GET /api/v1/loans/{id}/schedule → installments |
| test_repayment | POST /api/v1/repayments → waterfall allocation |
| test_dpd_history | GET /api/v1/loans/{id}/dpd → DPD tracking |
| test_loan_not_found | GET /api/v1/loans/{bad-id} → 404 |
| test_restructure | POST /api/v1/loans/{id}/restructure → modified schedule |

### test_08_payments.py — Payment Processing (5 tests)
| Test | What it validates |
|------|-------------------|
| test_create_payment | POST /api/v1/payments → 201 |
| test_list_payments | GET /api/v1/payments → 200 + paginated |
| test_get_payment | GET /api/v1/payments/{id} → 200 |
| test_complete_payment | POST /api/v1/payments/{id}/complete → COMPLETED |
| test_by_customer | GET /api/v1/payments/customer/{cid} → list |

### test_09_accounting.py — Double-Entry Bookkeeping (9 tests)
| Test | What it validates |
|------|-------------------|
| test_list_chart_of_accounts | GET /api/v1/accounting/accounts → system accounts |
| test_create_account | POST /api/v1/accounting/accounts → 201 |
| test_get_by_code | GET /api/v1/accounting/accounts/code/{code} → 200 |
| test_post_journal_entry | POST /api/v1/accounting/journal-entries → balanced |
| test_entry_must_balance | POST with unbalanced → 400/422 |
| test_list_entries | GET /api/v1/accounting/journal-entries → paginated |
| test_get_balance | GET /accounts/{id}/balance?year&month → 200 |
| test_trial_balance | GET /api/v1/accounting/trial-balance → balanced |
| test_ledger | GET /accounts/{id}/ledger → line items |

### test_10_float.py — Float Management (4 tests)
| Test | What it validates |
|------|-------------------|
| test_list_accounts | GET /api/v1/float/accounts → 200 |
| test_create_account | POST /api/v1/float/accounts → 201 |
| test_draw | POST /api/v1/float/accounts/{id}/draw → balance updated |
| test_summary | GET /api/v1/float/summary → totals |

### test_11_collections.py — Collections (5 tests)
| Test | What it validates |
|------|-------------------|
| test_summary | GET /api/v1/collections/summary → case counts |
| test_list_cases | GET /api/v1/collections/cases → 200 |
| test_get_case | GET /api/v1/collections/cases/{id} → 200 |
| test_add_action | POST /cases/{id}/actions → action created |
| test_add_ptp | POST /cases/{id}/ptps → promise created |

### test_12_compliance.py — AML/KYC (7 tests)
| Test | What it validates |
|------|-------------------|
| test_summary | GET /api/v1/compliance/summary → counts |
| test_create_alert | POST /api/v1/compliance/alerts → 201 |
| test_list_alerts | GET /api/v1/compliance/alerts → 200 |
| test_resolve_alert | POST /alerts/{id}/resolve → resolved |
| test_file_sar | POST /alerts/{id}/sar → SAR filed |
| test_kyc_create | POST /api/v1/compliance/kyc → 201 |
| test_kyc_pass | POST /kyc/{cid}/pass → PASSED |

### test_13_scoring.py — AI Scoring (4 tests)
| Test | What it validates |
|------|-------------------|
| test_manual_score | POST /api/v1/scoring/requests → 201 |
| test_list_requests | GET /api/v1/scoring/requests → 200 |
| test_get_by_application | GET /scoring/applications/{id}/result → 200 |
| test_latest_by_customer | GET /scoring/customers/{cid}/latest → 200 |

### test_14_overdraft.py — Overdraft/Wallet (9 tests)
| Test | What it validates |
|------|-------------------|
| test_create_wallet | POST /api/v1/wallet → 201 |
| test_get_wallet | GET /api/v1/wallet/{id} → 200 |
| test_deposit | POST /api/v1/wallet/{id}/deposit → balance up |
| test_withdraw | POST /api/v1/wallet/{id}/withdraw → balance down |
| test_apply_overdraft | POST /api/v1/overdraft → facility created |
| (5 skipped) | Depend on wallet existing |

### test_15_media.py — File Upload (5 tests)
| Test | What it validates |
|------|-------------------|
| test_upload | POST /api/v1/media/upload multipart → 201 |
| test_download | GET /api/v1/media/download/{id} → file content |
| test_metadata | GET /api/v1/media/metadata/{id} → 200 |
| test_search | GET /api/v1/media/customer/{cid} → list |
| test_stats | GET /api/v1/media/stats → counts |

### test_16_notifications.py — Notifications (4 tests)
| Test | What it validates |
|------|-------------------|
| test_list_logs | GET /api/v1/notifications/logs → 200 |
| test_get_config | GET /api/v1/notifications/config/EMAIL → 200 |
| test_update_config | POST /api/v1/notifications/config → 200 |
| test_send | POST /api/v1/notifications/send → sent/skipped |

### test_17_gateway.py — API Gateway (22 tests)
| Test | What it validates |
|------|-------------------|
| test_gateway_health | GET /actuator/health → 200 + component status |
| test_gateway_routes[{service}] | GET /lms/api/v1/{path} → proxied correctly |
| test_gateway_auth | JWT required for protected routes |
| test_gateway_strip_prefix | /lms/ prefix stripped before forwarding |

### test_18_transfers.py — Fund Transfers (4 tests)
| Test | What it validates |
|------|-------------------|
| test_initiate_transfer | POST /api/v1/transfers → 201 |
| test_get_transfer | GET /api/v1/transfers/{id} → 200 |
| test_by_account | GET /api/v1/transfers/account/{id} → list |
| test_idempotency | Same idempotency key → same result |

### test_19_e2e_loan_lifecycle.py — Full Loan E2E (6 tests)
| Test | What it validates |
|------|-------------------|
| test_full_loan_lifecycle[NANO] | Create → submit → approve → disburse → pay → close |
| test_full_loan_lifecycle[PERSONAL] | Same for personal loan product |
| test_full_loan_lifecycle[SME] | Same for SME loan product |
| test_zero_amount | Zero amount rejected |
| test_negative_amount | Negative amount rejected |
| test_double_submit | Can't submit twice |

### test_20_e2e_overdraft.py — Full Overdraft E2E (3 tests)
| Test | What it validates |
|------|-------------------|
| test_wallet_lifecycle | Create wallet → deposit → withdraw → balance |
| test_overdraft_application | Apply → approve → draw → repay |
| test_exceed_balance | Withdraw more than balance → rejected |

### test_21_e2e_accounting_gl.py — Accounting Verification (9 tests)
| Test | What it validates |
|------|-------------------|
| test_system_accounts_seeded | 1000/1100/4000/5000 accounts exist |
| test_disbursement_journal | Disbursement creates DR Loans / CR Cash |
| test_repayment_journal | Payment creates DR Cash / CR Loans+Interest |
| test_trial_balance_balanced | Total DR == Total CR |
| (5 more) | Various GL verification tests |

### test_22_e2e_organization.py — Org & Swagger (29 tests)
| Test | What it validates |
|------|-------------------|
| test_get_org_settings | GET /api/v1/organization/settings → 200 |
| test_swagger_ui[{service}] | GET /swagger-ui.html → 200 (Spring-only) |
| test_api_docs[{service}] | GET /api-docs → 200 (Spring-only) |

### test_23_fraud_detection.py — Fraud (20 tests)
| Test | What it validates |
|------|-------------------|
| test_health | GET /actuator/health → UP |
| test_summary | GET /api/v1/fraud/summary → counts |
| test_list_alerts | GET /api/v1/fraud/alerts → paginated |
| test_alert_filters | Filter by status, severity |
| test_customer_risk | GET /api/v1/fraud/customers/{cid}/risk → profile |
| test_list_rules | GET /api/v1/fraud/rules → 20+ rules |
| (more) | Cases, network analysis, audit log |

---

## 4. UI Test Catalog (Playwright)

### P0 — Critical Path
| Test | File | What it validates |
|------|------|-------------------|
| Login success | auth.spec.ts | admin/admin123 → dashboard |
| Login failure | auth.spec.ts | wrong password → error |
| Dashboard loads | dashboard.spec.ts | metrics cards visible |
| Customer list | customers.spec.ts | table with customer data |
| Customer search | customers.spec.ts | search returns results |
| Loan list | loans.spec.ts | applications table |
| Account list | accounts.spec.ts | accounts with balances |

### P1 — High Priority
| Test | File | What it validates |
|------|------|-------------------|
| Fraud dashboard | compliance.spec.ts | alerts summary, charts |
| Fraud alert list | compliance.spec.ts | filter by severity |
| AML page | compliance.spec.ts | AML alerts visible |
| SAR reports | compliance.spec.ts | SAR list |
| Ledger page | finance.spec.ts | journal entries |
| Trial balance | finance.spec.ts | balanced report |
| Collections queue | collections.spec.ts | delinquent cases |

### P2 — Sidebar Navigation
| Test | File | What it validates |
|------|------|-------------------|
| All menu items | navigation.spec.ts | Each sidebar item loads page |
| No 404 pages | navigation.spec.ts | No blank/error pages |
| Breadcrumb/header | navigation.spec.ts | Page title matches nav |

---

## 5. Event-Driven Integration Tests

These tests validate RabbitMQ event flows between services:

| Event Flow | Trigger | Expected Side Effect |
|------------|---------|---------------------|
| loan.disbursed | Disburse a loan | Accounting: DR 1100 / CR 1000 journal entry |
| loan.disbursed | Disburse a loan | Float: allocation created |
| loan.disbursed | Disburse a loan | Notification: email sent |
| payment.completed | Complete a payment | Accounting: DR 1000 / CR 1100+4000 |
| payment.completed | Complete a payment | Collections: case updated if delinquent |
| loan.dpd.updated | DPD refresh scheduler | Collections: case created if DPD >= 1 |
| loan.stage.changed | Stage transition | Collections: case escalated |
| aml.alert.raised | Create AML alert | Compliance: event logged |
| customer.kyc.passed | Pass KYC | Compliance: KYC record updated |
| fraud.alert.raised | Rule triggered | Fraud: alert created, risk profile updated |

Test these by:
1. Performing the trigger action via API
2. Waiting 2-5 seconds for async processing
3. Querying the downstream service for the expected state

---

## 6. Non-Functional Tests

### Performance
- Each Go service uses 3-5 MiB memory (vs ~300 MiB Java)
- Health endpoint response < 5ms
- List endpoint response < 100ms for < 1000 records

### Security
- All endpoints require JWT or service-key (except /actuator/health, /api/auth/login)
- Invalid JWT → 401
- Wrong tenant → no data leakage
- SQL injection in search params → no effect (parameterized queries)

### Resilience
- Service starts without RabbitMQ (no-op publisher)
- Service starts with dirty migrations (warns, continues)
- Circuit breaker in gateway (5 failures → open → 503)
