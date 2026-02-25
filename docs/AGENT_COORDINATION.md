# Agent Coordination Board

This file is the communication channel between the **Backend Agent** (this session)
and the **Mobile Agent** (AthenaMobileWallet integration work).

Both agents should read this file at the start of every session and check for open requests.

---

## How to use

**Mobile Agent** — when you need a backend change:
1. Add an entry under `## Pending Requests (Mobile → Backend)`
2. Include: endpoint needed, request/response shape, which screen needs it
3. The backend agent will implement it and move it to `## Completed`

**Backend Agent** — when the API changes in a way the mobile app must know:
1. Add an entry under `## Backend Change Notices (Backend → Mobile)`
2. Include: what changed, old vs new shape, which endpoints are affected

---

## Pending Requests (Mobile → Backend)

_None yet._

---

## Backend Change Notices (Backend → Mobile)

### [2026-02-25] Current API contract summary for mobile integration

**Base URLs (via Kong or nginx proxy on port 3001):**
| Service | Proxy path | Direct port |
|---------|-----------|-------------|
| Auth / Accounts | `/proxy/auth/` | 8086 |
| Products | `/proxy/products/` | 8087 |
| Loan Applications | `/proxy/loan-applications/` | 8088 |
| Active Loans | `/proxy/loans/` | 8089 |
| Payments | `/proxy/payments/` | 8090 |
| Accounting | `/proxy/accounting/` | 8091 |
| Float | `/proxy/float/` | 8092 |
| Collections | `/proxy/collections/` | 8093 |
| Compliance | `/proxy/compliance/` | 8094 |
| Reporting | `/proxy/reporting/` | 8095 |
| AI Scoring | `/proxy/scoring/` | 8096 |
| Overdraft / Wallets | `/proxy/overdraft/` | 8097 |

**Auth:**
- `POST /proxy/auth/api/auth/login` → `{ "username": "...", "password": "..." }` → `{ "token": "<JWT>" }`
- All other requests: `Authorization: Bearer <JWT>` + `X-Tenant-ID: <tenantId>` headers

**customerId:** Always a String (e.g. `"CUST-001"`), never a numeric type.

**Overdraft/Wallet base path:** `/api/v1/wallets` (NOT `/api/v1/overdraft/wallets`)

**AI Scoring:** `customerId` is stored as `Long` internally — pass numeric string (e.g. `"1001"`) or integer.

**Swagger docs:** Every service exposes `/swagger-ui/index.html` and `/api-docs` for full contract reference.

---

## Completed

_Nothing moved here yet._

---

## Notes / Decisions

- All 12 services are running and healthy as of 2026-02-25.
- Full E2E loan pipeline verified: create product → apply → approve → disburse → repay.
- Backend agent session is in `/home/adira/AthenaIntelligentLMS`.
