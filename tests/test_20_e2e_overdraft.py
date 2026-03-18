"""
E2E Test: Overdraft/Wallet Lifecycle
Create wallet → Deposit → Withdraw → Apply overdraft → Overdraft draw → Transactions
"""
import pytest
import requests
from conftest import url, unique_id, TIMEOUT


@pytest.mark.e2e
class TestOverdraftLifecycleE2E:

    def test_full_wallet_lifecycle(self, admin_headers):
        """Complete wallet lifecycle: create → deposit → withdraw → check balance."""

        # 1. Create customer
        cid = unique_id("E2E-OD")
        r = requests.post(url("account", "/api/v1/customers"), headers=admin_headers,
                          json={"customerId": cid, "firstName": "Overdraft", "lastName": "E2E",
                                "email": f"{cid.lower()}@e2e.test", "phone": "+254700000002",
                                "customerType": "INDIVIDUAL", "status": "ACTIVE"},
                          timeout=TIMEOUT)
        assert r.status_code == 201

        # 2. Create wallet
        r = requests.post(url("overdraft", "/api/v1/wallets"),
                          json={"customerId": cid, "currency": "KES"},
                          headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code in (200, 201), f"Wallet create: {r.status_code}"
        wallet_id = r.json()["id"]

        # 3. Deposit
        r = requests.post(url("overdraft", f"/api/v1/wallets/{wallet_id}/deposit"),
                          json={"amount": 25000, "reference": unique_id("DEP")},
                          headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200, f"Deposit: {r.status_code}"

        # 4. Verify balance
        r = requests.get(url("overdraft", f"/api/v1/wallets/{wallet_id}"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200
        bal = float(r.json().get("currentBalance", r.json().get("balance", 0)))
        assert bal >= 25000, f"Expected >= 25000, got {bal}"

        # 5. Withdraw
        r = requests.post(url("overdraft", f"/api/v1/wallets/{wallet_id}/withdraw"),
                          json={"amount": 5000, "reference": unique_id("WDR")},
                          headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200, f"Withdraw: {r.status_code}"

        # 6. Verify reduced balance
        r = requests.get(url("overdraft", f"/api/v1/wallets/{wallet_id}"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200
        bal2 = float(r.json().get("currentBalance", r.json().get("balance", 0)))
        assert bal2 >= 20000, f"Expected >= 20000 after withdraw, got {bal2}"

        # 7. Check transactions
        r = requests.get(url("overdraft", f"/api/v1/wallets/{wallet_id}/transactions"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200
        txns = r.json()
        items = txns.get("content", txns) if isinstance(txns, dict) else txns
        assert len(items) >= 2, f"Expected >= 2 transactions, got {len(items)}"

    def test_overdraft_application(self, admin_headers):
        """Apply for overdraft facility on a wallet."""
        cid = unique_id("E2E-ODF")
        requests.post(url("account", "/api/v1/customers"), headers=admin_headers,
                      json={"customerId": cid, "firstName": "OD", "lastName": "Facility",
                            "email": f"{cid.lower()}@e2e.test", "phone": "+254700000003",
                            "customerType": "INDIVIDUAL", "status": "ACTIVE"},
                      timeout=TIMEOUT)

        r = requests.post(url("overdraft", "/api/v1/wallets"),
                          json={"customerId": cid, "currency": "KES"},
                          headers=admin_headers, timeout=TIMEOUT)
        wallet_id = r.json()["id"]

        # Deposit to establish history
        requests.post(url("overdraft", f"/api/v1/wallets/{wallet_id}/deposit"),
                      json={"amount": 50000, "reference": unique_id("DEP")},
                      headers=admin_headers, timeout=TIMEOUT)

        # Apply for overdraft
        r = requests.post(url("overdraft", f"/api/v1/wallets/{wallet_id}/overdraft/apply"),
                          json={"requestedLimit": 10000},
                          headers=admin_headers, timeout=TIMEOUT)
        # Overdraft may be approved or denied based on scoring
        assert r.status_code in (200, 201, 400, 409)

        # Check facility status
        r = requests.get(url("overdraft", f"/api/v1/wallets/{wallet_id}/overdraft"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code in (200, 404)

    def test_withdraw_exceeding_balance_no_overdraft(self, admin_headers):
        """Withdraw more than balance without overdraft should fail."""
        cid = unique_id("E2E-NOOVR")
        requests.post(url("account", "/api/v1/customers"), headers=admin_headers,
                      json={"customerId": cid, "firstName": "NoOD", "lastName": "Test",
                            "email": f"{cid.lower()}@e2e.test", "phone": "+254700000004",
                            "customerType": "INDIVIDUAL", "status": "ACTIVE"},
                      timeout=TIMEOUT)

        r = requests.post(url("overdraft", "/api/v1/wallets"),
                          json={"customerId": cid, "currency": "KES"},
                          headers=admin_headers, timeout=TIMEOUT)
        wallet_id = r.json()["id"]

        # Deposit small amount
        requests.post(url("overdraft", f"/api/v1/wallets/{wallet_id}/deposit"),
                      json={"amount": 1000, "reference": unique_id("DEP")},
                      headers=admin_headers, timeout=TIMEOUT)

        # Try to withdraw more than balance
        r = requests.post(url("overdraft", f"/api/v1/wallets/{wallet_id}/withdraw"),
                          json={"amount": 50000, "reference": unique_id("WDR")},
                          headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code in (400, 422, 500), "Should reject overdraw without facility"
