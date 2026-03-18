#!/usr/bin/env bash
# =============================================================================
# AthenaLMS Deploy & Test
#
# Usage:
#   ./scripts/deploy.sh                 # Full deploy: build + start + wait + test
#   ./scripts/deploy.sh --no-build      # Start (no rebuild) + wait + test
#   ./scripts/deploy.sh --test-only     # Skip deploy, just wait + test
#   ./scripts/deploy.sh --build-only    # Build + start, skip tests
#   ./scripts/deploy.sh --smoke         # Deploy + run smoke tests only
#   ./scripts/deploy.sh --service account-service  # Rebuild one service
#
# Environment:
#   LMS_BASE=http://localhost           # Override service host
#   COMPOSE_BASE=../AthenaCreditScore   # Override base compose path
#   WAIT_TIMEOUT=300                    # Max seconds to wait for health (default 300)
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
COMPOSE_BASE="${COMPOSE_BASE:-$PROJECT_DIR/../AthenaCreditScore}"
WAIT_TIMEOUT="${WAIT_TIMEOUT:-300}"
LMS_BASE="${LMS_BASE:-http://localhost}"

# Flags
DO_BUILD=true
DO_TEST=true
TEST_MARKER=""
SINGLE_SERVICE=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        --no-build)    DO_BUILD=false; shift ;;
        --test-only)   DO_BUILD=false; shift ;;
        --build-only)  DO_TEST=false; shift ;;
        --smoke)       TEST_MARKER="smoke"; shift ;;
        --e2e)         TEST_MARKER="e2e"; shift ;;
        --service)     SINGLE_SERVICE="$2"; shift 2 ;;
        -m)            TEST_MARKER="$2"; shift 2 ;;
        *)             echo "Unknown option: $1"; exit 1 ;;
    esac
done

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

log()  { echo -e "${CYAN}[deploy]${NC} $*"; }
ok()   { echo -e "${GREEN}[  OK  ]${NC} $*"; }
warn() { echo -e "${YELLOW}[ WARN ]${NC} $*"; }
fail() { echo -e "${RED}[ FAIL ]${NC} $*"; }

# ─── Service health map ──────────────────────────────────────────────────────
declare -A SERVICES=(
    [account-service]=18086
    [product-service]=18087
    [loan-origination-service]=18088
    [loan-management-service]=18089
    [payment-service]=18090
    [accounting-service]=18091
    [float-service]=18092
    [collections-service]=18093
    [compliance-service]=18094
    [reporting-service]=18095
    [ai-scoring-service]=18096
    [overdraft-service]=18097
    [media-service]=18098
    [notification-service]=18099
    [lms-api-gateway]=18105
)

# ─── Step 1: Build & Start ───────────────────────────────────────────────────
if [ "$DO_BUILD" = true ]; then
    echo ""
    echo "==========================================="
    echo "  AthenaLMS Deploy"
    echo "  $(date)"
    echo "==========================================="
    echo ""

    cd "$COMPOSE_BASE"

    if [ -n "$SINGLE_SERVICE" ]; then
        log "Rebuilding $SINGLE_SERVICE..."
        docker compose \
            -f docker-compose.yml \
            -f "$PROJECT_DIR/docker-compose.lms.yml" \
            up -d --build --no-deps "lms-${SINGLE_SERVICE}"
    else
        log "Starting full stack (build + detach)..."
        docker compose \
            -f docker-compose.yml \
            -f "$PROJECT_DIR/docker-compose.lms.yml" \
            up -d --build
    fi

    ok "Docker compose started"
    cd "$PROJECT_DIR"
fi

# ─── Step 2: Wait for all services healthy ────────────────────────────────────
echo ""
log "Waiting for all 15 services to become healthy (timeout: ${WAIT_TIMEOUT}s)..."
echo ""

STARTED=$(date +%s)
ALL_UP=false

while true; do
    ELAPSED=$(( $(date +%s) - STARTED ))
    if [ "$ELAPSED" -ge "$WAIT_TIMEOUT" ]; then
        break
    fi

    UP_COUNT=0
    DOWN_LIST=()

    for svc in "${!SERVICES[@]}"; do
        port="${SERVICES[$svc]}"
        HTTP_CODE=$(curl -sf -o /dev/null -w "%{http_code}" \
            "${LMS_BASE}:${port}/actuator/health" 2>/dev/null || echo "000")
        if [ "$HTTP_CODE" = "200" ]; then
            UP_COUNT=$((UP_COUNT + 1))
        else
            DOWN_LIST+=("$svc:$port")
        fi
    done

    if [ "$UP_COUNT" -eq "${#SERVICES[@]}" ]; then
        ALL_UP=true
        break
    fi

    printf "\r  [%3ds / %ds] %d/%d services UP — waiting for: %s" \
        "$ELAPSED" "$WAIT_TIMEOUT" "$UP_COUNT" "${#SERVICES[@]}" "${DOWN_LIST[*]:0:3}"
    sleep 5
done

echo ""
if [ "$ALL_UP" = true ]; then
    ELAPSED=$(( $(date +%s) - STARTED ))
    ok "All ${#SERVICES[@]} services healthy in ${ELAPSED}s"
else
    fail "Timed out after ${WAIT_TIMEOUT}s. Down services:"
    for svc in "${DOWN_LIST[@]}"; do
        fail "  - $svc"
    done
    if [ "$DO_TEST" = true ]; then
        warn "Running tests anyway (some may fail)..."
    else
        exit 1
    fi
fi

# ─── Step 3: Run tests ───────────────────────────────────────────────────────
if [ "$DO_TEST" = true ]; then
    echo ""
    log "Running E2E test suite..."
    echo ""

    cd "$PROJECT_DIR/tests"

    if [ -n "$TEST_MARKER" ]; then
        exec bash run-tests.sh "$TEST_MARKER"
    else
        exec bash run-tests.sh
    fi
fi

echo ""
ok "Deploy complete."
