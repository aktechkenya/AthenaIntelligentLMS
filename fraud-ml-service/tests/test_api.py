"""Tests for the FastAPI endpoints (health, scoring, training)."""
import sys
import os
import types
import pytest
from unittest.mock import AsyncMock, MagicMock

# Ensure the app root is on sys.path
_app_root = os.path.join(os.path.dirname(__file__), "..")
if _app_root not in sys.path:
    sys.path.insert(0, _app_root)

# Inject mock db.database into sys.modules BEFORE main.py imports it,
# avoiding the real module which requires asyncpg at import time.
_mock_db_mod = types.ModuleType("db")
_mock_db_database = types.ModuleType("db.database")
_mock_db_database.init_db = AsyncMock()
_mock_db_database.engine = MagicMock()
_mock_db_database.async_session = MagicMock()
_mock_db_database.AsyncSessionLocal = MagicMock()
_mock_db_database.get_db = MagicMock()
sys.modules.setdefault("db", _mock_db_mod)
sys.modules["db.database"] = _mock_db_database

# Also mock feedback.loop to avoid scheduler side-effects
_mock_feedback = types.ModuleType("feedback")
_mock_feedback_loop = types.ModuleType("feedback.loop")
_mock_sched = MagicMock()
_mock_sched.shutdown = MagicMock()
_mock_feedback_loop.start_scheduler = MagicMock(return_value=_mock_sched)
sys.modules.setdefault("feedback", _mock_feedback)
sys.modules["feedback.loop"] = _mock_feedback_loop

from main import app  # noqa: E402
from fastapi.testclient import TestClient  # noqa: E402


@pytest.fixture
def client():
    """Create test client with mocked DB and scheduler."""
    with TestClient(app) as c:
        yield c


class TestHealthEndpoint:

    def test_health_returns_ok(self, client):
        r = client.get("/health")
        assert r.status_code == 200
        body = r.json()
        assert body["status"] == "ok"
        assert body["service"] == "fraud-ml-service"
        assert "models" in body
        assert "anomaly_detector" in body["models"]
        assert "fraud_scorer" in body["models"]

    def test_health_reports_model_status(self, client):
        r = client.get("/health")
        body = r.json()
        assert body["models"]["anomaly_detector"] in ("loaded", "not_loaded")
        assert body["models"]["fraud_scorer"] in ("loaded", "not_loaded")


class TestScoringEndpoints:

    def test_score_transaction_missing_fields(self, client):
        """Missing required fields should return 422."""
        r = client.post("/api/v1/score/transaction", json={})
        assert r.status_code == 422

    def test_score_customer_missing_fields(self, client):
        r = client.post("/api/v1/score/customer", json={})
        assert r.status_code == 422

    def test_score_combined_missing_fields(self, client):
        r = client.post("/api/v1/score/combined", json={})
        assert r.status_code == 422


class TestTrainingEndpoints:

    def test_training_status(self, client):
        r = client.get("/api/v1/train/status")
        assert r.status_code == 200
        body = r.json()
        assert "anomaly" in body
        assert "lgbm" in body
        assert body["anomaly"]["status"] in ("idle", "running", "completed", "failed")

    def test_reload_models(self, client):
        r = client.post("/api/v1/models/reload")
        assert r.status_code == 200
        body = r.json()
        assert body["status"] == "reloaded"


class TestMetricsEndpoint:

    def test_prometheus_metrics(self, client):
        r = client.get("/metrics")
        assert r.status_code == 200
        assert "fraud_ml_score_requests" in r.text
