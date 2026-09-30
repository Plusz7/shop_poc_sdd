# Shop PoC — built with Spec-Driven Development

A guest online shop — browse a catalog, fill a cart, pay with Stripe (test mode) — with Prometheus
metrics, alert rules and Grafana dashboards.

The shop itself is deliberately small. **The point of this repository is the process:** every line of
code traces back to a written specification, an implementation plan and a numbered task, produced with
[GitHub Spec Kit](https://github.com/github/spec-kit) and implemented together with Claude Code.

## The Spec-Driven Development workflow

Instead of prompting an AI for code directly, the work moved through explicit, reviewable artifacts.
Each step is a Spec Kit skill (see [`.claude/skills`](.claude/skills)), and each produces a file
that lives in the repo:

| Step | Skill | Artifact | What it answers |
|---|---|---|---|
| 1. Constitution | `/speckit-constitution` | [`.specify/memory/constitution.md`](.specify/memory/constitution.md) | Which principles are non-negotiable (secrets, payments, DDD, tests, YAGNI)? |
| 2. Specify | `/speckit-specify` | [`spec.md`](specs/001-shop-browse-cart-checkout/spec.md) | *What* and *why*: 5 prioritized user stories, 34 functional requirements, edge cases, measurable success criteria — no tech choices |
| 3. Clarify | `/speckit-clarify` | updates to `spec.md` | Targeted questions that close ambiguities before any design work |
| 4. Plan | `/speckit-plan` | [`plan.md`](specs/001-shop-browse-cart-checkout/plan.md), [`research.md`](specs/001-shop-browse-cart-checkout/research.md), [`data-model.md`](specs/001-shop-browse-cart-checkout/data-model.md), [`contracts/`](specs/001-shop-browse-cart-checkout/contracts) | *How*: architecture, decisions with rationale, data model, and machine-checkable contracts (OpenAPI, Stripe webhook, metrics, frontend routes) |
| 5. Tasks | `/speckit-tasks` | [`tasks.md`](specs/001-shop-browse-cart-checkout/tasks.md) | 159 dependency-ordered tasks (`T001…`), grouped by user story so each is an independently shippable slice |
| 6. Analyze | `/speckit-analyze`, `/speckit-checklist` | [`checklists/`](specs/001-shop-browse-cart-checkout/checklists) | Cross-artifact consistency check: does every requirement have a task and a contract? |
| 7. Implement | `/speckit-implement` | `backend/`, `frontend/`, `observability/` | Tasks executed in order, one atomic commit per task |
| 8. Converge | `/speckit-converge` | new tasks appended to `tasks.md` | Compares code against the spec and turns any gap into tasks |

### How the specification stays connected to the code

- **The constitution is enforced, not just written.** For example, "BCs talk only through Facades" is
  an ArchUnit test ([`ArchitectureTest`](backend/src/test/java)); "no secrets in the repo" is a
  gitleaks pre-commit hook and CI job.
- **Contracts are executable.** The OpenAPI file in `contracts/` is checked against the running API by
  `OpenApiContractTest`; `metrics.md` is checked against Grafana dashboards and alert rules by
  [`scripts/check-dashboards.mjs`](scripts/check-dashboards.mjs).
- **`tasks.md` is the source of truth for scope.** Scope changes go into the task list first;
  a Trello board (managed by Claude through the Trello connector, see
  [`docs/kanban-trello.md`](docs/kanban-trello.md)) only mirrors status.
- **Conventions are encoded as skills** — Conventional Commits with one commit per task (`commit`),
  and an English-only repository policy (`english-only`).

## What was built

| User story | Priority | Summary |
|---|---|---|
| US1 Browse and search | P1 (MVP) | Catalog with search, filtering and product details |
| US2 Add to cart | P1 | Guest cart with quantity rules and stock checks |
| US3 View and edit cart | P1 | Change quantities, remove lines, totals recomputed by the server |
| US4 Order and pay | P1 | Checkout with Stripe; the server, via a signed webhook, is the source of truth for payment status |
| US5 Monitoring | P2 | Prometheus metrics, alert rules with `promtool` tests, provisioned Grafana dashboards |

## Architecture

A modular **DDD monolith** with explicit bounded contexts: `catalog`, `cart`, `order`, `payment`,
plus `shared`. Each context exposes a single public `Facade`; everything else is package-private.
Inside a context the layering is `api → application → domain ← infrastructure`. The domain model is
free of JPA annotations. Events use the **outbox pattern** (saved in the same transaction as the
aggregate; dispatching them is deliberately left to a future feature), and the Stripe integration is
isolated behind a domain port (`PaymentGateway`) so the SDK never leaks into the domain.

| Part | Stack |
|---|---|
| `backend/` | Spring Boot 4.0, Java 21, SQL Server, JPA/Hibernate, Flyway |
| `frontend/` | React, TypeScript, Vite, TanStack Query, Playwright |
| `observability/` | Prometheus (scrape config, alert rules, rule tests), Grafana (provisioned dashboards) |

## Technologies

Every choice below was made in the planning phase and is recorded, with rejected alternatives, in
[`research.md`](specs/001-shop-browse-cart-checkout/research.md).

### Payments — Stripe

- **Stripe Checkout (hosted page), test mode.** The backend creates a Checkout Session from the
  immutable order lines and redirects the browser to Stripe. Card data never touches this system
  (PCI SAQ A scope) and no Stripe key reaches the frontend.
- **The server is the source of truth.** An order becomes *paid* only after a signed webhook
  (`checkout.session.completed`), never because the browser landed on the success URL.
- **Webhook hardening:** signature verification on the raw body (`Stripe-Signature`), idempotent event
  handling (a `processed_stripe_event` table, deduplicated in the same transaction as the effect),
  amount and currency re-check against the order (mismatch → `NEEDS_REVIEW`), and an
  `Idempotency-Key` on session creation.
- **Money is never a float:** amounts are integers in minor units (grosze), which map 1:1 to Stripe's
  `unit_amount`.
- **Local development:** the Stripe CLI (`stripe listen`, run from `docker compose`) forwards
  webhooks to the backend; a `sk_live_` key blocks application startup.

### Backend

| Technology | Role |
|---|---|
| Spring Boot 4.0 / Java 21 | Web MVC, validation, Actuator |
| SQL Server 2022 + JPA/Hibernate | Persistence; the `Latin1_General_100_CI_AI` collation gives case- and diacritic-insensitive search (`LODZ` finds `łódź`) with no extra infrastructure |
| Flyway | Versioned schema migrations and sample data |
| springdoc-openapi | Swagger UI and the OpenAPI document checked against the contract |
| Micrometer + Prometheus registry | Technical and business metrics on a separate management port |
| Guest cookie (`shop_guest`) | Server-side cart tied to an `HttpOnly`, `SameSite=Lax` UUID cookie — no accounts, no Spring Security |

### Frontend

React 19, TypeScript, Vite, TanStack Query (server state), React Router, React Hook Form + Zod
(checkout form validation), and `openapi-typescript` / `openapi-fetch`, so API types are generated
from the same OpenAPI contract the backend is tested against.

### Observability

Prometheus scrapes the backend; alert rules (including a "paid within 30 s" SLO that detects missing
webhooks) are unit-tested with `promtool`; Grafana dashboards are provisioned as code.

### Testing and tooling

JUnit 5, Testcontainers (real SQL Server), WireMock (Stripe API stub), ArchUnit (bounded-context
boundaries), Vitest + Testing Library + MSW (frontend), Playwright (end-to-end), gitleaks and
`pre-commit` (secret scanning), GitHub Actions (CI), Docker Compose (local stack).

### Deliberately not used

RabbitMQ (no out-of-process consumer yet — the outbox table is in place for when there is one),
Elasticsearch, Spring Security and embedded Stripe elements were rejected as unjustified for a
proof of concept (constitution principle VII, YAGNI).

### Quality gates

Quality gates run in [CI](.github/workflows/ci.yml): unit and integration tests against a real SQL
Server (Testcontainers, no mocked repositories), architecture tests, OpenAPI contract test,
performance tests, frontend lint/typecheck/unit tests, Playwright e2e, secret scan, and Prometheus
rule tests.

## Getting started

### Prerequisites

- JDK 21 (Maven is provided by `backend/mvnw`)
- Node.js 22 LTS
- Docker Desktop (SQL Server, Prometheus, Grafana, Testcontainers, Stripe CLI)
- A Stripe account in **test** mode (only needed to pay for an order)
- [gitleaks](https://github.com/gitleaks/gitleaks) and `pre-commit` (`pre-commit install` after cloning)

### Configuration

Copy `.env.example` to `.env` (the file is git-ignored; never commit real values) and fill in at least:

| Variable | Value |
|---|---|
| `DB_PASSWORD` | any strong password for the local SQL Server |
| `GRAFANA_ADMIN_PASSWORD` | any strong password for Grafana (`docker compose up` refuses to start without it) |
| `STRIPE_SECRET_KEY` | `sk_test_…` key (an `sk_live_` key blocks startup); `sk_test_dummy` is enough without Stripe |
| `STRIPE_WEBHOOK_SECRET` | `whsec_…` printed by `stripe listen`; `whsec_dummy` is enough without Stripe |
| `APP_BASE_URL` | `http://localhost:5173` |

### Run

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

### Tests and checks

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

## Where to read next

1. [`constitution.md`](.specify/memory/constitution.md) — the rules of the project
2. [`spec.md`](specs/001-shop-browse-cart-checkout/spec.md) — what was asked for
3. [`plan.md`](specs/001-shop-browse-cart-checkout/plan.md) and [`research.md`](specs/001-shop-browse-cart-checkout/research.md) — how and why it was designed
4. [`tasks.md`](specs/001-shop-browse-cart-checkout/tasks.md) — the executable work breakdown
5. [`quickstart.md`](specs/001-shop-browse-cart-checkout/quickstart.md) — a step-by-step walkthrough of every scenario
