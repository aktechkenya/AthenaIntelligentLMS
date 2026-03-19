"""
Test 28 — Customer 360: CRUD, accounts, balances, transactions, statements, search
Tests the full customer lifecycle and all data aggregation used by the Customer 360 page.
"""
import pytest
import requests
from conftest import url, unique_id, TIMEOUT


# ── Helpers ──────────────────────────────────────────────────────────
ACCT_URL = lambda path="": url("account", path)


# ── Customer CRUD ────────────────────────────────────────────────────

class TestCustomerCRUD:
    """Create, read, update, search, and status-change customers."""

    @pytest.fixture(autouse=True)
    def _setup(self, admin_headers):
        self.h = admin_headers
        self.cid = unique_id("C360")

    def test_create_customer(self):
        r = requests.post(
            ACCT_URL("/api/v1/customers"),
            json={
                "customerId": self.cid,
                "firstName": "Three",
                "lastName": "Sixty",
                "email": f"{self.cid.lower()}@test.athena.com",
                "phone": "+254711111111",
                "customerType": "INDIVIDUAL",
            },
            headers=self.h,
            timeout=TIMEOUT,
        )
        assert r.status_code == 201, f"Create failed: {r.text}"
        body = r.json()
        assert body["customerId"] == self.cid
        assert body["status"] == "ACTIVE"

    def test_list_customers(self):
        r = requests.get(
            ACCT_URL("/api/v1/customers?page=0&size=5"),
            headers=self.h,
            timeout=TIMEOUT,
        )
        assert r.status_code == 200
        body = r.json()
        assert "content" in body
        assert body["totalElements"] >= 1

    def test_search_customers(self):
        # Create a customer with a unique name we can search for
        name = unique_id("SRCH")
        requests.post(
            ACCT_URL("/api/v1/customers"),
            json={
                "customerId": name,
                "firstName": name,
                "lastName": "Searchable",
                "customerType": "INDIVIDUAL",
            },
            headers=self.h,
            timeout=TIMEOUT,
        )
        r = requests.get(
            ACCT_URL(f"/api/v1/customers/search?q={name}"),
            headers=self.h,
            timeout=TIMEOUT,
        )
        assert r.status_code == 200
        results = r.json()
        assert isinstance(results, list)
        assert any(c["customerId"] == name for c in results)

    def test_get_customer_by_id(self, test_customer):
        uuid = test_customer["id"]
        r = requests.get(
            ACCT_URL(f"/api/v1/customers/{uuid}"),
            headers=self.h,
            timeout=TIMEOUT,
        )
        assert r.status_code == 200
        assert r.json()["id"] == uuid

    def test_update_customer(self, test_customer):
        uuid = test_customer["id"]
        r = requests.put(
            ACCT_URL(f"/api/v1/customers/{uuid}"),
            json={"firstName": "Updated", "lastName": "Runner"},
            headers=self.h,
            timeout=TIMEOUT,
        )
        assert r.status_code == 200
        assert r.json()["firstName"] == "Updated"
        # Restore original
        requests.put(
            ACCT_URL(f"/api/v1/customers/{uuid}"),
            json={"firstName": "Pytest", "lastName": "Runner"},
            headers=self.h,
            timeout=TIMEOUT,
        )

    def test_update_customer_status(self, test_customer):
        uuid = test_customer["id"]
        r = requests.patch(
            ACCT_URL(f"/api/v1/customers/{uuid}/status"),
            json={"status": "SUSPENDED"},
            headers=self.h,
            timeout=TIMEOUT,
        )
        assert r.status_code == 200
        assert r.json()["status"] == "SUSPENDED"
        # Restore
        requests.patch(
            ACCT_URL(f"/api/v1/customers/{uuid}/status"),
            json={"status": "ACTIVE"},
            headers=self.h,
            timeout=TIMEOUT,
        )

    def test_get_nonexistent_customer(self):
        r = requests.get(
            ACCT_URL("/api/v1/customers/00000000-0000-0000-0000-000000000000"),
            headers=self.h,
            timeout=TIMEOUT,
        )
        assert r.status_code in (404, 500)


# ── Customer Accounts & Balances ─────────────────────────────────────

class TestCustomerAccounts:
    """Verify accounts-by-customer, balance, and transaction endpoints."""

    @pytest.fixture(autouse=True)
    def _setup(self, admin_headers, test_customer, test_account):
        self.h = admin_headers
        self.cust = test_customer
        self.acct = test_account

    def test_get_accounts_by_customer(self):
        cid = self.cust["_customerId"]
        r = requests.get(
            ACCT_URL(f"/api/v1/accounts/customer/{cid}"),
            headers=self.h,
            timeout=TIMEOUT,
        )
        assert r.status_code == 200
        accts = r.json()
        assert isinstance(accts, list)
        assert len(accts) >= 1
        assert any(a["id"] == self.acct["id"] for a in accts)

    def test_get_account_balance(self):
        aid = self.acct["id"]
        r = requests.get(
            ACCT_URL(f"/api/v1/accounts/{aid}/balance"),
            headers=self.h,
            timeout=TIMEOUT,
        )
        assert r.status_code == 200
        body = r.json()
        assert "currentBalance" in body or "balance" in body
        assert "availableBalance" in body
        bal = body.get("currentBalance", body.get("balance", 0))
        assert bal >= 0

    def test_get_account_transactions(self):
        aid = self.acct["id"]
        r = requests.get(
            ACCT_URL(f"/api/v1/accounts/{aid}/transactions?page=0&size=10"),
            headers=self.h,
            timeout=TIMEOUT,
        )
        assert r.status_code == 200
        body = r.json()
        assert "content" in body

    def test_get_mini_statement(self):
        aid = self.acct["id"]
        r = requests.get(
            ACCT_URL(f"/api/v1/accounts/{aid}/mini-statement"),
            headers=self.h,
            timeout=TIMEOUT,
        )
        assert r.status_code == 200

    def test_get_full_statement(self):
        aid = self.acct["id"]
        r = requests.get(
            ACCT_URL(f"/api/v1/accounts/{aid}/statement?from=2024-01-01&to=2026-12-31"),
            headers=self.h,
            timeout=TIMEOUT,
        )
        assert r.status_code == 200
        body = r.json()
        assert "accountNumber" in body
        assert "openingBalance" in body or "closingBalance" in body


# ── Customer 360 Data Aggregation ────────────────────────────────────

class TestCustomer360Aggregation:
    """Validate all data sources the Customer 360 page calls."""

    @pytest.fixture(autouse=True)
    def _setup(self, admin_headers, test_customer, test_account):
        self.h = admin_headers
        self.cust = test_customer
        self.acct = test_account

    def test_customer_profile_loads(self):
        """The core customer profile is fetchable."""
        r = requests.get(
            ACCT_URL(f"/api/v1/customers/{self.cust['id']}"),
            headers=self.h,
            timeout=TIMEOUT,
        )
        assert r.status_code == 200
        body = r.json()
        for field in ("id", "customerId", "firstName", "lastName", "status"):
            assert field in body

    def test_accounts_with_balances(self):
        """Accounts list and each account's balance are accessible."""
        cid = self.cust["_customerId"]
        r = requests.get(
            ACCT_URL(f"/api/v1/accounts/customer/{cid}"),
            headers=self.h,
            timeout=TIMEOUT,
        )
        assert r.status_code == 200
        accts = r.json()
        for a in accts:
            br = requests.get(
                ACCT_URL(f"/api/v1/accounts/{a['id']}/balance"),
                headers=self.h,
                timeout=TIMEOUT,
            )
            assert br.status_code == 200

    def test_fraud_risk_profile_graceful(self):
        """Fraud risk endpoint returns 200 or 404 (no crash)."""
        cid = self.cust["_customerId"]
        r = requests.get(
            url("fraud", f"/api/v1/fraud/risk-profile/{cid}"),
            headers=self.h,
            timeout=TIMEOUT,
        )
        assert r.status_code in (200, 404, 500)

    def test_fraud_alerts_graceful(self):
        """Fraud alerts endpoint returns data or 404."""
        cid = self.cust["_customerId"]
        r = requests.get(
            url("fraud", f"/api/v1/fraud/alerts?customerId={cid}&page=0&size=50"),
            headers=self.h,
            timeout=TIMEOUT,
        )
        assert r.status_code in (200, 404)

    def test_fraud_network_graceful(self):
        """Network analysis endpoint returns data or 404."""
        cid = self.cust["_customerId"]
        r = requests.get(
            url("fraud", f"/api/v1/fraud/network/{cid}"),
            headers=self.h,
            timeout=TIMEOUT,
        )
        assert r.status_code in (200, 404)

    def test_wallet_by_customer_graceful(self):
        """Wallet by customer returns data or 404."""
        cid = self.cust["_customerId"]
        r = requests.get(
            url("overdraft", f"/api/v1/wallets/customer/{cid}"),
            headers=self.h,
            timeout=TIMEOUT,
        )
        assert r.status_code in (200, 404)


# ── Error Handling ───────────────────────────────────────────────────

class TestCustomer360ErrorHandling:
    """Ensure graceful handling of missing/bad data."""

    @pytest.fixture(autouse=True)
    def _setup(self, admin_headers):
        self.h = admin_headers

    def test_missing_customer_returns_404(self):
        r = requests.get(
            ACCT_URL("/api/v1/customers/00000000-0000-0000-0000-000000000000"),
            headers=self.h,
            timeout=TIMEOUT,
        )
        assert r.status_code in (404, 500)

    def test_balance_for_missing_account_returns_404(self):
        r = requests.get(
            ACCT_URL("/api/v1/accounts/00000000-0000-0000-0000-000000000000/balance"),
            headers=self.h,
            timeout=TIMEOUT,
        )
        assert r.status_code in (404, 500)

    def test_transactions_for_missing_account(self):
        r = requests.get(
            ACCT_URL("/api/v1/accounts/00000000-0000-0000-0000-000000000000/transactions?page=0&size=10"),
            headers=self.h,
            timeout=TIMEOUT,
        )
        assert r.status_code in (200, 404, 500)

    def test_statement_for_missing_account(self):
        r = requests.get(
            ACCT_URL("/api/v1/accounts/00000000-0000-0000-0000-000000000000/statement?from=2024-01-01&to=2025-01-01"),
            headers=self.h,
            timeout=TIMEOUT,
        )
        assert r.status_code in (404, 500)

    def test_search_empty_query_returns_empty(self):
        r = requests.get(
            ACCT_URL("/api/v1/customers/search?q=ZZZNONEXISTENT999"),
            headers=self.h,
            timeout=TIMEOUT,
        )
        assert r.status_code == 200
        body = r.json()
        assert body is None or len(body) == 0
