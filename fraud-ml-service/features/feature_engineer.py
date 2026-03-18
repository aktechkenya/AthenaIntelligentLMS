"""
Feature Engineering Pipeline for Fraud ML Models.

Extracts features from fraud_events, velocity_counters, and customer_risk_profiles
tables in the athena_fraud database.

Two feature sets:
  1. Transaction-level features — for real-time anomaly scoring per event
  2. Customer-level features  — for application fraud / customer risk scoring
"""
from __future__ import annotations

import os
from datetime import datetime, timedelta
from typing import Any, Dict, List, Optional

import numpy as np
import pandas as pd
import structlog
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession

from db.database import AsyncSessionLocal

logger = structlog.get_logger(__name__)


async def extract_transaction_features(
    tenant_id: str,
    customer_id: str,
    event_type: str,
    amount: Optional[float],
    db: AsyncSession,
) -> Dict[str, Any]:
    """
    Build a feature vector for a single transaction event.
    Used for real-time scoring by the Isolation Forest anomaly model.
    """
    features: Dict[str, Any] = {}
    now = datetime.utcnow()

    # ── Raw event features ───────────────────────────────────────────────
    features["amount"] = amount or 0.0
    features["amount_log"] = np.log1p(amount) if amount and amount > 0 else 0.0
    features["is_round_amount"] = 1 if amount and amount % 10000 == 0 else 0
    features["hour_of_day"] = now.hour
    features["day_of_week"] = now.weekday()
    features["is_weekend"] = 1 if now.weekday() >= 5 else 0
    features["is_night"] = 1 if now.hour < 6 or now.hour >= 22 else 0

    # Event type one-hot (top categories)
    event_categories = [
        "payment.completed", "transfer.completed", "loan.application.submitted",
        "account.credit.received", "payment.reversed", "mobile.transfer.completed",
        "loan.closed", "overdraft.granted",
    ]
    for cat in event_categories:
        features[f"evt_{cat.replace('.', '_')}"] = 1 if event_type == cat else 0

    # ── Velocity features (from velocity_counters table) ─────────────────
    velocity_rows = await db.execute(text("""
        SELECT counter_type,
               COALESCE(SUM(count), 0)        AS total_count,
               COALESCE(SUM(total_amount), 0)  AS total_amount
        FROM velocity_counters
        WHERE tenant_id = :tid AND customer_id = :cid
          AND window_end >= :cutoff_1h
        GROUP BY counter_type
    """), {"tid": tenant_id, "cid": customer_id, "cutoff_1h": now - timedelta(hours=1)})
    vel_1h = {r[0]: (int(r[1]), float(r[2])) for r in velocity_rows.fetchall()}

    velocity_rows_24h = await db.execute(text("""
        SELECT counter_type,
               COALESCE(SUM(count), 0)        AS total_count,
               COALESCE(SUM(total_amount), 0)  AS total_amount
        FROM velocity_counters
        WHERE tenant_id = :tid AND customer_id = :cid
          AND window_end >= :cutoff_24h
        GROUP BY counter_type
    """), {"tid": tenant_id, "cid": customer_id, "cutoff_24h": now - timedelta(hours=24)})
    vel_24h = {r[0]: (int(r[1]), float(r[2])) for r in velocity_rows_24h.fetchall()}

    features["txn_count_1h"] = vel_1h.get("TXN_COUNT", (0, 0))[0]
    features["txn_amount_1h"] = vel_1h.get("TXN_AMOUNT", (0, 0))[1]
    features["transfer_count_1h"] = vel_1h.get("TRANSFER_OUT", (0, 0))[0]
    features["credit_count_1h"] = vel_1h.get("CREDIT_RECEIVED", (0, 0))[0]
    features["round_amount_count_1h"] = vel_1h.get("ROUND_AMOUNT", (0, 0))[0]

    features["txn_count_24h"] = vel_24h.get("TXN_COUNT", (0, 0))[0]
    features["txn_amount_24h"] = vel_24h.get("TXN_AMOUNT", (0, 0))[1]
    features["transfer_count_24h"] = vel_24h.get("TRANSFER_OUT", (0, 0))[0]
    features["loan_app_count_24h"] = vel_24h.get("LOAN_APP", (0, 0))[0]
    features["payment_reversed_24h"] = vel_24h.get("PAYMENT_REVERSED", (0, 0))[0]

    # Ratio features
    features["amount_to_24h_ratio"] = (
        (amount / features["txn_amount_24h"])
        if amount and features["txn_amount_24h"] > 0
        else 0.0
    )

    # ── Customer risk profile features ───────────────────────────────────
    risk_row = await db.execute(text("""
        SELECT risk_score, total_alerts, open_alerts, confirmed_fraud,
               false_positives, transaction_count_30d, avg_transaction_amount
        FROM customer_risk_profiles
        WHERE tenant_id = :tid AND customer_id = :cid
    """), {"tid": tenant_id, "cid": customer_id})
    risk = risk_row.fetchone()

    if risk:
        features["customer_risk_score"] = float(risk[0] or 0)
        features["customer_total_alerts"] = int(risk[1] or 0)
        features["customer_open_alerts"] = int(risk[2] or 0)
        features["customer_confirmed_fraud"] = int(risk[3] or 0)
        features["customer_false_positives"] = int(risk[4] or 0)
        features["customer_txn_count_30d"] = int(risk[5] or 0)
        features["customer_avg_txn_amount"] = float(risk[6] or 0)
    else:
        features["customer_risk_score"] = 0.0
        features["customer_total_alerts"] = 0
        features["customer_open_alerts"] = 0
        features["customer_confirmed_fraud"] = 0
        features["customer_false_positives"] = 0
        features["customer_txn_count_30d"] = 0
        features["customer_avg_txn_amount"] = 0.0

    # Deviation from average
    features["amount_deviation"] = (
        abs(features["amount"] - features["customer_avg_txn_amount"])
        / max(features["customer_avg_txn_amount"], 1.0)
    )

    return features


async def extract_customer_features(
    tenant_id: str,
    customer_id: str,
    db: AsyncSession,
) -> Dict[str, Any]:
    """
    Build a customer-level feature vector for application fraud scoring.
    Aggregates historical behavior patterns.
    """
    features: Dict[str, Any] = {}
    now = datetime.utcnow()

    # ── Event history stats ──────────────────────────────────────────────
    event_stats = await db.execute(text("""
        SELECT
            COUNT(*)                                         AS total_events,
            COUNT(DISTINCT event_type)                       AS distinct_event_types,
            COALESCE(AVG(amount), 0)                         AS avg_amount,
            COALESCE(STDDEV(amount), 0)                      AS stddev_amount,
            COALESCE(MAX(amount), 0)                         AS max_amount,
            COALESCE(MIN(amount), 0)                         AS min_amount,
            COUNT(*) FILTER (WHERE processed_at >= :w7d)     AS events_7d,
            COUNT(*) FILTER (WHERE processed_at >= :w30d)    AS events_30d,
            COALESCE(SUM(amount) FILTER (WHERE processed_at >= :w7d), 0)  AS amount_7d,
            COALESCE(SUM(amount) FILTER (WHERE processed_at >= :w30d), 0) AS amount_30d,
            EXTRACT(EPOCH FROM (NOW() - MIN(processed_at))) / 86400.0     AS account_age_days
        FROM fraud_events
        WHERE tenant_id = :tid AND customer_id = :cid
    """), {
        "tid": tenant_id, "cid": customer_id,
        "w7d": now - timedelta(days=7), "w30d": now - timedelta(days=30),
    })
    stats = event_stats.fetchone()

    features["total_events"] = int(stats[0] or 0)
    features["distinct_event_types"] = int(stats[1] or 0)
    features["avg_amount"] = float(stats[2] or 0)
    features["stddev_amount"] = float(stats[3] or 0)
    features["max_amount"] = float(stats[4] or 0)
    features["min_amount"] = float(stats[5] or 0)
    features["events_7d"] = int(stats[6] or 0)
    features["events_30d"] = int(stats[7] or 0)
    features["amount_7d"] = float(stats[8] or 0)
    features["amount_30d"] = float(stats[9] or 0)
    features["account_age_days"] = float(stats[10] or 0)

    # Activity acceleration
    features["events_7d_to_30d_ratio"] = (
        features["events_7d"] / max(features["events_30d"], 1)
    )
    features["amount_7d_to_30d_ratio"] = (
        features["amount_7d"] / max(features["amount_30d"], 1.0)
    )

    # ── Alert history ────────────────────────────────────────────────────
    alert_stats = await db.execute(text("""
        SELECT
            COUNT(*)                                                       AS total_alerts,
            COUNT(*) FILTER (WHERE severity IN ('HIGH', 'CRITICAL'))       AS high_sev_alerts,
            COUNT(*) FILTER (WHERE status = 'OPEN')                        AS open_alerts,
            COUNT(*) FILTER (WHERE status = 'CONFIRMED_FRAUD')             AS confirmed_fraud,
            COUNT(*) FILTER (WHERE status = 'FALSE_POSITIVE')              AS false_positives,
            COUNT(*) FILTER (WHERE escalated_to_compliance = TRUE)         AS escalated,
            COUNT(DISTINCT alert_type)                                     AS distinct_alert_types,
            COUNT(*) FILTER (WHERE created_at >= :w30d)                    AS alerts_30d
        FROM fraud_alerts
        WHERE tenant_id = :tid AND customer_id = :cid
    """), {"tid": tenant_id, "cid": customer_id, "w30d": now - timedelta(days=30)})
    a = alert_stats.fetchone()

    features["total_alerts"] = int(a[0] or 0)
    features["high_sev_alerts"] = int(a[1] or 0)
    features["open_alerts"] = int(a[2] or 0)
    features["confirmed_fraud"] = int(a[3] or 0)
    features["false_positives"] = int(a[4] or 0)
    features["escalated_alerts"] = int(a[5] or 0)
    features["distinct_alert_types"] = int(a[6] or 0)
    features["alerts_30d"] = int(a[7] or 0)

    # Alert rate
    features["alert_rate"] = (
        features["total_alerts"] / max(features["total_events"], 1)
    )
    features["false_positive_rate"] = (
        features["false_positives"] / max(features["total_alerts"], 1)
    )

    # ── Risk profile ─────────────────────────────────────────────────────
    risk_row = await db.execute(text("""
        SELECT risk_score, avg_transaction_amount, transaction_count_30d
        FROM customer_risk_profiles
        WHERE tenant_id = :tid AND customer_id = :cid
    """), {"tid": tenant_id, "cid": customer_id})
    risk = risk_row.fetchone()
    features["risk_score"] = float(risk[0] or 0) if risk else 0.0
    features["avg_txn_amount"] = float(risk[1] or 0) if risk else 0.0
    features["txn_count_30d"] = int(risk[2] or 0) if risk else 0

    return features


async def build_training_dataset(
    min_events: int = 100,
    lookback_days: int = 90,
) -> pd.DataFrame:
    """
    Build a labeled training dataset from historical fraud alerts.
    Labels: 1 = confirmed fraud customer, 0 = false positive or no alerts.

    Returns a DataFrame with customer-level features + label column.
    """
    async with AsyncSessionLocal() as db:
        cutoff = datetime.utcnow() - timedelta(days=lookback_days)

        # Get all customers with resolved alerts (labeled data)
        labeled = await db.execute(text("""
            SELECT DISTINCT customer_id, tenant_id,
                   MAX(CASE WHEN status = 'CONFIRMED_FRAUD' THEN 1 ELSE 0 END) AS fraud_label
            FROM fraud_alerts
            WHERE customer_id IS NOT NULL
              AND status IN ('CONFIRMED_FRAUD', 'FALSE_POSITIVE')
              AND created_at >= :cutoff
            GROUP BY customer_id, tenant_id
        """), {"cutoff": cutoff})
        labeled_rows = labeled.fetchall()

        if len(labeled_rows) < min_events:
            logger.warning(
                "Not enough labeled data for training",
                count=len(labeled_rows), required=min_events,
            )
            return pd.DataFrame()

        rows = []
        for cust_id, tid, label in labeled_rows:
            feats = await extract_customer_features(tid, cust_id, db)
            feats["fraud_label"] = int(label)
            rows.append(feats)

        # Also sample customers with no alerts as negatives
        clean_customers = await db.execute(text("""
            SELECT DISTINCT fe.customer_id, fe.tenant_id
            FROM fraud_events fe
            LEFT JOIN fraud_alerts fa ON fa.customer_id = fe.customer_id AND fa.tenant_id = fe.tenant_id
            WHERE fa.id IS NULL AND fe.customer_id IS NOT NULL AND fe.processed_at >= :cutoff
            LIMIT :lim
        """), {"cutoff": cutoff, "lim": len(labeled_rows) * 2})

        for cust_id, tid in clean_customers.fetchall():
            feats = await extract_customer_features(tid, cust_id, db)
            feats["fraud_label"] = 0
            rows.append(feats)

        df = pd.DataFrame(rows)
        logger.info(
            "Training dataset built",
            total=len(df),
            fraud=int(df["fraud_label"].sum()),
            clean=int((df["fraud_label"] == 0).sum()),
        )
        return df


async def build_anomaly_training_data(lookback_days: int = 60) -> pd.DataFrame:
    """
    Build an unlabeled transaction-level dataset for Isolation Forest training.
    Uses recent fraud_events to extract per-event features.
    """
    async with AsyncSessionLocal() as db:
        cutoff = datetime.utcnow() - timedelta(days=lookback_days)

        events = await db.execute(text("""
            SELECT tenant_id, customer_id, event_type, amount
            FROM fraud_events
            WHERE customer_id IS NOT NULL AND processed_at >= :cutoff
            ORDER BY processed_at DESC
            LIMIT 5000
        """), {"cutoff": cutoff})
        event_rows = events.fetchall()

        if not event_rows:
            logger.warning("No events available for anomaly training data")
            return pd.DataFrame()

        rows = []
        for tid, cid, evt, amt in event_rows:
            feats = await extract_transaction_features(
                tid, cid, evt, float(amt) if amt else None, db
            )
            rows.append(feats)

        df = pd.DataFrame(rows)
        logger.info("Anomaly training data built", rows=len(df))
        return df
