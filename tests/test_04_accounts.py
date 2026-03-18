"""Account operations tests (account-service)."""
import pytest
import requests
from conftest import url, unique_id, TIMEOUT


@pytest.mark.accounts
class TestAccountCRUD:

    def test_create_savings_account(self, admin_headers, test_customer):
        payload = {
            "customerId": test_customer["_customerId"],
            "accountType": "SAVINGS",
            "currency": "KES",
            "name": "Test Savings",
        }
        r = requests.post(url("account", "/api/v1/accounts"),
                          json=payload, headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 201
        assert r.json()["accountType"] == "SAVINGS"

    def test_create_wallet_account(self, admin_headers, test_customer):
        payload = {
            "customerId": test_customer["_customerId"],
            "accountType": "WALLET",
            "currency": "KES",
            "name": "Test Wallet",
        }
        r = requests.post(url("account", "/api/v1/accounts"),
                          json=payload, headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 201

    def test_list_accounts(self, admin_headers):
        r = requests.get(url("account", "/api/v1/accounts"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200

    def test_get_account_by_id(self, admin_headers, test_account):
        r = requests.get(url("account", f"/api/v1/accounts/{test_account['id']}"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200
        assert r.json()["id"] == test_account["id"]

    def test_get_account_balance(self, admin_headers, test_account):
        r = requests.get(url("account", f"/api/v1/accounts/{test_account['id']}/balance"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200
        bal = r.json()
        assert float(bal.get("balance", bal.get("currentBalance", 0))) >= 100000

    def test_get_accounts_by_customer(self, admin_headers, test_customer):
        cid = test_customer["_customerId"]
        r = requests.get(url("account", f"/api/v1/accounts/customer/{cid}"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200

    def test_search_accounts(self, admin_headers):
        r = requests.get(url("account", "/api/v1/accounts/search?q=Pytest"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200


@pytest.mark.accounts
class TestAccountOperations:

    def test_credit_account(self, admin_headers, test_account):
        r = requests.post(
            url("account", f"/api/v1/accounts/{test_account['id']}/credit"),
            json={"amount": 5000, "description": "Test credit", "reference": unique_id("CR")},
            headers=admin_headers, timeout=TIMEOUT,
        )
        assert r.status_code == 200

    def test_debit_account(self, admin_headers, test_account):
        r = requests.post(
            url("account", f"/api/v1/accounts/{test_account['id']}/debit"),
            json={"amount": 1000, "description": "Test debit", "reference": unique_id("DR")},
            headers=admin_headers, timeout=TIMEOUT,
        )
        assert r.status_code == 200

    def test_debit_insufficient_funds(self, admin_headers, test_account):
        r = requests.post(
            url("account", f"/api/v1/accounts/{test_account['id']}/debit"),
            json={"amount": 999999999, "description": "Overdraw", "reference": unique_id("DR")},
            headers=admin_headers, timeout=TIMEOUT,
        )
        assert r.status_code in (400, 422, 500)

    def test_transaction_history(self, admin_headers, test_account):
        r = requests.get(
            url("account", f"/api/v1/accounts/{test_account['id']}/transactions"),
            headers=admin_headers, timeout=TIMEOUT,
        )
        assert r.status_code == 200

    def test_mini_statement(self, admin_headers, test_account):
        r = requests.get(
            url("account", f"/api/v1/accounts/{test_account['id']}/mini-statement"),
            headers=admin_headers, timeout=TIMEOUT,
        )
        assert r.status_code == 200

    def test_account_statement(self, admin_headers, test_account):
        r = requests.get(
            url("account", f"/api/v1/accounts/{test_account['id']}/statement?from=2025-01-01&to=2026-12-31"),
            headers=admin_headers, timeout=TIMEOUT,
        )
        assert r.status_code == 200
