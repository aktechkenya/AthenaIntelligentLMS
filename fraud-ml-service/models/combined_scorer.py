"""
Combined Scoring Pipeline.

Merges rule-engine scores from the Java fraud-detection-service with
ML model scores to produce a final fraud risk assessment.

final_score = w_rule * rule_score + w_anomaly * anomaly_score + w_lgbm * lgbm_score
"""
from __future__ import annotations

import os
from typing import Any, Dict, Optional

import structlog

from models.anomaly_detector import get_anomaly_detector
from models.fraud_scorer import get_fraud_scorer

logger = structlog.get_logger(__name__)

# Configurable weights for the ensemble
W_RULE = float(os.getenv("SCORE_WEIGHT_RULE", "0.3"))
W_ANOMALY = float(os.getenv("SCORE_WEIGHT_ANOMALY", "0.3"))
W_LGBM = float(os.getenv("SCORE_WEIGHT_LGBM", "0.4"))


def combined_score(
    transaction_features: Dict[str, Any],
    customer_features: Optional[Dict[str, Any]] = None,
    rule_score: Optional[float] = None,
) -> Dict[str, Any]:
    """
    Produce a combined fraud risk score from all available signals.

    Args:
        transaction_features: Per-event features for anomaly model
        customer_features: Aggregated customer features for LightGBM (optional)
        rule_score: Score from the Java rule engine (0-1), passed from upstream

    Returns:
        Combined score dict with individual component scores and risk level.
    """
    result: Dict[str, Any] = {
        "rule_score": rule_score,
        "anomaly_result": None,
        "lgbm_result": None,
        "final_score": None,
        "risk_level": "LOW",
        "model_available": False,
    }

    # ── Anomaly Detection ────────────────────────────────────────────────
    detector = get_anomaly_detector()
    anomaly_result = detector.predict(transaction_features)
    result["anomaly_result"] = anomaly_result

    # ── LightGBM Customer Fraud Scorer ───────────────────────────────────
    lgbm_result = None
    if customer_features:
        scorer = get_fraud_scorer("champion")
        lgbm_result = scorer.predict(customer_features)
        result["lgbm_result"] = lgbm_result

    # ── Compute Combined Score ───────────────────────────────────────────
    scores = []
    weights = []

    if rule_score is not None:
        scores.append(rule_score)
        weights.append(W_RULE)

    if anomaly_result is not None:
        scores.append(anomaly_result["anomaly_score"])
        weights.append(W_ANOMALY)
        result["model_available"] = True

    if lgbm_result is not None:
        scores.append(lgbm_result["fraud_probability"])
        weights.append(W_LGBM)
        result["model_available"] = True

    if scores:
        total_weight = sum(weights)
        final = sum(s * w for s, w in zip(scores, weights)) / total_weight
        result["final_score"] = round(final, 4)

        if final >= 0.8:
            result["risk_level"] = "CRITICAL"
        elif final >= 0.6:
            result["risk_level"] = "HIGH"
        elif final >= 0.3:
            result["risk_level"] = "MEDIUM"
        else:
            result["risk_level"] = "LOW"
    elif rule_score is not None:
        # Fallback: only rule score available
        result["final_score"] = rule_score
        if rule_score >= 0.8:
            result["risk_level"] = "CRITICAL"
        elif rule_score >= 0.6:
            result["risk_level"] = "HIGH"
        elif rule_score >= 0.3:
            result["risk_level"] = "MEDIUM"

    return result
