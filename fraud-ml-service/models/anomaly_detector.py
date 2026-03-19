"""
Transaction Anomaly Detection — Isolation Forest Model.

Detects unusual transaction patterns in real-time by scoring individual
events against a model trained on normal transaction distributions.
"""
from __future__ import annotations

import os
import pickle
from pathlib import Path
from typing import Dict, Any, Optional

import numpy as np
import pandas as pd
import structlog
from sklearn.ensemble import IsolationForest
from sklearn.preprocessing import StandardScaler

logger = structlog.get_logger(__name__)

MODEL_DIR = Path(os.getenv("MODEL_DIR", "/app/model_artifacts"))
ANOMALY_MODEL_PATH = MODEL_DIR / "isolation_forest.pkl"
ANOMALY_SCALER_PATH = MODEL_DIR / "anomaly_scaler.pkl"

# Features used by the anomaly detector (must match feature_engineer output)
ANOMALY_FEATURES = [
    "amount_log", "is_round_amount", "hour_of_day", "day_of_week",
    "is_weekend", "is_night",
    "txn_count_1h", "txn_amount_1h", "transfer_count_1h",
    "credit_count_1h", "round_amount_count_1h",
    "txn_count_24h", "txn_amount_24h", "transfer_count_24h",
    "loan_app_count_24h", "payment_reversed_24h",
    "amount_to_24h_ratio", "amount_deviation",
    "customer_risk_score", "customer_total_alerts",
    "customer_open_alerts", "customer_confirmed_fraud",
    "customer_txn_count_30d",
]


class AnomalyDetector:
    """Isolation Forest-based transaction anomaly scorer."""

    def __init__(self):
        self.model: Optional[IsolationForest] = None
        self.scaler: Optional[StandardScaler] = None
        self._load_model()

    def _load_model(self):
        try:
            if ANOMALY_MODEL_PATH.exists() and ANOMALY_SCALER_PATH.exists():
                with open(ANOMALY_MODEL_PATH, "rb") as f:
                    self.model = pickle.load(f)
                with open(ANOMALY_SCALER_PATH, "rb") as f:
                    self.scaler = pickle.load(f)
                logger.info("Anomaly detector loaded", path=str(ANOMALY_MODEL_PATH))
            else:
                logger.warning("No anomaly model found — will use rule-based fallback")
        except Exception as e:
            logger.error("Failed to load anomaly model", error=str(e))

    def predict(self, features: Dict[str, Any]) -> Optional[Dict[str, Any]]:
        """
        Score a transaction.
        Returns dict with anomaly_score (0-1, higher=more anomalous) and is_anomaly flag.
        """
        if self.model is None or self.scaler is None:
            return None

        try:
            df = pd.DataFrame([{k: features.get(k, 0) for k in ANOMALY_FEATURES}])
            X_scaled = self.scaler.transform(df)

            # decision_function: negative = anomaly, positive = normal
            raw_score = self.model.decision_function(X_scaled)[0]
            prediction = self.model.predict(X_scaled)[0]  # -1 = anomaly, 1 = normal

            # Normalize to 0-1 scale (higher = more anomalous)
            anomaly_score = max(0.0, min(1.0, 0.5 - raw_score))

            return {
                "anomaly_score": round(float(anomaly_score), 4),
                "is_anomaly": bool(prediction == -1),
                "raw_score": round(float(raw_score), 4),
            }
        except Exception as e:
            logger.error("Anomaly prediction failed", error=str(e))
            return None

    def reload(self):
        self._load_model()


class AnomalyTrainer:
    """Train and persist the Isolation Forest model."""

    @staticmethod
    def train(df: pd.DataFrame, contamination: float = 0.05) -> Dict[str, Any]:
        """
        Train an Isolation Forest on transaction features.
        Returns training metrics.
        """
        available = [f for f in ANOMALY_FEATURES if f in df.columns]
        if len(available) < len(ANOMALY_FEATURES) * 0.5:
            raise ValueError(
                f"Too few features available: {len(available)}/{len(ANOMALY_FEATURES)}"
            )

        X = df[available].fillna(0)

        scaler = StandardScaler()
        X_scaled = scaler.fit_transform(X)

        model = IsolationForest(
            n_estimators=200,
            max_samples="auto",
            contamination=contamination,
            random_state=42,
            n_jobs=-1,
        )
        model.fit(X_scaled)

        # Compute training metrics
        scores = model.decision_function(X_scaled)
        predictions = model.predict(X_scaled)
        n_anomalies = int((predictions == -1).sum())

        MODEL_DIR.mkdir(parents=True, exist_ok=True)
        with open(ANOMALY_MODEL_PATH, "wb") as f:
            pickle.dump(model, f)
        with open(ANOMALY_SCALER_PATH, "wb") as f:
            pickle.dump(scaler, f)

        metrics = {
            "n_samples": len(df),
            "n_features": len(available),
            "n_anomalies_detected": n_anomalies,
            "anomaly_rate": round(n_anomalies / len(df), 4),
            "mean_score": round(float(np.mean(scores)), 4),
            "std_score": round(float(np.std(scores)), 4),
            "contamination": contamination,
        }

        logger.info("Isolation Forest trained", **metrics)
        return metrics


# Module-level singleton
_detector: Optional[AnomalyDetector] = None


def get_anomaly_detector() -> AnomalyDetector:
    global _detector
    if _detector is None:
        _detector = AnomalyDetector()
    return _detector
