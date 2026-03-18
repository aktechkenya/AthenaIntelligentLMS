"""Tests for the Isolation Forest anomaly detector."""
import numpy as np
import pandas as pd
import pytest

from models.anomaly_detector import (
    AnomalyDetector, AnomalyTrainer, ANOMALY_FEATURES,
)


class TestAnomalyTrainer:
    """Test training pipeline."""

    def test_train_produces_model_and_metrics(self, tmp_path, monkeypatch):
        """Training on synthetic data should produce valid metrics."""
        monkeypatch.setenv("MODEL_DIR", str(tmp_path))
        # Re-import to pick up new MODEL_DIR
        import importlib
        import models.anomaly_detector as mod
        importlib.reload(mod)

        np.random.seed(42)
        n = 200
        data = {feat: np.random.randn(n) for feat in ANOMALY_FEATURES}
        df = pd.DataFrame(data)

        trainer = mod.AnomalyTrainer()
        metrics = trainer.train(df, contamination=0.05)

        assert metrics["n_samples"] == n
        assert metrics["n_features"] == len(ANOMALY_FEATURES)
        assert 0 < metrics["n_anomalies_detected"] <= n
        assert 0 < metrics["anomaly_rate"] < 1
        assert (tmp_path / "isolation_forest.pkl").exists()
        assert (tmp_path / "anomaly_scaler.pkl").exists()

    def test_train_rejects_too_few_features(self, tmp_path, monkeypatch):
        """Training with too few features should raise ValueError."""
        monkeypatch.setenv("MODEL_DIR", str(tmp_path))
        import importlib
        import models.anomaly_detector as mod
        importlib.reload(mod)

        df = pd.DataFrame({"only_one_col": [1, 2, 3]})
        trainer = mod.AnomalyTrainer()

        with pytest.raises(ValueError, match="Too few features"):
            trainer.train(df)


class TestAnomalyDetector:
    """Test inference pipeline."""

    def test_predict_without_model_returns_none(self):
        """Detector with no loaded model should return None."""
        detector = AnomalyDetector()
        detector.model = None
        detector.scaler = None
        result = detector.predict({"amount_log": 1.0})
        assert result is None

    def test_predict_with_model(self, tmp_path, monkeypatch):
        """Detector with trained model should return valid scores."""
        monkeypatch.setenv("MODEL_DIR", str(tmp_path))
        import importlib
        import models.anomaly_detector as mod
        importlib.reload(mod)

        # Train first
        np.random.seed(42)
        data = {feat: np.random.randn(100) for feat in ANOMALY_FEATURES}
        df = pd.DataFrame(data)
        mod.AnomalyTrainer().train(df, contamination=0.1)

        # Now load and predict
        detector = mod.AnomalyDetector()
        assert detector.model is not None

        features = {feat: 0.0 for feat in ANOMALY_FEATURES}
        result = detector.predict(features)

        assert result is not None
        assert "anomaly_score" in result
        assert "is_anomaly" in result
        assert 0.0 <= result["anomaly_score"] <= 1.0
        assert isinstance(result["is_anomaly"], (bool, np.bool_))

    def test_extreme_values_detected_as_anomaly(self, tmp_path, monkeypatch):
        """Extreme feature values should score higher anomaly."""
        monkeypatch.setenv("MODEL_DIR", str(tmp_path))
        import importlib
        import models.anomaly_detector as mod
        importlib.reload(mod)

        np.random.seed(42)
        data = {feat: np.random.randn(200) for feat in ANOMALY_FEATURES}
        df = pd.DataFrame(data)
        mod.AnomalyTrainer().train(df, contamination=0.05)

        detector = mod.AnomalyDetector()

        # Normal transaction
        normal = {feat: 0.0 for feat in ANOMALY_FEATURES}
        normal_result = detector.predict(normal)

        # Extreme transaction (all features at 10 std devs)
        extreme = {feat: 10.0 for feat in ANOMALY_FEATURES}
        extreme_result = detector.predict(extreme)

        assert extreme_result["anomaly_score"] > normal_result["anomaly_score"]
