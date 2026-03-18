"""
Training API — endpoints for triggering model training and checking status.
"""
from __future__ import annotations

import asyncio
from typing import Any, Dict

import structlog
from fastapi import APIRouter, BackgroundTasks
from pydantic import BaseModel

from features.feature_engineer import build_training_dataset, build_anomaly_training_data
from models.anomaly_detector import AnomalyTrainer, get_anomaly_detector
from models.fraud_scorer import train_and_register, get_fraud_scorer
from monitoring.metrics import TRAIN_RUNS

logger = structlog.get_logger(__name__)

router = APIRouter()

# Simple in-memory training status tracker
_training_status: Dict[str, Any] = {
    "anomaly": {"status": "idle", "last_result": None},
    "lgbm": {"status": "idle", "last_result": None},
}


class TrainRequest(BaseModel):
    lookback_days: int = 90
    contamination: float = 0.05


class TrainResponse(BaseModel):
    status: str
    message: str
    details: Dict[str, Any] = {}


@router.post("/train/anomaly", response_model=TrainResponse)
async def train_anomaly(req: TrainRequest, background_tasks: BackgroundTasks):
    """Trigger Isolation Forest training on recent transaction data."""
    if _training_status["anomaly"]["status"] == "running":
        return TrainResponse(status="busy", message="Anomaly training already in progress")

    _training_status["anomaly"]["status"] = "running"

    async def _train():
        try:
            df = await build_anomaly_training_data(lookback_days=req.lookback_days)
            if df.empty:
                _training_status["anomaly"] = {
                    "status": "failed",
                    "last_result": {"error": "No training data available"},
                }
                TRAIN_RUNS.labels(model_type="anomaly", status="no_data").inc()
                return

            trainer = AnomalyTrainer()
            metrics = trainer.train(df, contamination=req.contamination)

            # Reload the singleton detector
            get_anomaly_detector().reload()

            _training_status["anomaly"] = {"status": "completed", "last_result": metrics}
            TRAIN_RUNS.labels(model_type="anomaly", status="success").inc()
            logger.info("Anomaly training completed", **metrics)
        except Exception as e:
            _training_status["anomaly"] = {
                "status": "failed",
                "last_result": {"error": str(e)},
            }
            TRAIN_RUNS.labels(model_type="anomaly", status="error").inc()
            logger.error("Anomaly training failed", error=str(e))

    background_tasks.add_task(asyncio.create_task, _train())
    return TrainResponse(status="started", message="Anomaly model training started")


@router.post("/train/fraud-scorer", response_model=TrainResponse)
async def train_fraud_scorer(req: TrainRequest, background_tasks: BackgroundTasks):
    """Trigger LightGBM fraud scorer training on labeled alert data."""
    if _training_status["lgbm"]["status"] == "running":
        return TrainResponse(status="busy", message="Fraud scorer training already in progress")

    _training_status["lgbm"]["status"] = "running"

    async def _train():
        try:
            df = await build_training_dataset(lookback_days=req.lookback_days)
            if df.empty:
                _training_status["lgbm"] = {
                    "status": "failed",
                    "last_result": {"error": "Not enough labeled data"},
                }
                TRAIN_RUNS.labels(model_type="lgbm", status="no_data").inc()
                return

            result = train_and_register(df, register_as="challenger")

            # Reload the scorer
            get_fraud_scorer("challenger").reload()

            _training_status["lgbm"] = {"status": "completed", "last_result": result}
            TRAIN_RUNS.labels(model_type="lgbm", status="success").inc()
            logger.info("Fraud scorer training completed", run_id=result["run_id"])
        except Exception as e:
            _training_status["lgbm"] = {
                "status": "failed",
                "last_result": {"error": str(e)},
            }
            TRAIN_RUNS.labels(model_type="lgbm", status="error").inc()
            logger.error("Fraud scorer training failed", error=str(e))

    background_tasks.add_task(asyncio.create_task, _train())
    return TrainResponse(status="started", message="Fraud scorer training started")


@router.get("/train/status")
async def training_status():
    """Check training status for all models."""
    return _training_status


@router.post("/models/reload")
async def reload_models():
    """Force-reload all models from disk/registry."""
    get_anomaly_detector().reload()
    get_fraud_scorer("champion").reload()
    return {"status": "reloaded", "models": ["anomaly_detector", "fraud_scorer_champion"]}
