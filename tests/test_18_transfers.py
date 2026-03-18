"""Account transfer tests."""
import pytest
import requests
from conftest import url, unique_id, TIMEOUT


@pytest.mark.transfers
class TestTransfers:

    @pytest.fixture(autouse=True)
    def _setup_two_accounts(self, admin_headers, test_customer):
        cid = test_customer["_customerId"]
        # Create source account with funds
        r1 = requests.post(url("account", "/api/v1/accounts"),
                           json={"customerId": cid, "accountType": "SAVINGS",
                                 "currency": "KES", "name": "Transfer Source"},
                           headers=admin_headers, timeout=TIMEOUT)
        assert r1.status_code == 201
        self.source = r1.json()
        # Seed source
        requests.post(url("account", f"/api/v1/accounts/{self.source['id']}/credit"),
                      json={"amount": 50000, "description": "Seed", "reference": unique_id("SEED")},
                      headers=admin_headers, timeout=TIMEOUT)

        # Create destination
        r2 = requests.post(url("account", "/api/v1/accounts"),
                           json={"customerId": cid, "accountType": "SAVINGS",
                                 "currency": "KES", "name": "Transfer Dest"},
                           headers=admin_headers, timeout=TIMEOUT)
        assert r2.status_code == 201
        self.dest = r2.json()

    def test_transfer_between_accounts(self, admin_headers):
        payload = {
            "sourceAccountId": self.source["id"],
            "destinationAccountId": self.dest["id"],
            "amount": 5000,
            "description": "Pytest transfer",
            "reference": unique_id("TXF"),
            "transferType": "INTERNAL",
        }
        r = requests.post(url("account", "/api/v1/transfers"),
                          json=payload, headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 201, f"Transfer: {r.status_code} {r.text[:200]}"
        body = r.json()
        assert body["id"]

    def test_transfer_insufficient_funds(self, admin_headers):
        payload = {
            "sourceAccountId": self.source["id"],
            "destinationAccountId": self.dest["id"],
            "amount": 999999999,
            "description": "Should fail",
            "reference": unique_id("TXF"),
            "transferType": "INTERNAL",
        }
        r = requests.post(url("account", "/api/v1/transfers"),
                          json=payload, headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code in (400, 422, 500)

    def test_get_transfer_by_id(self, admin_headers):
        # Create transfer first
        payload = {
            "sourceAccountId": self.source["id"],
            "destinationAccountId": self.dest["id"],
            "amount": 1000,
            "description": "Lookup test",
            "reference": unique_id("TXF"),
            "transferType": "INTERNAL",
        }
        r = requests.post(url("account", "/api/v1/transfers"),
                          json=payload, headers=admin_headers, timeout=TIMEOUT)
        if r.status_code == 201:
            tid = r.json()["id"]
            r2 = requests.get(url("account", f"/api/v1/transfers/{tid}"),
                              headers=admin_headers, timeout=TIMEOUT)
            assert r2.status_code == 200

    def test_list_transfers_by_account(self, admin_headers):
        r = requests.get(
            url("account", f"/api/v1/transfers/account/{self.source['id']}"),
            headers=admin_headers, timeout=TIMEOUT,
        )
        assert r.status_code == 200
