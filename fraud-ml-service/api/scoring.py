"""
Scoring API — called by the Java fraud-detection-service for real-time ML scoring.

Endpoints:
  POST /api/v1/score/transaction  — Score a single transaction event
  POST /api/v1/score/customer     — Score a customer's overall fraud risk
  POST /api/v1/score/combined     — Full combined scoring (rule + anomaly + LightGBM)
"""
from __future__ import annotations

import time
from typing import Any, Dict, Optional

import structlog
from fastapi import APIRouter, Depends
from pydantic import BaseModel
from sqlalchemy.ext.asyncio import AsyncSession

from db.database import get_db
from features.feature_engineer import (
    extract_transaction_features,
    extract_customer_features,
)
from models.anomaly_detector import get_anomaly_detector
from models.fraud_scorer import get_fraud_scorer
from models.combined_scorer import combined_score
from monitoring.metrics import SCORE_REQUESTS, SCORE_LATENCY

logger = structlog.get_logger(__name__)

router = APIRouter()


class TransactionScoreRequest(BaseModel):
    tenant_id: str
    customer_id: str
    event_type: str
    amount: Optional[float] = None
    rule_score: Optional[float] = None


class CustomerScoreRequest(BaseModel):
    tenant_id: str
    customer_id: str


class CombinedScoreRequest(BaseModel):
    tenant_id: str
    customer_id: str
    event_type: str
    amount: Optional[float] = None
    rule_score: Optional[float] = None


class ScoreResponse(BaseModel):
    score: Optional[float] = None
    risk_level: str = "LOW"
    model_available: bool = False
    details: Dict[str, Any] = {}
    latency_ms: float = 0.0


@router.post("/score/transaction", response_model=ScoreResponse)
async def score_transaction(
    req: TransactionScoreRequest,
    db: AsyncSession = Depends(get_db),
):
    """Score a single transaction for anomaly detection."""
    start = time.monotonic()

    features = await extract_transaction_features(
        req.tenant_id, req.customer_id, req.event_type, req.amount, db,
    )

    detector = get_anomaly_detector()
    result = detector.predict(features)

    latency = (time.monotonic() - start) * 1000

    if result is None:
        return ScoreResponse(
            risk_level="UNKNOWN",
            model_available=False,
            details={"reason": "anomaly model not loaded"},
            latency_ms=round(latency, 2),
        )

    risk_level = "LOW"
    if result["anomaly_score"] >= 0.8:
        risk_level = "CRITICAL"
    elif result["anomaly_score"] >= 0.6:
        risk_level = "HIGH"
    elif result["anomaly_score"] >= 0.3:
        risk_level = "MEDIUM"

    SCORE_REQUESTS.labels(model="anomaly", risk_level=risk_level).inc()
    SCORE_LATENCY.labels(model="anomaly").observe(latency / 1000)

    return ScoreResponse(
        score=result["anomaly_score"],
        risk_level=risk_level,
        model_available=True,
        details=result,
        latency_ms=round(latency, 2),
    )


@router.post("/score/customer", response_model=ScoreResponse)
async def score_customer(
    req: CustomerScoreRequest,
    db: AsyncSession = Depends(get_db),
):
    """Score a customer's overall fraud risk using LightGBM model."""
    start = time.monotonic()

    features = await extract_customer_features(req.tenant_id, req.customer_id, db)

    scorer = get_fraud_scorer("champion")
    result = scorer.predict(features)

    latency = (time.monotonic() - start) * 1000

    if result is None:
        return ScoreResponse(
            risk_level="UNKNOWN",
            model_available=False,
            details={"reason": "fraud model not loaded"},
            latency_ms=round(latency, 2),
        )

    SCORE_REQUESTS.labels(model="lgbm", risk_level=result["risk_level"]).inc()
    SCORE_LATENCY.labels(model="lgbm").observe(latency / 1000)

    return ScoreResponse(
        score=result["fraud_probability"],
        risk_level=result["risk_level"],
        model_available=True,
        details=result,
        latency_ms=round(latency, 2),
    )


@router.post("/score/combined", response_model=ScoreResponse)
async def score_combined(
    req: CombinedScoreRequest,
    db: AsyncSession = Depends(get_db),
):
    """
    Full combined scoring: anomaly detection + LightGBM + rule engine score.
    This is the primary endpoint called by fraud-detection-service.
    """
    start = time.monotonic()

    txn_features = await extract_transaction_features(
        req.tenant_id, req.customer_id, req.event_type, req.amount, db,
    )
    cust_features = await extract_customer_features(
        req.tenant_id, req.customer_id, db,
    )

    result = combined_score(txn_features, cust_features, req.rule_score)

    latency = (time.monotonic() - start) * 1000

    SCORE_REQUESTS.labels(model="combined", risk_level=result["risk_level"]).inc()
    SCORE_LATENCY.labels(model="combined").observe(latency / 1000)

    return ScoreResponse(
        score=result["final_score"],
        risk_level=result["risk_level"],
        model_available=result["model_available"],
        details={
            "rule_score": result["rule_score"],
            "anomaly": result["anomaly_result"],
            "lgbm": result["lgbm_result"],
        },
        latency_ms=round(latency, 2),
    )
