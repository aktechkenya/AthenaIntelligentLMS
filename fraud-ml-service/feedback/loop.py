"""
Fraud ML Feedback Loop — Weekly drift detection and auto-retraining.

Runs on a schedule:
  - Computes KS statistic on recent model predictions vs actual outcomes
  - Computes PSI on feature distributions
  - Triggers retraining if drift exceeds thresholds
"""
from __future__ import annotations

import os
from datetime import date, timedelta
from typing import List

import numpy as np
import structlog
from apscheduler.schedulers.asyncio import AsyncIOScheduler
from sqlalchemy import text

from db.database import AsyncSessionLocal

logger = structlog.get_logger(__name__)

PSI_THRESHOLD = float(os.getenv("PSI_THRESHOLD", "0.2"))
KS_DROP_THRESHOLD = float(os.getenv("KS_DROP_THRESHOLD", "0.05"))


def compute_psi(base: List[float], current: List[float], buckets: int = 10) -> float:
    base_arr = np.array(base)
    cur_arr = np.array(current)
    if len(base_arr) < buckets or len(cur_arr) < buckets:
        return 0.0
    bins = np.percentile(base_arr, np.linspace(0, 100, buckets + 1))
    bins[0] -= 1e-9
    bins[-1] += 1e-9

    base_pcts = np.histogram(base_arr, bins=bins)[0] / len(base_arr)
    cur_pcts = np.histogram(cur_arr, bins=bins)[0] / len(cur_arr)

    base_pcts = np.where(base_pcts == 0, 1e-4, base_pcts)
    cur_pcts = np.where(cur_pcts == 0, 1e-4, cur_pcts)

    return float(np.sum((cur_pcts - base_pcts) * np.log(cur_pcts / base_pcts)))


async def run_feedback_loop():
    """
    Weekly drift detection:
    1. Check prediction accuracy using resolved alerts as ground truth
    2. Compute PSI on risk score distributions
    3. Log results and trigger retraining if needed
    """
    logger.info("Fraud ML feedback loop started")

    async with AsyncSessionLocal() as db:
        window_start = date.today() - timedelta(days=30)

        # ── Accuracy check on resolved alerts ────────────────────────────
        rows = await db.execute(text("""
            SELECT risk_score,
                   CASE WHEN status = 'CONFIRMED_FRAUD' THEN 1 ELSE 0 END AS actual
            FROM fraud_alerts
            WHERE status IN ('CONFIRMED_FRAUD', 'FALSE_POSITIVE')
              AND risk_score IS NOT NULL
              AND resolved_at >= :window_start
        """), {"window_start": window_start})
        results = rows.fetchall()

        if len(results) < 30:
            logger.info("Not enough resolved alerts for feedback loop", count=len(results))
            return

        y_prob = [float(r[0]) for r in results]
        y_true = [int(r[1]) for r in results]

        from models.fraud_scorer import ks_statistic
        current_ks = ks_statistic(y_true, y_prob)
        logger.info("Current KS statistic", ks=current_ks, n_samples=len(results))

        # ── PSI on risk score distribution ───────────────────────────────
        base_rows = await db.execute(text("""
            SELECT risk_score FROM fraud_alerts
            WHERE risk_score IS NOT NULL
              AND created_at < :window_start
            ORDER BY RANDOM() LIMIT 500
        """), {"window_start": window_start})
        base_scores = [float(r[0]) for r in base_rows.fetchall()]

        current_scores = [float(r[0]) for r in results]
        psi_val = compute_psi(base_scores, current_scores) if len(base_scores) >= 20 else 0.0

        logger.info("PSI check", psi=psi_val, threshold=PSI_THRESHOLD)

        # ── Decide on retraining ─────────────────────────────────────────
        should_retrain = current_ks < 0.15 or psi_val > PSI_THRESHOLD

        if should_retrain:
            logger.warning(
                "Drift detected — triggering model retraining",
                ks=current_ks, psi=psi_val,
            )
            # Trigger async retraining
            try:
                from features.feature_engineer import build_training_dataset
                from models.fraud_scorer import train_and_register

                df = await build_training_dataset()
                if not df.empty and len(df) >= 50:
                    result = train_and_register(df, register_as="challenger")
                    logger.info("Retraining complete", **result["metrics"])
                else:
                    logger.warning("Not enough data for retraining")
            except Exception as e:
                logger.error("Auto-retraining failed", error=str(e))
        else:
            logger.info("No drift detected — model stable", ks=current_ks, psi=psi_val)


def start_scheduler() -> AsyncIOScheduler:
    scheduler = AsyncIOScheduler()
    # Run every Sunday at 03:00
    scheduler.add_job(run_feedback_loop, "cron", day_of_week="sun", hour=3, minute=0)
    scheduler.start()
    logger.info("Fraud ML feedback loop scheduler started")
    return scheduler
