"""
Application Fraud Scorer — LightGBM supervised model.

Predicts the probability that a customer is engaged in fraudulent activity
based on their aggregated behavioral features. Uses the MLflow model
registry with champion/challenger pattern.
"""
from __future__ import annotations

import os
from typing import Any, Dict, List, Optional

import lightgbm as lgb
import mlflow
import mlflow.lightgbm
import numpy as np
import pandas as pd
import structlog
from sklearn.metrics import roc_auc_score, average_precision_score, f1_score
from sklearn.model_selection import train_test_split

logger = structlog.get_logger(__name__)

MLFLOW_URI = os.getenv("MLFLOW_TRACKING_URI", "http://mlflow:5000")
EXPERIMENT_NAME = os.getenv("MLFLOW_EXPERIMENT_NAME", "athena-fraud-scorer")
MODEL_NAME = os.getenv("MLFLOW_MODEL_NAME", "AthenaFraudScorer")


def ks_statistic(y_true: List, y_prob: List) -> float:
    """Kolmogorov-Smirnov statistic between positive and negative distributions."""
    from scipy.stats import ks_2samp
    pos = [p for p, y in zip(y_prob, y_true) if y == 1]
    neg = [p for p, y in zip(y_prob, y_true) if y == 0]
    if not pos or not neg:
        return 0.0
    stat, _ = ks_2samp(pos, neg)
    return float(stat)


class FraudScorer:
    """
    Loads a LightGBM fraud scoring model from MLflow registry.
    Supports champion/challenger aliasing.
    """

    def __init__(self, model_alias: str = "champion"):
        mlflow.set_tracking_uri(MLFLOW_URI)
        self.model_alias = model_alias
        self.model = None
        self._load_model()

    def _load_model(self):
        # Try alias first, then fall back to latest version
        for uri in [
            f"models:/{MODEL_NAME}@{self.model_alias}",
            f"models:/{MODEL_NAME}/latest",
        ]:
            try:
                self.model = mlflow.lightgbm.load_model(uri)
                logger.info("Fraud model loaded", alias=self.model_alias, uri=uri)
                return
            except Exception:
                continue

        logger.warning(
            "Fraud model not found — scoring uses rule-based fallback",
            alias=self.model_alias,
        )
        self.model = None

    def predict(self, features: Dict[str, Any]) -> Optional[Dict[str, Any]]:
        """
        Predict fraud probability for a customer.
        Returns dict with fraud_probability and risk_level.
        """
        if self.model is None:
            return None
        try:
            df = pd.DataFrame([features])
            proba = self.model.predict_proba(df)
            fraud_prob = float(proba[0, 1])

            risk_level = "LOW"
            if fraud_prob >= 0.8:
                risk_level = "CRITICAL"
            elif fraud_prob >= 0.6:
                risk_level = "HIGH"
            elif fraud_prob >= 0.3:
                risk_level = "MEDIUM"

            return {
                "fraud_probability": round(fraud_prob, 4),
                "risk_level": risk_level,
                "model_alias": self.model_alias,
            }
        except Exception as e:
            logger.error("Fraud scoring failed", error=str(e))
            return None

    def reload(self):
        self._load_model()


def train_and_register(
    df: pd.DataFrame,
    target_col: str = "fraud_label",
    register_as: str = "challenger",
) -> Dict[str, Any]:
    """
    Train a LightGBM fraud detection model and register it in MLflow.
    Returns training metrics and run_id.
    """
    mlflow.set_tracking_uri(MLFLOW_URI)
    mlflow.set_experiment(EXPERIMENT_NAME)

    X = df.drop(columns=[target_col])
    y = df[target_col]

    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.2, random_state=42, stratify=y,
    )

    params = {
        "objective": "binary",
        "metric": "binary_logloss",
        "learning_rate": 0.05,
        "num_leaves": 31,
        "max_depth": 6,
        "min_child_samples": 20,
        "reg_alpha": 0.1,
        "reg_lambda": 0.1,
        "n_estimators": 500,
        "early_stopping_rounds": 50,
        "verbose": -1,
        "class_weight": "balanced",
    }

    with mlflow.start_run(run_name=f"fraud-lgbm-{register_as}") as run:
        mlflow.log_params(params)

        model = lgb.LGBMClassifier(**params)
        model.fit(
            X_train, y_train,
            eval_set=[(X_test, y_test)],
            callbacks=[lgb.early_stopping(50, verbose=False)],
        )

        y_prob = model.predict_proba(X_test)[:, 1]
        y_pred = (y_prob > 0.5).astype(int)

        auc = roc_auc_score(y_test, y_prob)
        ks = ks_statistic(y_test.tolist(), y_prob.tolist())
        pr_auc = average_precision_score(y_test, y_prob)
        f1 = f1_score(y_test, y_pred)

        metrics = {
            "auc_roc": round(auc, 4),
            "ks_statistic": round(ks, 4),
            "pr_auc": round(pr_auc, 4),
            "f1_score": round(f1, 4),
            "n_train": len(X_train),
            "n_test": len(X_test),
            "fraud_rate_train": round(float(y_train.mean()), 4),
            "fraud_rate_test": round(float(y_test.mean()), 4),
        }
        mlflow.log_metrics(metrics)

        # Feature importance
        importance = dict(zip(X.columns.tolist(), model.feature_importances_.tolist()))
        top_features = sorted(importance.items(), key=lambda kv: -kv[1])[:10]
        mlflow.log_param("top_features", str(top_features[:5]))

        result = mlflow.lightgbm.log_model(
            model,
            artifact_path="model",
            registered_model_name=MODEL_NAME,
        )

        # Set alias on the registered model version
        try:
            client = mlflow.tracking.MlflowClient()
            # Get latest version number
            versions = client.search_model_versions(f"name='{MODEL_NAME}'")
            if versions:
                latest_version = max(v.version for v in versions)
                client.set_registered_model_alias(MODEL_NAME, register_as, latest_version)
                # Also set as champion if first model
                if len(versions) <= 1 or register_as == "champion":
                    client.set_registered_model_alias(MODEL_NAME, "champion", latest_version)
                logger.info("Set model alias", alias=register_as, version=latest_version)
        except Exception as e:
            logger.warning("Failed to set model alias", error=str(e))

        run_id = run.info.run_id
        logger.info("Fraud model trained and registered", run_id=run_id, **metrics)

        return {
            "run_id": run_id,
            "metrics": metrics,
            "top_features": top_features,
            "alias": register_as,
        }


# Module-level singleton
_champion: Optional[FraudScorer] = None
_challenger: Optional[FraudScorer] = None


def get_fraud_scorer(alias: str = "champion") -> FraudScorer:
    global _champion, _challenger
    if alias == "champion":
        if _champion is None:
            _champion = FraudScorer("champion")
        return _champion
    else:
        if _challenger is None:
            _challenger = FraudScorer("challenger")
        return _challenger
