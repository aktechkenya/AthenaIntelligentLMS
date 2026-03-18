"""Loan management service tests — active loans, schedules, repayments."""
import pytest
import requests
from conftest import url, TIMEOUT


@pytest.mark.loan_management
class TestLoanManagement:

    def test_list_loans(self, admin_headers):
        r = requests.get(url("loan_management", "/api/v1/loans"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200

    def test_list_loans_by_customer(self, admin_headers, test_customer):
        cid = test_customer["_customerId"]
        r = requests.get(url("loan_management", f"/api/v1/loans/customer/{cid}"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200

    def test_get_loan_schedule(self, admin_headers):
        """Get schedule for any existing loan."""
        r = requests.get(url("loan_management", "/api/v1/loans"),
                         headers=admin_headers, timeout=TIMEOUT)
        loans = r.json()
        items = loans.get("content", loans) if isinstance(loans, dict) else loans
        if not items:
            pytest.skip("No loans exist yet")
        loan_id = items[0]["id"]
        r2 = requests.get(url("loan_management", f"/api/v1/loans/{loan_id}/schedule"),
                          headers=admin_headers, timeout=TIMEOUT)
        assert r2.status_code == 200

    def test_get_loan_by_id(self, admin_headers):
        r = requests.get(url("loan_management", "/api/v1/loans"),
                         headers=admin_headers, timeout=TIMEOUT)
        loans = r.json()
        items = loans.get("content", loans) if isinstance(loans, dict) else loans
        if not items:
            pytest.skip("No loans exist yet")
        loan_id = items[0]["id"]
        r2 = requests.get(url("loan_management", f"/api/v1/loans/{loan_id}"),
                          headers=admin_headers, timeout=TIMEOUT)
        assert r2.status_code == 200

    def test_get_loan_repayments(self, admin_headers):
        r = requests.get(url("loan_management", "/api/v1/loans"),
                         headers=admin_headers, timeout=TIMEOUT)
        loans = r.json()
        items = loans.get("content", loans) if isinstance(loans, dict) else loans
        if not items:
            pytest.skip("No loans exist yet")
        loan_id = items[0]["id"]
        r2 = requests.get(url("loan_management", f"/api/v1/loans/{loan_id}/repayments"),
                          headers=admin_headers, timeout=TIMEOUT)
        assert r2.status_code == 200

    def test_get_loan_dpd(self, admin_headers):
        r = requests.get(url("loan_management", "/api/v1/loans"),
                         headers=admin_headers, timeout=TIMEOUT)
        loans = r.json()
        items = loans.get("content", loans) if isinstance(loans, dict) else loans
        if not items:
            pytest.skip("No loans exist yet")
        loan_id = items[0]["id"]
        r2 = requests.get(url("loan_management", f"/api/v1/loans/{loan_id}/dpd"),
                          headers=admin_headers, timeout=TIMEOUT)
        assert r2.status_code == 200

    def test_get_nonexistent_loan(self, admin_headers):
        r = requests.get(
            url("loan_management", "/api/v1/loans/00000000-0000-0000-0000-000000000000"),
            headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code in (404, 500)
