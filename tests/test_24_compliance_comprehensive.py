"""
Comprehensive Compliance Tab Tests — KYC, AML, Fraud Detection, SAR, Watchlist

Covers all 9 pages of the Compliance tab powered by:
- compliance-service (port 28094)
- fraud-detection-service (port 28100)
"""
import pytest
import requests
from conftest import url, TIMEOUT, unique_id, SERVICES

FRAUD_BASE = SERVICES.get("fraud", "http://localhost:28100")


def fraud_url(path: str) -> str:
    return f"{FRAUD_BASE}{path}"


# ===========================================================================
# 1. KYC & Compliance Page — compliance-service
# ===========================================================================
@pytest.mark.compliance
class TestKycLifecycle:

    def test_create_kyc(self, admin_headers, test_customer):
        cid = test_customer["_customerId"]
        payload = {
            "customerId": cid,
            "firstName": "Comp",
            "lastName": "Test",
            "idType": "NATIONAL_ID",
            "idNumber": unique_id("KYC"),
            "tier": "BASIC",
        }
        r = requests.post(url("compliance", "/api/v1/compliance/kyc"),
                          json=payload, headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code in (200, 201), f"KYC create: {r.status_code} {r.text[:200]}"

    def test_get_kyc(self, admin_headers, test_customer):
        cid = test_customer["_customerId"]
        r = requests.get(url("compliance", f"/api/v1/compliance/kyc/{cid}"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200

    def test_kyc_pass(self, admin_headers, test_customer):
        cid = test_customer["_customerId"]
        r = requests.post(url("compliance", f"/api/v1/compliance/kyc/{cid}/pass"),
                          headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200

    def test_kyc_fail(self, admin_headers, test_customer):
        """Create a new KYC then fail it."""
        cid2 = unique_id("KYCF")
        # Create a customer for this test
        requests.post(url("account", "/api/v1/customers"),
                      json={"customerId": cid2, "firstName": "Fail", "lastName": "KYC",
                            "email": f"{cid2.lower()}@test.athena.com", "phone": "+254700000001",
                            "customerType": "INDIVIDUAL", "status": "ACTIVE"},
                      headers=admin_headers, timeout=TIMEOUT)
        # Create KYC
        requests.post(url("compliance", "/api/v1/compliance/kyc"),
                      json={"customerId": cid2, "firstName": "Fail", "lastName": "KYC",
                            "idType": "NATIONAL_ID", "idNumber": unique_id("ID"), "tier": "BASIC"},
                      headers=admin_headers, timeout=TIMEOUT)
        r = requests.post(url("compliance", f"/api/v1/compliance/kyc/{cid2}/fail"),
                          headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code in (200, 400, 404)

    def test_kyc_upsert_behavior(self, admin_headers, test_customer):
        """Creating KYC for same customer twice should upsert or succeed."""
        cid = test_customer["_customerId"]
        payload = {
            "customerId": cid,
            "firstName": "Updated",
            "lastName": "KYC",
            "idType": "NATIONAL_ID",
            "idNumber": unique_id("KYC"),
            "tier": "ENHANCED",
        }
        r = requests.post(url("compliance", "/api/v1/compliance/kyc"),
                          json=payload, headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code in (200, 201, 409)

    def test_kyc_unknown_customer(self, admin_headers):
        cid = unique_id("NOKYC")
        r = requests.get(url("compliance", f"/api/v1/compliance/kyc/{cid}"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code in (404, 500)

    def test_kyc_missing_fields(self, admin_headers):
        r = requests.post(url("compliance", "/api/v1/compliance/kyc"),
                          json={}, headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code in (400, 422, 500)

    def test_kyc_requires_auth(self, test_customer):
        cid = test_customer["_customerId"]
        r = requests.get(url("compliance", f"/api/v1/compliance/kyc/{cid}"),
                         timeout=TIMEOUT)
        assert r.status_code in (401, 403)


# ===========================================================================
# 2. AML Monitoring — compliance-service
# ===========================================================================
@pytest.mark.compliance
class TestAmlAlertsCrud:

    ALERT_TYPES = [
        "LARGE_TRANSACTION", "STRUCTURING", "HIGH_VELOCITY",
        "RAPID_FUND_MOVEMENT", "KYC_BYPASS", "DORMANT_REACTIVATION",
        "SUSPICIOUS_WRITEOFF", "OVERPAYMENT", "WATCHLIST_MATCH",
    ]

    @pytest.fixture(scope="class")
    def created_alert_id(self, admin_headers, test_customer):
        """Create an alert and return its ID for subsequent tests."""
        payload = {
            "customerId": test_customer["_customerId"],
            "alertType": "LARGE_TRANSACTION",
            "severity": "HIGH",
            "description": "Comprehensive test alert",
            "subjectType": "CUSTOMER",
            "subjectId": test_customer["_customerId"],
        }
        r = requests.post(url("compliance", "/api/v1/compliance/alerts"),
                          json=payload, headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 201
        return r.json().get("id")

    def test_create_alert_large_transaction(self, admin_headers, test_customer):
        payload = {
            "customerId": test_customer["_customerId"],
            "alertType": "LARGE_TRANSACTION",
            "severity": "HIGH",
            "description": "Large TX alert",
            "subjectType": "CUSTOMER",
            "subjectId": test_customer["_customerId"],
        }
        r = requests.post(url("compliance", "/api/v1/compliance/alerts"),
                          json=payload, headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 201

    def test_create_alert_structuring(self, admin_headers, test_customer):
        payload = {
            "customerId": test_customer["_customerId"],
            "alertType": "STRUCTURING",
            "severity": "CRITICAL",
            "description": "Structuring alert",
            "subjectType": "CUSTOMER",
            "subjectId": test_customer["_customerId"],
        }
        r = requests.post(url("compliance", "/api/v1/compliance/alerts"),
                          json=payload, headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 201

    def test_create_alert_all_severities(self, admin_headers, test_customer):
        """Test all 4 severity levels."""
        for sev in ["LOW", "MEDIUM", "HIGH", "CRITICAL"]:
            payload = {
                "customerId": test_customer["_customerId"],
                "alertType": "LARGE_TRANSACTION",
                "severity": sev,
                "description": f"Alert with {sev} severity",
                "subjectType": "CUSTOMER",
                "subjectId": test_customer["_customerId"],
            }
            r = requests.post(url("compliance", "/api/v1/compliance/alerts"),
                              json=payload, headers=admin_headers, timeout=TIMEOUT)
            assert r.status_code == 201, f"Failed for severity {sev}: {r.text[:200]}"

    def test_create_alert_missing_fields(self, admin_headers):
        r = requests.post(url("compliance", "/api/v1/compliance/alerts"),
                          json={}, headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code in (400, 422, 500)

    def test_list_alerts(self, admin_headers):
        r = requests.get(url("compliance", "/api/v1/compliance/alerts"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200
        body = r.json()
        assert "content" in body
        assert isinstance(body["content"], list)

    def test_list_alerts_pagination(self, admin_headers):
        r = requests.get(url("compliance", "/api/v1/compliance/alerts?page=0&size=3"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200
        body = r.json()
        assert body["size"] <= 3

    def test_list_alerts_status_filter(self, admin_headers):
        r = requests.get(url("compliance", "/api/v1/compliance/alerts?status=OPEN"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200

    def test_get_alert_by_id(self, admin_headers, created_alert_id):
        if not created_alert_id:
            pytest.skip("No alert ID available")
        r = requests.get(url("compliance", f"/api/v1/compliance/alerts/{created_alert_id}"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200

    def test_resolve_alert(self, admin_headers, created_alert_id):
        if not created_alert_id:
            pytest.skip("No alert ID available")
        r = requests.post(
            url("compliance", f"/api/v1/compliance/alerts/{created_alert_id}/resolve"),
            json={"resolvedBy": "pytest", "resolution": "FALSE_POSITIVE",
                  "notes": "Test resolution"},
            headers=admin_headers, timeout=TIMEOUT,
        )
        assert r.status_code in (200, 400, 404)


# ===========================================================================
# 3. Compliance Summary & Events — compliance-service
# ===========================================================================
@pytest.mark.compliance
class TestComplianceSummary:

    def test_summary_structure(self, admin_headers):
        r = requests.get(url("compliance", "/api/v1/compliance/summary"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200
        body = r.json()
        assert isinstance(body, dict)

    def test_summary_auth_required(self):
        r = requests.get(url("compliance", "/api/v1/compliance/summary"),
                         timeout=TIMEOUT)
        assert r.status_code in (401, 403)


@pytest.mark.compliance
class TestComplianceEvents:

    def test_list_events(self, admin_headers):
        r = requests.get(url("compliance", "/api/v1/compliance/events"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200

    def test_events_pagination(self, admin_headers):
        r = requests.get(url("compliance", "/api/v1/compliance/events?page=0&size=5"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200

    def test_events_default_page(self, admin_headers):
        r = requests.get(url("compliance", "/api/v1/compliance/events?page=0"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200


# ===========================================================================
# 4. Fraud Dashboard — fraud-service
# ===========================================================================
@pytest.mark.fraud
class TestFraudDashboard:

    def test_summary_structure(self, admin_headers):
        r = requests.get(fraud_url("/api/v1/fraud/summary"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200
        body = r.json()
        for key in ("openAlerts", "underReviewAlerts", "escalatedAlerts",
                     "confirmedFraud", "criticalAlerts", "highRiskCustomers"):
            assert key in body, f"Missing key: {key}"
            assert isinstance(body[key], int)

    def test_analytics_structure(self, admin_headers):
        r = requests.get(fraud_url("/api/v1/fraud/analytics"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200
        body = r.json()
        for key in ("totalAlerts", "resolvedAlerts", "resolutionRate",
                     "activeCases", "ruleEffectiveness", "dailyTrend"):
            assert key in body, f"Missing key: {key}"

    def test_analytics_rate_ranges(self, admin_headers):
        r = requests.get(fraud_url("/api/v1/fraud/analytics"),
                         headers=admin_headers, timeout=TIMEOUT)
        body = r.json()
        assert 0 <= body["resolutionRate"] <= 100
        assert 0 <= body["precisionRate"] <= 100

    def test_recent_events(self, admin_headers):
        r = requests.get(fraud_url("/api/v1/fraud/events/recent"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200
        body = r.json()
        assert "content" in body
        assert "totalElements" in body

    def test_dashboard_auth_required(self):
        r = requests.get(fraud_url("/api/v1/fraud/summary"), timeout=TIMEOUT)
        assert r.status_code in (401, 403)


# ===========================================================================
# 5. Fraud Alerts — fraud-service
# ===========================================================================
@pytest.mark.fraud
class TestFraudAlertsCrud:

    def test_list_alerts(self, admin_headers):
        r = requests.get(fraud_url("/api/v1/fraud/alerts"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200
        body = r.json()
        assert "content" in body
        assert "totalElements" in body

    def test_list_alerts_pagination(self, admin_headers):
        r = requests.get(fraud_url("/api/v1/fraud/alerts?page=0&size=3"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200
        assert r.json()["size"] <= 3

    def test_filter_alerts_open(self, admin_headers):
        r = requests.get(fraud_url("/api/v1/fraud/alerts?status=OPEN"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200
        for a in r.json().get("content", []):
            assert a["status"] == "OPEN"

    def test_filter_alerts_confirmed(self, admin_headers):
        r = requests.get(fraud_url("/api/v1/fraud/alerts?status=CONFIRMED_FRAUD"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200

    def test_filter_alerts_false_positive(self, admin_headers):
        r = requests.get(fraud_url("/api/v1/fraud/alerts?status=FALSE_POSITIVE"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200

    def test_get_alert_not_found(self, admin_headers):
        fake_id = "00000000-0000-0000-0000-000000000000"
        r = requests.get(fraud_url(f"/api/v1/fraud/alerts/{fake_id}"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code in (404, 500)

    def test_get_alert_invalid_id(self, admin_headers):
        r = requests.get(fraud_url("/api/v1/fraud/alerts/not-a-uuid"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 400


@pytest.mark.fraud
class TestFraudAlertAssignResolve:

    @pytest.fixture(scope="class")
    def open_alert(self, admin_headers):
        """Find or skip if no OPEN alerts exist."""
        r = requests.get(fraud_url("/api/v1/fraud/alerts?status=OPEN&size=1"),
                         headers=admin_headers, timeout=TIMEOUT)
        if r.status_code != 200:
            pytest.skip("Cannot fetch alerts")
        alerts = r.json().get("content", [])
        if not alerts:
            pytest.skip("No OPEN alerts for lifecycle test")
        return alerts[0]

    def test_assign_alert(self, admin_headers, open_alert):
        r = requests.post(
            fraud_url(f"/api/v1/fraud/alerts/{open_alert['id']}/assign"),
            json={"assignee": "compliance-officer"},
            headers=admin_headers, timeout=TIMEOUT,
        )
        assert r.status_code == 200
        assert r.json()["status"] == "UNDER_REVIEW"

    def test_resolve_confirmed(self, admin_headers):
        """Find an UNDER_REVIEW alert and resolve as confirmed fraud."""
        r = requests.get(fraud_url("/api/v1/fraud/alerts?status=UNDER_REVIEW&size=1"),
                         headers=admin_headers, timeout=TIMEOUT)
        alerts = r.json().get("content", [])
        if not alerts:
            pytest.skip("No UNDER_REVIEW alerts")
        r = requests.post(
            fraud_url(f"/api/v1/fraud/alerts/{alerts[0]['id']}/resolve"),
            json={"resolvedBy": "pytest", "confirmedFraud": True, "notes": "Confirmed in test"},
            headers=admin_headers, timeout=TIMEOUT,
        )
        assert r.status_code == 200
        assert r.json()["status"] == "CONFIRMED_FRAUD"

    def test_resolve_false_positive(self, admin_headers):
        """Find an alert and resolve as false positive."""
        r = requests.get(fraud_url("/api/v1/fraud/alerts?status=OPEN&size=1"),
                         headers=admin_headers, timeout=TIMEOUT)
        alerts = r.json().get("content", [])
        if not alerts:
            pytest.skip("No OPEN alerts to resolve")
        # Assign first
        requests.post(
            fraud_url(f"/api/v1/fraud/alerts/{alerts[0]['id']}/assign"),
            json={"assignee": "pytest"},
            headers=admin_headers, timeout=TIMEOUT,
        )
        r = requests.post(
            fraud_url(f"/api/v1/fraud/alerts/{alerts[0]['id']}/resolve"),
            json={"resolvedBy": "pytest", "confirmedFraud": False, "notes": "FP in test"},
            headers=admin_headers, timeout=TIMEOUT,
        )
        assert r.status_code == 200
        assert r.json()["status"] == "FALSE_POSITIVE"

    def test_resolve_nonexistent(self, admin_headers):
        fake_id = "00000000-0000-0000-0000-000000000000"
        r = requests.post(
            fraud_url(f"/api/v1/fraud/alerts/{fake_id}/resolve"),
            json={"resolvedBy": "test", "confirmedFraud": False, "notes": "n/a"},
            headers=admin_headers, timeout=TIMEOUT,
        )
        assert r.status_code in (404, 500)

    def test_assign_invalid_body(self, admin_headers):
        r = requests.post(
            fraud_url("/api/v1/fraud/alerts/00000000-0000-0000-0000-000000000001/assign"),
            data="not json",
            headers=admin_headers, timeout=TIMEOUT,
        )
        assert r.status_code in (400, 500)

    def test_bulk_assign(self, admin_headers):
        r = requests.post(
            fraud_url("/api/v1/fraud/alerts/bulk/assign"),
            json={"alertIds": [], "performedBy": "pytest", "notes": "bulk test"},
            headers=admin_headers, timeout=TIMEOUT,
        )
        assert r.status_code == 200
        assert r.json()["assigned"] == 0

    def test_bulk_resolve(self, admin_headers):
        r = requests.post(
            fraud_url("/api/v1/fraud/alerts/bulk/resolve"),
            json={"alertIds": [], "performedBy": "pytest", "notes": "bulk test"},
            headers=admin_headers, timeout=TIMEOUT,
        )
        assert r.status_code == 200
        assert r.json()["resolved"] == 0


# ===========================================================================
# 6. Investigation Cases — fraud-service
# ===========================================================================
@pytest.mark.fraud
class TestFraudCases:

    @pytest.fixture(scope="class")
    def created_case(self, admin_headers, test_customer):
        """Create a case for subsequent tests."""
        payload = {
            "title": f"Test Case {unique_id('CS')}",
            "description": "Comprehensive test case",
            "priority": "HIGH",
            "customerId": test_customer["_customerId"],
            "assignedTo": "pytest-analyst",
            "alertIds": [],
            "tags": ["test", "automated"],
        }
        r = requests.post(fraud_url("/api/v1/fraud/cases"),
                          json=payload, headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 201, f"Create case: {r.status_code} {r.text[:300]}"
        return r.json()

    def test_list_cases(self, admin_headers):
        r = requests.get(fraud_url("/api/v1/fraud/cases"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200
        body = r.json()
        assert "content" in body
        assert "totalElements" in body

    def test_list_cases_pagination(self, admin_headers):
        r = requests.get(fraud_url("/api/v1/fraud/cases?page=0&size=5"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200

    def test_list_cases_filter_status(self, admin_headers):
        r = requests.get(fraud_url("/api/v1/fraud/cases?status=OPEN"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200

    def test_create_case(self, admin_headers, created_case):
        assert "id" in created_case
        assert "caseNumber" in created_case
        assert created_case["status"] == "OPEN"

    def test_get_case_by_id(self, admin_headers, created_case):
        r = requests.get(fraud_url(f"/api/v1/fraud/cases/{created_case['id']}"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200
        assert r.json()["id"] == created_case["id"]

    def test_get_case_not_found(self, admin_headers):
        fake_id = "00000000-0000-0000-0000-000000000000"
        r = requests.get(fraud_url(f"/api/v1/fraud/cases/{fake_id}"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code in (404, 500)

    def test_update_case_status(self, admin_headers, created_case):
        r = requests.put(
            fraud_url(f"/api/v1/fraud/cases/{created_case['id']}"),
            json={"status": "INVESTIGATING"},
            headers=admin_headers, timeout=TIMEOUT,
        )
        assert r.status_code == 200
        assert r.json()["status"] == "INVESTIGATING"

    def test_add_case_note(self, admin_headers, created_case):
        r = requests.post(
            fraud_url(f"/api/v1/fraud/cases/{created_case['id']}/notes"),
            json={"content": "Automated test note", "author": "pytest", "noteType": "COMMENT"},
            headers=admin_headers, timeout=TIMEOUT,
        )
        assert r.status_code == 201

    def test_get_case_timeline(self, admin_headers, created_case):
        r = requests.get(fraud_url(f"/api/v1/fraud/cases/{created_case['id']}/timeline"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200
        body = r.json()
        assert "caseId" in body
        assert "events" in body
        assert isinstance(body["events"], list)
        assert len(body["events"]) >= 1  # At least the CREATED event


# ===========================================================================
# 7. Detection Rules — fraud-service
# ===========================================================================
@pytest.mark.fraud
class TestFraudRules:

    @pytest.fixture(scope="class")
    def first_rule(self, admin_headers):
        """Get the first rule from the list."""
        r = requests.get(fraud_url("/api/v1/fraud/rules"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200
        rules = r.json()
        if not rules:
            pytest.skip("No fraud rules seeded")
        return rules[0]

    def test_list_rules(self, admin_headers):
        r = requests.get(fraud_url("/api/v1/fraud/rules"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200
        rules = r.json()
        assert isinstance(rules, list)

    def test_rules_have_categories(self, admin_headers):
        r = requests.get(fraud_url("/api/v1/fraud/rules"),
                         headers=admin_headers, timeout=TIMEOUT)
        rules = r.json()
        if rules:
            categories = {rule["category"] for rule in rules}
            assert len(categories) >= 1

    def test_get_rule_by_id(self, admin_headers, first_rule):
        r = requests.get(fraud_url(f"/api/v1/fraud/rules/{first_rule['id']}"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200
        assert r.json()["id"] == first_rule["id"]

    def test_get_rule_not_found(self, admin_headers):
        fake_id = "00000000-0000-0000-0000-000000000000"
        r = requests.get(fraud_url(f"/api/v1/fraud/rules/{fake_id}"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code in (404, 500)

    def test_toggle_rule_enabled(self, admin_headers, first_rule):
        original = first_rule["enabled"]
        # Disable
        r = requests.put(fraud_url(f"/api/v1/fraud/rules/{first_rule['id']}"),
                         json={"enabled": not original},
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200
        assert r.json()["enabled"] == (not original)
        # Restore
        r2 = requests.put(fraud_url(f"/api/v1/fraud/rules/{first_rule['id']}"),
                          json={"enabled": original},
                          headers=admin_headers, timeout=TIMEOUT)
        assert r2.status_code == 200
        assert r2.json()["enabled"] == original

    def test_change_rule_severity(self, admin_headers, first_rule):
        original = first_rule["severity"]
        new_severity = "CRITICAL" if original != "CRITICAL" else "HIGH"
        r = requests.put(fraud_url(f"/api/v1/fraud/rules/{first_rule['id']}"),
                         json={"severity": new_severity},
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200
        assert r.json()["severity"] == new_severity
        # Restore
        requests.put(fraud_url(f"/api/v1/fraud/rules/{first_rule['id']}"),
                     json={"severity": original},
                     headers=admin_headers, timeout=TIMEOUT)


# ===========================================================================
# 8. SAR/CTR Reports — fraud-service
# ===========================================================================
@pytest.mark.fraud
class TestSarReports:

    @pytest.fixture(scope="class")
    def created_sar(self, admin_headers, test_customer):
        """Create a SAR report for subsequent tests."""
        payload = {
            "reportType": "SAR",
            "subjectCustomerId": test_customer["_customerId"],
            "subjectName": "Pytest Runner",
            "subjectNationalId": unique_id("NID"),
            "narrative": "Suspicious activity detected during automated testing",
            "suspiciousAmount": 500000,
            "preparedBy": "pytest",
        }
        r = requests.post(fraud_url("/api/v1/fraud/sar-reports"),
                          json=payload, headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 201, f"Create SAR: {r.status_code} {r.text[:300]}"
        return r.json()

    def test_create_sar(self, admin_headers, created_sar):
        assert "id" in created_sar
        assert "reportNumber" in created_sar
        assert created_sar["reportType"] == "SAR"
        assert created_sar["status"] == "DRAFT"

    def test_create_ctr(self, admin_headers, test_customer):
        payload = {
            "reportType": "CTR",
            "subjectCustomerId": test_customer["_customerId"],
            "subjectName": "Pytest CTR",
            "subjectNationalId": unique_id("NID"),
            "narrative": "Currency transaction report",
            "suspiciousAmount": 1000000,
            "preparedBy": "pytest",
        }
        r = requests.post(fraud_url("/api/v1/fraud/sar-reports"),
                          json=payload, headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 201
        assert r.json()["reportType"] == "CTR"

    def test_list_sar_reports(self, admin_headers, created_sar):
        r = requests.get(fraud_url("/api/v1/fraud/sar-reports"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200
        body = r.json()
        assert "content" in body
        assert body["totalElements"] >= 1

    def test_list_sar_filter_type(self, admin_headers, created_sar):
        r = requests.get(fraud_url("/api/v1/fraud/sar-reports?reportType=SAR"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200

    def test_get_sar_by_id(self, admin_headers, created_sar):
        r = requests.get(fraud_url(f"/api/v1/fraud/sar-reports/{created_sar['id']}"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200
        assert r.json()["id"] == created_sar["id"]

    def test_sar_workflow_draft_to_filed(self, admin_headers, created_sar):
        """Walk through DRAFT → PENDING_REVIEW → APPROVED → FILED."""
        sar_id = created_sar["id"]

        # DRAFT → PENDING_REVIEW
        r = requests.put(fraud_url(f"/api/v1/fraud/sar-reports/{sar_id}"),
                         json={"status": "PENDING_REVIEW", "reviewedBy": "reviewer1"},
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200
        assert r.json()["status"] == "PENDING_REVIEW"

        # PENDING_REVIEW → APPROVED
        r = requests.put(fraud_url(f"/api/v1/fraud/sar-reports/{sar_id}"),
                         json={"status": "APPROVED"},
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200
        assert r.json()["status"] == "APPROVED"

        # APPROVED → FILED
        r = requests.put(fraud_url(f"/api/v1/fraud/sar-reports/{sar_id}"),
                         json={"status": "FILED", "filedBy": "filer1",
                               "filingReference": unique_id("FIL")},
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200
        assert r.json()["status"] == "FILED"
        assert r.json()["filedBy"] is not None

    def test_sar_reject(self, admin_headers, test_customer):
        """Create a SAR and reject it."""
        payload = {
            "reportType": "SAR",
            "subjectCustomerId": test_customer["_customerId"],
            "subjectName": "Reject Test",
            "subjectNationalId": unique_id("NID"),
            "narrative": "To be rejected",
            "preparedBy": "pytest",
        }
        r = requests.post(fraud_url("/api/v1/fraud/sar-reports"),
                          json=payload, headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 201
        sar_id = r.json()["id"]

        r = requests.put(fraud_url(f"/api/v1/fraud/sar-reports/{sar_id}"),
                         json={"status": "REJECTED", "reviewedBy": "reviewer2"},
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200
        assert r.json()["status"] == "REJECTED"


# ===========================================================================
# 9. Watchlist — fraud-service
# ===========================================================================
@pytest.mark.fraud
class TestWatchlist:

    @pytest.fixture(scope="class")
    def created_entry(self, admin_headers):
        """Create a watchlist entry."""
        payload = {
            "listType": "PEP",
            "entryType": "INDIVIDUAL",
            "name": f"Test PEP {unique_id('WL')}",
            "nationalId": unique_id("NID"),
            "phone": "+254700000099",
            "reason": "Automated test entry",
            "source": "pytest",
        }
        r = requests.post(fraud_url("/api/v1/fraud/watchlist"),
                          json=payload, headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 201, f"Create watchlist: {r.status_code} {r.text[:300]}"
        return r.json()

    def test_create_pep_entry(self, admin_headers, created_entry):
        assert created_entry["listType"] == "PEP"
        assert created_entry["active"] is True

    def test_create_sanctions_entry(self, admin_headers):
        payload = {
            "listType": "SANCTIONS",
            "entryType": "INDIVIDUAL",
            "name": f"Sanctioned {unique_id('WL')}",
            "nationalId": unique_id("NID"),
            "reason": "OFAC test",
            "source": "pytest",
        }
        r = requests.post(fraud_url("/api/v1/fraud/watchlist"),
                          json=payload, headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 201
        assert r.json()["listType"] == "SANCTIONS"

    def test_create_blacklist_entry(self, admin_headers):
        payload = {
            "listType": "INTERNAL_BLACKLIST",
            "entryType": "INDIVIDUAL",
            "name": f"Blacklisted {unique_id('WL')}",
            "reason": "Internal policy",
            "source": "pytest",
        }
        r = requests.post(fraud_url("/api/v1/fraud/watchlist"),
                          json=payload, headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 201

    def test_create_adverse_media(self, admin_headers):
        payload = {
            "listType": "ADVERSE_MEDIA",
            "entryType": "INDIVIDUAL",
            "name": f"Media {unique_id('WL')}",
            "reason": "News report",
            "source": "pytest",
        }
        r = requests.post(fraud_url("/api/v1/fraud/watchlist"),
                          json=payload, headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 201

    def test_list_watchlist(self, admin_headers, created_entry):
        r = requests.get(fraud_url("/api/v1/fraud/watchlist"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200
        body = r.json()
        assert "content" in body
        assert body["totalElements"] >= 1

    def test_list_watchlist_filter_active(self, admin_headers, created_entry):
        r = requests.get(fraud_url("/api/v1/fraud/watchlist?active=true"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200

    def test_get_watchlist_by_id(self, admin_headers, created_entry):
        r = requests.get(fraud_url(f"/api/v1/fraud/watchlist/{created_entry['id']}"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200
        assert r.json()["id"] == created_entry["id"]

    def test_deactivate_watchlist_entry(self, admin_headers, created_entry):
        r = requests.put(
            fraud_url(f"/api/v1/fraud/watchlist/{created_entry['id']}/deactivate"),
            headers=admin_headers, timeout=TIMEOUT,
        )
        assert r.status_code == 200
        assert r.json()["active"] is False

    def test_screen_customer(self, admin_headers):
        """Screen a customer name against watchlist."""
        r = requests.post(
            fraud_url("/api/v1/fraud/watchlist/screen"),
            json={"customerId": "CUST-999", "name": "No Match Name",
                  "nationalId": "NOMATCH", "phone": "+000000000000"},
            headers=admin_headers, timeout=TIMEOUT,
        )
        assert r.status_code == 200
        body = r.json()
        assert "matches" in body
        assert "matchCount" in body
        assert isinstance(body["matchCount"], int)


# ===========================================================================
# 10. Network & Audit — fraud-service
# ===========================================================================
@pytest.mark.fraud
class TestNetworkAnalysis:

    def test_get_network_links(self, admin_headers, test_customer):
        cid = test_customer["_customerId"]
        r = requests.get(fraud_url(f"/api/v1/fraud/network/{cid}"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200
        assert isinstance(r.json(), list)

    def test_network_unknown_customer(self, admin_headers):
        r = requests.get(fraud_url(f"/api/v1/fraud/network/{unique_id('NET')}"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200
        assert r.json() == []


@pytest.mark.fraud
class TestFraudAuditLog:

    def test_list_audit_log(self, admin_headers):
        r = requests.get(fraud_url("/api/v1/fraud/audit"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200
        body = r.json()
        assert "content" in body
        assert "totalElements" in body

    def test_audit_pagination(self, admin_headers):
        r = requests.get(fraud_url("/api/v1/fraud/audit?page=0&size=5"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200
        assert r.json()["size"] <= 5

    def test_audit_filter_entity_type(self, admin_headers):
        r = requests.get(fraud_url("/api/v1/fraud/audit?entityType=ALERT"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200

    def test_audit_field_validation(self, admin_headers):
        r = requests.get(fraud_url("/api/v1/fraud/audit"),
                         headers=admin_headers, timeout=TIMEOUT)
        body = r.json()
        content = body.get("content") or []
        for entry in content[:3]:
            assert "action" in entry
            assert "entityType" in entry
            assert "createdAt" in entry


# ===========================================================================
# 11. Customer Risk Profiles — fraud-service
# ===========================================================================
@pytest.mark.fraud
class TestCustomerRiskProfiles:

    def test_get_profile_unknown(self, admin_headers):
        cid = unique_id("RISK")
        r = requests.get(fraud_url(f"/api/v1/fraud/risk-profiles/{cid}"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code in (404, 500)

    def test_high_risk_list(self, admin_headers):
        r = requests.get(fraud_url("/api/v1/fraud/risk-profiles/high-risk"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200
        body = r.json()
        assert "content" in body
        assert "totalElements" in body

    def test_high_risk_pagination(self, admin_headers):
        r = requests.get(fraud_url("/api/v1/fraud/risk-profiles/high-risk?page=0&size=5"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200
        assert r.json()["size"] <= 5
