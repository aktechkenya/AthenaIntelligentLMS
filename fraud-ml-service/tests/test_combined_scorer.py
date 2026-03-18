"""Tests for the combined scoring pipeline."""
from unittest.mock import patch, MagicMock
from models.combined_scorer import combined_score


class TestCombinedScorer:
    """Test ensemble scoring logic."""

    def test_rule_only_score(self):
        """When only rule score is available, it should be the final score."""
        with patch("models.combined_scorer.get_anomaly_detector") as mock_det, \
             patch("models.combined_scorer.get_fraud_scorer") as mock_scorer:
            mock_det.return_value.predict.return_value = None
            mock_scorer.return_value.predict.return_value = None

            result = combined_score(
                transaction_features={},
                customer_features={},
                rule_score=0.7,
            )

            assert result["final_score"] == 0.7
            assert result["risk_level"] == "HIGH"
            assert result["model_available"] is False

    def test_all_models_available(self):
        """Combined score with all three signals."""
        with patch("models.combined_scorer.get_anomaly_detector") as mock_det, \
             patch("models.combined_scorer.get_fraud_scorer") as mock_scorer:
            mock_det.return_value.predict.return_value = {
                "anomaly_score": 0.8, "is_anomaly": True, "raw_score": -0.3,
            }
            mock_scorer.return_value.predict.return_value = {
                "fraud_probability": 0.6, "risk_level": "HIGH", "model_alias": "champion",
            }

            result = combined_score(
                transaction_features={},
                customer_features={"feature1": 1.0},
                rule_score=0.5,
            )

            assert result["final_score"] is not None
            assert result["model_available"] is True
            assert result["anomaly_result"]["anomaly_score"] == 0.8
            assert result["lgbm_result"]["fraud_probability"] == 0.6
            # Weighted: (0.3*0.5 + 0.3*0.8 + 0.4*0.6) / 1.0 = 0.63
            assert abs(result["final_score"] - 0.63) < 0.01
            assert result["risk_level"] == "HIGH"

    def test_anomaly_only_with_rule(self):
        """Combined score with rule + anomaly (no LightGBM)."""
        with patch("models.combined_scorer.get_anomaly_detector") as mock_det, \
             patch("models.combined_scorer.get_fraud_scorer") as mock_scorer:
            mock_det.return_value.predict.return_value = {
                "anomaly_score": 0.9, "is_anomaly": True, "raw_score": -0.4,
            }
            mock_scorer.return_value.predict.return_value = None

            result = combined_score(
                transaction_features={},
                customer_features=None,
                rule_score=0.7,
            )

            assert result["final_score"] is not None
            # Weighted: (0.3*0.7 + 0.3*0.9) / 0.6 = 0.8
            assert abs(result["final_score"] - 0.8) < 0.01
            assert result["risk_level"] == "CRITICAL"

    def test_no_signals_returns_none_score(self):
        """When no signals are available, final_score should be None."""
        with patch("models.combined_scorer.get_anomaly_detector") as mock_det, \
             patch("models.combined_scorer.get_fraud_scorer") as mock_scorer:
            mock_det.return_value.predict.return_value = None
            mock_scorer.return_value.predict.return_value = None

            result = combined_score(
                transaction_features={},
                customer_features=None,
                rule_score=None,
            )

            assert result["final_score"] is None
            assert result["risk_level"] == "LOW"

    def test_risk_level_boundaries(self):
        """Test risk level classification at boundaries."""
        with patch("models.combined_scorer.get_anomaly_detector") as mock_det, \
             patch("models.combined_scorer.get_fraud_scorer"):
            mock_det.return_value.predict.return_value = None

            # LOW: < 0.3
            r1 = combined_score({}, None, rule_score=0.2)
            assert r1["risk_level"] == "LOW"

            # MEDIUM: 0.3-0.6
            r2 = combined_score({}, None, rule_score=0.45)
            assert r2["risk_level"] == "MEDIUM"

            # HIGH: 0.6-0.8
            r3 = combined_score({}, None, rule_score=0.7)
            assert r3["risk_level"] == "HIGH"

            # CRITICAL: >= 0.8
            r4 = combined_score({}, None, rule_score=0.9)
            assert r4["risk_level"] == "CRITICAL"
