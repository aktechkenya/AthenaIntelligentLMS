# AthenaLMS — Claude Code Instructions

## Auto-commit & Push
- Always commit and push changes automatically after completing work. Do not ask for confirmation.
- Use descriptive commit messages following the project convention: `type: short description`
- Types: `feat`, `fix`, `docs`, `chore`, `refactor`, `test`

## Project Structure
- **Go monorepo**: `go-services/` — all 16 microservices in one Go module
- **Frontend**: `lms-portal-ui/` — React + TypeScript + Vite + Tailwind + shadcn/ui
- **Tests**: `tests/` — pytest API tests (187 tests), `tests/ui/` — Playwright UI tests
- **Docker**: `docker-compose.go.yml` overlays on `~/AthenaCreditScore/docker-compose.yml`
- **Archived Java**: `_archived_java/` — original Spring Boot services (reference only)

## Go Services (ports 28xxx on host)
- All services share `go-services/internal/common/` (auth, config, db, event, middleware)
- Each service: `go-services/cmd/<service>/main.go` + `go-services/internal/<domain>/`
- Migrations: `go-services/migrations/<service>/`
- Config via env vars (Viper): DB_HOST, DB_NAME, DB_USER, DB_PASSWORD, PORT, JWT_SECRET

## Running Locally
```bash
# Start everything
cd ~/AthenaCreditScore
docker compose -f docker-compose.yml -f ~/AthenaIntelligentLMS/docker-compose.go.yml up -d --build

# Portal UI
cd ~/AthenaIntelligentLMS/lms-portal-ui && npx vite --port 3001

# Run tests
cd ~/AthenaIntelligentLMS && python3 -m pytest tests/ -v
```

## Test Accounts
- admin / admin123 (ADMIN), manager / manager123 (MANAGER), officer / officer123 (OFFICER)
