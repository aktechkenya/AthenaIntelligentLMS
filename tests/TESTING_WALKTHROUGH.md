# Athena LMS — Step-by-Step Testing Walkthrough

Test each component one by one. Open **http://172.20.10.2:30088** and login as `admin` / `admin123`.

---

## 1. Authentication (account-service:8086)

**UI Test:**
1. Go to login page → enter `admin` / `admin123` → click Sign In
2. Verify dashboard loads with "Overview Dashboard" heading
3. Click "Log out" in sidebar footer → redirected to login
4. Try wrong password → error message shown

**API Test:**
```bash
# Login
curl -X POST http://localhost:30088/proxy/auth/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'

# Check /me
curl http://localhost:30088/proxy/auth/api/auth/me \
  -H "Authorization: Bearer <token>"
```

**Expected:** Token returned, username/roles in /me response.

---

## 2. Customers (account-service:8086)

**UI Test:**
1. Click Customers → Customer Directory in sidebar
2. Verify table shows 277 customers
3. Search for "Pytest" → results filter
4. Click "+ Add Customer" → fill form → Create
5. Click any customer row → Customer 360 page loads

**API Test:**
```bash
# List
curl http://localhost:30088/proxy/auth/api/v1/customers?page=0&size=5 \
  -H "Authorization: Bearer $TOKEN"

# Create
curl -X POST http://localhost:30088/proxy/auth/api/v1/customers \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"customerId":"TEST-001","firstName":"Test","lastName":"User","email":"test@example.com","phone":"+254700000001","customerType":"INDIVIDUAL"}'

# Search
curl "http://localhost:30088/proxy/auth/api/v1/customers/search?q=Test" \
  -H "Authorization: Bearer $TOKEN"
```

**Expected:** 277 customers listed, new customer created with 201, search returns matches.

---

## 3. Accounts (account-service:8086)

**UI Test:**
1. Navigate to Finance → Accounts (or use customer 360)
2. Verify 327 accounts listed
3. Click an account → see balance and transactions

**API Test:**
```bash
# List accounts
curl "http://localhost:30088/proxy/accounts/api/v1/accounts?page=0&size=5" \
  -H "Authorization: Bearer $TOKEN"

# Create account
curl -X POST http://localhost:30088/proxy/accounts/api/v1/accounts \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"customerId":"TEST-001","accountType":"SAVINGS","currency":"KES"}'

# Credit (use account ID from above)
curl -X POST "http://localhost:30088/proxy/accounts/api/v1/accounts/<id>/credit" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"amount":50000,"description":"Initial deposit","reference":"DEP-001"}'

# Check balance
curl "http://localhost:30088/proxy/accounts/api/v1/accounts/<id>/balance" \
  -H "Authorization: Bearer $TOKEN"

# Debit
curl -X POST "http://localhost:30088/proxy/accounts/api/v1/accounts/<id>/debit" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"amount":5000,"description":"Withdrawal","reference":"WD-001"}'

# Mini statement
curl "http://localhost:30088/proxy/accounts/api/v1/accounts/<id>/mini-statement" \
  -H "Authorization: Bearer $TOKEN"
```

**Expected:** Account created, balance = 50000 after credit, 45000 after debit, 2 transactions in mini statement.

---

## 4. Products (product-service:8087)

**UI Test:**
1. Navigate to Products → Product Catalogue
2. Verify 5 products listed (NANO, PERSONAL, SME, etc.)
3. Click a product → view details
4. Click "Simulate" → enter amount, tenor → see amortization schedule

**API Test:**
```bash
# List products
curl "http://localhost:30088/proxy/products/api/v1/products?page=0&size=10" \
  -H "Authorization: Bearer $TOKEN"

# Get product by ID
curl "http://localhost:30088/proxy/products/api/v1/products/<productId>" \
  -H "Authorization: Bearer $TOKEN"

# Simulate schedule
curl -X POST "http://localhost:30088/proxy/products/api/v1/products/<productId>/simulate" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"principal":100000,"tenorMonths":12,"interestRate":15}'
```

**Expected:** 5 products listed, simulation returns monthly installment schedule.

---

## 5. Loan Applications (loan-origination-service:8088)

**UI Test:**
1. Navigate to Lending → Loan Applications
2. Verify 55 applications in kanban/list view
3. Click "+ New Application" → fill Customer ID, Product, Amount, Tenor → Create
4. Click an application → see detail page with status timeline

**API Test:**
```bash
# List applications
curl "http://localhost:30088/proxy/loan-applications/api/v1/loan-applications?page=0&size=5" \
  -H "Authorization: Bearer $TOKEN"

# Create application
curl -X POST http://localhost:30088/proxy/loan-applications/api/v1/loan-applications \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"customerId":"TEST-001","productId":"<productId>","requestedAmount":50000,"tenorMonths":6,"purpose":"Business expansion","currency":"KES"}'

# Submit
curl -X POST "http://localhost:30088/proxy/loan-applications/api/v1/loan-applications/<appId>/submit" \
  -H "Authorization: Bearer $TOKEN"

# Approve
curl -X POST "http://localhost:30088/proxy/loan-applications/api/v1/loan-applications/<appId>/review/approve" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"approvedAmount":50000,"interestRate":15,"reviewNotes":"Good credit"}'
```

**Expected:** Application created as DRAFT, moves to SUBMITTED, then APPROVED.

---

## 6. Active Loans (loan-management-service:8089)

**UI Test:**
1. Navigate to Lending → Active Loans
2. Verify 4 active loans shown
3. Click a loan → see schedule, repayments, DPD history

**API Test:**
```bash
# List active loans
curl "http://localhost:30088/proxy/loans/api/v1/loans?page=0&size=10" \
  -H "Authorization: Bearer $TOKEN"

# Get loan details + schedule
curl "http://localhost:30088/proxy/loans/api/v1/loans/<loanId>" \
  -H "Authorization: Bearer $TOKEN"

curl "http://localhost:30088/proxy/loans/api/v1/loans/<loanId>/schedule" \
  -H "Authorization: Bearer $TOKEN"
```

**Expected:** 4 loans with schedules showing installment breakdown (principal, interest, fees).

---

## 7. Payments (payment-service:8090)

**UI Test:**
1. Navigate to Lending → Repayment Schedule
2. View payment history

**API Test:**
```bash
# List payments
curl "http://localhost:30088/proxy/payments/api/v1/payments?page=0&size=10" \
  -H "Authorization: Bearer $TOKEN"

# Create payment
curl -X POST http://localhost:30088/proxy/payments/api/v1/payments \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"customerId":"TEST-001","loanId":"<loanId>","amount":5000,"paymentType":"LOAN_REPAYMENT","paymentChannel":"CASH","description":"Monthly payment"}'

# Complete payment
curl -X POST "http://localhost:30088/proxy/payments/api/v1/payments/<paymentId>/complete" \
  -H "Authorization: Bearer $TOKEN"
```

**Expected:** Payment created as PENDING, transitions to COMPLETED.

---

## 8. Accounting (accounting-service:8091)

**UI Test:**
1. Navigate to Finance → General Ledger
2. Verify journal entries listed
3. Navigate to Finance → Trial Balance
4. Verify debits = credits (balanced)

**API Test:**
```bash
# Chart of accounts
curl "http://localhost:30088/proxy/accounting/api/v1/accounting/accounts" \
  -H "Authorization: Bearer $TOKEN"

# Journal entries
curl "http://localhost:30088/proxy/accounting/api/v1/accounting/journal-entries?page=0&size=10" \
  -H "Authorization: Bearer $TOKEN"

# Trial balance
curl "http://localhost:30088/proxy/accounting/api/v1/accounting/trial-balance?year=2026&month=3" \
  -H "Authorization: Bearer $TOKEN"

# Post manual entry
curl -X POST http://localhost:30088/proxy/accounting/api/v1/accounting/journal-entries \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"reference":"TEST-JE-001","description":"Test entry","lines":[{"accountCode":"1000","debitAmount":1000,"creditAmount":0},{"accountCode":"4000","debitAmount":0,"creditAmount":1000}]}'
```

**Expected:** System accounts (1000 Cash, 1100 Loans, 4000 Interest Income), balanced trial balance.

---

## 9. Float (float-service:8092)

**UI Test:**
1. Navigate to Float & Wallet → AthenaFloat Overview
2. Verify float account shown with balance

**API Test:**
```bash
# List float accounts
curl "http://localhost:30088/proxy/float/api/v1/float/accounts" \
  -H "Authorization: Bearer $TOKEN"

# Float summary
curl "http://localhost:30088/proxy/float/api/v1/float/summary" \
  -H "Authorization: Bearer $TOKEN"
```

**Expected:** Float account with limit, drawn amount, and available balance.

---

## 10. Collections (collections-service:8093)

**UI Test:**
1. Navigate to Collections → Delinquency Queue
2. View summary cards (Total Active, Delinquent, On Track)
3. Click a case → see actions, PTPs

**API Test:**
```bash
# Summary
curl "http://localhost:30088/proxy/collections/api/v1/collections/summary" \
  -H "Authorization: Bearer $TOKEN"

# List cases
curl "http://localhost:30088/proxy/collections/api/v1/collections/cases?page=0&size=10" \
  -H "Authorization: Bearer $TOKEN"
```

**Expected:** Summary with case counts by stage.

---

## 11. Compliance (compliance-service:8094)

**UI Test:**
1. Navigate to Compliance → AML Monitoring
2. Verify 9 open alerts shown
3. Click an alert → view details
4. Click "Resolve" → enter notes → confirm

**API Test:**
```bash
# Summary
curl "http://localhost:30088/proxy/compliance/api/v1/compliance/summary" \
  -H "Authorization: Bearer $TOKEN"

# List alerts
curl "http://localhost:30088/proxy/compliance/api/v1/compliance/alerts?page=0&size=10" \
  -H "Authorization: Bearer $TOKEN"

# Create alert
curl -X POST http://localhost:30088/proxy/compliance/api/v1/compliance/alerts \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"alertType":"LARGE_TRANSACTION","severity":"HIGH","subjectType":"CUSTOMER","subjectId":"TEST-001","description":"Large cash deposit over threshold"}'

# Create KYC
curl -X POST http://localhost:30088/proxy/compliance/api/v1/compliance/kyc \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"customerId":"TEST-001","checkType":"ID_VERIFICATION","nationalId":"12345678","fullName":"Test User","phone":"+254700000001"}'

# Pass KYC
curl -X POST "http://localhost:30088/proxy/compliance/api/v1/compliance/kyc/TEST-001/pass" \
  -H "Authorization: Bearer $TOKEN"
```

**Expected:** 9 alerts, new alert created, KYC passes.

---

## 12. Reporting (reporting-service:8095)

**UI Test:**
1. Navigate to Dashboard (home page)
2. Verify KPI cards show: Active Loans=4, Loan Book=145,000, Outstanding=136,604, Collected=15,943

**API Test:**
```bash
# Summary (powers the dashboard)
curl "http://localhost:30088/proxy/reporting/api/v1/reporting/summary" \
  -H "Authorization: Bearer $TOKEN"

# Latest snapshot
curl "http://localhost:30088/proxy/reporting/api/v1/reporting/snapshots/latest" \
  -H "Authorization: Bearer $TOKEN"

# Generate fresh snapshot
curl -X POST "http://localhost:30088/proxy/reporting/api/v1/reporting/snapshots/generate" \
  -H "Authorization: Bearer $TOKEN"
```

**Expected:** Dashboard shows real loan portfolio data.

---

## 13. Wallets & Overdraft (overdraft-service:8097)

**UI Test:**
1. Navigate to Float & Wallet → Wallet Accounts
2. Verify 125 wallets shown with total balance KES 6.17M
3. Navigate to Float & Wallet → Overdraft Management
4. View overdraft summary

**API Test:**
```bash
# List wallets
curl "http://localhost:30088/proxy/overdraft/api/v1/wallets" \
  -H "Authorization: Bearer $TOKEN"

# Overdraft summary
curl "http://localhost:30088/proxy/overdraft/api/v1/overdraft/summary" \
  -H "Authorization: Bearer $TOKEN"

# Create wallet
curl -X POST http://localhost:30088/proxy/overdraft/api/v1/wallets \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"customerId":"TEST-001","currency":"KES"}'

# Deposit
curl -X POST "http://localhost:30088/proxy/overdraft/api/v1/wallets/<walletId>/deposit" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"amount":10000,"reference":"DEP-001"}'

# Withdraw
curl -X POST "http://localhost:30088/proxy/overdraft/api/v1/wallets/<walletId>/withdraw" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"amount":3000,"reference":"WD-001"}'

# Apply for overdraft
curl -X POST "http://localhost:30088/proxy/overdraft/api/v1/wallets/<walletId>/overdraft/apply" \
  -H "Authorization: Bearer $TOKEN"

# Check overdraft facility
curl "http://localhost:30088/proxy/overdraft/api/v1/wallets/<walletId>/overdraft" \
  -H "Authorization: Bearer $TOKEN"
```

**Expected:** 125 wallets, deposit increases balance, withdrawal decreases it, overdraft application submitted.

---

## 14. Media (media-service:8098)

**API Test:**
```bash
# Upload a file
curl -X POST "http://localhost:30088/proxy/media/api/v1/media/upload/TEST-001" \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@/tmp/test.txt" \
  -F "category=CUSTOMER_DOCUMENT" \
  -F "mediaType=OTHER"

# List by customer
curl "http://localhost:30088/proxy/media/api/v1/media/customer/TEST-001" \
  -H "Authorization: Bearer $TOKEN"
```

**Expected:** File uploaded, metadata returned, listed under customer.

---

## 15. Notifications (notification-service:8099)

**UI Test:**
1. Navigate to Administration → Notifications
2. View notification logs

**API Test:**
```bash
# List logs
curl "http://localhost:30088/proxy/notifications/api/v1/notifications/logs?page=0&size=10" \
  -H "Authorization: Bearer $TOKEN"

# Send test notification
curl -X POST "http://localhost:30088/proxy/notifications/api/v1/notifications/send" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"serviceName":"test","type":"EMAIL","recipient":"test@example.com","subject":"Test","message":"Hello from Go"}'
```

**Expected:** Notification logged (EMAIL will be SKIPPED if SMTP not configured).

---

## 16. Fraud Detection (fraud-detection-service:8100)

**UI Test:**
1. Navigate to Compliance → Fraud Dashboard → view summary metrics
2. Navigate to Compliance → Fraud Alerts → see alert list
3. Filter by severity (CRITICAL, HIGH)
4. Navigate to Compliance → Detection Rules → see 20+ rules
5. Navigate to Compliance → Watchlist

**API Test:**
```bash
# Summary
curl "http://localhost:30088/proxy/fraud/api/v1/fraud/summary" \
  -H "Authorization: Bearer $TOKEN"

# List alerts
curl "http://localhost:30088/proxy/fraud/api/v1/fraud/alerts?page=0&size=10" \
  -H "Authorization: Bearer $TOKEN"

# List rules
curl "http://localhost:30088/proxy/fraud/api/v1/fraud/rules" \
  -H "Authorization: Bearer $TOKEN"

# Audit log
curl "http://localhost:30088/proxy/fraud/api/v1/fraud/audit?page=0&size=10" \
  -H "Authorization: Bearer $TOKEN"
```

**Expected:** Fraud summary with counts, alert list, 20+ detection rules.

---

## 17. Transfers (account-service:8086)

**API Test:**
```bash
# Create a second account for transfer target
curl -X POST http://localhost:30088/proxy/accounts/api/v1/accounts \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"customerId":"TEST-001","accountType":"CURRENT","currency":"KES"}'

# Transfer between accounts
curl -X POST http://localhost:30088/proxy/accounts/api/v1/transfers \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"fromAccountId":"<account1>","toAccountId":"<account2>","amount":5000,"reference":"TRF-001","narration":"Internal transfer"}'

# Check transfer
curl "http://localhost:30088/proxy/accounts/api/v1/transfers/<transferId>" \
  -H "Authorization: Bearer $TOKEN"
```

**Expected:** Transfer completed, both account balances updated.

---

## End-to-End: Full Loan Lifecycle

This tests the complete flow across 6 services:

```bash
TOKEN=$(curl -s -X POST http://localhost:30088/proxy/auth/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])')

# 1. Create customer
CID="E2E-$(date +%s)"
curl -s -X POST http://localhost:30088/proxy/auth/api/v1/customers \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d "{\"customerId\":\"$CID\",\"firstName\":\"E2E\",\"lastName\":\"Test\",\"email\":\"$CID@test.com\",\"phone\":\"+254711111111\",\"customerType\":\"INDIVIDUAL\"}"

# 2. Create savings account
ACCT=$(curl -s -X POST http://localhost:30088/proxy/accounts/api/v1/accounts \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d "{\"customerId\":\"$CID\",\"accountType\":\"SAVINGS\",\"currency\":\"KES\"}" | python3 -c 'import sys,json;print(json.load(sys.stdin)["id"])')
echo "Account: $ACCT"

# 3. Seed balance
curl -s -X POST "http://localhost:30088/proxy/accounts/api/v1/accounts/$ACCT/credit" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"amount":100000,"description":"Seed"}'

# 4. Get product ID
PROD=$(curl -s "http://localhost:30088/proxy/products/api/v1/products?page=0&size=1" \
  -H "Authorization: Bearer $TOKEN" | python3 -c 'import sys,json;print(json.load(sys.stdin)["content"][0]["id"])')
echo "Product: $PROD"

# 5. Create loan application
APP=$(curl -s -X POST http://localhost:30088/proxy/loan-applications/api/v1/loan-applications \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d "{\"customerId\":\"$CID\",\"productId\":\"$PROD\",\"requestedAmount\":25000,\"tenorMonths\":3,\"purpose\":\"E2E test\",\"currency\":\"KES\"}" | python3 -c 'import sys,json;print(json.load(sys.stdin)["id"])')
echo "Application: $APP"

# 6. Submit
curl -s -X POST "http://localhost:30088/proxy/loan-applications/api/v1/loan-applications/$APP/submit" \
  -H "Authorization: Bearer $TOKEN"

# 7. Approve
curl -s -X POST "http://localhost:30088/proxy/loan-applications/api/v1/loan-applications/$APP/review/approve" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"approvedAmount":25000,"interestRate":12,"reviewNotes":"E2E approved"}'

# 8. Check application status
curl -s "http://localhost:30088/proxy/loan-applications/api/v1/loan-applications/$APP" \
  -H "Authorization: Bearer $TOKEN" | python3 -c 'import sys,json;d=json.load(sys.stdin);print(f"Status: {d[\"status\"]}")'

echo "✓ Loan lifecycle test complete"
```

---

## Quick Reference

| Component | Portal URL | API Base |
|-----------|-----------|----------|
| Auth | /login | /proxy/auth/api/auth/ |
| Customers | /borrowers | /proxy/auth/api/v1/customers |
| Accounts | /accounts | /proxy/accounts/api/v1/accounts |
| Products | /products | /proxy/products/api/v1/products |
| Loan Apps | /loans | /proxy/loan-applications/api/v1/loan-applications |
| Active Loans | /active-loans | /proxy/loans/api/v1/loans |
| Payments | /repayments | /proxy/payments/api/v1/payments |
| Accounting | /ledger | /proxy/accounting/api/v1/accounting |
| Float | /float | /proxy/float/api/v1/float |
| Collections | /collections | /proxy/collections/api/v1/collections |
| Compliance | /compliance | /proxy/compliance/api/v1/compliance |
| Reporting | / (dashboard) | /proxy/reporting/api/v1/reporting |
| Wallets | /wallets | /proxy/overdraft/api/v1/wallets |
| Overdraft | /overdraft | /proxy/overdraft/api/v1/overdraft |
| Notifications | /notifications | /proxy/notifications/api/v1/notifications |
| Fraud | /fraud-dashboard | /proxy/fraud/api/v1/fraud |
| Transfers | — | /proxy/accounts/api/v1/transfers |
| Media | /documents | /proxy/media/api/v1/media |

**Portal:** http://172.20.10.2:30088
**Login:** admin / admin123
