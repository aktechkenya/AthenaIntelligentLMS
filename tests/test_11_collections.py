"""Collections service tests."""
import pytest
import requests
from conftest import url, TIMEOUT


@pytest.mark.collections
class TestCollections:

    def test_list_cases(self, admin_headers):
        r = requests.get(url("collections", "/api/v1/collections/cases"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200

    def test_collections_summary(self, admin_headers):
        r = requests.get(url("collections", "/api/v1/collections/summary"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200

    def test_get_case_by_id(self, admin_headers):
        r = requests.get(url("collections", "/api/v1/collections/cases"),
                         headers=admin_headers, timeout=TIMEOUT)
        cases = r.json()
        items = cases.get("content", cases) if isinstance(cases, dict) else cases
        if items:
            cid = items[0]["id"]
            r2 = requests.get(url("collections", f"/api/v1/collections/cases/{cid}"),
                              headers=admin_headers, timeout=TIMEOUT)
            assert r2.status_code == 200

    def test_get_case_actions(self, admin_headers):
        r = requests.get(url("collections", "/api/v1/collections/cases"),
                         headers=admin_headers, timeout=TIMEOUT)
        cases = r.json()
        items = cases.get("content", cases) if isinstance(cases, dict) else cases
        if items:
            cid = items[0]["id"]
            r2 = requests.get(url("collections", f"/api/v1/collections/cases/{cid}/actions"),
                              headers=admin_headers, timeout=TIMEOUT)
            assert r2.status_code == 200

    def test_get_case_ptps(self, admin_headers):
        r = requests.get(url("collections", "/api/v1/collections/cases"),
                         headers=admin_headers, timeout=TIMEOUT)
        cases = r.json()
        items = cases.get("content", cases) if isinstance(cases, dict) else cases
        if items:
            cid = items[0]["id"]
            r2 = requests.get(url("collections", f"/api/v1/collections/cases/{cid}/ptps"),
                              headers=admin_headers, timeout=TIMEOUT)
            assert r2.status_code == 200
