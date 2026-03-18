"""Accounting service tests — GL accounts, journal entries, trial balance."""
import pytest
import requests
from conftest import url, unique_id, TIMEOUT


@pytest.mark.accounting
class TestChartOfAccounts:

    def test_list_gl_accounts(self, admin_headers):
        r = requests.get(url("accounting", "/api/v1/accounting/accounts"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200

    def test_get_gl_account_by_code(self, admin_headers):
        r = requests.get(url("accounting", "/api/v1/accounting/accounts/code/1000"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200

    def test_create_gl_account(self, admin_headers):
        code = f"9{unique_id('')[4:7]}"  # 9xxx range for test accounts
        payload = {
            "code": code,
            "name": f"Test Account {code}",
            "accountType": "ASSET",
            "category": "OTHER",
        }
        r = requests.post(url("accounting", "/api/v1/accounting/accounts"),
                          json=payload, headers=admin_headers, timeout=TIMEOUT)
        # May get 409 if code exists
        assert r.status_code in (201, 409, 400)


@pytest.mark.accounting
class TestJournalEntries:

    def test_list_journal_entries(self, admin_headers):
        r = requests.get(url("accounting", "/api/v1/accounting/journal-entries"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200

    def test_get_journal_entry(self, admin_headers):
        r = requests.get(url("accounting", "/api/v1/accounting/journal-entries"),
                         headers=admin_headers, timeout=TIMEOUT)
        entries = r.json()
        items = entries.get("content", entries) if isinstance(entries, dict) else entries
        if items:
            eid = items[0]["id"]
            r2 = requests.get(url("accounting", f"/api/v1/accounting/journal-entries/{eid}"),
                              headers=admin_headers, timeout=TIMEOUT)
            assert r2.status_code == 200


@pytest.mark.accounting
class TestTrialBalance:

    def test_trial_balance(self, admin_headers):
        r = requests.get(url("accounting", "/api/v1/accounting/trial-balance"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200

    def test_trial_balance_with_period(self, admin_headers):
        r = requests.get(url("accounting", "/api/v1/accounting/trial-balance?year=2026&month=3"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200

    def test_gl_account_balance(self, admin_headers):
        # Get account 1000 (Loan Portfolio)
        r = requests.get(url("accounting", "/api/v1/accounting/accounts/code/1000"),
                         headers=admin_headers, timeout=TIMEOUT)
        if r.status_code == 200:
            acct_id = r.json()["id"]
            r2 = requests.get(url("accounting", f"/api/v1/accounting/accounts/{acct_id}/balance"),
                              headers=admin_headers, timeout=TIMEOUT)
            assert r2.status_code == 200

    def test_gl_account_ledger(self, admin_headers):
        r = requests.get(url("accounting", "/api/v1/accounting/accounts/code/1000"),
                         headers=admin_headers, timeout=TIMEOUT)
        if r.status_code == 200:
            acct_id = r.json()["id"]
            r2 = requests.get(url("accounting", f"/api/v1/accounting/accounts/{acct_id}/ledger"),
                              headers=admin_headers, timeout=TIMEOUT)
            assert r2.status_code == 200
