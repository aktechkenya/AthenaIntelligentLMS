#!/usr/bin/env bash
set -uo pipefail

###############################################################################
# E2E Stress Test: 100 Customers through Full Loan Lifecycle
#
# Steps per customer:
#   1. Create customer
#   2. Create savings account
#   3. Seed account balance (100K KES)
#   4. Create loan application (random 5K-50K, 3-12 months)
#   5. Submit application
#   6. Start review
#   7. Approve (with approved amount + interest rate)
#   8. Disburse
#   9. Verify loan ACTIVE in loan-management
#  10. Make 1st installment repayment
#  11. Verify outstanding principal reduced
###############################################################################

# --- Configuration ---
ACCOUNT_SVC="http://localhost:8086"
PRODUCT_SVC="http://localhost:8087"
ORIGINATION_SVC="http://localhost:8088"
MANAGEMENT_SVC="http://localhost:8089"
TENANT="admin"
NUM_CUSTOMERS=${1:-100}

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

# Counters per step
declare -A PASS FAIL
STEPS=("customer" "account" "credit" "loan_app" "submit" "review" "approve" "disburse" "verify_active" "repayment" "verify_outstanding")
for s in "${STEPS[@]}"; do PASS[$s]=0; FAIL[$s]=0; done
TOTAL_PASS=0
TOTAL_FAIL=0
ERRORS=()
START_TIME=$(date +%s)

log_pass() { ((PASS[$1]++)); ((TOTAL_PASS++)); }
log_fail() {
    ((FAIL[$1]++)); ((TOTAL_FAIL++))
    ERRORS+=("Customer $2 step=$1: $3")
}

# --- Helper: curl with standard headers ---
api() {
    local method=$1 url=$2
    shift 2
    curl -s -X "$method" "$url" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer $TOKEN" \
        -H "X-Tenant-ID: $TENANT" \
        "$@" 2>/dev/null || echo '{"error":"curl_failed"}'
}

echo -e "${CYAN}╔══════════════════════════════════════════════════════╗${NC}"
echo -e "${CYAN}║   E2E Stress Test: $NUM_CUSTOMERS Customers × Full Loan Lifecycle   ║${NC}"
echo -e "${CYAN}╚══════════════════════════════════════════════════════╝${NC}"
echo ""

# ═══════════════════════════════════════════════════════
# PHASE 0: Authentication
# ═══════════════════════════════════════════════════════
echo -e "${YELLOW}[Phase 0] Authenticating...${NC}"
AUTH_RESP=$(curl -s -X POST "$ACCOUNT_SVC/api/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"admin123"}')
TOKEN=$(echo "$AUTH_RESP" | jq -r '.token // empty')
if [[ -z "$TOKEN" ]]; then
    echo -e "${RED}FATAL: Authentication failed${NC}"
    echo "$AUTH_RESP"
    exit 1
fi
echo -e "${GREEN}  ✓ Authenticated (token ${TOKEN:0:20}...)${NC}"

# ═══════════════════════════════════════════════════════
# PHASE 1: Create & Activate Product
# ═══════════════════════════════════════════════════════
echo -e "${YELLOW}[Phase 1] Setting up product...${NC}"

# Check if product already exists
EXISTING=$(api GET "$PRODUCT_SVC/api/v1/products?page=0&size=10")
EXISTING_COUNT=$(echo "$EXISTING" | jq -r '.totalElements // 0')

if [[ "$EXISTING_COUNT" -gt 0 ]]; then
    # Find an ACTIVE product
    PRODUCT_ID=$(echo "$EXISTING" | jq -r '[.content[] | select(.status=="ACTIVE")][0].id // empty')
    if [[ -n "$PRODUCT_ID" ]]; then
        echo -e "${GREEN}  ✓ Using existing ACTIVE product: $PRODUCT_ID${NC}"
    else
        # Activate the first one
        PRODUCT_ID=$(echo "$EXISTING" | jq -r '.content[0].id')
        api POST "$PRODUCT_SVC/api/v1/products/$PRODUCT_ID/activate" > /dev/null
        echo -e "${GREEN}  ✓ Activated existing product: $PRODUCT_ID${NC}"
    fi
else
    # Create new product
    PROD_RESP=$(api POST "$PRODUCT_SVC/api/v1/products" -d  '{
        "productCode": "E2E-PL-001",
        "name": "E2E Personal Loan",
        "productType": "PERSONAL_LOAN",
        "description": "Personal loan for E2E stress test",
        "currency": "KES",
        "minAmount": 1000,
        "maxAmount": 100000,
        "minTenorDays": 30,
        "maxTenorDays": 365,
        "nominalRate": 15.0,
        "penaltyRate": 2.0,
        "gracePeriodDays": 0,
        "scheduleType": "FLAT",
        "repaymentFrequency": "MONTHLY"
    }')
    PRODUCT_ID=$(echo "$PROD_RESP" | jq -r '.id')
    if [[ -z "$PRODUCT_ID" || "$PRODUCT_ID" == "null" ]]; then
        echo -e "${RED}FATAL: Product creation failed${NC}"
        echo "$PROD_RESP"
        exit 1
    fi
    # Activate
    api POST "$PRODUCT_SVC/api/v1/products/$PRODUCT_ID/activate" > /dev/null
    echo -e "${GREEN}  ✓ Created & activated product: $PRODUCT_ID${NC}"
fi

# ═══════════════════════════════════════════════════════
# PHASE 2: Process 100 Customers
# ═══════════════════════════════════════════════════════
echo ""
echo -e "${YELLOW}[Phase 2] Processing $NUM_CUSTOMERS customers...${NC}"
echo ""

for i in $(seq 1 "$NUM_CUSTOMERS"); do
    CUST_ID=$(printf "CUST-E2E-%03d" "$i")
    PAD_I=$(printf "%03d" "$i")

    # Random loan amount: 5000-50000 (multiples of 1000)
    LOAN_AMT=$(( (RANDOM % 46 + 5) * 1000 ))
    # Random tenor: 3-12 months
    TENOR=$(( RANDOM % 10 + 3 ))

    printf "  [%3d/%d] %-15s amt=%-6d tenor=%-2d " "$i" "$NUM_CUSTOMERS" "$CUST_ID" "$LOAN_AMT" "$TENOR"

    # --- Step 1: Create Customer ---
    RESP=$(api POST "$ACCOUNT_SVC/api/v1/customers" -d "{
        \"customerId\": \"$CUST_ID\",
        \"firstName\": \"Test\",
        \"lastName\": \"User${PAD_I}\",
        \"customerType\": \"INDIVIDUAL\",
        \"phone\": \"07001${PAD_I}00\",
        \"email\": \"test${PAD_I}@athena.test\"
    }")
    CUST_UUID=$(echo "$RESP" | jq -r '.id // empty')
    if [[ -z "$CUST_UUID" ]]; then
        log_fail customer "$CUST_ID" "$(echo "$RESP" | jq -r '.message // "unknown"')"
        echo -e "${RED}FAIL (customer)${NC}"
        continue
    fi
    log_pass customer

    # --- Step 2: Create Account ---
    RESP=$(api POST "$ACCOUNT_SVC/api/v1/accounts" -d "{
        \"customerId\": \"$CUST_ID\",
        \"accountType\": \"SAVINGS\",
        \"currency\": \"KES\"
    }")
    ACCT_ID=$(echo "$RESP" | jq -r '.id // empty')
    ACCT_NUM=$(echo "$RESP" | jq -r '.accountNumber // empty')
    if [[ -z "$ACCT_ID" || "$ACCT_ID" == "null" ]]; then
        log_fail account "$CUST_ID" "$(echo "$RESP" | jq -r '.message // "unknown"')"
        echo -e "${RED}FAIL (account)${NC}"
        continue
    fi
    log_pass account

    # --- Step 3: Credit Account (seed 100K) ---
    RESP=$(api POST "$ACCOUNT_SVC/api/v1/accounts/$ACCT_ID/credit" -d "{
        \"amount\": 100000,
        \"description\": \"E2E seed balance\",
        \"reference\": \"SEED-$PAD_I\"
    }")
    BAL=$(echo "$RESP" | jq -r '.balanceAfter // empty')
    if [[ -z "$BAL" ]]; then
        log_fail credit "$CUST_ID" "$(echo "$RESP" | jq -r '.message // "unknown"')"
        echo -e "${RED}FAIL (credit)${NC}"
        continue
    fi
    log_pass credit

    # --- Step 4: Create Loan Application ---
    RESP=$(api POST "$ORIGINATION_SVC/api/v1/loan-applications" -d "{
        \"customerId\": \"$CUST_ID\",
        \"productId\": \"$PRODUCT_ID\",
        \"requestedAmount\": $LOAN_AMT,
        \"tenorMonths\": $TENOR,
        \"purpose\": \"E2E stress test customer $PAD_I\",
        \"currency\": \"KES\"
    }")
    APP_ID=$(echo "$RESP" | jq -r '.id // empty')
    if [[ -z "$APP_ID" || "$APP_ID" == "null" ]]; then
        log_fail loan_app "$CUST_ID" "$(echo "$RESP" | jq -r '.message // "unknown"')"
        echo -e "${RED}FAIL (loan_app)${NC}"
        continue
    fi
    log_pass loan_app

    # --- Step 5: Submit ---
    RESP=$(api POST "$ORIGINATION_SVC/api/v1/loan-applications/$APP_ID/submit")
    STATUS=$(echo "$RESP" | jq -r '.status // empty')
    if [[ "$STATUS" != "SUBMITTED" ]]; then
        log_fail submit "$CUST_ID" "status=$STATUS"
        echo -e "${RED}FAIL (submit: $STATUS)${NC}"
        continue
    fi
    log_pass submit

    # --- Step 6: Start Review ---
    RESP=$(api POST "$ORIGINATION_SVC/api/v1/loan-applications/$APP_ID/review/start")
    STATUS=$(echo "$RESP" | jq -r '.status // empty')
    if [[ "$STATUS" != "UNDER_REVIEW" ]]; then
        log_fail review "$CUST_ID" "status=$STATUS"
        echo -e "${RED}FAIL (review: $STATUS)${NC}"
        continue
    fi
    log_pass review

    # --- Step 7: Approve ---
    RESP=$(api POST "$ORIGINATION_SVC/api/v1/loan-applications/$APP_ID/review/approve" -d "{
        \"approvedAmount\": $LOAN_AMT,
        \"interestRate\": 15.0
    }")
    STATUS=$(echo "$RESP" | jq -r '.status // empty')
    if [[ "$STATUS" != "APPROVED" ]]; then
        log_fail approve "$CUST_ID" "status=$STATUS"
        echo -e "${RED}FAIL (approve: $STATUS)${NC}"
        continue
    fi
    log_pass approve

    # --- Step 8: Disburse ---
    RESP=$(api POST "$ORIGINATION_SVC/api/v1/loan-applications/$APP_ID/disburse" -d "{
        \"disbursedAmount\": $LOAN_AMT,
        \"disbursementAccount\": \"$ACCT_NUM\"
    }")
    STATUS=$(echo "$RESP" | jq -r '.status // empty')
    if [[ "$STATUS" != "DISBURSED" ]]; then
        log_fail disburse "$CUST_ID" "status=$STATUS $(echo "$RESP" | jq -r '.message // ""')"
        echo -e "${RED}FAIL (disburse: $STATUS)${NC}"
        continue
    fi
    log_pass disburse

    # --- Step 9: Verify Loan ACTIVE (with retry for RabbitMQ propagation) ---
    LOAN_ID=""
    for attempt in 1 2 3; do
        sleep 1
        LOANS_RESP=$(api GET "$MANAGEMENT_SVC/api/v1/loans?page=0&size=200")
        # Find this customer's loan
        LOAN_ID=$(echo "$LOANS_RESP" | jq -r "[.content[] | select(.customerId==\"$CUST_ID\" and .status==\"ACTIVE\")][0].id // empty")
        if [[ -n "$LOAN_ID" ]]; then break; fi
    done
    if [[ -z "$LOAN_ID" ]]; then
        log_fail verify_active "$CUST_ID" "loan not found ACTIVE after 3s"
        echo -e "${RED}FAIL (verify_active)${NC}"
        continue
    fi
    log_pass verify_active

    # --- Step 10: Get schedule + make 1st repayment ---
    SCHED=$(api GET "$MANAGEMENT_SVC/api/v1/loans/$LOAN_ID/schedule")
    FIRST_DUE=$(echo "$SCHED" | jq -r '.[0].totalDue // empty')
    if [[ -z "$FIRST_DUE" || "$FIRST_DUE" == "null" ]]; then
        log_fail repayment "$CUST_ID" "no schedule found"
        echo -e "${RED}FAIL (no schedule)${NC}"
        continue
    fi

    RESP=$(api POST "$MANAGEMENT_SVC/api/v1/loans/$LOAN_ID/repayments" -d "{
        \"amount\": $FIRST_DUE,
        \"paymentMethod\": \"BANK_TRANSFER\"
    }")
    REP_STATUS=$(echo "$RESP" | jq -r '.status // empty')
    if [[ "$REP_STATUS" != "COMPLETED" ]]; then
        log_fail repayment "$CUST_ID" "repayment status=$REP_STATUS $(echo "$RESP" | jq -r '.message // ""')"
        echo -e "${RED}FAIL (repayment: $REP_STATUS)${NC}"
        continue
    fi
    log_pass repayment

    # --- Step 11: Verify outstanding reduced ---
    LOAN_AFTER=$(api GET "$MANAGEMENT_SVC/api/v1/loans/$LOAN_ID")
    OUTSTANDING=$(echo "$LOAN_AFTER" | jq -r '.outstandingPrincipal // empty')
    DISBURSED=$(echo "$LOAN_AFTER" | jq -r '.disbursedAmount // empty')
    if [[ -z "$OUTSTANDING" ]]; then
        log_fail verify_outstanding "$CUST_ID" "could not read outstanding"
        echo -e "${RED}FAIL (outstanding)${NC}"
        continue
    fi
    # Compare: outstanding should be < disbursed
    REDUCED=$(echo "$OUTSTANDING < $DISBURSED" | bc -l 2>/dev/null || echo "0")
    if [[ "$REDUCED" != "1" ]]; then
        log_fail verify_outstanding "$CUST_ID" "outstanding=$OUTSTANDING not < disbursed=$DISBURSED"
        echo -e "${RED}FAIL (outstanding not reduced)${NC}"
        continue
    fi
    log_pass verify_outstanding

    echo -e "${GREEN}PASS${NC} (outstanding: ${DISBURSED} → ${OUTSTANDING})"
done

# ═══════════════════════════════════════════════════════
# PHASE 3: Summary
# ═══════════════════════════════════════════════════════
END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))

echo ""
echo -e "${CYAN}╔══════════════════════════════════════════════════════╗${NC}"
echo -e "${CYAN}║                    RESULTS SUMMARY                   ║${NC}"
echo -e "${CYAN}╚══════════════════════════════════════════════════════╝${NC}"
echo ""
echo -e "  Duration: ${DURATION}s"
echo ""

printf "  %-20s %5s %5s %5s\n" "Step" "Pass" "Fail" "Total"
printf "  %-20s %5s %5s %5s\n" "────────────────────" "─────" "─────" "─────"
for s in "${STEPS[@]}"; do
    TOTAL=$((PASS[$s] + FAIL[$s]))
    if [[ ${FAIL[$s]} -gt 0 ]]; then
        COLOR=$RED
    else
        COLOR=$GREEN
    fi
    printf "  ${COLOR}%-20s %5d %5d %5d${NC}\n" "$s" "${PASS[$s]}" "${FAIL[$s]}" "$TOTAL"
done
echo ""
printf "  %-20s %5d %5d %5d\n" "TOTAL" "$TOTAL_PASS" "$TOTAL_FAIL" "$((TOTAL_PASS + TOTAL_FAIL))"
echo ""

if [[ ${#ERRORS[@]} -gt 0 ]]; then
    echo -e "${RED}  ERRORS:${NC}"
    for err in "${ERRORS[@]}"; do
        echo -e "    ${RED}• $err${NC}"
    done
    echo ""
fi

# Final assertions
echo -e "${YELLOW}[Phase 3] Final Assertions...${NC}"

CUST_COUNT=$(api GET "$ACCOUNT_SVC/api/v1/customers?page=0&size=1" | jq -r '.totalElements // 0')
ACCT_COUNT=$(api GET "$ACCOUNT_SVC/api/v1/accounts?page=0&size=1" | jq -r '.totalElements // 0')
LOAN_COUNT=$(api GET "$MANAGEMENT_SVC/api/v1/loans?page=0&size=1" | jq -r '.totalElements // 0')

echo -e "  Customers:   $CUST_COUNT (expected ≥ $NUM_CUSTOMERS)"
echo -e "  Accounts:    $ACCT_COUNT (expected ≥ $NUM_CUSTOMERS)"
echo -e "  Active Loans: $LOAN_COUNT (expected ≥ $NUM_CUSTOMERS)"
echo ""

if [[ $TOTAL_FAIL -eq 0 ]]; then
    echo -e "${GREEN}  ████████████████████████████████████████████████${NC}"
    echo -e "${GREEN}  █         ALL $NUM_CUSTOMERS/$NUM_CUSTOMERS CUSTOMERS PASSED            █${NC}"
    echo -e "${GREEN}  ████████████████████████████████████████████████${NC}"
else
    PASS_RATE=$((TOTAL_PASS * 100 / (TOTAL_PASS + TOTAL_FAIL)))
    echo -e "${RED}  PASS RATE: ${PASS_RATE}% ($TOTAL_PASS pass, $TOTAL_FAIL fail)${NC}"
fi
echo ""
