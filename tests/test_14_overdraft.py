"""Overdraft / Wallet service tests."""
import pytest
import requests
from conftest import url, unique_id, TIMEOUT


@pytest.mark.overdraft
class TestWalletCRUD:

    def test_list_wallets(self, admin_headers):
        r = requests.get(url("overdraft", "/api/v1/wallets"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200

    def test_create_wallet(self, admin_headers, test_customer):
        payload = {
            "customerId": test_customer["_customerId"],
            "currency": "KES",
        }
        r = requests.post(url("overdraft", "/api/v1/wallets"),
                          json=payload, headers=admin_headers, timeout=TIMEOUT)
        # 201 new, or 409/400 if already exists
        assert r.status_code in (200, 201, 409, 400)

    def test_get_wallet_by_customer(self, admin_headers, test_customer):
        cid = test_customer["_customerId"]
        r = requests.get(url("overdraft", f"/api/v1/wallets/customer/{cid}"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code in (200, 404)

    def test_overdraft_summary(self, admin_headers):
        r = requests.get(url("overdraft", "/api/v1/overdraft/summary"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200


@pytest.mark.overdraft
class TestWalletOperations:

    @pytest.fixture(autouse=True)
    def _ensure_wallet(self, admin_headers, test_customer):
        cid = test_customer["_customerId"]
        # Create if not exists
        requests.post(url("overdraft", "/api/v1/wallets"),
                      json={"customerId": cid, "currency": "KES"},
                      headers=admin_headers, timeout=TIMEOUT)
        r = requests.get(url("overdraft", f"/api/v1/wallets/customer/{cid}"),
                         headers=admin_headers, timeout=TIMEOUT)
        if r.status_code != 200:
            pytest.skip("Could not find/create wallet")
        self.wallet = r.json()
        self.wallet_id = self.wallet["id"]

    def test_deposit(self, admin_headers):
        r = requests.post(
            url("overdraft", f"/api/v1/wallets/{self.wallet_id}/deposit"),
            json={"amount": 10000, "reference": unique_id("DEP")},
            headers=admin_headers, timeout=TIMEOUT,
        )
        assert r.status_code == 200

    def test_withdraw(self, admin_headers):
        # Deposit first to ensure balance
        requests.post(
            url("overdraft", f"/api/v1/wallets/{self.wallet_id}/deposit"),
            json={"amount": 5000, "reference": unique_id("DEP")},
            headers=admin_headers, timeout=TIMEOUT,
        )
        r = requests.post(
            url("overdraft", f"/api/v1/wallets/{self.wallet_id}/withdraw"),
            json={"amount": 1000, "reference": unique_id("WDR")},
            headers=admin_headers, timeout=TIMEOUT,
        )
        assert r.status_code == 200

    def test_wallet_transactions(self, admin_headers):
        r = requests.get(
            url("overdraft", f"/api/v1/wallets/{self.wallet_id}/transactions"),
            headers=admin_headers, timeout=TIMEOUT,
        )
        assert r.status_code == 200

    def test_apply_overdraft(self, admin_headers):
        r = requests.post(
            url("overdraft", f"/api/v1/wallets/{self.wallet_id}/overdraft/apply"),
            json={"requestedLimit": 5000},
            headers=admin_headers, timeout=TIMEOUT,
        )
        # May succeed or fail based on scoring
        assert r.status_code in (200, 201, 400, 409)

    def test_get_overdraft_facility(self, admin_headers):
        r = requests.get(
            url("overdraft", f"/api/v1/wallets/{self.wallet_id}/overdraft"),
            headers=admin_headers, timeout=TIMEOUT,
        )
        assert r.status_code in (200, 404)
