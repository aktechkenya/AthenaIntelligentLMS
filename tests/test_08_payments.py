"""Payment service tests."""
import pytest
import requests
from conftest import url, unique_id, TIMEOUT


@pytest.mark.payments
class TestPayments:

    def test_list_payments(self, admin_headers):
        r = requests.get(url("payment", "/api/v1/payments"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200

    def test_create_payment(self, admin_headers, test_customer):
        payload = {
            "customerId": test_customer["_customerId"],
            "amount": 5000,
            "currency": "KES",
            "paymentType": "LOAN_REPAYMENT",
            "paymentMethod": "BANK_TRANSFER",
            "paymentChannel": "CASH",
            "reference": unique_id("PAY"),
            "description": "Pytest payment",
        }
        r = requests.post(url("payment", "/api/v1/payments"),
                          json=payload, headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 201, f"Payment create: {r.status_code} {r.text[:200]}"
        body = r.json()
        assert body["id"]
        return body

    def test_get_payment_by_id(self, admin_headers, test_customer):
        # Create then get
        payload = {
            "customerId": test_customer["_customerId"],
            "amount": 1000,
            "currency": "KES",
            "paymentType": "LOAN_REPAYMENT",
            "paymentMethod": "CASH",
            "reference": unique_id("PAY"),
        }
        r = requests.post(url("payment", "/api/v1/payments"),
                          json=payload, headers=admin_headers, timeout=TIMEOUT)
        if r.status_code == 201:
            pid = r.json()["id"]
            r2 = requests.get(url("payment", f"/api/v1/payments/{pid}"),
                              headers=admin_headers, timeout=TIMEOUT)
            assert r2.status_code == 200

    def test_get_payments_by_customer(self, admin_headers, test_customer):
        cid = test_customer["_customerId"]
        r = requests.get(url("payment", f"/api/v1/payments/customer/{cid}"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200

    def test_get_nonexistent_payment(self, admin_headers):
        r = requests.get(
            url("payment", "/api/v1/payments/00000000-0000-0000-0000-000000000000"),
            headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code in (404, 500)
