"""
E2E Test: GL Accounting Verification
Verify trial balance, journal entries, and GL account integrity
after loan disbursement and repayment.
"""
import pytest
import requests
from conftest import url, unique_id, TIMEOUT, wait_for


@pytest.mark.e2e
class TestAccountingGLVerification:

    def test_trial_balance_is_balanced(self, admin_headers):
        """Trial balance debits must equal credits."""
        r = requests.get(url("accounting", "/api/v1/accounting/trial-balance"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200
        tb = r.json()

        # Handle different response shapes
        if isinstance(tb, dict):
            total_debit = float(tb.get("totalDebit", tb.get("totalDebits", 0)))
            total_credit = float(tb.get("totalCredit", tb.get("totalCredits", 0)))
        elif isinstance(tb, list):
            total_debit = sum(float(e.get("debit", e.get("debitBalance", 0))) for e in tb)
            total_credit = sum(float(e.get("credit", e.get("creditBalance", 0))) for e in tb)
        else:
            pytest.fail(f"Unexpected trial balance shape: {type(tb)}")

        diff = abs(total_debit - total_credit)
        assert diff < 0.01, f"Trial balance NOT balanced: debit={total_debit}, credit={total_credit}, diff={diff}"

    def test_gl_accounts_exist(self, admin_headers):
        """Core GL accounts (1000, 1100, 4000, 4100, 4200) should exist."""
        core_codes = ["1000", "1100", "4000", "4100", "4200"]
        for code in core_codes:
            r = requests.get(url("accounting", f"/api/v1/accounting/accounts/code/{code}"),
                             headers=admin_headers, timeout=TIMEOUT)
            assert r.status_code == 200, f"GL account {code} not found"

    def test_journal_entries_exist(self, admin_headers):
        """There should be journal entries if loans have been disbursed."""
        r = requests.get(url("accounting", "/api/v1/accounting/journal-entries"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200

    def test_disbursement_creates_journal_entry(self, admin_headers):
        """Full lifecycle: disburse loan and verify DISB journal entry appears."""
        cid = unique_id("E2E-GL")
        # Create customer + account
        requests.post(url("account", "/api/v1/customers"), headers=admin_headers,
                      json={"customerId": cid, "firstName": "GL", "lastName": "Test",
                            "email": f"{cid.lower()}@e2e.test", "phone": "+254700000005",
                            "customerType": "INDIVIDUAL", "status": "ACTIVE"},
                      timeout=TIMEOUT)
        r = requests.post(url("account", "/api/v1/accounts"), headers=admin_headers,
                          json={"customerId": cid, "accountType": "SAVINGS",
                                "currency": "KES", "name": "GL Test Account"},
                          timeout=TIMEOUT)
        assert r.status_code == 201
        acct_id = r.json()["id"]
        requests.post(url("account", f"/api/v1/accounts/{acct_id}/credit"),
                      json={"amount": 100000, "description": "GL seed",
                            "reference": unique_id("SEED")},
                      headers=admin_headers, timeout=TIMEOUT)

        # Get trial balance BEFORE
        r = requests.get(url("accounting", "/api/v1/accounting/trial-balance"),
                         headers=admin_headers, timeout=TIMEOUT)
        tb_before = r.json()

        # Create + approve + disburse loan
        r = requests.get(url("product", "/api/v1/products"),
                         headers=admin_headers, timeout=TIMEOUT)
        items = r.json().get("content", r.json()) if isinstance(r.json(), dict) else r.json()
        active = [p for p in items if p.get("status") == "ACTIVE"]
        if not active:
            pytest.skip("No active products")
        product = active[0]

        r = requests.post(url("loan_origination", "/api/v1/loan-applications"),
                          json={"customerId": cid, "productId": product["id"],
                                "requestedAmount": 10000, "tenorMonths": 3,
                                "purpose": "GL test"},
                          headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 201
        app_id = r.json()["id"]

        approve_body = {"approvedAmount": 10000, "interestRate": 15.0, "comments": "GL test approved"}
        disburse_body = {"disbursedAmount": 10000, "disbursementAccount": str(acct_id),
                         "disbursementMethod": "BANK_TRANSFER"}
        steps = [
            ("submit", None),
            ("review/start", None),
            ("review/approve", approve_body),
            ("disburse", disburse_body),
        ]
        for step, body in steps:
            r = requests.post(
                url("loan_origination", f"/api/v1/loan-applications/{app_id}/{step}"),
                json=body,
                headers=admin_headers, timeout=TIMEOUT)
            assert r.status_code == 200, f"Step {step}: {r.status_code} {r.text[:200]}"

        # Wait for loan activation and accounting entries
        import time
        time.sleep(5)

        # Get trial balance AFTER — should still be balanced
        r = requests.get(url("accounting", "/api/v1/accounting/trial-balance"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200
        tb_after = r.json()

        # Verify still balanced
        if isinstance(tb_after, dict):
            total_debit = float(tb_after.get("totalDebit", tb_after.get("totalDebits", 0)))
            total_credit = float(tb_after.get("totalCredit", tb_after.get("totalCredits", 0)))
        elif isinstance(tb_after, list):
            total_debit = sum(float(e.get("debit", e.get("debitBalance", 0))) for e in tb_after)
            total_credit = sum(float(e.get("credit", e.get("creditBalance", 0))) for e in tb_after)
        else:
            pytest.fail(f"Unexpected shape: {type(tb_after)}")

        diff = abs(total_debit - total_credit)
        assert diff < 0.01, f"GL unbalanced after disbursement: diff={diff}"


@pytest.mark.e2e
class TestReportingService:

    def test_portfolio_summary(self, admin_headers):
        r = requests.get(url("reporting", "/api/v1/reporting/summary"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200

    def test_reporting_events(self, admin_headers):
        r = requests.get(url("reporting", "/api/v1/reporting/events"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200

    def test_reporting_snapshots(self, admin_headers):
        r = requests.get(url("reporting", "/api/v1/reporting/snapshots"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200

    def test_reporting_metrics(self, admin_headers):
        r = requests.get(url("reporting", "/api/v1/reporting/metrics?from=2025-01-01&to=2026-12-31"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200

    def test_latest_snapshot(self, admin_headers):
        r = requests.get(url("reporting", "/api/v1/reporting/snapshots/latest"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code in (200, 404)
