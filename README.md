# Shop PoC

A guest online shop: browse a catalog, fill a cart and pay for an order with Stripe (test mode), with
Prometheus metrics, alert rules and Grafana dashboards. Built spec-first (Spec Kit); the feature
`001-shop-browse-cart-checkout` is described in [`specs/001-shop-browse-cart-checkout`](specs/001-shop-browse-cart-checkout).

| Part | Stack |
|---|---|
| `backend/` | Spring Boot 4.0, Java 21, SQL Server, JPA/Hibernate, Flyway, DDD with bounded contexts |
| `frontend/` | React, TypeScript, Vite, TanStack Query, Playwright |
| `observability/` | Prometheus (scrape config, alert rules, rule tests), Grafana (provisioned dashboards) |

## Prerequisites

- JDK 21 (Maven is provided by `backend/mvnw`)
- Node.js 22 LTS
- Docker Desktop (SQL Server, Prometheus, Grafana, Testcontainers, Stripe CLI)
- A Stripe account in **test** mode (only needed to pay for an order)
- [gitleaks](https://github.com/gitleaks/gitleaks) and `pre-commit` (`pre-commit install` after cloning)

## Configuration

Copy `.env.example` to `.env` (the file is git-ignored; never commit real values) and fill in at least:

| Variable | Value |
|---|---|
| `DB_PASSWORD` | any strong password for the local SQL Server |
| `GRAFANA_ADMIN_PASSWORD` | any strong password for Grafana (`docker compose up` refuses to start without it) |
| `STRIPE_SECRET_KEY` | `sk_test_…` key (an `sk_live_` key blocks startup); `sk_test_dummy` is enough without Stripe |
| `STRIPE_WEBHOOK_SECRET` | `whsec_…` printed by `stripe listen`; `whsec_dummy` is enough without Stripe |
| `APP_BASE_URL` | `http://localhost:5173` |

## Run

```bash
docker compose up -d
```

Forward Stripe webhooks to the backend (first run: copy the printed `whsec_…` into `.env`):

```bash
docker compose --profile stripe up -d stripe-cli
```

Backend (`:8080` API, `:8081` metrics and health):

```bash
./backend/mvnw -f backend/pom.xml spring-boot:run -Dspring-boot.run.profiles=local
```

Frontend (`/api` and `/images` are proxied to the backend):

```bash
npm --prefix frontend ci
npm --prefix frontend run dev
```

| What | Address |
|---|---|
| Shop | <http://localhost:5173> |
| Swagger UI (`local` profile only) | <http://localhost:8080/swagger-ui.html> |
| Grafana (`admin` / `GRAFANA_ADMIN_PASSWORD`) | <http://127.0.0.1:3000> |
| Prometheus | <http://127.0.0.1:9090> |

## Tests and checks

```bash
./backend/mvnw -f backend/pom.xml verify        # unit, integration (Testcontainers), architecture, OpenAPI contract, performance
npm --prefix frontend run lint
npm --prefix frontend run typecheck
npm --prefix frontend test
npm --prefix frontend run e2e                   # Playwright; needs the full stack and a Stripe test key
gitleaks detect --no-banner                     # secret scan
node scripts/check-dashboards.mjs               # dashboards and alert rules refer only to contract metrics
```

Alert rules and Prometheus config (same image version as `compose.yaml`):

```bash
docker run --rm -v "$PWD/observability/prometheus:/etc/prometheus" --entrypoint promtool prom/prometheus:v3.13.3 check config /etc/prometheus/prometheus.yml
docker run --rm -v "$PWD/observability/prometheus:/etc/prometheus" --entrypoint promtool prom/prometheus:v3.13.3 check rules /etc/prometheus/rules/shop.yml
docker run --rm -v "$PWD/observability/prometheus:/etc/prometheus" --entrypoint promtool prom/prometheus:v3.13.3 test rules /etc/prometheus/tests/shop.test.yml
```

## Documentation

The step-by-step walkthrough of every scenario is in
[`specs/001-shop-browse-cart-checkout/quickstart.md`](specs/001-shop-browse-cart-checkout/quickstart.md).
