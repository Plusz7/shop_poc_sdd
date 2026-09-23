# Implementation Plan: Shop Browsing, Cart and Checkout

**Branch**: `001-shop-browse-cart-checkout` | **Date**: 2026-09-23 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/001-shop-browse-cart-checkout/spec.md`

## Summary

The first feature of the shop PoC: the full guest purchase path — browsing the catalog
(categories, diacritic-insensitive search, price filter, sorting, pagination in the URL),
a server-side cart tied to the browser with a cookie, cart editing with pricing taken only from
the catalog, and an order paid through **hosted Stripe Checkout**. An order becomes "Paid" only
after a verified, deduplicated Stripe webhook, which in a single transaction decreases stock,
clears the cart and writes the `OrderPaidEvent` event to the Outbox for the future Trello
integration.

Technically: a Spring Boot 4 (Java 21) modular monolith with the BCs `catalog`, `cart`, `order`,
`payment` + `shared`, SQL Server with Flyway, and a React + Vite + TypeScript SPA using the OpenAPI
contract (API-first). Decision details: [research.md](research.md).

**Observability (US5, P2)**: Actuator + Micrometer expose Prometheus metrics on a separate
management port `8081` (not reachable through the frontend proxy). Application services report
business metrics through `*Metrics` ports in each BC, and recording happens only after commit.
The Stripe adapter measures calls and retries, and the webhook measures confirmation outcomes and
the delay relative to Stripe. `docker compose up -d` also starts Prometheus (15-day retention,
9 alert rules tested with `promtool`) and Grafana with 4 provisioned dashboards. Logs carry the
`X-Request-Id` correlation identifier. Metrics catalog: [contracts/metrics.md](contracts/metrics.md);
decisions R-26–R-34.

## Technical Context

**Language/Version**: Java 21 (backend), TypeScript 5.x strict (frontend)

**Primary Dependencies**: Spring Boot 4.0 (Web MVC, Data JPA/Hibernate 7, Validation,
Actuator), Flyway (+ SQL Server module), `com.stripe:stripe-java`, springdoc-openapi (line
compatible with Boot 4), ArchUnit, `micrometer-registry-prometheus`; React 19, Vite, React Router,
TanStack Query, React Hook Form + Zod, `openapi-typescript` + `openapi-fetch`;
local observability: Prometheus and Grafana (containers, versions pinned in `compose.yaml`)

**Storage**: SQL Server 2022 (collation of column `product.name` = `Polish_100_CI_AI`),
schema via Flyway; static seed images in backend resources

**Testing**: JUnit Jupiter + AssertJ (domain), `@SpringBootTest` + Testcontainers
(`MSSQLServerContainer`, `stripe-mock`), WireMock (Stripe failures), ArchUnit, OpenAPI
conformance test; Vitest + Testing Library + MSW (frontend); Playwright + Stripe CLI (E2E);
`SimpleMeterRegistry` (metrics adapters), `promtool test rules` (alerts), `scripts/check-dashboards.mjs`
(dashboards vs the metrics contract)

**Target Platform**: backend — JVM in a Linux container / locally Windows/macOS;
frontend — modern desktop and mobile browsers (from 360 px)

**Project Type**: web application — REST backend (modular monolith) + SPA

**Performance Goals**: list, search and cart < 1 s for the customer with ≥ 500 products
(SC-003; backend budget 300 ms); "Paid" status ≤ 30 s after the Stripe confirmation (SC-007);
dashboards with data ≤ 2 min after startup (SC-009); events in metrics ≤ 1 min (SC-010);
metrics overhead on p95 < 5% (SC-012)

**Constraints**: amounts in grosze (`long`), no `double`/`float`; Stripe test keys only
(`sk_test_`), no Stripe key in the frontend; no external calls inside transactions; no logging of
personal data or secrets; Polish user interface; metrics without personal data and with labels
from a closed set of values; metrics endpoint only on the management port; metric collection never
blocks the purchase path

**Scale/Scope**: PoC — ~500 products, a dozen or so categories, 5 screens, 4 BCs, a single backend
instance, demo traffic; 14 domain metrics, 9 alert rules, 4 dashboards

No "NEEDS CLARIFICATION" items — all choices are settled in research.md (R-01–R-34).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Requirement | How the plan satisfies it | Pre | Post |
|---|---|---|---|---|
| **I. Secrets** | env/`@ConfigurationProperties`, `.env` in `.gitignore`, `.env.example`, no `sk_` in the frontend, startup validation, gitleaks | `StripeProperties` with `@Validated` + `sk_test_`/`whsec_` patterns that block startup; `.env.example` extended with `STRIPE_*`, `DB_PASSWORD`, `APP_BASE_URL`; the frontend gets **no** key at all (hosted Checkout); gitleaks in pre-commit and CI; PII masking in logs (R-20). Observability: Grafana password only from `GRAFANA_ADMIN_PASSWORD` (missing → `compose up` aborts), no `admin/admin` and no anonymous access; metrics without secrets and PII, checked by the `MetricsEndpointIT` test (R-31, R-33) | ✅ | ✅ |
| **II. Payments** | Stripe Checkout/Elements, server-side amount, grosze, webhook with signature only, `event.id` idempotency + idempotency key, test mode only | Hosted Checkout (SAQ A); amount from the immutable, server-priced order lines; `Money(long minor)`; `Webhook.constructEvent` + `livemode` rejection; table `processed_stripe_event` in the same transaction; `Idempotency-Key: checkout-{paymentId}`; comparison of `amount_total` with the total (R-11, R-12, R-14) | ✅ | ✅ |
| **III. Modular DDD monolith** | BCs with layers and a Facade, communication via Facade/events, annotation-free domain, frontend via OpenAPI without business rules | BCs `catalog`, `cart`, `order`, `payment` + `shared`; no cycle thanks to `payment → order` events; ArchUnit enforces boundaries; contract `contracts/openapi.yaml`, the frontend only presents statuses/flags from the API (R-02, R-03, R-19). BC `fulfillment` — a separate feature (a temporary deviation from the "minimal set" — see Complexity Tracking). Metrics: `*Metrics` ports in each BC's `application/`, Micrometer adapters in `infrastructure/metrics/`; domain unchanged; a new ArchUnit rule — `io.micrometer..` only in `..infrastructure..` (R-27) | ✅ | ✅ |
| **IV. Marketplace paths** | 4 independently testable P1 paths, quantity limits, showing price changes | US1–US4 mapped to routes and endpoints (`contracts/frontend-routes.md`); quantity rules in the `Cart` aggregate; `priceChanged` + `409 SUMMARY_OUTDATED`; E2E for every path | ✅ | ✅ |
| **V. Isolated integrations** | port in `domain/`, adapter in `infrastructure/`, Outbox, timeouts/retries, Trello does not block a purchase | `PaymentGateway` port + Stripe adapter; 5 s/10 s timeouts, 2 retries; Stripe calls outside transactions; `outbox_event` written atomically with the payment; the purchase path has no dependency on Trello whatsoever (R-13, R-17). Stripe calls measured in the adapter (duration, outcome, retries — the adapter retries, not the SDK); pull-based metric collection, so a Prometheus/Grafana failure does not affect the purchase path (R-26, R-27) | ✅ | ✅ |
| **VI. Tests** | domain unit tests without Spring, `*Service` integration tests with Testcontainers, adapter contract tests, webhook with valid and forged signature, P1 E2E, no real secrets | Test matrix in R-21 and `contracts/stripe-webhook.md`; Playwright E2E + Stripe CLI with secrets from CI. Observability: matrix in R-34 — metrics adapter unit tests, counter assertions after commit and rollback in `*Service` tests, `MetricsEndpointIT`, `promtool test rules` for every rule (SC-011) | ✅ | ✅ |
| **VII. YAGNI** | every additional component justified | No RabbitMQ, cache, Spring Security, Spring Session, Elasticsearch, Alertmanager, OpenTelemetry Collector or tracing. Stripe CLI (dev/E2E tool). **Prometheus + Grafana** — new containers, explicitly justified in the spec (US5, FR-032, explicit request of the owner) — entry in Complexity Tracking | ✅ | ✅ |
| Stack and process | Java 21, Boot 4, SQL Server, React+Vite (approved here), OpenAPI, `docker compose up`, PR with gates | **We approve React 19 + Vite + TypeScript**; `compose.yaml`; CI: build, tests, gitleaks, dependency audit with `osv-scanner` (Maven + npm) and `npm audit` (R-18, R-22); `docker compose up -d` also starts Prometheus and Grafana; CI additionally: `promtool test rules`, `check-dashboards.mjs` (R-31, R-34) | ✅ | ✅ |

**Gate result**: PASS (before Phase 0 and after Phase 1) with 2 documented, temporary deviations
and 2 justified additions for US5 (see Complexity Tracking).

**Notes on compliance with `AGENTS.md`** (code conventions, not violations):

- `AGENTS.md` lists RabbitMQ in the stack — the constitution (Principle VII and "Asynchrony")
  takes precedence: RabbitMQ is not added because there is no out-of-process consumer (R-17).
- The `WniosekProcessor`/XML validation convention from `AGENTS.md` comes from another domain and
  does not apply to the shop. Recommended update of `AGENTS.md` (the "project description" section
  and examples) in a separate PR — per the constitution's Governance.
- `AGENTS.md` says domain classes are named in Polish; constitution 1.1.0 ("Language") requires
  English — `AGENTS.md` to be updated in the same separate PR.
- `fulfillment` from the minimal BC set will be created in the Trello integration feature; this
  feature only provides the Outbox event for it (per the spec assumption).
- `AGENTS.md` requires package-private `*Service`, but `api/` and `application/` are different Java
  packages, so the controller would not see the service. Application services are `public`, and
  their use outside their own BC is blocked by ArchUnit (T012). `AGENTS.md` update in a separate PR.

## Project Structure

### Documentation (this feature)

```text
specs/001-shop-browse-cart-checkout/
├── spec.md
├── plan.md                  # this file
├── research.md              # Phase 0 — decisions R-01…R-34 (R-26…R-34: observability)
├── data-model.md            # Phase 1 — entities, tables, state machines, facades, metrics ports
├── quickstart.md            # Phase 1 — running and validation scenarios (US1–US5)
├── contracts/
│   ├── openapi.yaml         # REST contract (API-first) + X-Request-Id header
│   ├── stripe-webhook.md    # Stripe integration contract (outgoing + webhook + metrics)
│   ├── metrics.md           # metrics catalog, alert rules, dashboards (US5)
│   └── frontend-routes.md   # SPA routes, URL parameters, view states
├── checklists/
│   └── requirements.md
└── tasks.md                 # Phase 2 — /speckit-tasks (US1–US5)
```

### Source Code (repository root)

```text
backend/
├── pom.xml                                  # + mvnw, .mvn/
└── src/
    ├── main/
    │   ├── java/com/project/custom/
    │   │   ├── ShopApplication.java
    │   │   ├── shared/
    │   │   │   ├── domain/                  # Money, GuestId, outbox/OutboxEventPublisher (port)
    │   │   │   ├── api/                     # CorrelationFilter (X-Request-Id → MDC), GuestIdFilter (shop_guest cookie), GlobalExceptionHandler (ProblemDetail)
    │   │   │   └── infrastructure/
    │   │   │       ├── outbox/              # OutboxEventJpaEntity, JpaOutboxEventPublisher (adapter)
    │   │   │       ├── metrics/             # AfterCommit, OutboxMetrics, FlywayMetrics, MetricsConfig (MeterFilter: uri limit)
    │   │   │       └── config/              # AppProperties (APP_BASE_URL), PII masking
    │   │   ├── catalog/
    │   │   │   ├── CatalogQueryFacade.java, CatalogCommandFacade.java  # + public record DTOs
    │   │   │   ├── domain/                  # Product, Category, AvailabilityStatus, SearchCriteria, ProductRepository
    │   │   │   ├── application/             # CatalogService
    │   │   │   ├── infrastructure/persistence/  # ProductJpaEntity, CategoryJpaEntity, ProductJpaRepository, repository adapters
    │   │   │   └── api/                     # CatalogController
    │   │   ├── cart/
    │   │   │   ├── CartQueryFacade.java, CartCommandFacade.java
    │   │   │   ├── domain/                  # Cart, CartLine, domain exceptions, CartRepository
    │   │   │   ├── application/             # CartService (pricing via CatalogQueryFacade), CartMetrics (port)
    │   │   │   ├── infrastructure/persistence/
    │   │   │   ├── infrastructure/metrics/  # MicrometerCartMetrics
    │   │   │   └── api/                     # CartController
    │   │   ├── order/
    │   │   │   ├── OrderQueryFacade.java, OrderPaidEvent.java
    │   │   │   ├── domain/                  # Order, OrderLine, OrderStatus, CustomerDetails, ShippingAddress, OrderNumber, OrderRepository
    │   │   │   ├── application/             # PlaceOrderService, PaymentEventsListener, OrderMetrics (port), MismatchKind
    │   │   │   ├── infrastructure/persistence/
    │   │   │   ├── infrastructure/metrics/  # MicrometerOrderMetrics
    │   │   │   └── api/                     # OrderController
    │   │   └── payment/
    │   │       ├── PaymentFacade.java, PaymentConfirmedEvent.java, PaymentFailedEvent.java
    │   │       ├── domain/                  # Payment, PaymentStatus, PaymentGateway (port), ProviderConfirmation, PaymentRepository
    │   │       ├── application/             # PaymentService, WebhookHandlingService, PaymentMetrics (port), WebhookOutcome, PaymentOutcome
    │   │       ├── infrastructure/
    │   │       │   ├── persistence/         # PaymentJpaEntity, ProcessedEventJpaEntity
    │   │       │   ├── metrics/             # MicrometerPaymentMetrics
    │   │       │   └── stripe/              # StripeProperties, StripePaymentGateway (+ retries and call metrics), StripeWebhookVerifier
    │   │       └── api/                     # StripeWebhookController
    │   └── resources/
    │       ├── application.yaml, application-local.yaml   # + management.server.port=8081, exposure health,prometheus, histograms (R-26)
    │       ├── openapi/shop-api.yaml        # copy of contracts/openapi.yaml
    │       ├── db/migration/                # V1__catalog.sql, V2__cart.sql, V3__order.sql, V4__payment.sql, V5__outbox.sql
    │       ├── db/seed/                     # R__seed_catalog.sql (local/e2e profile)
    │       └── static/images/
    └── test/java/com/project/custom/
        ├── ArchitectureTest.java, OpenApiContractTest.java, MetricsEndpointIT.java
        ├── support/                         # IntegrationTest (Testcontainers), StripeWebhookSigner, MetricsAssert
        ├── catalog/  cart/  order/  payment/   # domain/ (unit), application/ (integration), infrastructure/ (contract)

frontend/
├── package.json, vite.config.ts, tsconfig.json, playwright.config.ts
├── src/
│   ├── api/                 # schema.d.ts (generated from contracts/openapi.yaml), client.ts
│   ├── app/                 # router, QueryClientProvider, Layout with header and cart counter
│   ├── features/
│   │   ├── catalog/         # CatalogPage, ProductPage, Filters, useUrlFilters
│   │   ├── cart/            # CartPage, CartLineItem, useCart (mutations + invalidate)
│   │   └── checkout/        # CheckoutPage (form + summary), OrderConfirmationPage (polling)
│   ├── i18n/pl.json         # Polish UI copy (the only place with Polish text)
│   └── shared/              # formatPln, UI components, toasts
└── tests/
    ├── unit/                # Vitest + Testing Library + MSW
    └── e2e/                 # Playwright: browsing, cart-add, cart-edit, payment

observability/
├── prometheus/
│   ├── prometheus.yml       # scrape host.docker.internal:8081, 15 s interval, rule_files
│   ├── rules/shop.yml       # 9 rules from contracts/metrics.md §4
│   └── tests/shop.test.yml  # promtool: firing + resolved for every rule
└── grafana/
    ├── provisioning/
    │   ├── datasources/prometheus.yaml   # uid: prometheus
    │   └── dashboards/shop.yaml          # file provider → folder "Shop"
    └── dashboards/          # http.json, purchase-funnel.json, payments-integrations.json, jvm-database.json

scripts/check-dashboards.mjs # validates dashboards and rules against contracts/metrics.md
compose.yaml                 # sqlserver (+ database init), prometheus, grafana; profile "stripe": stripe-cli listen
.env.example                 # + STRIPE_SECRET_KEY, STRIPE_WEBHOOK_SECRET, DB_PASSWORD, APP_BASE_URL, GRAFANA_ADMIN_PASSWORD
.pre-commit-config.yaml      # gitleaks
.github/workflows/ci.yml     # backend verify, frontend test/lint/typecheck, gitleaks, dependency audit, promtool, check-dashboards, E2E (when secrets exist)
```

**Structure Decision**: a web application in a monorepo — `backend/` (a single Maven module,
a modular monolith with one package per BC per `AGENTS.md`) and `frontend/` (SPA). BC boundaries
are enforced by ArchUnit, not by build modules (R-01, R-03). The `contracts/openapi.yaml` contract
is shared: a copy in backend resources and the source of frontend types.

## Key flows (summary)

1. **Placing an order** (`POST /api/orders`): `CartQueryFacade.price` → comparison with the
   confirmed summary (409 on a difference) → `PaymentFacade.expireOpen(guest)` →
   **TX1**: save `Order(AWAITING_PAYMENT)` + `Payment(CREATED)` →
   **Stripe** `checkout.sessions.create` (outside TX) → **TX2**: `Payment(OPEN, sessionId, url)`
   or `Order(PAYMENT_FAILED)` + 503 → `201 {number, paymentUrl}`.
2. **Webhook** (`POST /api/payments/stripe/webhook`): signature verification → **TX**:
   dedup `event.id` → `Payment(CONFIRMED)` → `PaymentConfirmedEvent` →
   `order`: amount check → `CatalogCommandFacade.decreaseStock` →
   `PAID` | `NEEDS_REVIEW` → `CartCommandFacade.clear` → `OutboxEvent` → commit → `200`.
3. **Customer return** (`/orders/{number}`): read-only and status polling — no state change.
4. **Metrics** (cross-cutting): application service → `*Metrics` port → Micrometer adapter →
   `AfterCommit` (recording only after commit; a rollback counts nothing) → registry →
   `:8081/actuator/prometheus` ← Prometheus every 15 s → alert rules and Grafana dashboards.
   Webhook: verification/deduplication outcome → `shop.payment.webhook`, and after commit
   `shop.payments` and the delay relative to `event.created`.

## Delivery order (input for `/speckit-tasks`)

1. **Foundation**: `backend/` and `frontend/` skeleton, `compose.yaml`, Flyway, `shared`
   (`Money`, `GuestId`, cookie filter, error handling), `StripeProperties` with validation,
   `.env.example`, gitleaks, CI, ArchitectureTest, contract in resources and frontend types.
2. **US1** catalog: migration + seed, search with collation, API, list and product pages.
3. **US2** cart — adding: `Cart` aggregate, POST/GET API, header counter.
4. **US3** cart — editing: PUT/DELETE, pricing with price/availability changes, price acceptance.
5. **US4** order and payment: `order` + `payment`, Stripe adapter, webhook, Outbox, checkout and
   confirmation pages.
6. **US5** observability — two parts:
   - **Foundation** (in phase 1 with the rest of the skeleton, so that metrics ports exist before
     the services are created): Actuator on `8081`, Prometheus registry, `AfterCommit`,
     `CorrelationFilter`, ArchUnit rule for `io.micrometer`, Prometheus/Grafana containers with
     provisioning.
   - **US5 proper** (after US4): `*Metrics` ports and adapters wired into the US2–US4 services,
     Stripe and webhook metrics (including `payment_intent.payment_failed`), Outbox and Flyway
     gauges, rules + `promtool` tests, 4 dashboards, `MetricsEndpointIT`, `check-dashboards.mjs`.
7. **Wrap-up**: E2E of the 4 paths, performance test on 500 products (including metrics overhead),
   a run through `quickstart.md` including the US5 section.

US1–US3 do not require a Stripe account or connection and can be demonstrated independently
(Principle IV) — the application starts with dummy keys in the `sk_test_…`/`whsec_…` format
(the format validation from Principles I/II always applies).

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| No `fulfillment` BC from the minimal set (Principle III) | The spec moves the Trello integration to a separate feature; this feature only provides the `OrderPaidEvent` event in the Outbox | An empty `fulfillment` package without use cases is dead code (Principle VII); the BC will be created in the Trello integration feature |
| Outbox without a dispatch job (Principle V: "a job with retries sends them") | There is no event consumer yet; saving in a single transaction guarantees the event is not lost (R-17) | A job without handlers is dead code; `OutboxEventSchedulerJob` will be created together with the Trello handler in the `fulfillment` feature |
| New Prometheus + Grafana containers (Principle VII) | US5, FR-032/FR-033 and the spec assumption ("explicit request of the project owner"): dashboards and alerts without manual configuration | Actuator metrics alone without Prometheus — no history, no `histogram_quantile` queries or alert rules; Grafana Cloud — an external account and secret; Alertmanager — notification delivery is out of spec scope |
| `OutboxBacklog` rule permanently active locally until the `fulfillment` feature (R-30) | FR-034 requires the rule now, and the lack of a dispatch job (R-17) means events really do wait | Postponing the rule — contradicts FR-034; a job marking events as sent without a handler — falsifies the metric; the rule is labeled `requires="fulfillment"` and described on the dashboard |
