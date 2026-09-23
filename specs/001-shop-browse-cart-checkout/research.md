# Research: Shop Browsing, Cart and Checkout

**Feature**: `001-shop-browse-cart-checkout` | **Date**: 2026-09-23 | **Plan**: [plan.md](plan.md)

Every decision uses the format **Decision / Rationale / Alternatives considered** (R-01–R-25:
purchase path; R-26–R-34: observability, US5). After this phase no "NEEDS CLARIFICATION" remains
in the plan's Technical Context.

---

## R-01. Repository layout and build tool

- **Decision**: a monorepo with two applications: `backend/` (a single Maven module with the
  `mvnw` wrapper, Java 21, Spring Boot 4.0) and `frontend/` (React + Vite + TypeScript, npm).
  A `compose.yaml` with local dependencies in the root directory.
- **Rationale**: the constitution (Principle III) requires a separate frontend communicating over
  REST. A single Maven module is enough for a modular monolith — BC boundaries are enforced by an
  architecture test (R-03), not by Maven modules. `.gitignore` already has a Maven section.
- **Alternatives considered**: multi-module Maven (one module per BC) — stronger boundaries, but
  configuration overhead unjustified in a PoC (Principle VII); Gradle — equivalent, Maven chosen
  for consistency with the team's typical Spring stack.

## R-02. Bounded Contexts and communication between them

- **Decision**: this feature creates the BCs `catalog`, `cart`, `order`, `payment` and the
  `shared` package (shared kernel: `Money`, `GuestId`, Outbox). The `fulfillment` BC (Trello)
  is out of scope — it will be created in a separate feature as a consumer of the
  `OrderPaidEvent` event. Dependency directions (only through `*Facade`):

  ```text
  cart ──► catalog              (line pricing, availability)
  order ──► cart, catalog, payment
  payment ──(PaymentConfirmedEvent / PaymentFailedEvent)──► order
  ```

  `payment` does NOT depend on `order`. `payment` publishes the result of a verified webhook as
  a domain event (Spring `ApplicationEventPublisher`), which `order` handles **synchronously in
  the same transaction** (`@TransactionalEventListener(phase = BEFORE_COMMIT)` or a plain
  `@EventListener` invoked within the webhook transaction).
- **Rationale**: removes the `order ⇄ payment` cycle. Synchronous handling in a single
  transaction gives atomicity: `event.id` deduplication + status change + stock decrease
  + cart clearing + `OutboxEvent` write happen all or nothing (FR-019–FR-021, SC-005).
  Everything lives in one database, so no saga is needed.
- **Alternatives considered**: `payment` calls an `OrderCommandFacade` — creates a dependency
  cycle; asynchronous handling through Outbox/RabbitMQ — unnecessary complexity and a risk of
  missing SC-007 with no gain; a separate "payment processing" process — overkill.

## R-03. Enforcing BC boundaries

- **Decision**: an ArchUnit architecture test (`ArchitectureTest`) run in `mvn verify`:
  (1) classes of BC A import nothing from BC B except its root package (`*Facade`, public
  DTOs/event records); (2) `domain/` does not depend on `org.springframework..`,
  `jakarta.persistence..`, `com.stripe..`; (3) no field `@Autowired`; (4) no dependency on
  `ApplicationContext`.
- **Rationale**: in a single Maven module only an architecture test turns the rules of
  `AGENTS.md` and Principle III into a PR gate. ArchUnit expresses the convention "Facade in the
  BC root, layers in subpackages" exactly, which Spring Modulith's default conventions do not
  (Modulith treats subpackages as internal, while `domain/*Repository` must be public).
- **Alternatives considered**: Spring Modulith `ApplicationModules.verify()` — would require
  bending the package structure; code review only — not enforceable.

## R-04. Database, migrations and sample data

- **Decision**: SQL Server 2022 (image `mcr.microsoft.com/mssql/server:2022-latest`)
  locally in `compose.yaml` and in tests through Testcontainers (`MSSQLServerContainer`).
  Schema managed by Flyway (`db/migration/V*.sql`); `spring.jpa.hibernate.ddl-auto=validate`.
  Sample data (categories, ≥ 500 products generated with a recursive CTE, images) in a separate
  `db/seed` location included only by the `local`/`e2e` profile.
- **Rationale**: the stack from `AGENTS.md`; Flyway gives a repeatable schema in tests and
  locally; the seed stays outside the production migration path. 500+ products are needed to
  verify SC-003.
- **Alternatives considered**: Liquibase — a heavier DSL with no benefit; `ddl-auto=update` —
  unpredictable schema; H2 in tests — breaks Principle VI (real database) and lacks the Polish
  collation from R-05.

## R-05. Case- and diacritic-insensitive search (FR-003)

- **Decision**: the `product.name` column with the `Polish_100_CI_AI` collation; the query
  `WHERE name LIKE :pattern ESCAPE '\'` with the pattern `%phrase%`, where the phrase's
  `% _ [ \` characters are escaped, and the phrase is trimmed to 100 characters and `trim()`med
  (edge case about a long phrase). The parameter is always bound (no SQL concatenation).
- **Rationale**: the CI_AI collation does all the work on the database side (`łódź` = `LODZ`),
  without an extra normalized column. For ~500 to a few thousand rows a full scan with
  `LIKE '%…%'` stays well below 1 s (SC-003).
- **Alternatives considered**: a `normalized_name` column maintained in code — more code, risk
  of drift; SQL Server Full-Text Search — requires extra container configuration and does not
  support "word fragments"; Elasticsearch — contradicts Principle VII.

## R-06. Filtering, sorting, pagination (FR-001, FR-004)

- **Decision**: `GET /api/products` with the parameters `category`, `q`, `minPrice`, `maxPrice`
  (in grosze), `sort ∈ {price_asc, price_desc, name_asc}` (allow-list, default `name_asc`),
  `page` (from 0), `size` (default 24, max 48). A stable secondary sort key `id`.
  The frontend mirrors the same parameters 1:1 in the URL query string
  (`/?category=…&q=…&page=2`), so a link to the results is shareable. The list returns only
  products with `active = 1`.
- **Rationale**: a single source of truth for the filter state (the URL), no in-memory frontend
  state; the sort allow-list protects against column name injection.
- **Alternatives considered**: cursor pagination — unnecessary with 500 products and makes
  "jump to page N" harder; passing `Pageable.sort` directly — exposes column names.

## R-07. Representing money (FR-024, Principle II)

- **Decision**: a value object `Money(long minor, Currency currency)` in `shared/domain`, the
  currency always `PLN`; in the database `BIGINT` columns with the `_minor` suffix; in the API
  fields `…Minor: integer` (int64). Formatting as "1 234,56 zł" only in the frontend
  (`Intl.NumberFormat('pl-PL', {style:'currency', currency:'PLN'})`).
- **Rationale**: Stripe accepts `unit_amount` in grosze — zero conversions, zero rounding.
  The constitution forbids `double`/`float`.
- **Alternatives considered**: `BigDecimal` + `DECIMAL(12,2)` — acceptable, but requires a
  conversion to grosze for Stripe and scale discipline; `javax.money` (Moneta) — an unnecessary
  dependency.

## R-08. Guest identification and cart storage (FR-006, FR-012)

- **Decision**: the cart is kept **server-side** in the database, tied to a guest identifier.
  The identifier is a random UUIDv4 in the `shop_guest` cookie: `HttpOnly`, `Secure` (outside
  `local`), `SameSite=Lax`, `Path=/`, `Max-Age=30 days`, renewed on every cart change. The
  backend issues the cookie on the first `/api/cart*` / `/api/orders*` request (the
  `GuestIdFilter` filter). In dev the frontend calls the API through the Vite proxy (same
  origin), so there is no CORS and no cross-site cookies.
- **Rationale**: FR-010 requires the server to calculate totals from current prices — a cart in
  the database is natural. The same guest identifier serves as the order "owner" (R-15) and
  allows clearing the cart after the webhook, even when the customer closed the tab (edge case).
  `HttpOnly` protects against theft via XSS; `SameSite=Lax` blocks sending the cookie in a
  cross-site POST (CSRF protection without Spring Security).
- **Alternatives considered**: a cart in `localStorage` — the server cannot see the cart during
  the webhook (it cannot be cleared), and pricing has to go through the server anyway; an HTTP
  session (`JSESSIONID`) — expires after 30 min by default, and a durable session requires
  Spring Session; a JWT with the cart contents — overkill and cookie size limits.
- **Retention**: FR-012 says "at least 30 days" — in the PoC carts are never deleted. A job
  cleaning up old carts is a deliberately postponed extension.

## R-09. Cart pricing and price change notices (FR-010, FR-011)

- **Decision**: `CartLine` stores `productId`, `quantity` and `priceWhenAddedMinor`
  (informational only). Every cart read prices the lines through
  `CatalogQueryFacade.getForPricing(ids)` and returns per line: the current price, line total,
  status `AVAILABLE | LOW_STOCK | UNAVAILABLE`, `maxQuantity = min(stock, 99)`, the flags
  `priceChanged` (with the previous price) and `quantityExceedsStock`. The cart total = the sum
  of the line totals of available lines; `canPlaceOrder = non-empty && all available in the
  required quantity`. After the price change is shown, the customer "accepts" it through
  `POST /api/cart/accept-prices`, which overwrites `priceWhenAddedMinor` with current prices.
- **Rationale**: browser prices are irrelevant (FR-010, Principle II), and the change notice
  does not disappear accidentally on refresh, only after a deliberate confirmation.
- **Alternatives considered**: freezing the price in the cart — contradicts FR-010; comparison
  on the frontend side against a cache — the frontend must not contain pricing rules
  (Principle III).

## R-10. Quantity rules (FR-007, FR-008, edge case about an invalid quantity)

- **Decision**: rules in the `Cart` domain entity: `add(product, quantity)` merges with an
  existing line; the quantity must be an integer 1..99 (`0` in `changeQuantity` = remove the
  line); adding/changing above `min(stock, 99)` → the domain exception
  `QuantityExceedsLimit(max)`. The behaviors differ deliberately per the spec: **adding** above
  the limit is **blocked** (US2-3), while **changing the quantity** in the cart is **capped at the
  available stock** with a message (US3-3). In both cases the API returns `maxQuantity`. Type
  validation (`-1`, `1.5`, `abc`) on the DTO through Bean Validation (`@Min(0) @Max(99)` on
  `Integer`) → `400` with a field error. Optimistic locking with `@Version` on the cart protects
  against concurrent changes from two tabs.
- **Rationale**: logic in the domain, unit-testable without Spring (Principle VI).
- **Alternatives considered**: uniform "capping" also when adding — contradicts US2-3.

## R-11. Payment integration: Stripe Checkout (FR-018, Principle II)

- **Decision**: **Stripe Checkout Session in hosted mode** (`mode=payment`,
  `payment_method_types=[card]`, `currency=pln`, `line_items` with `price_data.unit_amount`
  in grosze calculated from the immutable order lines, `customer_email`,
  `client_reference_id = order.id`, `metadata.orderId`, `expires_at = now + 30 min`
  (the Stripe minimum), `success_url = {APP_BASE_URL}/orders/{number}?session_id={CHECKOUT_SESSION_ID}`,
  `cancel_url = {APP_BASE_URL}/cart?payment=canceled`). The backend returns `session.url`, the
  frontend does `window.location.assign(url)`. The `com.stripe:stripe-java` SDK only in
  `payment/infrastructure/stripe`, behind the `PaymentGateway` domain port.
  The session-creating call carries the header `Idempotency-Key = "checkout-" + paymentId`.
- **Rationale**: the spec explicitly describes a redirect to the provider's page (US4-3). Hosted
  Checkout = PCI SAQ A scope, zero card form code, and the frontend **does not even need the
  publishable key** — no Stripe key reaches the browser. Stripe itself shows the declined card
  error and allows a retry on its page.
- **Alternatives considered**: Payment Element (embedded) — better UX, but requires
  `pk_test_…` in the frontend, 3DS handling and form states — more work with no value for the
  PoC; Payment Links — no control over the amount per order.

## R-12. Stripe webhook: verification, idempotency, amount match (FR-019, FR-020, SC-004/005)

- **Decision**: the endpoint `POST /api/payments/stripe/webhook` accepts the raw body
  (`@RequestBody String`) and the `Stripe-Signature` header and passes both to
  `WebhookHandlingService`, which verifies them through the domain port `ProviderEventVerifier`;
  its Stripe adapter `StripeWebhookVerifier` calls
  `Webhook.constructEvent(payload, signature, webhookSecret)` (tolerance 300 s). A missing or
  invalid signature → `400`, with no changes at all. Handled types:

  | Stripe event | Condition | Effect |
  |---|---|---|
  | `checkout.session.completed` | `payment_status = paid` | `PaymentConfirmedEvent` |
  | `checkout.session.async_payment_succeeded` | — | `PaymentConfirmedEvent` |
  | `checkout.session.expired` | — | `PaymentFailedEvent` |
  | `checkout.session.async_payment_failed` | — | `PaymentFailedEvent` |
  | others | — | `200`, ignored |

  In a single transaction: `INSERT` into `processed_stripe_event(event_id PK)` — a key violation
  = a duplicate → `200` with no effects; then the `Payment` update and publication of the domain
  event handled synchronously by `order` (R-02). Before marking the order as paid, `order`
  compares `amount_total` and `currency` with the order total — a mismatch → status
  `NEEDS_REVIEW`. The webhook responds `2xx` only after commit; a technical error → `500`, which
  triggers retries on the Stripe side.
- **Rationale**: exactly Principle II and the "repeated" and "forged" confirmation edge cases.
  Deduplication in the same transaction as the effect removes the race window for concurrent
  deliveries of the same event.
- **Alternatives considered**: deduplication by order status — does not protect against a race
  and does not cover events without a status change; marking "paid" on `success_url` — forbidden
  (FR-019).

## R-13. Cancellation, retry, provider unavailable (FR-022, US4-5, edge cases)

- **Decision**:
  - **Cancellation** (the customer returns through `cancel_url`): the order stays
    `AWAITING_PAYMENT`, the cart is untouched (it was never cleared before payment), and the
    frontend shows a message based on `?payment=canceled`. The `PAYMENT_FAILED` status is set by
    the `checkout.session.expired` webhook (at most 30 min later).
  - **Retry**: every "Proceed to payment" creates a **new** order and session. Before creating a
    new session the backend expires (`sessions.expire`) the open sessions of the guest's previous
    pending orders — this protects against paying twice from two tabs. If a session is already
    `complete`, the new attempt is rejected with the message "The order has already been paid".
  - **Stripe unavailable**: HTTP client timeouts (connect 5 s, read 10 s), 2 retries (safe thanks
    to the idempotency key). The retries are performed by the adapter (`maxNetworkRetries=0` in the
    SDK, backoff 0.5 s / 1 s, only for connection errors, timeouts, `5xx` and `429`), so that they
    can be counted in the `shop.stripe.retries` metric (FR-028, R-27). The retry count and the
    backoff delays are constants in `StripeRetryPolicy` that reference
    `contracts/stripe-webhook.md` §1, not properties: they are part of the integration contract
    and of the tests (T086, T125), not environment configuration — unlike the timeouts, which
    stay in `StripeProperties`. After a failure the order →
    `PAYMENT_FAILED`, API `503` with the code `PAYMENT_UNAVAILABLE`, cart untouched.
  - Stripe calls are made **outside** the database transaction (transaction 1: create the order
    and the payment; the Stripe call; transaction 2: save `stripeSessionId`/URL or mark the
    failure) — Principle V and the `AGENTS.md` ban on external calls within a transaction.
- **Rationale**: the simplest behavior satisfying FR-022 and SC-006; the cart is cleared only by
  a verified webhook.
- **Alternatives considered**: reusing the same order — complicates line immutability when the
  cart changes; an "abandon order" endpoint on `cancel_url` — extra code, and `expired` arrives
  anyway.

## R-14. Re-verifying prices before payment (FR-016, US4-7)

- **Decision**: `POST /api/orders` sends, along with the customer details, the **confirmed
  summary** seen by the customer: a list of `{productId, quantity, unitPriceMinor}` and
  `totalMinor`. The backend re-prices the cart; if anything differs (price, quantity, contents,
  availability) → `409 SUMMARY_OUTDATED` with the current summary; no order is created. The
  frontend values are used **only for comparison** — the order amount and the Stripe amount
  always come from server-side pricing.
- **Rationale**: the customer confirms exactly what they will pay for; no trust in frontend
  prices.
- **Alternatives considered**: a cart version token/hash — a less readable error for the UI;
  no comparison — breaks FR-016.

## R-15. Access to the confirmation page (FR-023, edge case with someone else's number)

- **Decision**: the order number is unpredictable: `ORD-` + 10 Crockford Base32 characters
  from `SecureRandom` (e.g. `ORD-7K2Q9M4XTB`), unique index. `GET /api/orders/{number}`
  returns data only when `order.guestId == GuestId from the cookie`; otherwise `404`
  (not `403` — we do not reveal that the order exists).
- **Rationale**: two layers — the number cannot be guessed and it is bound to the customer's
  browser. Sufficient for guest purchases without accounts.
- **Alternatives considered**: a sequential number — enumerable; a separate token in the URL —
  would leak through history/Referer, and the cookie already exists.

## R-16. Decreasing stock and selling the last item (FR-021, edge case)

- **Decision**: `CatalogCommandFacade.decreaseStock(List<StockLineDto>)` returns the result
  `DECREASED | INSUFFICIENT_STOCK` and works "all or nothing" within the webhook transaction:
  (1) read the stock of all products with a `WITH (UPDLOCK, ROWLOCK)` lock in `id` order
  (no deadlocks), (2) if any stock < quantity → change nothing and return
  `INSUFFICIENT_STOCK`, (3) otherwise `UPDATE` all lines. On `INSUFFICIENT_STOCK` the order
  gets `NEEDS_REVIEW` — the payment stays confirmed, the cart is cleared, and no
  `OrderPaidEvent` event is created.
- **Rationale**: atomic, short locks only for the duration of the webhook transaction, no
  reservation (per the spec assumption); the absence of an exception means the Stripe event
  deduplication and the status change commit together.
- **Alternatives considered**: reserving stock for the duration of the payment — explicitly out
  of spec scope; `SELECT … FOR UPDATE` + write — longer locks with no benefit.

## R-17. Outbox and RabbitMQ (Principles V, VII)

- **Decision**: this feature creates the `outbox_event` table and the `OutboxEventPublisher`
  port (in `shared/domain/outbox`, without framework annotations) with the
  `JpaOutboxEventPublisher` adapter (in `shared/infrastructure/outbox`) that writes the
  `OrderPaidEvent` event in the same transaction as marking the order as paid
  (JSON: number, lines, total, shipping details — for the future Trello card). **Dispatch**
  (`OutboxEventSchedulerJob` and the Trello handler) belongs to the `fulfillment` feature.
  **RabbitMQ is not used** — there is no out-of-process consumer.
- **Rationale**: the spec obliges this feature only to provide the event; the Outbox guarantees
  the event is not lost. A broker without a consumer breaks Principle VII.
- **Alternatives considered**: a dispatch job now without handlers — dead code; RabbitMQ — no
  need in the PoC.

## R-18. Frontend

- **Decision**: React 19 + Vite + TypeScript (strict), React Router (data mode /
  `createBrowserRouter`), TanStack Query for server state (the header cart counter = the
  `['cart']` query invalidated after every mutation — FR-013), the checkout form: React Hook
  Form + Zod (UX validation; the server validates again), styles: CSS Modules with a responsive
  layout, no component library. API types generated by `openapi-typescript` from the contract,
  the `openapi-fetch` client. Polish UI copy lives only in `src/i18n/pl.json`; components refer
  to it by key. Tests: Vitest + Testing Library; E2E: Playwright.
- **Rationale**: the default choice from the constitution; TanStack Query handles caching and
  refetching without a custom store; generated types bind the frontend to the OpenAPI contract
  (Principle III).
- **Alternatives considered**: Next.js — SSR is unnecessary and blurs the frontend/backend
  boundary; Redux — excessive for fully server-side state; Tailwind/MUI — a matter of taste,
  skipped for minimal dependencies.

## R-19. API contract (Principle III)

- **Decision**: **API-first**. Source of truth:
  `specs/001-shop-browse-cart-checkout/contracts/openapi.yaml`, copied during implementation to
  `backend/src/main/resources/openapi/shop-api.yaml`. The backend exposes it through springdoc
  (`/v3/api-docs`, Swagger UI only in the `local` profile); controllers are written by hand
  (package-private, per `AGENTS.md`), and a contract test (`OpenApiContractTest`) compares the
  paths/methods/response codes generated by springdoc with the contract file. The frontend
  generates types from the same file (`npm run api:types`). Errors in the RFC 9457
  `application/problem+json` format (`ProblemDetail`) with the fields `code` and `errors[]`.
- **Rationale**: the contract is available before the code (frontend and backend can proceed in
  parallel), and at the same time there are no generated public controller interfaces that would
  break the package-private rule.
- **Alternatives considered**: openapi-generator (Spring interfaces) — public generated types and
  annotations in a foreign style; pure code-first — the contract appears only after the code.

## R-20. Secrets and configuration (Principle I)

- **Decision**: `@ConfigurationProperties("shop.stripe") record StripeProperties(String secretKey,
  String webhookSecret, Duration connectTimeout, Duration readTimeout, URI apiBase)` (`apiBase`
  optional, default `https://api.stripe.com` — overridden in tests with `stripe-mock`/WireMock),
  validated in the compact constructor (`^sk_test_.+`, `^whsec_.+`, positive timeouts) with
  messages that name only the property, plus a `StripePropertiesFailureAnalyzer` and a masking
  `toString()` — a missing value or an `sk_live_` key blocks startup with a message that contains
  no values. `@Validated` + `@Pattern` is rejected: the Bean Validation error and the standard
  `BindFailureAnalyzer` print the rejected value, i.e. the key. Values come from the
  environment variables `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`, `DB_PASSWORD`,
  `APP_BASE_URL`; `application.yaml` contains only `${…}` with no secret values. The two Stripe
  secrets use an empty default (`${STRIPE_SECRET_KEY:}`, `${STRIPE_WEBHOOK_SECRET:}`): the
  configuration binder leaves an unresolvable placeholder as the literal text `${…}`, which would
  fail the format check ("must be a test key") instead of reporting the missing variable; with an
  empty default the constructor reports "Missing required configuration: …". An empty default is
  not a secret.
  `.env.example` is extended with the new variables; `spring-dotenv` is NOT used — `compose.yaml`
  and the IDE/`mvnw` read `.env` explicitly (script `scripts/run-backend.ps1`/`.sh`). Masking:
  email and address logged in abbreviated form (`d***@g***.com`), never keys. Gitleaks: a
  pre-commit hook (`.pre-commit-config.yaml`) and a CI step (`.github/workflows/ci.yml`).
- **Rationale**: direct requirements of Principles I and II (blocking the live key).
- **Alternatives considered**: Vault/Azure Key Vault — overkill in a PoC; default values in
  `application.yaml` — risk of an accidental commit.

## R-21. Test strategy (Principle VI)

- **Decision**:

  | Level | Tools | Scope |
  |---|---|---|
  | Domain unit | JUnit Jupiter + AssertJ, no Spring/Mockito | `Cart` (merging, limits, total), `Order` (status transitions), `Money`, `ShippingAddress` (NN-NNN code), phrase escaping |
  | BC integration | `@SpringBootTest` + Testcontainers `MSSQLServerContainer` (shared, `@ServiceConnection`) | every `*Service` use case: listing/search (including diacritics), cart CRUD, order creation, webhook handling (duplicate, forged signature, amount mismatch, no stock) |
  | Adapter contract | `stripe-mock` (Testcontainers `GenericContainer`) for creating/expiring sessions; WireMock for network errors/timeouts; webhook: body signed with a test `whsec_` generated in the test | `StripePaymentGateway`, `StripeWebhookController` |
  | API contract | `OpenApiContractTest` | springdoc conformance with `shop-api.yaml` |
  | Architecture | ArchUnit | rules from R-03 |
  | Frontend | Vitest + Testing Library + MSW | components, filter state in the URL |
  | E2E | Playwright on the `compose` stack + `stripe listen --forward-to` (Stripe test mode, cards `4242 4242 4242 4242` and `4000 0000 0000 0002`) | 4 P1 paths |

  E2E with Stripe runs in CI only when the `STRIPE_TEST_*` secrets are set (skipped in PRs from
  forks); the remaining levels work without network access.
- **Rationale**: covers every item of Principle VI and `AGENTS.md`.
- **Alternatives considered**: mocking the Stripe SDK with Mockito — does not test serialization
  and headers; E2E without real Stripe — does not prove SC-004/SC-007.

## R-22. Local run (SC-008)

- **Decision**: `docker compose up -d` starts SQL Server (with a healthcheck and creation of the
  `shop` database) and — in the compose profile `stripe` — a `stripe/stripe-cli` container with
  `listen --forward-to http://host.docker.internal:8080/api/payments/stripe/webhook`.
  Backend: `./mvnw spring-boot:run -Dspring-boot.run.profiles=local`; frontend: `npm run dev`
  (port 5173, proxy `/api` and `/images` → 8080). Details in [quickstart.md](quickstart.md).
- **Rationale**: one command for dependencies, a fast dev loop for the applications.
- **Alternatives considered**: everything in compose (backend+frontend in containers) — a slower
  dev loop; may be added as a `full` profile for demos.

## R-23. Product images

- **Decision**: static files in `backend/src/main/resources/static/images/` (a dozen or so
  images shared by the seed products), served under `/images/**`; table
  `product_image(product_id, url, display_order)`, main image = `display_order = 0`.
  Frontend: `loading="lazy"`, fixed aspect ratios (no layout shift).
- **Rationale**: no admin panel (out of scope) — images are part of the seed.
- **Alternatives considered**: blob storage/CDN — unnecessary in a PoC; external placeholder URLs
  — a dependency on someone else's service in E2E tests.

## R-24. Performance (SC-003)

- **Decision**: indexes `product(category_id, active, price_minor)`, `product(active, price_minor)`,
  `cart_line(cart_id)`, unique `cart(guest_id)`; the product list fetches the main image in a
  single query (DTO projection, no N+1); `spring.jpa.open-in-view=false`.
  Verification: an integration test on the 500-product seed with a response time assertion
  (budget of 300 ms per backend query, the rest for rendering).
- **Rationale**: at this scale indexes and no N+1 are enough; a cache is unnecessary
  (Principle VII).
- **Alternatives considered**: a cache (Caffeine/Redis) — premature optimization.

## R-25. Order data validation (FR-015)

- **Decision**: the `PlaceOrderRequest` DTO with Bean Validation: `email` (`@Email`, max. 254),
  `fullName` (2–100), `streetAndNumber` (3–120), `postalCode` (`^\d{2}-\d{3}$`),
  `city` (2–60), `country` fixed to `PL` (not accepted from the frontend). The value objects
  `Email`, `ShippingAddress` in the `order` domain repeat the invariants. Per-field errors in
  `ProblemDetail.errors[] = {field, message}` — the frontend shows them next to the specific
  fields (US4-2).
- **Rationale**: validation at the boundary + domain invariants; the frontend validates only for
  UX.
- **Alternatives considered**: validation only in the frontend — unacceptable.

---

# Observability (US5, FR-025–FR-034, SC-009–SC-012)

## R-26. Metrics stack and management endpoint (FR-025, FR-026, FR-030, FR-031)

- **Decision**: Spring Boot Actuator (already a dependency) + Micrometer with
  `io.micrometer:micrometer-registry-prometheus` (version from the Boot 4 BOM). Actuator runs on a
  **separate management port** `management.server.port=8081`, exposing only the `health` and
  `prometheus` endpoints (`management.endpoints.web.exposure.include=health,prometheus`).
  Port `8080` (API) does not expose `/actuator/**`, and the Vite proxy forwards only `/api` and
  `/images` to `8080`, so a shop customer has no path to the metrics. Common tag
  `management.metrics.tags.application=${spring.application.name}` (= `shop`). The
  `http.server.requests` histogram: `percentiles-histogram=true` + SLO `100ms, 300ms, 500ms, 1s, 2s`
  (p50/p95/p99 computed in Prometheus through `histogram_quantile`). The `uri` label is the path
  template from Spring MVC (`/api/products/{id}`); unknown URLs end up in `uri="UNKNOWN"`/`"/**"`
  — Boot does not create a series per URL. Additionally `MeterFilter.maximumAllowableTags(
  "http.server.requests", "uri", 50, …)` as a cardinality safeguard. Health: `liveness`/`readiness`
  probes (`management.endpoint.health.probes.enabled=true`), readiness includes `db`; health
  response details `never` (no configuration data). JVM, GC, threads and HikariCP
  (`hikaricp_connections_*`) — built-in Boot metrics. Migrations: a custom gauge
  `shop.flyway.migrations{state}` computed once after startup from `Flyway.info()` (Boot does not
  publish Flyway metrics).
- **Rationale**: Actuator + Micrometer is the Boot standard — no custom format, and the technical
  metrics from FR-030 come without code. A separate port satisfies FR-025 without Spring Security
  (Principle VII) — isolation comes from the topology (a port that is neither proxied nor
  published), and an integration test checks that `:8080/actuator/prometheus` returns `404`.
- **Alternatives considered**: Spring Security with Basic auth for `/actuator` — an extra
  component and a secret just to protect an endpoint reachable from the internal network;
  OpenTelemetry SDK + Collector — more powerful (traces too), but the spec excludes tracing, and
  the Collector is another container; push export (Pushgateway) — the spec assumes pull.

## R-27. Business metrics and BC boundaries (FR-027, FR-028, Principle III)

- **Decision**: every BC that measures business events has a **metrics port** in the
  `application/` layer — `public interface CartMetrics`, `OrderMetrics`, `PaymentMetrics` —
  with methods in the domain language (`addedToCart()`, `orderCreated()`,
  `orderCompleted(OrderStatus, Money, Duration timeToPayment)`, `webhook(WebhookOutcome)`,
  `summaryMismatch(Set<MismatchKind>)`, …; full list in [data-model.md](data-model.md)). The
  `Micrometer<Port>` adapter is package-private in `<bc>/infrastructure/metrics/`. Application
  services call the port after a successful use case; **the domain knows nothing about metrics**.
  Recording metrics about changes made in a transaction is deferred until **after commit** by
  the shared helper `shared/infrastructure/metrics/AfterCommit`
  (`TransactionSynchronization.afterCommit`; with no active transaction — immediately), so a
  rolled-back transaction does not inflate counters (SC-010). Stripe call metrics (duration,
  outcome `success|error|timeout`, retries) are recorded directly by the `StripePaymentGateway`
  adapter (that is already the `infrastructure` layer). ArchUnit gets a new rule:
  `io.micrometer..` may be imported only from `..infrastructure..`.
- **Rationale**: metrics are an infrastructure detail — a port in `application/` keeps the
  Dependency Inversion from `AGENTS.md`, and a separate port per BC keeps Interface Segregation
  (the cart does not see payment methods). Deferring to commit gives numbers consistent with the
  database.
- **Alternatives considered**: `MeterRegistry` injected directly into `*Service` — simpler, but
  counters also grow on rollback and services depend on the library; listening to Spring events
  with `@TransactionalEventListener(AFTER_COMMIT)` — would require new events (`LineAddedEvent`,
  `OrderCreatedEvent`) only for metrics; a single shared port in `shared` — breaks ISP and ties
  `shared` to the concepts of all BCs; AOP aspects — hidden logic, hard to test.

## R-28. Metrics for declined and canceled payments (FR-027, US5-3)

- **Decision**: a card decline in hosted Checkout **does not end the session** — the customer can
  try another card, and Stripe sends no `checkout.session.*` event. Therefore the webhook also
  subscribes to `payment_intent.payment_failed`, handled **for metrics only** (the counter
  `shop.payments{outcome="declined"}`) — with no change to `Payment` or `Order`, with `event.id`
  deduplication like any other event. Outcome mapping:
  `checkout.session.completed` (paid) / `async_payment_succeeded` → `succeeded`;
  `payment_intent.payment_failed` / `async_payment_failed` → `declined`;
  `checkout.session.expired` (also after our `expire` on a retry) → `canceled`.
- **Rationale**: without this event the payments dashboard would never show a payment declined
  with the card `4000 0000 0000 0002` (US5-3), because the session expires only after 30 min as
  "canceled". No state change preserves FR-022 (cart and session untouched).
- **Alternatives considered**: marking the payment as `FAILED` after the first decline — would
  block a retry within the same Stripe session; counting returns through `cancel_url` — the
  customer does not have to return, and a return ≠ a decline.

## R-29. The "Paid ≤ 30 s" SLO and detecting missing webhooks (FR-034, SC-007)

- **Decision**: the backend does not know about a successful payment until it gets the webhook
  (FR-019), so the alert "order awaiting with a successful payment > 30 s" is implemented with
  two rules: (a) **confirmation delay** — the histogram `shop.payment.confirmation.delay`
  = time from `event.created` (Stripe) to the commit of the webhook handling; alert when p95 > 30 s
  for 5 min; (b) **missing confirmations** — orders are created but webhooks do not arrive:
  `increase(shop_orders_created_total[15m]) > 0 and increase(shop_payment_webhook_total[15m]) == 0`
  for 5 min (e.g. a stopped `stripe-cli`, a wrong `whsec_`). The time from order creation to
  payment (FR-028) is a separate histogram `shop.order.time.to.payment` — informational, because
  it includes the time the customer spends entering card details.
- **Rationale**: the two rules cover both ways of breaking SC-007 (a late webhook or a missing
  one) without polling Stripe.
- **Alternatives considered**: periodically polling Stripe for sessions of pending orders — a new
  job and API traffic just for an alert; an alert on the age of `AWAITING_PAYMENT` orders — false
  alarms, because a session may legitimately last up to 30 min.

## R-30. Outbox metrics and an alert without a dispatch job (FR-029, FR-034, R-17)

- **Decision**: the gauges `shop.outbox.pending` (number of `outbox_event` rows with
  `sent_at IS NULL`) and `shop.outbox.oldest` (age of the oldest of them in seconds) computed with
  a single query `SELECT COUNT(*), MIN(created_at) … WHERE sent_at IS NULL` (existing index
  `(sent_at, created_at)`), with the result cached for 15 s so that each Prometheus scrape does
  not hit the database. The alert rule `OutboxBacklog` (oldest > 5 min) **is created now**, but
  this feature has no dispatch job (R-17), so after every paid order it goes into "firing" after
  5 min. Therefore the rule carries the label `requires="fulfillment"` and an explanatory
  annotation, and the dashboard shows it in a separate panel "waiting for the fulfillment
  feature". The SC-011 test (`promtool test rules`) proves the `firing → resolved` transition on
  synthetic data; in the quickstart the `resolved` state is forced with a manual
  `UPDATE outbox_event SET sent_at = …`.
- **Rationale**: FR-029/FR-034 require the metric and the rule now; hiding the rule until
  `fulfillment` would break the spec, and a silent "firing" without an explanation would be
  confusing.
- **Alternatives considered**: postponing the rule to the `fulfillment` feature — contradicts
  FR-034; a job marking events as sent without a handler — would falsify the metric and break R-17.

## R-31. Prometheus and Grafana locally (FR-032, FR-033, SC-009)

- **Decision**: `compose.yaml` (default profile, so `docker compose up -d` starts them together
  with SQL Server) gets:
  - `prometheus` (`prom/prometheus`, version pinned in compose) with
    `--storage.tsdb.retention.time=15d`, `scrape_interval`/`evaluation_interval` 15 s, scraping
    `host.docker.internal:8081/actuator/prometheus` (the backend runs on the host — R-22;
    `extra_hosts: host.docker.internal:host-gateway` for Linux), rules from
    `observability/prometheus/rules/*.yml`. Port `127.0.0.1:9090`.
  - `grafana` (`grafana/grafana`, version pinned) with provisioning from the repository:
    `observability/grafana/provisioning/datasources/prometheus.yaml` (data source with
    `uid: prometheus`), `…/provisioning/dashboards/shop.yaml` (file provider) and JSON dashboards
    in `observability/grafana/dashboards/` (`http.json`, `purchase-funnel.json`,
    `payments-integrations.json`, `jvm-database.json` — FR-033 a–d). Admin password
    `GF_SECURITY_ADMIN_PASSWORD: ${GRAFANA_ADMIN_PASSWORD:?Set GRAFANA_ADMIN_PASSWORD in .env}`
    — a missing variable aborts `compose up`; `GF_USERS_ALLOW_SIGN_UP=false`,
    `GF_AUTH_ANONYMOUS_ENABLED=false`. Port `127.0.0.1:3000`.
  - Alerts are **Prometheus rules** (visible in Prometheus `/alerts` and in Grafana as data source
    alerts); no Alertmanager (notification delivery is out of scope — spec).
  - Dashboards refer only to metrics from [contracts/metrics.md](contracts/metrics.md); all
    counter queries use `rate`/`increase` (resilience to restarts — edge case).
- **Rationale**: Prometheus rules are YAML files testable with `promtool test rules` in CI
  (SC-011), and file provisioning satisfies "without manual clicking". Ports on `127.0.0.1` —
  operator tools are not exposed on the local network.
- **Alternatives considered**: Grafana-managed alerts (unified alerting) — harder to test in CI and
  to export deterministically; Grafana Cloud — an external account and a secret; the backend in a
  container next to Prometheus — a slower dev loop (R-22).

## R-32. Correlation identifier in logs (spec Assumptions)

- **Decision**: the `CorrelationFilter` filter in `shared/api` (before `GuestIdFilter`) accepts the
  `X-Request-Id` header if it matches `^[A-Za-z0-9-]{8,64}$`, otherwise it generates a UUID; it puts
  it into the MDC under the `requestId` key, returns it in the response header and clears the MDC
  after the request. Log pattern: `logging.pattern.level=%5p [%X{requestId:-}]`. Webhook handling
  adds `stripeEventId` to the MDC (a Stripe event identifier is not personal data). The correlation
  ID does **not** go into metrics (FR-031).
- **Rationale**: enough to link a log entry to an incident visible on a dashboard (spec), without
  tracing. Header validation protects the logs against character injection.
- **Alternatives considered**: Micrometer Tracing (traceId/spanId) — the spec excludes tracing;
  not accepting the header from outside — loses correlation with a proxy/E2E test.

## R-33. No personal data and cardinality control (FR-031, SC-012)

- **Decision**: the metrics catalog in [contracts/metrics.md](contracts/metrics.md) is a closed
  list: every label has an enumerable set of values (Java enums passed to the ports, never a
  `String` from input data). The `MetricsEndpointIT` test (Testcontainers) runs the full path
  (cart → order → successful, duplicate and forged webhook), fetches
  `:8081/actuator/prometheus` and checks: (1) no email address, full name, address, `ORD-…`
  number, `cs_test_…`, `pi_…`, `sk_…`, `whsec_…` (regex patterns); (2) every `shop_*` metric has
  the `application="shop"` label; (3) the set of `shop_*` names equals the list in the contract;
  (4) `:8080/actuator/prometheus` → `404`.
- **Rationale**: SC-012 requires an automated test; the comparison with the contract ensures that
  dashboards and rules refer to existing metrics.
- **Alternatives considered**: code review only — not enforceable; a `MeterFilter` removing
  unknown labels at runtime — masks the bug instead of reporting it.

## R-34. Observability tests and overhead (SC-010, SC-011, SC-012)

- **Decision**:

  | Level | Tool | Scope |
  |---|---|---|
  | Metrics adapter unit | `SimpleMeterRegistry`, no Spring | names, labels and values for every port method |
  | `*Service` integration | existing tests + assertions on `MeterRegistry` | the counter grows after commit and does **not** grow after rollback (SC-010) |
  | Metrics endpoint | `MetricsEndpointIT` | R-33 |
  | Alert rules | `promtool check rules` + `promtool test rules observability/prometheus/tests/*.test.yml` (`prom/prometheus` image in CI) | every rule from FR-034 has a `firing` and a `resolved` case (SC-011) |
  | Dashboards | `scripts/check-dashboards.mjs` in CI | valid JSON, `datasource.uid = prometheus`, every `shop_*` metric in the queries exists in the contract |
  | Overhead | `CatalogPerformanceIT` (T153) with metrics (production-like configuration) and once with `management.metrics.enable.all=false` | p95 difference < 5% or < 5 ms (noise threshold) — SC-012 |
  | Manual / demo | [quickstart.md](quickstart.md), US5 | dashboards after `compose up` (SC-009), alerts triggered live |

- **Rationale**: `promtool` tests rules deterministically and without waiting 5 minutes, so
  SC-011 can be verified in CI; the rollback test keeps the numbers consistent with the database.
- **Alternatives considered**: E2E waiting for "firing" in a running Prometheus — minutes per test
  and flakiness; no dashboard tests — a typo in a metric name gives an empty panel without an error.
