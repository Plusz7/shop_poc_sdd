<!--
Sync Impact Report
- Version: 1.0.0 → 1.1.0 (MINOR: full translation to English + new "Language" rule in
  Technology Stack and Constraints; no other change in meaning)
- Renamed identifiers: bounded contexts `katalog`, `koszyk`, `zamowienie`, `platnosc`, `realizacja`
  → `catalog`, `cart`, `order`, `payment`, `fulfillment` (see .claude/skills/english-only/glossary.md)
- Templates: no changes required
- Follow-up: AGENTS.md still says domain classes are named in Polish — update in a separate PR
-->

# Shop PoC Constitution

## Core Principles

### I. Security of Secrets and API Keys (NON-NEGOTIABLE)

- Secrets (Stripe secret key, Stripe webhook signing secret, Trello API key/token, database
  passwords) MUST be supplied only through environment variables or a secret manager and
  injected via `@ConfigurationProperties`/`@Value`. NEVER in code, in an `application.properties`
  committed to the repository, in tests, or in git history.
- Locally, secrets live in a `.env` file (or `application-local.properties`) that MUST be listed
  in `.gitignore`. The repository contains only `.env.example` with empty values and descriptions.
- The frontend NEVER receives secret keys. Only the Stripe **publishable key** (`pk_...`) is
  allowed; every operation that requires `sk_...` goes through the backend.
- Secrets MUST NOT appear in logs, error messages, API responses, or in MCP configuration
  committed to the repository (MCP configuration refers to environment variables).
- On startup the application MUST verify that all required secrets are present and refuse to
  start with a clear message (without revealing values) if any are missing.
- The repository MUST have secret scanning (e.g. `gitleaks`) as a pre-commit hook or CI step.
- A leaked key = immediate rotation at the provider, regardless of the PoC stage.

Rationale: a Stripe key grants access to financial operations, a Trello token to team data.
A leak is irreversible, and the cost of discipline from day one is minimal.

### II. Payments Only Through Stripe, Server as the Source of Truth

- Card data NEVER passes through our frontend or backend — we use Stripe Checkout or Stripe
  Elements / Payment Element (PCI DSS SAQ A scope).
- The amount to pay MUST be calculated server-side from current catalog prices and cart
  contents. Prices and totals sent by the frontend are ignored.
- Monetary amounts MUST be represented as integers in minor units (grosze) or as `BigDecimal`
  with an explicit currency (PLN by default). `double`/`float` are forbidden.
- An order becomes paid ONLY on the basis of a verified Stripe webhook (`Stripe-Signature`
  verification), never on the basis of a frontend redirect.
- Webhook handling MUST be idempotent (deduplication by `event.id`); calls that create a
  payment use an idempotency key.
- The PoC uses Stripe **test mode** only (`sk_test_...`/`pk_test_...`); a live key in the PoC
  environment is a configuration error that blocks application startup.

Rationale: payment is the riskiest part of the shop — trusting the client or a redirect leads
to "paid" orders without money.

### III. Modular DDD Monolith with Bounded Contexts

- The backend is a modular monolith following `AGENTS.md`: each Bounded Context is a separate
  package with `domain / application / infrastructure / api` layers and a single public entry
  point through `*Facade`.
- Minimal set of BCs: `catalog` (products, categories, search), `cart`, `order`, `payment`
  (Stripe adapter), `fulfillment` (Trello integration).
- Communication between BCs only through a Facade or domain events; reaching into another BC's
  repositories and entities is forbidden.
- Domain entities are free of framework annotations; mapping to `*JpaEntity` lives in the
  infrastructure layer.
- The frontend is a separate application that talks to the backend only through a documented
  REST API (OpenAPI contract). The frontend contains no business rules for prices, availability
  or payment status — it only presents them.

Rationale: clear boundaries let the PoC grow into a product without a rewrite and isolate risky
integrations from the domain core.

### IV. Key Purchase Paths Modeled on Marketplaces

Priority user paths (P1) — each MUST be independently testable and demonstrable:

1. **Browsing the shop** — paginated product list, categories, search by name, filtering
   (e.g. price) and sorting; product page with image, description, price and availability.
2. **Adding to cart** — from the list and from the product page, with quantity selection;
   immediate feedback (cart counter in the header), without forcing a login.
3. **Editing the cart** — changing quantities, removing lines, viewing line subtotals and the
   total calculated by the backend; a guest cart survives a page refresh.
4. **Payment** — moving from the cart to checkout, Stripe payment, confirmation page, and
   handling of failed/canceled payments without losing the cart.

UX rules inspired by Allegro/eBay: adding an unavailable product, or a quantity larger than the
stock, is blocked with a clear message; a price change between adding to the cart and paying is
shown to the user before payment. Features outside P1 (reviews, auctions, seller accounts,
recommendations) are out of scope until P1 works end-to-end.

Rationale: we measure the PoC's value by whether the full path from browsing to a paid order
can be completed.

### V. External Integrations Isolated and Resilient

- Every integration (Stripe, Trello via MCP/REST) is hidden behind a port (an interface in
  `domain/`) with an adapter in `infrastructure/`. Domain logic does not know the provider SDK.
- Events for external systems (e.g. "order paid" → card on the Trello board) are sent
  asynchronously through the **Outbox** pattern: the domain change and the `OutboxEvent` are
  saved in one transaction, and a job with retries sends them.
- A Trello failure or outage MUST NOT block or roll back a purchase or payment.
- Every external call has a timeout, a bounded number of retries, and error logging (without
  secrets or personal data).
- The MCP server configuration for Trello refers to secrets through environment variables
  (Principle I).

Rationale: external systems fail; a customer must not lose an order because the kanban board
is temporarily unavailable.

### VI. Tests at Every Risk Level

- Domain logic (cart totals, quantity and availability rules, order status transitions) —
  unit tests without a Spring context.
- Every use case in a `*Service` — an `@SpringBootTest` integration test with Testcontainers
  (real database, no repository mocks).
- Stripe and Trello adapters — contract tests against fakes (e.g. `stripe-mock`, WireMock);
  webhook handling tested with a valid and a forged signature.
- Every P1 path from Principle IV — at least one end-to-end UI test (e.g. Playwright) with a
  Stripe test card.
- Tests NEVER use real secrets from the repository; test secrets come from CI environment
  variables.

Rationale: the cart and payment are where a bug costs money and trust.

### VII. Proof of Concept Simplicity (YAGNI)

- We build the simplest solution that satisfies the P1 paths. Every additional infrastructure
  component (message broker, cache, microservice, Kubernetes) must be justified in the plan's
  "Complexity Tracking" section.
- Scope exclusions (no user accounts, no refunds, no invoices) are documented in the
  specification as deliberate assumptions, not silently omitted.
- Simplifications NEVER apply to Principles I and II — secret security and payment correctness
  apply in full in the PoC.

Rationale: the PoC must prove feasibility quickly, but without debt that cannot be repaid.

## Technology Stack and Constraints

- **Backend**: Java 21, Spring Boot 4.0, JPA/Hibernate, SQL Server (per `AGENTS.md`);
  constructor injection, no field `@Autowired`, no static state.
- **Asynchrony**: Outbox + scheduler job; RabbitMQ only if the plan demonstrates the need
  (Principle VII).
- **Frontend**: TypeScript SPA; React + Vite by default — the final choice is approved in
  `/speckit-plan`. Responsive layout (mobile and desktop).
- **Payments**: Stripe (test mode), Checkout Session or Payment Element + webhooks.
- **Kanban**: Trello, integrated through an MCP server and/or the REST API behind the
  `fulfillment` port.
- **API contract**: OpenAPI, generated or maintained together with the controller code.
- **Local run**: a single command (e.g. `docker compose up`) starts the database and
  dependencies; required secrets are described in `.env.example` and the README.
- **Personal data**: we collect only the data needed for an order (email, shipping address);
  we never log it in plain text.
- **Language**: code, identifiers, documentation, specifications, tasks and commit messages are
  written in English. Polish is allowed only in customer-facing UI copy (translation files).

## Development Process and Quality Gates

- Work is driven by Spec Kit: `/speckit-specify` → `/speckit-clarify` → `/speckit-plan`
  → `/speckit-tasks` → `/speckit-implement`. The plan MUST contain a "Constitution Check"
  confirming compliance with Principles I–VII.
- Changes land through feature branches and Pull Requests; pushing directly to `main` is
  not allowed.
- PR gates (all MUST pass): build, unit and integration tests, secret scan, no new high/critical
  dependency security warnings.
- PR review MUST check: no secrets in the diff, server-side amount calculation, webhook
  signature verification, respect for BC boundaries.
- A feature is "done" when its P1 path passes an end-to-end test and is described in the
  feature's `quickstart.md`.

## Governance

- The constitution takes precedence over other project practices. `AGENTS.md` complements it
  with detailed code conventions; in case of conflict the constitution wins and `AGENTS.md`
  must be updated.
- Amending the constitution requires a PR with a description of the change, a rationale, an
  impact report (Sync Impact Report), and a migration plan for existing code if affected.
- Semantic versioning: MAJOR — removal or redefinition of a principle; MINOR — a new principle
  or a material expansion; PATCH — clarifications and editorial fixes.
- Every plan and every PR verifies compliance with the constitution; any deviation MUST be
  explicitly justified in "Complexity Tracking". Deviations from Principles I and II are not
  allowed.
- A compliance review of the whole repository takes place at every PoC milestone.

**Version**: 1.1.0 | **Ratified**: 2026-09-23 | **Last Amended**: 2026-09-23
