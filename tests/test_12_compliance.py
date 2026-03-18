"""Compliance service tests — KYC, AML alerts."""
import pytest
import requests
from conftest import url, unique_id, TIMEOUT


@pytest.mark.compliance
class TestKYC:

    def test_create_kyc(self, admin_headers, test_customer):
        cid = test_customer["_customerId"]
        payload = {
            "customerId": cid,
            "firstName": "Pytest",
            "lastName": "Runner",
            "idType": "NATIONAL_ID",
            "idNumber": unique_id("ID"),
            "tier": "BASIC",
        }
        r = requests.post(url("compliance", "/api/v1/compliance/kyc"),
                          json=payload, headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code in (200, 201)

    def test_get_kyc_by_customer(self, admin_headers, test_customer):
        cid = test_customer["_customerId"]
        r = requests.get(url("compliance", f"/api/v1/compliance/kyc/{cid}"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code in (200, 404)  # 404 ok if no KYC yet

    def test_pass_kyc(self, admin_headers, test_customer):
        cid = test_customer["_customerId"]
        # Ensure KYC exists
        requests.post(url("compliance", "/api/v1/compliance/kyc"),
                      json={"customerId": cid, "firstName": "Test", "lastName": "KYC",
                            "idType": "NATIONAL_ID", "idNumber": unique_id("ID"), "tier": "BASIC"},
                      headers=admin_headers, timeout=TIMEOUT)
        r = requests.post(url("compliance", f"/api/v1/compliance/kyc/{cid}/pass"),
                          headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200


@pytest.mark.compliance
class TestAMLAlerts:

    def test_list_alerts(self, admin_headers):
        r = requests.get(url("compliance", "/api/v1/compliance/alerts"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200

    def test_create_alert(self, admin_headers, test_customer):
        payload = {
            "customerId": test_customer["_customerId"],
            "alertType": "LARGE_TRANSACTION",
            "severity": "MEDIUM",
            "description": "Pytest test alert",
            "subjectType": "CUSTOMER",
            "subjectId": test_customer["_customerId"],
        }
        r = requests.post(url("compliance", "/api/v1/compliance/alerts"),
                          json=payload, headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 201, f"Alert create: {r.status_code} {r.text[:200]}"

    def test_compliance_summary(self, admin_headers):
        r = requests.get(url("compliance", "/api/v1/compliance/summary"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200

    def test_compliance_events(self, admin_headers):
        r = requests.get(url("compliance", "/api/v1/compliance/events"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200
