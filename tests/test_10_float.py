"""Float service tests."""
import pytest
import requests
from conftest import url, TIMEOUT


@pytest.mark.float
class TestFloatService:

    def test_list_float_accounts(self, admin_headers):
        r = requests.get(url("float", "/api/v1/float/accounts"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200

    def test_float_summary(self, admin_headers):
        r = requests.get(url("float", "/api/v1/float/summary"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200

    def test_get_float_account(self, admin_headers):
        r = requests.get(url("float", "/api/v1/float/accounts"),
                         headers=admin_headers, timeout=TIMEOUT)
        accounts = r.json()
        items = accounts if isinstance(accounts, list) else accounts.get("content", [])
        if items:
            fid = items[0]["id"]
            r2 = requests.get(url("float", f"/api/v1/float/accounts/{fid}"),
                              headers=admin_headers, timeout=TIMEOUT)
            assert r2.status_code == 200

    def test_float_transactions(self, admin_headers):
        r = requests.get(url("float", "/api/v1/float/accounts"),
                         headers=admin_headers, timeout=TIMEOUT)
        accounts = r.json()
        items = accounts if isinstance(accounts, list) else accounts.get("content", [])
        if items:
            fid = items[0]["id"]
            r2 = requests.get(url("float", f"/api/v1/float/accounts/{fid}/transactions"),
                              headers=admin_headers, timeout=TIMEOUT)
            assert r2.status_code == 200
