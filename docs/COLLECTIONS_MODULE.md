# Collections Module — Enterprise Overhaul

## Overview

The collections module manages the full lifecycle of delinquent loan recovery: automated case creation from DPD events, officer workload management, action tracking, promise-to-pay management, strategy-driven automation, write-off workflows, and analytics.

**Service:** `collections-service` (port 8093, k8s: `collections-service.lms.svc.cluster.local`)
**Database:** `athena_collections`
**Frontend:** 6 pages under `/collections/*`

---

## Architecture

```
loan-management-service ──(RabbitMQ)──> collections-service
                                              │
        ┌─────────────┬──────────────┬────────┤
        ▼             ▼              ▼        ▼
   Case Lifecycle  Actions/PTPs  Strategies  Analytics
        │             │              │        │
        └─────────────┴──────────────┴────────┘
                      │
              PostgreSQL (athena_collections)
```

### Event Flow

| Inbound Event | Routing Key | Handler |
|---|---|---|
| DPD Updated | `loan.dpd.#` | Opens/updates case, adjusts priority |
| Stage Changed | `loan.stage.#` | Escalates or closes case |
| Loan Closed | `loan.closed` | Closes associated case |
| Loan Written Off | `loan.written.off` | Closes associated case |
| Repayment Received | `loan.repayment.received` | Auto-fulfils pending PTPs |

| Outbound Event | Published When |
|---|---|
| `collection.case.created` | New case opened |
| `collection.case.escalated` | Stage worsens (e.g. WATCH -> SUBSTANDARD) |
| `collection.case.closed` | Case closed |
| `collection.action.taken` | Action recorded |
| `collection.writeoff.approved` | Write-off approved (for accounting GL entry) |
| `collection.restructure.requested` | Restructure requested (for loan-management) |

---

## API Reference

Base path: `/api/v1/collections`

### Summary & Analytics

| Method | Path | Description |
|---|---|---|
| GET | `/summary` | Aggregate counts and amounts by stage |
| GET | `/analytics/dashboard?from=&to=` | Recovery rate, ageing, new/closed cases, PTP fulfilment |
| GET | `/analytics/officer-performance?from=&to=` | Per-officer metrics |
| GET | `/analytics/ageing` | DPD bucket report (1-30, 31-60, 61-90, 90+) |

### Cases

| Method | Path | Description |
|---|---|---|
| GET | `/cases` | Paginated list with filters (see below) |
| GET | `/cases/{id}` | Single case |
| GET | `/cases/{id}/detail` | Case + actions + PTPs composite |
| GET | `/cases/loan/{loanId}` | Case by loan ID |
| GET | `/cases/overdue-followups` | Cases with overdue next action date |
| PUT | `/cases/{id}` | Update assignedTo, priority, notes |
| POST | `/cases/{id}/close` | Close a case |
| POST | `/cases/{id}/request-writeoff` | Request write-off (sets WRITE_OFF_REQUESTED) |
| POST | `/cases/{id}/approve-writeoff` | Approve write-off (sets WRITTEN_OFF, publishes GL event) |
| POST | `/cases/{id}/request-restructure` | Request loan restructuring |

#### Case List Filters

| Query Param | Type | Example |
|---|---|---|
| `page` | int | `0` |
| `size` | int | `20` |
| `status` | string | `OPEN`, `IN_PROGRESS`, `PENDING_LEGAL`, `WRITE_OFF_REQUESTED`, `WRITTEN_OFF`, `CLOSED` |
| `stage` | string | `WATCH`, `SUBSTANDARD`, `DOUBTFUL`, `LOSS` |
| `priority` | string | `LOW`, `NORMAL`, `HIGH`, `CRITICAL` |
| `assignedTo` | string | username |
| `minDpd` | int | `1` |
| `maxDpd` | int | `90` |
| `search` | string | ILIKE on case_number or customer_id |
| `sort` | string | `current_dpd`, `created_at`, `outstanding_amount`, `current_stage`, `priority` |
| `dir` | string | `asc` or `desc` |

### Actions

| Method | Path | Description |
|---|---|---|
| POST | `/cases/{id}/actions` | Add action to case |
| GET | `/cases/{id}/actions` | List actions for case |
| GET | `/cases/{id}/recommended-actions` | Strategy-based recommendations |

Action types: `PHONE_CALL`, `SMS`, `EMAIL`, `FIELD_VISIT`, `LEGAL_NOTICE`, `RESTRUCTURE_OFFER`, `WRITE_OFF`, `OTHER`
Outcomes: `CONTACTED`, `NO_ANSWER`, `PROMISE_RECEIVED`, `REFUSED_TO_PAY`, `PAYMENT_RECEIVED`, `ESCALATED`, `OTHER`

### Promises to Pay

| Method | Path | Description |
|---|---|---|
| POST | `/cases/{id}/ptps` | Add promise to pay |
| GET | `/cases/{id}/ptps` | List PTPs for case |

PTP statuses: `PENDING`, `FULFILLED`, `BROKEN`, `CANCELLED`

### Bulk Operations

| Method | Path | Body |
|---|---|---|
| POST | `/cases/bulk-assign` | `{ caseIds: [], assignedTo: "officer" }` |
| POST | `/cases/bulk-action` | `{ caseIds: [], actionType: "PHONE_CALL", outcome: "...", notes: "..." }` |
| POST | `/cases/bulk-priority` | `{ caseIds: [], priority: "HIGH" }` |

### Strategies

| Method | Path | Description |
|---|---|---|
| GET | `/strategies` | List all strategies |
| POST | `/strategies` | Create strategy |
| PUT | `/strategies/{id}` | Update strategy |
| DELETE | `/strategies/{id}` | Delete strategy |

### Officers (Workload Management)

| Method | Path | Description |
|---|---|---|
| GET | `/officers` | List officers |
| POST | `/officers` | Register officer |
| PUT | `/officers/{id}` | Update officer (maxCases, isActive) |
| GET | `/officers/workload` | Per-officer case counts by stage |

---

## Database Schema

### Tables

| Table | Purpose |
|---|---|
| `collection_cases` | Main case entity (20 columns including write-off fields, product_type) |
| `collection_actions` | Action audit trail per case |
| `promises_to_pay` | Promise commitments with status tracking |
| `collection_strategies` | Automated action rules by DPD range/product type |
| `collection_officers` | Officer capacity management |

### Migrations

| # | File | Description |
|---|---|---|
| 000001 | `initial` | Core tables: cases, actions, promises_to_pay |
| 000002 | `customer_id_to_varchar` | No-op (schema parity) |
| 000003 | `collection_strategies` | Strategies table, product_type on cases |
| 000004 | `phase3_phase4` | Officers table, write-off columns, analytics indexes |

---

## Scheduled Jobs

| Schedule | Job | Description |
|---|---|---|
| Daily 06:00 UTC | PTP Expiry Check | Marks overdue PENDING PTPs as BROKEN |
| Daily 07:00 UTC | Follow-up SLA | Escalates priority of cases with overdue follow-ups (>3 days) |

---

## Frontend Pages

| Route | Page | Description |
|---|---|---|
| `/collections` | CollectionsPage | Delinquency queue with filters, KPI cards, paginated case table |
| `/collections-workbench` | CollectionsWorkbenchPage | Officer daily view, overdue highlighting, inline action logging |
| `/collections/case/:id` | CaseDetailPage | Full case view with action timeline, PTPs, assign/close/write-off controls |
| `/legal` | LegalPage | Legal & write-off cases (PENDING_LEGAL status or LOSS stage) |
| `/collections/strategies` | CollectionStrategiesPage | Strategy CRUD with create/edit dialog, active toggle |
| `/collections/analytics` | CollectionsAnalyticsPage | Dashboard with charts: stage donut, ageing bar, officer leaderboard |

### Sidebar Navigation (Collections section)

1. Delinquency Queue (`/collections`)
2. Collections Workbench (`/collections-workbench`)
3. Legal & Write-Offs (`/legal`)
4. Strategies (`/collections/strategies`)
5. Analytics (`/collections/analytics`)

---

## Configuration

| Env Var | Default | Description |
|---|---|---|
| `PORT` | `8093` | HTTP server port |
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_NAME` | `athena_collections` | Database name |
| `DB_USER` | `athena` | Database user |
| `DB_PASSWORD` | `athena` | Database password |
| `RABBITMQ_HOST` | `localhost` | RabbitMQ host |
| `RABBITMQ_CONSUME_ENABLED` | `false` | Enable event consumption |
| `MIGRATE_ON_STARTUP` | `false` | Auto-run migrations on boot |
| `JWT_SECRET` | (required) | JWT signing key |

---

## Testing

**File:** `tests/test_11_collections.py`
**Total:** 31 tests across 6 test classes

| Class | Tests | Coverage |
|---|---|---|
| `TestCollections` | 15 | List, filter (stage/priority/DPD/search), detail, actions, PTPs, update, close, summary |
| `TestCollectionStrategies` | 4 | Strategy CRUD |
| `TestCollectionsBulkOps` | 2 | Bulk assign, bulk priority |
| `TestCollectionsOfficers` | 3 | List, create, workload |
| `TestCollectionsWriteOff` | 1 | Request write-off |
| `TestCollectionsAnalytics` | 3 | Dashboard, officer performance, ageing |

Run tests:
```bash
python3 -m pytest tests/test_11_collections.py -v
```

---

## Deployment

### Docker Compose (local dev)
```bash
cd ~/AthenaCreditScore
docker compose -f docker-compose.yml -f ~/AthenaIntelligentLMS/docker-compose.go.yml up -d --build go-collections-service
```

### k3s
```bash
# Build and import image
cd /mnt/storage/AntigravityProjects/athena-device-finance/k8s/scripts
bash build-lms.sh collections-service

# Apply manifests
kubectl apply -f k8s/lms/services.yaml

# Restart pod
kubectl rollout restart deployment/collections-service -n lms
```

---

## Key Files

### Backend (`go-services/internal/collections/`)
- `model/model.go` — Entities, DTOs, enums, converters
- `repository/repository.go` — Case, action, PTP queries (including filters, aggregates, analytics)
- `repository/strategy_repository.go` — Strategy CRUD
- `repository/officer_repository.go` — Officer CRUD + workload queries
- `service/service.go` — Business logic (26 methods)
- `handler/handler.go` — HTTP routes (31 endpoints)
- `consumer/consumer.go` — RabbitMQ event handlers
- `scheduler/scheduler.go` — PTP expiry + follow-up SLA cron jobs
- `event/publisher.go` — Outbound event publishing

### Frontend (`lms-portal-ui/src/`)
- `services/collectionsService.ts` — API client (15 methods)
- `pages/CollectionsPage.tsx` — Delinquency queue
- `pages/CollectionsWorkbenchPage.tsx` — Officer workbench
- `pages/CaseDetailPage.tsx` — Case detail view
- `pages/LegalPage.tsx` — Legal cases
- `pages/CollectionStrategiesPage.tsx` — Strategy management
- `pages/CollectionsAnalyticsPage.tsx` — Analytics dashboard

### Infrastructure
- `internal/common/rabbitmq/topology.go` — Exchange/queue/binding declarations
- `migrations/collections/` — 4 migration files
- `cmd/collections-service/main.go` — Service entry point
