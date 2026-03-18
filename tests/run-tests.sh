#!/usr/bin/env bash
# =============================================================================
# AthenaLMS E2E Test Runner
# Usage:
#   ./run-tests.sh                    # Run ALL tests with HTML report
#   ./run-tests.sh smoke              # Run smoke tests only (health + gateway)
#   ./run-tests.sh health             # Run health checks only
#   ./run-tests.sh auth               # Run auth tests only
#   ./run-tests.sh e2e                # Run E2E lifecycle tests only
#   ./run-tests.sh customers accounts # Run multiple markers
#   ./run-tests.sh -k "test_admin"    # Run tests matching pattern
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

REPORT_DIR="$SCRIPT_DIR/reports"
mkdir -p "$REPORT_DIR"

TIMESTAMP=$(date +%Y%m%d_%H%M%S)
HTML_REPORT="$REPORT_DIR/athena_e2e_report_${TIMESTAMP}.html"
JUNIT_REPORT="$REPORT_DIR/athena_e2e_junit_${TIMESTAMP}.xml"
LATEST_HTML="$REPORT_DIR/latest_report.html"

# Setup venv if needed
VENV_DIR="$SCRIPT_DIR/.venv"
if [ ! -d "$VENV_DIR" ]; then
    echo "Creating virtual environment..."
    python3 -m venv "$VENV_DIR"
fi
source "$VENV_DIR/bin/activate"

# Install deps if needed
if ! python3 -c "import pytest" 2>/dev/null; then
    echo "Installing test dependencies..."
    pip install -q -r requirements.txt
fi

# Build pytest args
PYTEST_ARGS=(
    -v
    --tb=short
    --html="$HTML_REPORT"
    --self-contained-html
    --junitxml="$JUNIT_REPORT"
)

# Handle markers or pass-through args
if [ $# -eq 0 ]; then
    echo "Running ALL tests..."
elif [[ "$1" == -* ]]; then
    # Pass-through args like -k "pattern"
    PYTEST_ARGS+=("$@")
else
    # Treat args as markers
    MARKER_EXPR=""
    for m in "$@"; do
        if [ -n "$MARKER_EXPR" ]; then
            MARKER_EXPR="$MARKER_EXPR or $m"
        else
            MARKER_EXPR="$m"
        fi
    done
    PYTEST_ARGS+=(-m "$MARKER_EXPR")
    echo "Running tests with markers: $MARKER_EXPR"
fi

echo "==========================================="
echo "  AthenaLMS E2E Test Suite"
echo "  $(date)"
echo "==========================================="
echo ""

# Run
python3 -m pytest "${PYTEST_ARGS[@]}" || true

# Symlink latest
ln -sf "$HTML_REPORT" "$LATEST_HTML"

echo ""
echo "==========================================="
echo "  Reports:"
echo "  HTML: $HTML_REPORT"
echo "  JUnit XML: $JUNIT_REPORT"
echo "  Latest: $LATEST_HTML"
echo "==========================================="
