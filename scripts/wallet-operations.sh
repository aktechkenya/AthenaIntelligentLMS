#!/usr/bin/env bash
set -uo pipefail

###############################################################################
# Wallet Operations: Create Wallets, Repay Loans via Wallet, Activate Overdraft
#
# Operates on existing CUST-E2E-{001..100} customers with active loans.
#
# Phases:
#   0. Authenticate
#   1. Create wallets (100 customers)
#   2. Fund wallets + repay 2nd loan installment via wallet (100 customers)
#   3. Activate overdraft for every 4th customer (25 customers)
#   4. Summary report
###############################################################################

# --- Configuration ---
ACCOUNT_SVC="http://localhost:8086"
MANAGEMENT_SVC="http://localhost:8089"
OVERDRAFT_SVC="http://localhost:8097"
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
STEPS=("wallet_create" "find_loan" "get_schedule" "deposit" "withdraw" "repayment" "verify_outstanding" "overdraft")
for s in "${STEPS[@]}"; do PASS[$s]=0; FAIL[$s]=0; done
TOTAL_PASS=0
TOTAL_FAIL=0
ERRORS=()
START_TIME=$(date +%s)

# Aggregation trackers
TOTAL_DEPOSITED=0
TOTAL_WITHDRAWN=0
TOTAL_REPAID=0
OVERDRAFT_COUNT=0
declare -A OVERDRAFT_BY_BAND
for b in A B C D; do OVERDRAFT_BY_BAND[$b]=0; done

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

echo -e "${CYAN}+======================================================+${NC}"
echo -e "${CYAN}|   Wallet Operations: $NUM_CUSTOMERS Customers                      |${NC}"
echo -e "${CYAN}|   Wallets + Repayments + Overdraft                   |${NC}"
echo -e "${CYAN}+======================================================+${NC}"
echo ""

# =============================================================================
# PHASE 0: Authentication
# =============================================================================
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
echo -e "${GREEN}  OK Authenticated (token ${TOKEN:0:20}...)${NC}"

# =============================================================================
# PHASE 1: Create Wallets
# =============================================================================
echo ""
echo -e "${YELLOW}[Phase 1] Creating wallets for $NUM_CUSTOMERS customers...${NC}"
echo ""

declare -A WALLET_IDS

for i in $(seq 1 "$NUM_CUSTOMERS"); do
    CUST_ID=$(printf "CUST-E2E-%03d" "$i")
    printf "  [%3d/%d] %-15s wallet " "$i" "$NUM_CUSTOMERS" "$CUST_ID"

    # Check if wallet already exists (idempotent)
    EXISTING=$(api GET "$OVERDRAFT_SVC/api/v1/wallets/customer/$CUST_ID")
    EXISTING_ID=$(echo "$EXISTING" | jq -r '.id // empty')

    if [[ -n "$EXISTING_ID" && "$EXISTING_ID" != "null" ]]; then
        WALLET_IDS[$CUST_ID]="$EXISTING_ID"
        log_pass wallet_create
        echo -e "${GREEN}EXISTS${NC} ($EXISTING_ID)"
        continue
    fi

    # Create wallet
    RESP=$(api POST "$OVERDRAFT_SVC/api/v1/wallets" -d "{\"customerId\": \"$CUST_ID\"}")
    WALLET_ID=$(echo "$RESP" | jq -r '.id // empty')
    if [[ -z "$WALLET_ID" || "$WALLET_ID" == "null" ]]; then
        log_fail wallet_create "$CUST_ID" "$(echo "$RESP" | jq -r '.message // "unknown"')"
        echo -e "${RED}FAIL${NC}"
        continue
    fi
    WALLET_IDS[$CUST_ID]="$WALLET_ID"
    log_pass wallet_create
    echo -e "${GREEN}OK${NC} ($WALLET_ID)"
done

# =============================================================================
# PHASE 2: Fund Wallets + Repay 2nd Installment
# =============================================================================
echo ""
echo -e "${YELLOW}[Phase 2] Fund wallets + repay 2nd installment...${NC}"
echo ""

for i in $(seq 1 "$NUM_CUSTOMERS"); do
    CUST_ID=$(printf "CUST-E2E-%03d" "$i")
    WALLET_ID="${WALLET_IDS[$CUST_ID]:-}"

    if [[ -z "$WALLET_ID" ]]; then
        printf "  [%3d/%d] %-15s " "$i" "$NUM_CUSTOMERS" "$CUST_ID"
        echo -e "${RED}SKIP (no wallet)${NC}"
        continue
    fi

    printf "  [%3d/%d] %-15s " "$i" "$NUM_CUSTOMERS" "$CUST_ID"

    # --- Find active loan ---
    LOANS_RESP=$(api GET "$MANAGEMENT_SVC/api/v1/loans/customer/$CUST_ID")
    LOAN_ID=$(echo "$LOANS_RESP" | jq -r '[.[] | select(.status=="ACTIVE")][0].id // empty')
    if [[ -z "$LOAN_ID" || "$LOAN_ID" == "null" ]]; then
        log_fail find_loan "$CUST_ID" "no ACTIVE loan found"
        echo -e "${RED}FAIL (no active loan)${NC}"
        continue
    fi
    log_pass find_loan

    # --- Get schedule, find first PENDING installment ---
    SCHED=$(api GET "$MANAGEMENT_SVC/api/v1/loans/$LOAN_ID/schedule")
    # First PENDING installment (installment 1 is already PAID from e2e script)
    INST_JSON=$(echo "$SCHED" | jq '[.[] | select(.status=="PENDING")][0] // empty')
    if [[ -z "$INST_JSON" || "$INST_JSON" == "null" ]]; then
        log_fail get_schedule "$CUST_ID" "no PENDING installment"
        echo -e "${RED}FAIL (no pending installment)${NC}"
        continue
    fi
    INST_NO=$(echo "$INST_JSON" | jq -r '.installmentNo')
    INST_BALANCE=$(echo "$INST_JSON" | jq -r '.balance')
    log_pass get_schedule

    # --- Check wallet balance to avoid double-deposit on re-run ---
    WALLET_RESP=$(api GET "$OVERDRAFT_SVC/api/v1/wallets/$WALLET_ID")
    CUR_BALANCE=$(echo "$WALLET_RESP" | jq -r '.currentBalance // "0"')
    NEEDS_DEPOSIT=$(echo "$CUR_BALANCE < $INST_BALANCE" | bc -l 2>/dev/null || echo "1")

    if [[ "$NEEDS_DEPOSIT" == "1" ]]; then
        # Deposit exactly the installment amount
        DEP_REF="DEP-${CUST_ID}-INST-${INST_NO}"
        DEP_RESP=$(api POST "$OVERDRAFT_SVC/api/v1/wallets/$WALLET_ID/deposit" -d "{
            \"amount\": $INST_BALANCE,
            \"reference\": \"$DEP_REF\",
            \"description\": \"Fund for installment $INST_NO\"
        }")
        DEP_AFTER=$(echo "$DEP_RESP" | jq -r '.balanceAfter // empty')
        if [[ -z "$DEP_AFTER" ]]; then
            log_fail deposit "$CUST_ID" "$(echo "$DEP_RESP" | jq -r '.message // "unknown"')"
            echo -e "${RED}FAIL (deposit)${NC}"
            continue
        fi
        TOTAL_DEPOSITED=$(echo "$TOTAL_DEPOSITED + $INST_BALANCE" | bc -l)
    else
        DEP_AFTER="$CUR_BALANCE"
    fi
    log_pass deposit

    # --- Withdraw from wallet (loan repayment reference) ---
    WD_REF="RPMT-${CUST_ID}-INST-${INST_NO}"
    WD_RESP=$(api POST "$OVERDRAFT_SVC/api/v1/wallets/$WALLET_ID/withdraw" -d "{
        \"amount\": $INST_BALANCE,
        \"reference\": \"$WD_REF\",
        \"description\": \"Loan repayment installment $INST_NO\"
    }")
    WD_AFTER=$(echo "$WD_RESP" | jq -r '.balanceAfter // empty')
    if [[ -z "$WD_AFTER" ]]; then
        log_fail withdraw "$CUST_ID" "$(echo "$WD_RESP" | jq -r '.message // "unknown"')"
        echo -e "${RED}FAIL (withdraw)${NC}"
        continue
    fi
    TOTAL_WITHDRAWN=$(echo "$TOTAL_WITHDRAWN + $INST_BALANCE" | bc -l)
    log_pass withdraw

    # --- Get outstanding BEFORE repayment ---
    LOAN_BEFORE=$(api GET "$MANAGEMENT_SVC/api/v1/loans/$LOAN_ID")
    OUTSTANDING_BEFORE=$(echo "$LOAN_BEFORE" | jq -r '.outstandingPrincipal // empty')

    # --- Apply repayment to loan ---
    REP_RESP=$(api POST "$MANAGEMENT_SVC/api/v1/loans/$LOAN_ID/repayments" -d "{
        \"amount\": $INST_BALANCE,
        \"paymentMethod\": \"WALLET\",
        \"paymentReference\": \"$WD_REF\"
    }")
    REP_STATUS=$(echo "$REP_RESP" | jq -r '.status // empty')
    if [[ "$REP_STATUS" != "COMPLETED" ]]; then
        log_fail repayment "$CUST_ID" "status=$REP_STATUS $(echo "$REP_RESP" | jq -r '.message // ""')"
        echo -e "${RED}FAIL (repayment: $REP_STATUS)${NC}"
        continue
    fi
    PRINCIPAL_APPLIED=$(echo "$REP_RESP" | jq -r '.principalApplied // "0"')
    TOTAL_REPAID=$(echo "$TOTAL_REPAID + $INST_BALANCE" | bc -l)
    log_pass repayment

    # --- Verify outstanding principal decreased ---
    LOAN_AFTER=$(api GET "$MANAGEMENT_SVC/api/v1/loans/$LOAN_ID")
    OUTSTANDING_AFTER=$(echo "$LOAN_AFTER" | jq -r '.outstandingPrincipal // empty')
    if [[ -z "$OUTSTANDING_AFTER" ]]; then
        log_fail verify_outstanding "$CUST_ID" "could not read outstanding"
        echo -e "${RED}FAIL (outstanding)${NC}"
        continue
    fi
    REDUCED=$(echo "$OUTSTANDING_AFTER < $OUTSTANDING_BEFORE" | bc -l 2>/dev/null || echo "0")
    if [[ "$REDUCED" != "1" ]]; then
        log_fail verify_outstanding "$CUST_ID" "outstanding=$OUTSTANDING_AFTER not < $OUTSTANDING_BEFORE"
        echo -e "${RED}FAIL (not reduced)${NC}"
        continue
    fi
    log_pass verify_outstanding

    echo -e "${GREEN}PASS${NC} inst#${INST_NO} amt=${INST_BALANCE} principal ${OUTSTANDING_BEFORE} -> ${OUTSTANDING_AFTER}"
done

# =============================================================================
# PHASE 3: Activate Overdraft (every 4th customer = 25)
# =============================================================================
echo ""
echo -e "${YELLOW}[Phase 3] Activating overdraft for every 4th customer...${NC}"
echo ""

for i in $(seq 1 "$NUM_CUSTOMERS"); do
    # Every 4th customer (4, 8, 12, ..., 100)
    if (( i % 4 != 0 )); then continue; fi

    CUST_ID=$(printf "CUST-E2E-%03d" "$i")
    WALLET_ID="${WALLET_IDS[$CUST_ID]:-}"

    if [[ -z "$WALLET_ID" ]]; then
        printf "  [%3d] %-15s " "$i" "$CUST_ID"
        echo -e "${RED}SKIP (no wallet)${NC}"
        continue
    fi

    printf "  [%3d] %-15s overdraft " "$i" "$CUST_ID"

    # Check if overdraft already exists
    OD_EXISTING=$(api GET "$OVERDRAFT_SVC/api/v1/wallets/$WALLET_ID/overdraft")
    OD_EXISTING_ID=$(echo "$OD_EXISTING" | jq -r '.id // empty')

    if [[ -n "$OD_EXISTING_ID" && "$OD_EXISTING_ID" != "null" ]]; then
        BAND=$(echo "$OD_EXISTING" | jq -r '.creditBand')
        LIMIT=$(echo "$OD_EXISTING" | jq -r '.approvedLimit')
        STATUS=$(echo "$OD_EXISTING" | jq -r '.status')
        ((OVERDRAFT_COUNT++))
        if [[ -n "$BAND" && "$BAND" != "null" ]]; then
            ((OVERDRAFT_BY_BAND[$BAND]++))
        fi
        log_pass overdraft
        echo -e "${GREEN}EXISTS${NC} band=$BAND limit=$LIMIT status=$STATUS"
        continue
    fi

    # Apply for overdraft (no body needed)
    OD_RESP=$(api POST "$OVERDRAFT_SVC/api/v1/wallets/$WALLET_ID/overdraft/apply")
    OD_STATUS=$(echo "$OD_RESP" | jq -r '.status // empty')
    if [[ "$OD_STATUS" != "ACTIVE" ]]; then
        log_fail overdraft "$CUST_ID" "status=$OD_STATUS $(echo "$OD_RESP" | jq -r '.message // ""')"
        echo -e "${RED}FAIL ($OD_STATUS)${NC}"
        continue
    fi
    BAND=$(echo "$OD_RESP" | jq -r '.creditBand')
    LIMIT=$(echo "$OD_RESP" | jq -r '.approvedLimit')
    ((OVERDRAFT_COUNT++))
    if [[ -n "$BAND" && "$BAND" != "null" ]]; then
        ((OVERDRAFT_BY_BAND[$BAND]++))
    fi
    log_pass overdraft
    echo -e "${GREEN}OK${NC} band=$BAND limit=$LIMIT"
done

# =============================================================================
# PHASE 4: Summary Report
# =============================================================================
END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))

echo ""
echo -e "${CYAN}+======================================================+${NC}"
echo -e "${CYAN}|                    RESULTS SUMMARY                    |${NC}"
echo -e "${CYAN}+======================================================+${NC}"
echo ""
echo -e "  Duration: ${DURATION}s"
echo ""

printf "  %-22s %5s %5s %5s\n" "Step" "Pass" "Fail" "Total"
printf "  %-22s %5s %5s %5s\n" "----------------------" "-----" "-----" "-----"
for s in "${STEPS[@]}"; do
    TOTAL=$((PASS[$s] + FAIL[$s]))
    if [[ ${FAIL[$s]} -gt 0 ]]; then
        COLOR=$RED
    else
        COLOR=$GREEN
    fi
    printf "  ${COLOR}%-22s %5d %5d %5d${NC}\n" "$s" "${PASS[$s]}" "${FAIL[$s]}" "$TOTAL"
done
echo ""
printf "  %-22s %5d %5d %5d\n" "TOTAL" "$TOTAL_PASS" "$TOTAL_FAIL" "$((TOTAL_PASS + TOTAL_FAIL))"
echo ""

# --- Financial summary ---
echo -e "${CYAN}  Financial Summary:${NC}"
printf "    Total deposited:  %12.2f KES\n" "$TOTAL_DEPOSITED"
printf "    Total withdrawn:  %12.2f KES\n" "$TOTAL_WITHDRAWN"
printf "    Total repaid:     %12.2f KES\n" "$TOTAL_REPAID"
echo ""

# --- Overdraft summary ---
echo -e "${CYAN}  Overdraft Summary:${NC}"
echo "    Facilities activated: $OVERDRAFT_COUNT"
for b in A B C D; do
    if [[ ${OVERDRAFT_BY_BAND[$b]} -gt 0 ]]; then
        echo "    Band $b: ${OVERDRAFT_BY_BAND[$b]}"
    fi
done
echo ""

# --- Fetch server-side overdraft summary ---
OD_SUMMARY=$(api GET "$OVERDRAFT_SVC/api/v1/overdraft/summary")
OD_TOTAL=$(echo "$OD_SUMMARY" | jq -r '.totalFacilities // "N/A"')
OD_ACTIVE=$(echo "$OD_SUMMARY" | jq -r '.activeFacilities // "N/A"')
OD_LIMIT=$(echo "$OD_SUMMARY" | jq -r '.totalApprovedLimit // "N/A"')
OD_DRAWN=$(echo "$OD_SUMMARY" | jq -r '.totalDrawnAmount // "N/A"')
echo -e "${CYAN}  Server Overdraft Aggregate:${NC}"
echo "    Total facilities:     $OD_TOTAL"
echo "    Active facilities:    $OD_ACTIVE"
echo "    Total approved limit: $OD_LIMIT KES"
echo "    Total drawn:          $OD_DRAWN KES"
echo ""

if [[ ${#ERRORS[@]} -gt 0 ]]; then
    echo -e "${RED}  ERRORS:${NC}"
    for err in "${ERRORS[@]}"; do
        echo -e "    ${RED}* $err${NC}"
    done
    echo ""
fi

# --- Final verdict ---
if [[ $TOTAL_FAIL -eq 0 ]]; then
    echo -e "${GREEN}  ################################################${NC}"
    echo -e "${GREEN}  #   ALL OPERATIONS PASSED                       #${NC}"
    echo -e "${GREEN}  #   ${PASS[wallet_create]} wallets | ${PASS[repayment]} repayments | $OVERDRAFT_COUNT overdrafts   #${NC}"
    echo -e "${GREEN}  ################################################${NC}"
else
    PASS_RATE=$((TOTAL_PASS * 100 / (TOTAL_PASS + TOTAL_FAIL)))
    echo -e "${RED}  PASS RATE: ${PASS_RATE}% ($TOTAL_PASS pass, $TOTAL_FAIL fail)${NC}"
fi
echo ""
