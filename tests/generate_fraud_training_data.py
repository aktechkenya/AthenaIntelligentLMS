"""
Generate synthetic labeled fraud data for LightGBM training.

Creates:
- 500 customers with fraud events and velocity counters
- 200 resolved fraud alerts (60 CONFIRMED_FRAUD, 140 FALSE_POSITIVE)
- 300 clean customers (events only, no alerts)
- Risk profiles for all customers

Run: python3 tests/generate_fraud_training_data.py
"""
import uuid
import random
import json
from datetime import datetime, timedelta

random.seed(42)

TENANT = "admin"
NOW = datetime.utcnow()

# Event types matching the fraud rule engine
EVENT_TYPES = [
    "TRANSFER", "PAYMENT", "LOAN_APPLICATION", "CREDIT",
    "DEBIT", "WITHDRAWAL", "DEPOSIT", "ACCOUNT_OPENING",
]

ALERT_TYPES = [
    "LARGE_TRANSACTION", "VELOCITY_BREACH", "UNUSUAL_PATTERN",
    "HIGH_RISK_COUNTRY", "STRUCTURING", "RAPID_MOVEMENT",
    "DORMANT_REACTIVATION", "NEW_ACCOUNT_HIGH_ACTIVITY",
]

RULE_CODES = [
    "RULE_LARGE_TXN", "RULE_HIGH_VELOCITY", "RULE_ROUND_AMOUNT",
    "RULE_OFF_HOURS", "RULE_NEW_PAYEE", "RULE_STRUCTURING",
    "RULE_RAPID_SUCCESSION", "RULE_DORMANT_REACTIVATION",
]


def gen_uuid():
    return str(uuid.uuid4())


def gen_customer_id(i):
    return f"FRAUD-CUST-{i:04d}"


def random_past(max_days=60):
    return NOW - timedelta(
        days=random.randint(1, max_days),
        hours=random.randint(0, 23),
        minutes=random.randint(0, 59),
    )


def gen_amount(is_fraud=False):
    if is_fraud:
        # Fraudulent transactions tend to be larger, round amounts, off-hours
        patterns = [
            lambda: round(random.uniform(50000, 500000), 2),  # large
            lambda: random.choice([10000, 50000, 100000, 200000, 500000]),  # round
            lambda: round(random.uniform(9500, 9999), 2),  # just under 10K threshold
        ]
        return random.choice(patterns)()
    else:
        return round(random.uniform(100, 30000), 2)


# ── SQL Builders ──────────────────────────────────────────────────────────

events_sql = []
alerts_sql = []
velocity_sql = []
profiles_sql = []

print("Generating synthetic fraud training data...")

# ── Fraud customers (60 confirmed fraud) ──────────────────────────────────
fraud_customers = []
for i in range(60):
    cid = gen_customer_id(i)
    fraud_customers.append(cid)

    # Generate 5-15 events per fraud customer
    n_events = random.randint(5, 15)
    for _ in range(n_events):
        eid = gen_uuid()
        evt = random.choice(EVENT_TYPES)
        amt = gen_amount(is_fraud=True)
        ts = random_past(60)
        risk = round(random.uniform(0.5, 0.95), 4)
        rules = random.choice(RULE_CODES)
        payload = json.dumps({
            "customerId": cid, "amount": amt, "eventType": evt,
            "channel": random.choice(["MOBILE", "ONLINE", "BRANCH"]),
        })

        events_sql.append(
            f"('{eid}','{TENANT}','{evt}','fraud-test','{cid}','{cid}',"
            f"{amt},{risk},'{rules}','{payload}','{ts.isoformat()}')"
        )

    # Generate 1-3 CONFIRMED_FRAUD alerts
    n_alerts = random.randint(1, 3)
    for _ in range(n_alerts):
        aid = gen_uuid()
        atype = random.choice(ALERT_TYPES)
        severity = random.choice(["HIGH", "CRITICAL"])
        rule = random.choice(RULE_CODES)
        amt = gen_amount(is_fraud=True)
        ts = random_past(45)
        resolved_at = ts + timedelta(days=random.randint(1, 10))
        risk = round(random.uniform(0.6, 0.95), 4)

        alerts_sql.append(
            f"('{aid}','{TENANT}','{atype}','{severity}','CONFIRMED_FRAUD','RULE_ENGINE',"
            f"'{rule}','{cid}','CUSTOMER','{cid}',"
            f"'Confirmed fraud: {atype}','{random.choice(EVENT_TYPES)}',{amt},{risk},"
            f"'v1.0','{{}}',false,false,NULL,NULL,'investigator',"
            f"'{resolved_at.isoformat()}','CONFIRMED_FRAUD','Investigation confirmed fraudulent activity',"
            f"'{ts.isoformat()}','{ts.isoformat()}')"
        )

    # Velocity counters (high values for fraud customers)
    for ctype in ["txn_count_1h", "txn_count_24h", "txn_amount_1h", "txn_amount_24h"]:
        vid = gen_uuid()
        ws = random_past(30)
        we = ws + timedelta(hours=1 if "1h" in ctype else 24)
        count = random.randint(5, 20) if "count" in ctype else 0
        amount = round(random.uniform(50000, 500000), 2) if "amount" in ctype else 0

        velocity_sql.append(
            f"('{vid}','{TENANT}','{cid}','{ctype}',"
            f"'{ws.isoformat()}','{we.isoformat()}',"
            f"{count},{amount},'{ws.isoformat()}','{ws.isoformat()}')"
        )

    # Risk profile
    pid = gen_uuid()
    profiles_sql.append(
        f"('{pid}','{TENANT}','{cid}',{round(random.uniform(0.6, 0.95), 4)},"
        f"'HIGH',{random.randint(2, 8)},{random.randint(0, 3)},{random.randint(1, 5)},0,"
        f"{round(random.uniform(50000, 200000), 4)},{random.randint(10, 50)},"
        f"'{random_past(30).isoformat()}','{random_past(5).isoformat()}',"
        f"'{{\"high_velocity\": true, \"large_amounts\": true}}',"
        f"'{random_past(60).isoformat()}','{NOW.isoformat()}')"
    )


# ── False positive customers (140) ───────────────────────────────────────
fp_customers = []
for i in range(60, 200):
    cid = gen_customer_id(i)
    fp_customers.append(cid)

    # Generate 3-8 events (normal patterns)
    n_events = random.randint(3, 8)
    for _ in range(n_events):
        eid = gen_uuid()
        evt = random.choice(EVENT_TYPES)
        amt = gen_amount(is_fraud=False)
        ts = random_past(60)
        risk = round(random.uniform(0.1, 0.5), 4)

        events_sql.append(
            f"('{eid}','{TENANT}','{evt}','fraud-test','{cid}','{cid}',"
            f"{amt},{risk},'','{{\"customerId\":\"{cid}\",\"amount\":{amt}}}',"
            f"'{ts.isoformat()}')"
        )

    # 1 FALSE_POSITIVE alert (investigated, cleared)
    aid = gen_uuid()
    atype = random.choice(ALERT_TYPES)
    severity = random.choice(["LOW", "MEDIUM"])
    amt = gen_amount(is_fraud=False)
    ts = random_past(45)
    resolved_at = ts + timedelta(days=random.randint(1, 5))
    risk = round(random.uniform(0.2, 0.5), 4)

    alerts_sql.append(
        f"('{aid}','{TENANT}','{atype}','{severity}','FALSE_POSITIVE','RULE_ENGINE',"
        f"'RULE_LARGE_TXN','{cid}','CUSTOMER','{cid}',"
        f"'False positive: {atype}','{random.choice(EVENT_TYPES)}',{amt},{risk},"
        f"'v1.0','{{}}',false,false,NULL,NULL,'analyst',"
        f"'{resolved_at.isoformat()}','FALSE_POSITIVE','Investigated and cleared',"
        f"'{ts.isoformat()}','{ts.isoformat()}')"
    )

    # Velocity counters (normal values)
    for ctype in ["txn_count_1h", "txn_count_24h"]:
        vid = gen_uuid()
        ws = random_past(30)
        we = ws + timedelta(hours=1 if "1h" in ctype else 24)
        velocity_sql.append(
            f"('{vid}','{TENANT}','{cid}','{ctype}',"
            f"'{ws.isoformat()}','{we.isoformat()}',"
            f"{random.randint(1, 3)},{round(random.uniform(1000, 10000), 2)},"
            f"'{ws.isoformat()}','{ws.isoformat()}')"
        )

    # Risk profile (low risk)
    pid = gen_uuid()
    profiles_sql.append(
        f"('{pid}','{TENANT}','{cid}',{round(random.uniform(0.05, 0.3), 4)},"
        f"'LOW',{random.randint(1, 3)},0,0,{random.randint(0, 2)},"
        f"{round(random.uniform(5000, 30000), 4)},{random.randint(5, 20)},"
        f"'{random_past(30).isoformat()}','{random_past(5).isoformat()}',"
        f"'{{\"normal_activity\": true}}',"
        f"'{random_past(60).isoformat()}','{NOW.isoformat()}')"
    )


# ── Clean customers (300 — no alerts at all) ─────────────────────────────
for i in range(200, 500):
    cid = gen_customer_id(i)

    n_events = random.randint(2, 6)
    for _ in range(n_events):
        eid = gen_uuid()
        evt = random.choice(EVENT_TYPES)
        amt = gen_amount(is_fraud=False)
        ts = random_past(60)
        risk = round(random.uniform(0.0, 0.2), 4)

        events_sql.append(
            f"('{eid}','{TENANT}','{evt}','fraud-test','{cid}','{cid}',"
            f"{amt},{risk},'','{{\"customerId\":\"{cid}\",\"amount\":{amt}}}',"
            f"'{ts.isoformat()}')"
        )

    # Velocity (minimal)
    vid = gen_uuid()
    ws = random_past(30)
    we = ws + timedelta(hours=24)
    velocity_sql.append(
        f"('{vid}','{TENANT}','{cid}','txn_count_24h',"
        f"'{ws.isoformat()}','{we.isoformat()}',"
        f"{random.randint(1, 3)},{round(random.uniform(500, 5000), 2)},"
        f"'{ws.isoformat()}','{ws.isoformat()}')"
    )

    # Risk profile (very low)
    pid = gen_uuid()
    profiles_sql.append(
        f"('{pid}','{TENANT}','{cid}',{round(random.uniform(0.01, 0.15), 4)},"
        f"'LOW',0,0,0,0,"
        f"{round(random.uniform(1000, 15000), 4)},{random.randint(1, 10)},"
        f"NULL,NULL,'{{}}','{random_past(60).isoformat()}','{NOW.isoformat()}')"
    )


# ── Write SQL file ───────────────────────────────────────────────────────
sql = "-- Auto-generated fraud training data\nBEGIN;\n\n"

sql += "-- Fraud events\n"
sql += ("INSERT INTO fraud_events (id,tenant_id,event_type,source_service,customer_id,"
        "subject_id,amount,risk_score,rules_triggered,payload,processed_at) VALUES\n")
sql += ",\n".join(events_sql)
sql += "\nON CONFLICT DO NOTHING;\n\n"

sql += "-- Fraud alerts (labeled)\n"
sql += ("INSERT INTO fraud_alerts (id,tenant_id,alert_type,severity,status,source,"
        "rule_code,customer_id,subject_type,subject_id,description,trigger_event,"
        "trigger_amount,risk_score,model_version,explanation,escalated,"
        "escalated_to_compliance,compliance_alert_id,assigned_to,resolved_by,"
        "resolved_at,resolution,resolution_notes,created_at,updated_at) VALUES\n")
sql += ",\n".join(alerts_sql)
sql += "\nON CONFLICT DO NOTHING;\n\n"

sql += "-- Velocity counters\n"
sql += ("INSERT INTO velocity_counters (id,tenant_id,customer_id,counter_type,"
        "window_start,window_end,count,total_amount,created_at,updated_at) VALUES\n")
sql += ",\n".join(velocity_sql)
sql += "\nON CONFLICT (tenant_id,customer_id,counter_type,window_start) DO NOTHING;\n\n"

sql += "-- Customer risk profiles\n"
sql += ("INSERT INTO customer_risk_profiles (id,tenant_id,customer_id,risk_score,"
        "risk_level,total_alerts,open_alerts,confirmed_fraud,false_positives,"
        "avg_transaction_amount,transaction_count_30d,last_alert_at,last_scored_at,"
        "factors,created_at,updated_at) VALUES\n")
sql += ",\n".join(profiles_sql)
sql += "\nON CONFLICT (tenant_id,customer_id) DO NOTHING;\n\n"

sql += "COMMIT;\n"

outpath = "/mnt/storage/AthenaIntelligentLMS/tests/fraud_training_data.sql"
with open(outpath, "w") as f:
    f.write(sql)

print(f"Generated {len(events_sql)} fraud events")
print(f"Generated {len(alerts_sql)} fraud alerts (60 CONFIRMED_FRAUD + 140 FALSE_POSITIVE)")
print(f"Generated {len(velocity_sql)} velocity counters")
print(f"Generated {len(profiles_sql)} customer risk profiles")
print(f"SQL written to: {outpath}")
