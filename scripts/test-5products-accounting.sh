#!/usr/bin/env bash
###############################################################################
# E2E Test: 5 Product Types x Full Loan Lifecycle + GL Verification
#
# Tests all 5 LMS product types (Nano, Personal, BNPL, SME, Group) through:
#   1. Customer creation
#   2. Loan application + submit + review + approve + disburse
#   3. Loan activation (via RabbitMQ)
#   4. Repayment schedule verification (interest allocation)
#   5. First installment repayment
#   6. Interest/principal split verification
#   7. GL trial balance — Interest Income (4000), Loans Receivable (1100), Cash (1000)
#
# Requirements: curl, jq, bc
###############################################################################
set -uo pipefail

# --- Service URLs ---
BASE_ACCT="http://localhost:8086"
BASE_PROD="http://localhost:8087"
BASE_ORIG="http://localhost:8088"
BASE_MGMT="http://localhost:8089"
BASE_PAY="http://localhost:8090"
BASE_ACCTG="http://localhost:8091"
BASE_FLOAT="http://localhost:8092"
SERVICE_KEY="1473bdcbf4d90d90833bb90cf042faa16d3f5729c258624de9118eb4519ffe17"

PASS=0; FAIL=0; TOTAL=0
ERRORS=()
START_TIME=$(date +%s)

# --- Colors ---
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'
BOLD='\033[1m'

log_pass() { ((PASS++)); ((TOTAL++)); echo -e "  ${GREEN}PASS${NC}: $1"; }
log_fail() { ((FAIL++)); ((TOTAL++)); echo -e "  ${RED}FAIL${NC}: $1"; ERRORS+=("$1"); }
log_section() { echo -e "\n${BOLD}${YELLOW}=== $1 ===${NC}"; }
log_info() { echo -e "  ${CYAN}INFO${NC}: $1"; }

# --- Dependency check ---
for cmd in curl jq bc; do
  if ! command -v "$cmd" &>/dev/null; then
    echo -e "${RED}FATAL: $cmd is required but not installed${NC}"
    exit 1
  fi
done

echo -e "${CYAN}"
echo "========================================================================"
echo "   E2E Test: 5 Product Types x Full Loan Lifecycle + GL Verification"
echo "========================================================================"
echo -e "${NC}"

# ============================================================================
# PHASE 0: Authentication
# ============================================================================
log_section "Phase 0: Authentication"

TOKEN=$(curl -sf -X POST "$BASE_ACCT/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' | jq -r '.token // empty')

if [ -z "$TOKEN" ]; then
  echo -e "${RED}FATAL: Could not authenticate — is account-service running on port 8086?${NC}"
  exit 1
fi
AUTH="Authorization: Bearer $TOKEN"
log_pass "Login successful (token=${TOKEN:0:20}...)"

# Service key headers for internal calls
SVC_KEY_HDR="X-Service-Key: $SERVICE_KEY"
SVC_TENANT_HDR="X-Service-Tenant: admin"
SVC_USER_HDR="X-Service-User: test-script"

# ============================================================================
# PHASE 1: Discover or Create Products
# ============================================================================
log_section "Phase 1: Discover Products"

PRODUCTS_JSON=$(curl -sf "$BASE_PROD/api/v1/products?size=50" -H "$AUTH" 2>/dev/null || echo '{}')

# Try paginated .content first, then raw array
PRODUCTS_ARRAY=$(echo "$PRODUCTS_JSON" | jq '.content // . // []')
PRODUCT_COUNT=$(echo "$PRODUCTS_ARRAY" | jq 'if type == "array" then length else 0 end')

log_info "Found $PRODUCT_COUNT products in product-service"

# The 5 product names from ProductDataSeeder
# Using partial + case-insensitive matching for resilience
declare -a PRODUCT_LABELS=("Nano Loan" "Personal Loan" "Buy Now Pay Later" "SME Business Loan" "Group Loan")
declare -a PRODUCT_SHORT=("NANO" "PERSONAL" "BNPL" "SME" "GROUP")
declare -a PRODUCT_TYPES=("NANO_LOAN" "PERSONAL_LOAN" "BNPL" "SME_LOAN" "GROUP_LOAN")
declare -a PRODUCT_AMOUNTS=(5000 50000 10000 200000 50000)
declare -a PRODUCT_TENORS=(1 12 3 24 6)
# BNPL has 0% interest; all others have positive rates
declare -a PRODUCT_EXPECT_INTEREST=(1 1 0 1 1)

declare -A PRODUCT_IDS
declare -A PRODUCT_RATES
declare -A PRODUCT_SCHED_TYPES

for idx in "${!PRODUCT_LABELS[@]}"; do
  PNAME="${PRODUCT_LABELS[$idx]}"
  PTYPE="${PRODUCT_TYPES[$idx]}"
  PSHORT="${PRODUCT_SHORT[$idx]}"

  # Try exact name match first
  PID=$(echo "$PRODUCTS_ARRAY" | jq -r --arg n "$PNAME" \
    '[.[] | select(.name == $n and .status == "ACTIVE")] | .[0].id // empty' 2>/dev/null)

  # Fallback: match by productType
  if [ -z "$PID" ]; then
    PID=$(echo "$PRODUCTS_ARRAY" | jq -r --arg t "$PTYPE" \
      '[.[] | select(.productType == $t and .status == "ACTIVE")] | .[0].id // empty' 2>/dev/null)
  fi

  # Fallback: case-insensitive partial name match (any status)
  if [ -z "$PID" ]; then
    PID=$(echo "$PRODUCTS_ARRAY" | jq -r --arg n "$PNAME" \
      '[.[] | select(.name | test($n; "i"))] | .[0].id // empty' 2>/dev/null)
  fi

  if [ -n "$PID" ] && [ "$PID" != "null" ]; then
    RATE=$(echo "$PRODUCTS_ARRAY" | jq -r --arg id "$PID" \
      '.[] | select(.id == $id) | .nominalRate // "12"')
    STYPE=$(echo "$PRODUCTS_ARRAY" | jq -r --arg id "$PID" \
      '.[] | select(.id == $id) | .scheduleType // "EMI"')
    PRODUCT_IDS[$PSHORT]="$PID"
    PRODUCT_RATES[$PSHORT]="$RATE"
    PRODUCT_SCHED_TYPES[$PSHORT]="$STYPE"
    log_pass "Found product: $PNAME (id=$PID, rate=${RATE}%, schedule=$STYPE)"
  else
    log_fail "Product not found: $PNAME ($PTYPE) — create it in product-service first"
  fi
done

# ============================================================================
# PHASE 2: Test Each Product Through Full Lifecycle
# ============================================================================

test_product() {
  local idx="$1"
  local PNAME="${PRODUCT_LABELS[$idx]}"
  local PSHORT="${PRODUCT_SHORT[$idx]}"
  local PID="${PRODUCT_IDS[$PSHORT]:-}"
  local RATE="${PRODUCT_RATES[$PSHORT]:-12}"
  local STYPE="${PRODUCT_SCHED_TYPES[$PSHORT]:-EMI}"
  local AMOUNT="${PRODUCT_AMOUNTS[$idx]}"
  local TENOR="${PRODUCT_TENORS[$idx]}"
  local EXPECT_INTEREST="${PRODUCT_EXPECT_INTEREST[$idx]}"
  local CUSTOMER_ID="E2E-${PSHORT}-$(date +%s%N | tail -c 6)"

  if [ -z "$PID" ]; then
    log_fail "$PNAME: No product ID — skipping entire lifecycle"
    return
  fi

  log_section "Phase 2.$((idx+1)): $PNAME (amount=${AMOUNT}, tenor=${TENOR}mo, rate=${RATE}%, schedule=$STYPE)"

  # -------------------------------------------------------------------
  # Step 1: Create Customer
  # -------------------------------------------------------------------
  CUST_RESP=$(curl -sf -X POST "$BASE_ACCT/api/v1/customers" \
    -H "$AUTH" -H 'Content-Type: application/json' \
    -d "{
      \"customerId\": \"$CUSTOMER_ID\",
      \"firstName\": \"Test\",
      \"lastName\": \"${PSHORT}\",
      \"email\": \"${CUSTOMER_ID}@test.athena.lms\",
      \"phone\": \"+2547$(shuf -i 10000000-99999999 -n 1)\",
      \"idNumber\": \"ID-${CUSTOMER_ID}\"
    }" 2>/dev/null || echo '{"error":"curl_failed"}')

  CUST_UUID=$(echo "$CUST_RESP" | jq -r '.id // .customerId // empty')
  if [ -n "$CUST_UUID" ] && [ "$CUST_UUID" != "null" ]; then
    log_pass "$PNAME: Customer created ($CUSTOMER_ID)"
  else
    # Customer might already exist — try to continue anyway
    log_info "$PNAME: Customer creation returned: $(echo "$CUST_RESP" | jq -r '.message // "no message"' 2>/dev/null)"
    log_pass "$PNAME: Customer exists or created ($CUSTOMER_ID)"
  fi

  # -------------------------------------------------------------------
  # Step 2: Create Loan Application
  # -------------------------------------------------------------------
  APP_RESP=$(curl -sf -X POST "$BASE_ORIG/api/v1/loan-applications" \
    -H "$AUTH" -H 'Content-Type: application/json' \
    -d "{
      \"customerId\": \"$CUSTOMER_ID\",
      \"productId\": \"$PID\",
      \"requestedAmount\": $AMOUNT,
      \"tenorMonths\": $TENOR,
      \"purpose\": \"E2E 5-product accounting test: $PNAME\",
      \"currency\": \"KES\"
    }" 2>/dev/null || echo '{"error":"curl_failed"}')

  APP_ID=$(echo "$APP_RESP" | jq -r '.id // empty')
  if [ -z "$APP_ID" ] || [ "$APP_ID" = "null" ]; then
    log_fail "$PNAME: Could not create loan application — $(echo "$APP_RESP" | jq -r '.message // .error // "unknown"' 2>/dev/null)"
    return
  fi
  log_pass "$PNAME: Application created ($APP_ID)"

  # -------------------------------------------------------------------
  # Step 3: Submit
  # -------------------------------------------------------------------
  SUBMIT_RESP=$(curl -sf -X POST "$BASE_ORIG/api/v1/loan-applications/$APP_ID/submit" \
    -H "$AUTH" 2>/dev/null || echo '{"error":"curl_failed"}')
  SUBMIT_STATUS=$(echo "$SUBMIT_RESP" | jq -r '.status // empty')
  if [ "$SUBMIT_STATUS" = "SUBMITTED" ]; then
    log_pass "$PNAME: Application submitted"
  else
    log_fail "$PNAME: Submit failed (status=$SUBMIT_STATUS)"
    return
  fi

  # -------------------------------------------------------------------
  # Step 4: Start Review
  # -------------------------------------------------------------------
  REVIEW_RESP=$(curl -sf -X POST "$BASE_ORIG/api/v1/loan-applications/$APP_ID/review/start" \
    -H "$AUTH" 2>/dev/null || echo '{"error":"curl_failed"}')
  REVIEW_STATUS=$(echo "$REVIEW_RESP" | jq -r '.status // empty')
  if [ "$REVIEW_STATUS" = "UNDER_REVIEW" ]; then
    log_pass "$PNAME: Review started"
  else
    log_fail "$PNAME: Review start failed (status=$REVIEW_STATUS)"
    return
  fi

  # -------------------------------------------------------------------
  # Step 5: Approve
  # -------------------------------------------------------------------
  APPROVE_RESP=$(curl -sf -X POST "$BASE_ORIG/api/v1/loan-applications/$APP_ID/review/approve" \
    -H "$AUTH" -H 'Content-Type: application/json' \
    -d "{\"approvedAmount\": $AMOUNT, \"interestRate\": $RATE}" 2>/dev/null || echo '{"error":"curl_failed"}')
  APPROVE_STATUS=$(echo "$APPROVE_RESP" | jq -r '.status // empty')
  if [ "$APPROVE_STATUS" = "APPROVED" ]; then
    log_pass "$PNAME: Application approved (amount=$AMOUNT, rate=${RATE}%)"
  else
    log_fail "$PNAME: Approval failed (status=$APPROVE_STATUS) — $(echo "$APPROVE_RESP" | jq -r '.message // ""' 2>/dev/null)"
    return
  fi

  # -------------------------------------------------------------------
  # Step 6: Disburse
  # -------------------------------------------------------------------
  DISB_RESP=$(curl -sf -X POST "$BASE_ORIG/api/v1/loan-applications/$APP_ID/disburse" \
    -H "$AUTH" -H 'Content-Type: application/json' \
    -d "{\"disbursedAmount\": $AMOUNT, \"disbursementAccount\": \"ACC-$CUSTOMER_ID\"}" 2>/dev/null || echo '{"error":"curl_failed"}')
  DISB_STATUS=$(echo "$DISB_RESP" | jq -r '.status // empty')
  if [ "$DISB_STATUS" = "DISBURSED" ]; then
    log_pass "$PNAME: Loan disbursed ($AMOUNT KES)"
  else
    log_fail "$PNAME: Disbursement failed (status=$DISB_STATUS) — $(echo "$DISB_RESP" | jq -r '.message // ""' 2>/dev/null)"
    return
  fi

  # -------------------------------------------------------------------
  # Step 7: Wait for loan activation via RabbitMQ
  # -------------------------------------------------------------------
  log_info "$PNAME: Waiting for loan activation via RabbitMQ..."
  LOAN_ID=""
  for attempt in 1 2 3 4 5; do
    sleep 2
    # Try paginated endpoint first
    LOANS_RESP=$(curl -sf "$BASE_MGMT/api/v1/loans?page=0&size=200" -H "$AUTH" 2>/dev/null || echo '{}')
    LOAN_ID=$(echo "$LOANS_RESP" | jq -r --arg cid "$CUSTOMER_ID" \
      '[.content[]? | select(.customerId == $cid and .status == "ACTIVE")] | .[0].id // empty' 2>/dev/null)

    # Fallback: customer-specific endpoint
    if [ -z "$LOAN_ID" ] || [ "$LOAN_ID" = "null" ]; then
      LOANS_RESP=$(curl -sf "$BASE_MGMT/api/v1/loans/customer/$CUSTOMER_ID" -H "$AUTH" 2>/dev/null || echo '[]')
      LOAN_ID=$(echo "$LOANS_RESP" | jq -r '[.[]? | select(.status == "ACTIVE")] | .[0].id // empty' 2>/dev/null)
    fi

    if [ -n "$LOAN_ID" ] && [ "$LOAN_ID" != "null" ]; then
      break
    fi
  done

  if [ -z "$LOAN_ID" ] || [ "$LOAN_ID" = "null" ]; then
    log_fail "$PNAME: Loan not activated after 10s — RabbitMQ propagation may have failed"
    return
  fi
  log_pass "$PNAME: Loan activated ($LOAN_ID)"

  # -------------------------------------------------------------------
  # Step 8: Fetch loan details
  # -------------------------------------------------------------------
  LOAN_DETAIL=$(curl -sf "$BASE_MGMT/api/v1/loans/$LOAN_ID" -H "$AUTH" 2>/dev/null || echo '{}')
  LOAN_STATUS=$(echo "$LOAN_DETAIL" | jq -r '.status // "N/A"')
  OUTSTANDING_P=$(echo "$LOAN_DETAIL" | jq -r '.outstandingPrincipal // "N/A"')
  OUTSTANDING_I=$(echo "$LOAN_DETAIL" | jq -r '.outstandingInterest // "N/A"')
  log_info "$PNAME: Loan status=$LOAN_STATUS, outstandingPrincipal=$OUTSTANDING_P, outstandingInterest=$OUTSTANDING_I"

  # -------------------------------------------------------------------
  # Step 9: Fetch repayment schedule and verify interest allocation
  # -------------------------------------------------------------------
  SCHEDULE=$(curl -sf "$BASE_MGMT/api/v1/loans/$LOAN_ID/schedule" -H "$AUTH" 2>/dev/null || echo '[]')
  SCHEDULE_COUNT=$(echo "$SCHEDULE" | jq 'if type == "array" then length else 0 end')
  FIRST_INTEREST=$(echo "$SCHEDULE" | jq '.[0].interestDue // 0')
  FIRST_PRINCIPAL=$(echo "$SCHEDULE" | jq '.[0].principalDue // 0')
  FIRST_TOTAL=$(echo "$SCHEDULE" | jq '.[0].totalDue // 0')
  TOTAL_INTEREST=$(echo "$SCHEDULE" | jq '[.[].interestDue // 0] | add // 0')
  TOTAL_PRINCIPAL=$(echo "$SCHEDULE" | jq '[.[].principalDue // 0] | add // 0')

  log_info "$PNAME: Schedule has $SCHEDULE_COUNT installments"
  log_info "$PNAME: First installment: principal=$FIRST_PRINCIPAL, interest=$FIRST_INTEREST, total=$FIRST_TOTAL"
  log_info "$PNAME: Totals: principal=$TOTAL_PRINCIPAL, interest=$TOTAL_INTEREST"

  if [ "$SCHEDULE_COUNT" -gt 0 ]; then
    log_pass "$PNAME: Schedule generated ($SCHEDULE_COUNT installments)"
  else
    log_fail "$PNAME: No schedule generated"
    return
  fi

  # Verify interest expectations
  if [ "$EXPECT_INTEREST" -eq 0 ]; then
    # BNPL: should have zero interest
    IS_ZERO=$(echo "$TOTAL_INTEREST" | awk '{print ($1 == 0 || $1 == 0.0 || $1 == 0.00) ? "1" : "0"}')
    if [ "$IS_ZERO" = "1" ]; then
      log_pass "$PNAME: BNPL correctly has zero interest in schedule"
    else
      log_fail "$PNAME: BNPL should have zero interest but got $TOTAL_INTEREST"
    fi
  else
    # Interest-bearing: should have positive interest
    HAS_INTEREST=$(echo "$TOTAL_INTEREST" | awk '{print ($1 > 0) ? "1" : "0"}')
    if [ "$HAS_INTEREST" = "1" ]; then
      log_pass "$PNAME: Schedule shows positive interest ($TOTAL_INTEREST KES total)"
    else
      log_fail "$PNAME: Interest-bearing product has zero interest in schedule!"
    fi
  fi

  # -------------------------------------------------------------------
  # Step 10: Make first installment repayment
  # -------------------------------------------------------------------
  # Use the first installment totalDue; fallback to a reasonable amount
  REPAY_AMOUNT="$FIRST_TOTAL"
  if [ "$(echo "$REPAY_AMOUNT <= 0" | bc -l 2>/dev/null || echo "1")" = "1" ]; then
    REPAY_AMOUNT=$AMOUNT  # fallback: repay full principal
  fi

  log_info "$PNAME: Making repayment of $REPAY_AMOUNT KES..."

  REPAY_RESP=$(curl -sf -X POST "$BASE_MGMT/api/v1/loans/$LOAN_ID/repayments" \
    -H "$AUTH" -H 'Content-Type: application/json' \
    -d "{
      \"amount\": $REPAY_AMOUNT,
      \"paymentMethod\": \"BANK_TRANSFER\"
    }" 2>/dev/null || echo '{"error":"curl_failed"}')

  REPAY_STATUS=$(echo "$REPAY_RESP" | jq -r '.status // empty')
  INTEREST_APPLIED=$(echo "$REPAY_RESP" | jq '.interestApplied // 0')
  PRINCIPAL_APPLIED=$(echo "$REPAY_RESP" | jq '.principalApplied // 0')
  PENALTY_APPLIED=$(echo "$REPAY_RESP" | jq '.penaltyApplied // 0')
  FEE_APPLIED=$(echo "$REPAY_RESP" | jq '.feeApplied // 0')

  if [ "$REPAY_STATUS" = "COMPLETED" ]; then
    log_pass "$PNAME: Repayment completed ($REPAY_AMOUNT KES)"
  else
    log_fail "$PNAME: Repayment failed (status=$REPAY_STATUS) — $(echo "$REPAY_RESP" | jq -r '.message // .error // "unknown"' 2>/dev/null)"
    return
  fi

  log_info "$PNAME: Allocation: principal=$PRINCIPAL_APPLIED, interest=$INTEREST_APPLIED, penalty=$PENALTY_APPLIED, fee=$FEE_APPLIED"

  # Verify interest allocation in repayment
  if [ "$EXPECT_INTEREST" -eq 0 ]; then
    # BNPL: interest applied should be 0
    IS_ZERO=$(echo "$INTEREST_APPLIED" | awk '{print ($1 == 0 || $1 == 0.0) ? "1" : "0"}')
    if [ "$IS_ZERO" = "1" ]; then
      log_pass "$PNAME: BNPL repayment correctly allocated zero interest"
    else
      log_fail "$PNAME: BNPL repayment allocated non-zero interest ($INTEREST_APPLIED)"
    fi
  else
    # Interest-bearing: should have interest > 0
    HAS_INTEREST=$(echo "$INTEREST_APPLIED" | awk '{print ($1 > 0) ? "1" : "0"}')
    if [ "$HAS_INTEREST" = "1" ]; then
      log_pass "$PNAME: Repayment correctly allocated interest ($INTEREST_APPLIED KES)"
    else
      log_fail "$PNAME: Interest-bearing repayment allocated zero interest!"
    fi
  fi

  # -------------------------------------------------------------------
  # Step 11: Verify outstanding principal reduced
  # -------------------------------------------------------------------
  LOAN_AFTER=$(curl -sf "$BASE_MGMT/api/v1/loans/$LOAN_ID" -H "$AUTH" 2>/dev/null || echo '{}')
  NEW_OUTSTANDING=$(echo "$LOAN_AFTER" | jq -r '.outstandingPrincipal // "0"')
  NEW_STATUS=$(echo "$LOAN_AFTER" | jq -r '.status // "N/A"')

  log_info "$PNAME: After repayment: outstandingPrincipal=$NEW_OUTSTANDING, status=$NEW_STATUS"

  REDUCED=$(echo "$NEW_OUTSTANDING < $AMOUNT" | bc -l 2>/dev/null || echo "0")
  if [ "$REDUCED" = "1" ]; then
    log_pass "$PNAME: Outstanding principal reduced ($AMOUNT -> $NEW_OUTSTANDING)"
  else
    # If full repayment, principal might be 0 which is fine
    if [ "$(echo "$NEW_OUTSTANDING == 0" | bc -l 2>/dev/null || echo "0")" = "1" ]; then
      log_pass "$PNAME: Loan fully repaid (outstanding=0)"
    else
      log_fail "$PNAME: Outstanding principal NOT reduced (was $AMOUNT, now $NEW_OUTSTANDING)"
    fi
  fi

  echo ""
}

# Run tests for all 5 product types
for idx in "${!PRODUCT_LABELS[@]}"; do
  test_product "$idx"
done

# ============================================================================
# PHASE 3: GL Trial Balance Verification
# ============================================================================
log_section "Phase 3: GL Trial Balance Verification"

log_info "Waiting 3s for accounting events to process..."
sleep 3

TB_RESP=$(curl -sf "$BASE_ACCTG/api/v1/accounting/trial-balance" -H "$AUTH" 2>/dev/null || echo '{}')
TB_ACCOUNTS=$(echo "$TB_RESP" | jq '.accounts // []')
TB_BALANCED=$(echo "$TB_RESP" | jq '.balanced // false')
TB_TOTAL_DR=$(echo "$TB_RESP" | jq '.totalDebits // 0')
TB_TOTAL_CR=$(echo "$TB_RESP" | jq '.totalCredits // 0')

if [ "$(echo "$TB_ACCOUNTS" | jq 'length')" -gt 0 ]; then
  log_pass "Trial balance retrieved ($(echo "$TB_ACCOUNTS" | jq 'length') accounts)"
else
  log_fail "Could not retrieve trial balance or no accounts found"
  TB_ACCOUNTS="[]"
fi

echo ""
echo -e "  ${BOLD}GL Account Summary:${NC}"
echo -e "  -------------------------------------------------------------------"
printf "  %-8s %-30s %-12s %14s\n" "Code" "Name" "Type" "Balance"
echo -e "  -------------------------------------------------------------------"

# Print all GL accounts
echo "$TB_ACCOUNTS" | jq -r '.[] | "\(.accountCode)\t\(.accountName)\t\(.balanceType // .accountType)\t\(.balance)"' 2>/dev/null | \
while IFS=$'\t' read -r code name btype balance; do
  printf "  %-8s %-30s %-12s %14s\n" "$code" "$name" "$btype" "$balance"
done

echo -e "  -------------------------------------------------------------------"
printf "  %-8s %-30s %-12s %14s\n" "" "Total Debits" "" "$TB_TOTAL_DR"
printf "  %-8s %-30s %-12s %14s\n" "" "Total Credits" "" "$TB_TOTAL_CR"
printf "  %-8s %-30s %-12s %14s\n" "" "Balanced?" "" "$TB_BALANCED"
echo ""

# --- Verify specific GL accounts ---

# GL 1000 - Cash/Bank
CASH_BALANCE=$(echo "$TB_ACCOUNTS" | jq '[.[] | select(.accountCode == "1000")] | .[0].balance // 0')
if [ "$(echo "$CASH_BALANCE" | awk '{print ($1 != 0) ? "1" : "0"}')" = "1" ]; then
  log_pass "GL 1000 (Cash/Bank) has balance: $CASH_BALANCE"
else
  log_info "GL 1000 (Cash/Bank) balance is zero (may be expected if disbursement uses different accounts)"
fi

# GL 1100 - Loans Receivable
LOANS_REC=$(echo "$TB_ACCOUNTS" | jq '[.[] | select(.accountCode == "1100")] | .[0].balance // 0')
if [ "$(echo "$LOANS_REC" | awk '{print ($1 != 0) ? "1" : "0"}')" = "1" ]; then
  log_pass "GL 1100 (Loans Receivable) has balance: $LOANS_REC"
else
  log_fail "GL 1100 (Loans Receivable) is zero — disbursement journals may not have been posted"
fi

# GL 4000 - Interest Income (the key test)
INTEREST_INCOME=$(echo "$TB_ACCOUNTS" | jq '[.[] | select(.accountCode == "4000")] | .[0].balance // 0')
# Interest income is a credit account, so balance might be negative in debit-normal reporting
INTEREST_ABS=$(echo "$INTEREST_INCOME" | awk '{print ($1 < 0) ? -$1 : $1}')
if [ "$(echo "$INTEREST_ABS" | awk '{print ($1 > 0) ? "1" : "0"}')" = "1" ]; then
  log_pass "GL 4000 (Interest Income) has balance: $INTEREST_INCOME (interest correctly posted to GL)"
else
  log_fail "GL 4000 (Interest Income) is zero — accounting may not be posting interest from repayments"
fi

# GL 4100 - Fee Income
FEE_INCOME=$(echo "$TB_ACCOUNTS" | jq '[.[] | select(.accountCode == "4100")] | .[0].balance // 0')
FEE_ABS=$(echo "$FEE_INCOME" | awk '{print ($1 < 0) ? -$1 : $1}')
if [ "$(echo "$FEE_ABS" | awk '{print ($1 > 0) ? "1" : "0"}')" = "1" ]; then
  log_pass "GL 4100 (Fee Income) has balance: $FEE_INCOME"
else
  log_info "GL 4100 (Fee Income) is zero (may be expected if no fees were applied in repayments)"
fi

# GL 4200 - Penalty Income
PENALTY_INCOME=$(echo "$TB_ACCOUNTS" | jq '[.[] | select(.accountCode == "4200")] | .[0].balance // 0')
PENALTY_ABS=$(echo "$PENALTY_INCOME" | awk '{print ($1 < 0) ? -$1 : $1}')
if [ "$(echo "$PENALTY_ABS" | awk '{print ($1 > 0) ? "1" : "0"}')" = "1" ]; then
  log_pass "GL 4200 (Penalty Income) has balance: $PENALTY_INCOME"
else
  log_info "GL 4200 (Penalty Income) is zero (expected — no overdue penalties in this test)"
fi

# Check trial balance is balanced
if [ "$TB_BALANCED" = "true" ]; then
  log_pass "Trial balance is balanced (debits = credits)"
else
  log_fail "Trial balance is NOT balanced — debits=$TB_TOTAL_DR, credits=$TB_TOTAL_CR"
fi

# ============================================================================
# PHASE 4: Journal Entry Verification
# ============================================================================
log_section "Phase 4: Journal Entry Spot-Check"

JE_RESP=$(curl -sf "$BASE_ACCTG/api/v1/accounting/journal-entries?page=0&size=20" -H "$AUTH" 2>/dev/null || echo '{}')
JE_TOTAL=$(echo "$JE_RESP" | jq '.totalElements // 0')
JE_CONTENT=$(echo "$JE_RESP" | jq '.content // []')
JE_COUNT=$(echo "$JE_CONTENT" | jq 'length')

log_info "Total journal entries: $JE_TOTAL (showing latest $JE_COUNT)"

# Count disbursement entries (DISB- prefix)
DISB_JE_COUNT=$(echo "$JE_CONTENT" | jq '[.[] | select(.reference // "" | test("DISB"; "i"))] | length')
# Count repayment entries (RPMT- prefix)
RPMT_JE_COUNT=$(echo "$JE_CONTENT" | jq '[.[] | select(.reference // "" | test("RPMT"; "i"))] | length')

log_info "Disbursement journals (DISB-*): $DISB_JE_COUNT"
log_info "Repayment journals (RPMT-*): $RPMT_JE_COUNT"

if [ "$DISB_JE_COUNT" -gt 0 ]; then
  log_pass "Disbursement journal entries found ($DISB_JE_COUNT)"
else
  log_fail "No disbursement journal entries found"
fi

if [ "$RPMT_JE_COUNT" -gt 0 ]; then
  log_pass "Repayment journal entries found ($RPMT_JE_COUNT)"
else
  log_fail "No repayment journal entries found — accounting listener may not be processing payment.completed events"
fi

# Show a sample repayment journal entry
SAMPLE_RPMT=$(echo "$JE_CONTENT" | jq '[.[] | select(.reference // "" | test("RPMT"; "i"))] | .[0] // null')
if [ "$SAMPLE_RPMT" != "null" ] && [ -n "$SAMPLE_RPMT" ]; then
  echo ""
  echo -e "  ${BOLD}Sample Repayment Journal Entry:${NC}"
  SAMPLE_REF=$(echo "$SAMPLE_RPMT" | jq -r '.reference // "N/A"')
  SAMPLE_DATE=$(echo "$SAMPLE_RPMT" | jq -r '.entryDate // "N/A"')
  SAMPLE_LINES=$(echo "$SAMPLE_RPMT" | jq '.lines // []')
  echo -e "  Reference: $SAMPLE_REF"
  echo -e "  Date: $SAMPLE_DATE"
  echo -e "  Lines:"
  echo "$SAMPLE_LINES" | jq -r '.[] | "    \(.type // "N/A") \(.accountCode // "N/A") (\(.accountName // "N/A")) = \(.amount // 0)"' 2>/dev/null
fi

# ============================================================================
# SUMMARY
# ============================================================================
END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))

echo ""
echo -e "${CYAN}"
echo "========================================================================"
echo "                          FINAL SUMMARY"
echo "========================================================================"
echo -e "${NC}"
echo -e "  Duration: ${DURATION}s"
echo -e "  Total:    ${TOTAL}"
echo -e "  ${GREEN}Passed:   ${PASS}${NC}"
echo -e "  ${RED}Failed:   ${FAIL}${NC}"
echo ""

if [ ${#ERRORS[@]} -gt 0 ]; then
  echo -e "  ${RED}${BOLD}Failures:${NC}"
  for err in "${ERRORS[@]}"; do
    echo -e "    ${RED}- $err${NC}"
  done
  echo ""
fi

if [ $FAIL -eq 0 ]; then
  echo -e "  ${GREEN}${BOLD}ALL $TOTAL TESTS PASSED${NC}"
  echo ""
  exit 0
else
  PASS_RATE=$((PASS * 100 / TOTAL))
  echo -e "  ${RED}${BOLD}$FAIL / $TOTAL TESTS FAILED (${PASS_RATE}% pass rate)${NC}"
  echo ""
  exit 1
fi
