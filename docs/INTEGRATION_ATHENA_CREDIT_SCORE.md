# AthenaCreditScore ↔ Athena LMS Integration Guide

## Overview

The Athena LMS `ai-scoring-service` (Go, port 8096) integrates with the **AthenaCreditScore** system to perform credit assessments during loan origination. When the external API is unavailable, the service falls back to deterministic mock scoring.

---

## Architecture

```
┌──────────────────────┐     loan.application.submitted     ┌────────────────────────┐
│ loan-origination     │ ─────────── RabbitMQ ──────────→  │ ai-scoring-service     │
│ (Go, port 8088)      │                                    │ (Go, port 8096)        │
└──────────────────────┘                                    └────────┬───────────────┘
                                                                     │
                                                                     │ HTTP GET
                                                                     ▼
                                                            ┌────────────────────────┐
                                                            │ AthenaCreditScore API  │
                                                            │ (Python, port 8001)    │
                                                            │ /api/v1/credit-score/  │
                                                            │        {customerId}    │
                                                            └────────┬───────────────┘
                                                                     │
                                                                     │ Response
                                                                     ▼
                                                            ┌────────────────────────┐
                                                            │ Score stored in DB     │
                                                            │ Event published:       │
                                                            │ loan.credit.assessed   │
                                                            └────────────────────────┘
                                                                     │
                                              ┌──────────────────────┼──────────────────────┐
                                              ▼                      ▼                      ▼
                                    ┌──────────────┐      ┌──────────────┐      ┌──────────────┐
                                    │ notification │      │ overdraft    │      │ compliance   │
                                    │ service      │      │ service      │      │ service      │
                                    └──────────────┘      └──────────────┘      └──────────────┘
```

---

## Configuration

### Environment Variables

| Variable | Service | Default | Description |
|----------|---------|---------|-------------|
| `SCORING_API_URL` | ai-scoring-service | `http://localhost:8001` | AthenaCreditScore base URL |
| `SCORING_API_TIMEOUT` | ai-scoring-service | `30s` | HTTP timeout for scoring API |

### K3s ConfigMap

Add to the `lms-go-common` ConfigMap in namespace `lms`:

```bash
kubectl patch configmap lms-go-common -n lms --type merge -p '{
  "data": {
    "SCORING_API_URL": "http://athena-credit-score.default.svc.cluster.local:8001"
  }
}'
```

Or if AthenaCreditScore runs outside K3s:

```bash
kubectl patch configmap lms-go-common -n lms --type merge -p '{
  "data": {
    "SCORING_API_URL": "http://<host-ip>:8001"
  }
}'
```

Then restart the scoring service:
```bash
kubectl rollout restart deploy ai-scoring-service -n lms
```

---

## API Contract

### AthenaCreditScore → ai-scoring-service

**Request** (made by ai-scoring-service):
```
GET /api/v1/credit-score/{customerId}
```

**Expected Response** (from AthenaCreditScore):
```json
{
  "customerId": "CUST-7610552B",
  "creditScore": 720,
  "scoreBand": "GOOD",
  "probabilityOfDefault": 0.05,
  "crbScore": 680,
  "crbAdjustment": 20,
  "llmAdjustment": 20,
  "factors": [
    "Low debt-to-income ratio",
    "Consistent repayment history",
    "Stable employment"
  ],
  "recommendation": "APPROVE",
  "maxRecommendedAmount": 500000,
  "assessedAt": "2026-03-18T10:30:00Z"
}
```

**Score Bands**:
| Band | Range | Meaning |
|------|-------|---------|
| EXCELLENT | 750-850 | Very low risk |
| GOOD | 670-749 | Low risk |
| FAIR | 580-669 | Moderate risk |
| MARGINAL | 500-579 | Elevated risk |
| POOR | 300-499 | High risk |

### ai-scoring-service → LMS Consumers

**Published Event** (via RabbitMQ, routing key `loan.credit.assessed`):
```json
{
  "id": "uuid",
  "type": "loan.credit.assessed",
  "version": 1,
  "source": "ai-scoring-service",
  "tenantId": "admin",
  "timestamp": "2026-03-18T10:30:00Z",
  "payload": {
    "applicationId": "uuid",
    "customerId": "CUST-7610552B",
    "creditScore": 720,
    "scoreBand": "GOOD",
    "probabilityOfDefault": 0.05,
    "recommendation": "APPROVE",
    "status": "COMPLETED"
  }
}
```

---

## LMS Endpoints for Scoring

### Manual Scoring
```bash
POST /api/v1/scoring/requests
Authorization: Bearer <jwt>
Content-Type: application/json

{
  "applicationId": "uuid-of-loan-application",
  "customerId": "CUST-7610552B"
}
```

### Query Scores
```bash
# List all scoring requests
GET /api/v1/scoring/requests?page=0&size=20

# Get specific request
GET /api/v1/scoring/requests/{id}

# Get score for loan application
GET /api/v1/scoring/applications/{applicationId}/result

# Get latest score for customer
GET /api/v1/scoring/customers/{customerId}/latest
```

---

## Testing the Integration

### 1. Verify ai-scoring-service is running
```bash
curl http://localhost:18096/actuator/health
# {"status":"UP"}
```

### 2. Trigger a manual score (uses mock if AthenaCreditScore is down)
```bash
TOKEN=$(curl -s -X POST http://localhost:18086/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | jq -r .token)

curl -X POST http://localhost:18096/api/v1/scoring/requests \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"applicationId":"00000000-0000-0000-0000-000000000001","customerId":"CUST-7610552B"}'
```

### 3. Check the score result
```bash
curl http://localhost:18096/api/v1/scoring/customers/CUST-7610552B/latest \
  -H "Authorization: Bearer $TOKEN"
```

### 4. Verify with real AthenaCreditScore
```bash
# Set the URL to the real service
kubectl set env deploy/ai-scoring-service -n lms SCORING_API_URL=http://<credit-score-host>:8001

# Restart
kubectl rollout restart deploy ai-scoring-service -n lms

# Trigger scoring — should now call the real API
```

---

## Deploying AthenaCreditScore on K3s

If you want to run AthenaCreditScore alongside LMS on the same K3s cluster:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: athena-credit-score
  namespace: lms
spec:
  replicas: 1
  selector:
    matchLabels:
      app: athena-credit-score
  template:
    metadata:
      labels:
        app: athena-credit-score
    spec:
      containers:
      - name: athena-credit-score
        image: athena-credit-score:latest
        imagePullPolicy: Never
        ports:
        - containerPort: 8001
        env:
        - name: DATABASE_URL
          value: "postgresql://admin:password@postgres.infra.svc.cluster.local:5432/athena_credit_score"
        readinessProbe:
          httpGet:
            path: /health
            port: 8001
          initialDelaySeconds: 10
---
apiVersion: v1
kind: Service
metadata:
  name: athena-credit-score
  namespace: lms
spec:
  selector:
    app: athena-credit-score
  ports:
  - port: 8001
```

Then set:
```bash
kubectl set env deploy/ai-scoring-service -n lms \
  SCORING_API_URL=http://athena-credit-score.lms.svc.cluster.local:8001
```

---

## Mock Scoring (Fallback)

When AthenaCreditScore is unavailable, the ai-scoring-service generates deterministic mock scores based on the customer ID hash:

- Base score: 550 + (hash % 300) → range 550-849
- CRB adjustment: -20 to +30
- LLM adjustment: -10 to +20
- Final score clamped to 300-850

This ensures:
- Same customer always gets the same mock score (deterministic)
- No external dependency required for development/testing
- Loan origination flow works end-to-end without AthenaCreditScore

---

## Legacy Events (AthenaCreditScore → Notification Service)

The notification service also handles legacy AthenaCreditScore events:

| Event Type | Action |
|------------|--------|
| `DISPUTE_FILED` | Sends dispute acknowledgement email |
| `SCORE_UPDATED` | Sends score update notification |
| `CONSENT_GRANTED` | Sends consent confirmation |
| `USER_INVITATION` | Sends invitation email |

These events are consumed from the `athena.lms.notification.queue` (wildcard binding).
