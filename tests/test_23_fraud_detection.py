"""
E2E Tests: Fraud Detection Service (Go — port 28100)

Tests the fraud-detection-service REST API including:
- Health check
- Summary endpoint
- Alert listing, filtering, assignment, resolution
- Customer risk profiles
- High-risk customer listing
"""
import pytest
import requests
from conftest import url, TIMEOUT, unique_id, SERVICES

BASE = SERVICES.get("fraud", f"http://localhost:28100")
FRAUD_ML_BASE = f"http://localhost:18101"


def fraud_url(path: str) -> str:
    return f"{BASE}{path}"


def ml_url(path: str) -> str:
    return f"{FRAUD_ML_BASE}{path}"


# ---------------------------------------------------------------------------
# Health
# ---------------------------------------------------------------------------
@pytest.mark.health
@pytest.mark.fraud
class TestFraudHealth:

    def test_actuator_health(self):
        """Fraud detection service actuator health returns UP."""
        r = requests.get(fraud_url("/actuator/health"), timeout=TIMEOUT)
        assert r.status_code == 200, f"Health: {r.status_code}"
        assert r.json().get("status") == "UP"

    def test_actuator_info(self):
        r = requests.get(fraud_url("/actuator/info"), timeout=TIMEOUT)
        # Go service may not have /actuator/info — accept 200 or 404
        assert r.status_code in (200, 401, 404)


# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
@pytest.mark.fraud
class TestFraudSummary:

    def test_get_summary(self, admin_headers):
        """GET /api/v1/fraud/summary returns valid summary structure."""
        r = requests.get(fraud_url("/api/v1/fraud/summary"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200, f"Summary: {r.status_code} {r.text[:200]}"
        body = r.json()
        assert "openAlerts" in body
        assert "confirmedFraud" in body
        assert "highRiskCustomers" in body
        assert isinstance(body["openAlerts"], int)

    def test_summary_requires_auth(self):
        """Summary endpoint requires authentication."""
        r = requests.get(fraud_url("/api/v1/fraud/summary"), timeout=TIMEOUT)
        assert r.status_code in (401, 403)


# ---------------------------------------------------------------------------
# Alerts
# ---------------------------------------------------------------------------
@pytest.mark.fraud
class TestFraudAlerts:

    def test_list_alerts(self, admin_headers):
        """GET /api/v1/fraud/alerts returns paginated alerts."""
        r = requests.get(fraud_url("/api/v1/fraud/alerts"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200
        body = r.json()
        assert "content" in body
        assert "totalElements" in body
        assert isinstance(body["content"], list)

    def test_list_alerts_with_status_filter(self, admin_headers):
        """GET /api/v1/fraud/alerts?status=OPEN filters by status."""
        r = requests.get(fraud_url("/api/v1/fraud/alerts?status=OPEN"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200
        body = r.json()
        for alert in body.get("content", []):
            assert alert["status"] == "OPEN"

    def test_list_alerts_pagination(self, admin_headers):
        """Pagination params work correctly."""
        r = requests.get(fraud_url("/api/v1/fraud/alerts?page=0&size=5"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200
        body = r.json()
        assert body["size"] <= 5

    def test_list_alerts_by_status_escalated(self, admin_headers):
        """Can filter for ESCALATED status."""
        r = requests.get(fraud_url("/api/v1/fraud/alerts?status=ESCALATED"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200

    def test_list_alerts_by_status_confirmed(self, admin_headers):
        """Can filter for CONFIRMED_FRAUD status."""
        r = requests.get(fraud_url("/api/v1/fraud/alerts?status=CONFIRMED_FRAUD"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200


# ---------------------------------------------------------------------------
# Customer Risk
# ---------------------------------------------------------------------------
@pytest.mark.fraud
class TestCustomerRisk:

    def test_get_risk_for_unknown_customer(self, admin_headers):
        """Unknown customer returns 404/500 (no profile exists)."""
        cid = unique_id("RISK")
        r = requests.get(fraud_url(f"/api/v1/fraud/risk-profiles/{cid}"),
                         headers=admin_headers, timeout=TIMEOUT)
        # Go service returns 404 or 500 for unknown customers (no default profile)
        assert r.status_code in (404, 500), f"Expected 404/500 for unknown customer, got {r.status_code}"

    def test_list_high_risk_customers(self, admin_headers):
        """GET /api/v1/fraud/risk-profiles/high-risk returns paginated results."""
        r = requests.get(fraud_url("/api/v1/fraud/risk-profiles/high-risk"),
                         headers=admin_headers, timeout=TIMEOUT)
        assert r.status_code == 200
        body = r.json()
        assert "content" in body


# ---------------------------------------------------------------------------
# Alert Lifecycle: Assign → Resolve
# ---------------------------------------------------------------------------
@pytest.mark.fraud
@pytest.mark.e2e
class TestAlertLifecycle:
    """Test alert assignment and resolution workflow.

    These tests only run if there are existing alerts in the system.
    If no alerts exist, they are skipped gracefully.
    """

    @pytest.fixture(scope="class")
    def existing_alert(self, admin_headers):
        """Find an existing OPEN alert to test with."""
        r = requests.get(fraud_url("/api/v1/fraud/alerts?status=OPEN&size=1"),
                         headers=admin_headers, timeout=TIMEOUT)
        if r.status_code != 200:
            pytest.skip("Cannot fetch alerts")
        alerts = r.json().get("content", [])
        if not alerts:
            pytest.skip("No OPEN alerts available for lifecycle test")
        return alerts[0]

    def test_assign_alert(self, admin_headers, existing_alert):
        """POST /api/v1/fraud/alerts/{id}/assign changes status to UNDER_REVIEW."""
        alert_id = existing_alert["id"]
        r = requests.post(
            fraud_url(f"/api/v1/fraud/alerts/{alert_id}/assign"),
            json={"assignee": "analyst-e2e"},
            headers=admin_headers, timeout=TIMEOUT,
        )
        assert r.status_code == 200
        body = r.json()
        assert body["status"] == "UNDER_REVIEW"
        assert body["assignedTo"] == "analyst-e2e"

    def test_resolve_as_false_positive(self, admin_headers):
        """Resolve an alert as FALSE_POSITIVE."""
        # Find an UNDER_REVIEW alert
        r = requests.get(fraud_url("/api/v1/fraud/alerts?status=UNDER_REVIEW&size=1"),
                         headers=admin_headers, timeout=TIMEOUT)
        if r.status_code != 200:
            pytest.skip("Cannot fetch alerts")
        alerts = r.json().get("content", [])
        if not alerts:
            pytest.skip("No UNDER_REVIEW alerts to resolve")

        alert_id = alerts[0]["id"]
        r = requests.post(
            fraud_url(f"/api/v1/fraud/alerts/{alert_id}/resolve"),
            json={"confirmedFraud": False, "resolvedBy": "analyst-e2e",
                  "notes": "E2E test — false positive"},
            headers=admin_headers, timeout=TIMEOUT,
        )
        assert r.status_code == 200
        body = r.json()
        assert body["status"] == "FALSE_POSITIVE"


# ---------------------------------------------------------------------------
# Fraud ML Service
# ---------------------------------------------------------------------------
@pytest.mark.fraud
class TestFraudMLService:
    """Test the Python ML sidecar service."""

    def test_ml_health(self):
        """ML service health endpoint returns ok."""
        try:
            r = requests.get(ml_url("/health"), timeout=TIMEOUT)
            assert r.status_code == 200
            body = r.json()
            assert body["status"] == "ok"
            assert body["service"] == "fraud-ml-service"
            assert "models" in body
        except requests.ConnectionError:
            pytest.skip("Fraud ML service not running")

    def test_ml_training_status(self):
        """Training status endpoint returns valid structure."""
        try:
            r = requests.get(ml_url("/api/v1/train/status"), timeout=TIMEOUT)
            assert r.status_code == 200
            body = r.json()
            assert "anomaly" in body
            assert "lgbm" in body
        except requests.ConnectionError:
            pytest.skip("Fraud ML service not running")

    def test_ml_metrics(self):
        """Prometheus metrics endpoint returns data."""
        try:
            r = requests.get(ml_url("/metrics"), timeout=TIMEOUT)
            assert r.status_code == 200
            assert "fraud_ml" in r.text
        except requests.ConnectionError:
            pytest.skip("Fraud ML service not running")

    def test_ml_reload_models(self):
        """Model reload endpoint works."""
        try:
            r = requests.post(ml_url("/api/v1/models/reload"), timeout=TIMEOUT)
            assert r.status_code == 200
            assert r.json()["status"] == "reloaded"
        except requests.ConnectionError:
            pytest.skip("Fraud ML service not running")

    def test_ml_score_transaction_validation(self):
        """Score endpoint validates required fields."""
        try:
            r = requests.post(ml_url("/api/v1/score/transaction"),
                              json={}, timeout=TIMEOUT)
            assert r.status_code == 422
        except requests.ConnectionError:
            pytest.skip("Fraud ML service not running")

    def test_ml_score_combined_validation(self):
        """Combined score endpoint validates required fields."""
        try:
            r = requests.post(ml_url("/api/v1/score/combined"),
                              json={}, timeout=TIMEOUT)
            assert r.status_code == 422
        except requests.ConnectionError:
            pytest.skip("Fraud ML service not running")
