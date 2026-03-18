from __future__ import annotations

from fastapi import APIRouter
from prometheus_client import Counter, Histogram, generate_latest
from starlette.responses import Response

metrics_router = APIRouter()

SCORE_REQUESTS = Counter(
    "fraud_ml_score_requests_total",
    "Total scoring requests",
    ["model", "risk_level"],
)

SCORE_LATENCY = Histogram(
    "fraud_ml_score_latency_seconds",
    "Scoring latency",
    ["model"],
)

TRAIN_RUNS = Counter(
    "fraud_ml_train_runs_total",
    "Total training runs",
    ["model_type", "status"],
)


@metrics_router.get("/metrics")
async def prometheus_metrics():
    return Response(content=generate_latest(), media_type="text/plain")
