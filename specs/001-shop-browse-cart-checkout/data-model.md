# Data Model: Shop Browsing, Cart and Checkout

**Feature**: `001-shop-browse-cart-checkout` | **Date**: 2026-09-23 | **Plan**: [plan.md](plan.md)

The model is split into Bounded Contexts (Principle III). Domain entities are free of framework
annotations; the columns describe the `*JpaEntity` tables in `infrastructure/persistence`.
There are **no foreign keys between BCs** — references go by identifier (e.g. `product_id` in the
cart), consistency is enforced by the facades. All amounts: `BIGINT` in grosze (minor units), PLN
(R-07). Timestamps: `DATETIMEOFFSET(3)` in UTC.

## Shared kernel (`shared`)

| Type | Kind | Description / invariants |
|---|---|---|
| `Money` | Value Object | `long minor ≥ 0`, `currency = PLN`; `plus`, `times(int)`; compared by value |
| `GuestId` | Value Object | UUID from the `shop_guest` cookie (R-08); never empty |
| `OutboxEvent` | Infrastructure entity | see table `outbox_event` |

### Table `outbox_event`

| Column | Type | Constraints |
|---|---|---|
| `id` | `UNIQUEIDENTIFIER` | PK |
| `type` | `NVARCHAR(100)` | NOT NULL, e.g. `OrderPaid` |
| `aggregate_id` | `NVARCHAR(50)` | NOT NULL (order number) |
| `payload` | `NVARCHAR(MAX)` | NOT NULL, JSON |
| `created_at` | `DATETIMEOFFSET(3)` | NOT NULL |
| `sent_at` | `DATETIMEOFFSET(3)` | NULL — filled by the job of the `fulfillment` feature |
| `attempts` | `INT` | NOT NULL DEFAULT 0 |

Index: `(sent_at, created_at)` — for the future dispatch job.

---

## BC `catalog`

### Category

| Field | Domain type | Column | Rules |
|---|---|---|---|
| id | `CategoryId(long)` | `id BIGINT IDENTITY` PK | |
| name | `String` | `name NVARCHAR(80)` | NOT NULL, unique |
| slug | `String` | `slug VARCHAR(80)` | NOT NULL, unique, `[a-z0-9-]+` — used in the URL (`?category=electronics`) |
| displayOrder | `int` | `display_order INT` | order in the navigation |

Flat list, no subcategories (spec assumption).

### Product (aggregate)

| Field | Domain type | Column | Rules |
|---|---|---|---|
| id | `ProductId(long)` | `id BIGINT IDENTITY` PK | |
| name | `String` | `name NVARCHAR(200) COLLATE Polish_100_CI_AI` | NOT NULL; collation for FR-003 (R-05) |
| description | `String` | `description NVARCHAR(4000)` | |
| price | `Money` | `price_minor BIGINT` | NOT NULL, `> 0` (CHECK) |
| categoryId | `CategoryId` | `category_id BIGINT` FK → `category` | NOT NULL (FK within the BC) |
| stock | `int` | `stock INT` | NOT NULL, `≥ 0` (CHECK) |
| active | `boolean` | `active BIT` | NOT NULL; inactive = "removed from the catalog" |
| images | `List<ProductImage>` | table `product_image` | min. 1; main = `display_order 0` |
| version | — | `version BIGINT` (`@Version`) | optimistic locking |

**Availability status (derived, FR-005)**:

| Condition | `AvailabilityStatus` |
|---|---|
| `!active` or `stock = 0` | `UNAVAILABLE` |
| `1 ≤ stock ≤ 3` | `LOW_STOCK` |
| `stock > 3` | `AVAILABLE` |

**Domain operations**: `availabilityStatus()`, `maxPurchasable() = min(stock, 99)`,
`canDecreaseStock(n)`, `decreaseStock(n)` (throws when `n > stock`).

### Table `product_image`

| Column | Type | Constraints |
|---|---|---|
| `product_id` | `BIGINT` | FK → `product`, part of PK |
| `display_order` | `INT` | part of PK, `≥ 0` |
| `url` | `NVARCHAR(300)` | NOT NULL, relative path `/images/...` |
| `alt` | `NVARCHAR(200)` | NOT NULL |

**Indexes**: `product(category_id, active, price_minor)`, `product(active, price_minor)`,
`product(active, name)` (R-24).

**Search criteria (`SearchCriteria`, VO)**: `categorySlug?`, `query?`
(trimmed, max. 100 characters, `LIKE` characters escaped), `minPrice?`, `maxPrice?`
(`minPrice ≤ maxPrice`, otherwise swap/400), `sort ∈ {PRICE_ASC, PRICE_DESC, NAME}`, `page ≥ 0`,
`size ∈ [1, 48]` (default 24).

---

## BC `cart`

### Cart (aggregate)

| Field | Domain type | Column | Rules |
|---|---|---|---|
| id | `CartId(UUID)` | `id UNIQUEIDENTIFIER` PK | |
| guestId | `GuestId` | `guest_id UNIQUEIDENTIFIER` | NOT NULL, UNIQUE — one cart per browser |
| lines | `List<CartLine>` | table `cart_line` | max. 1 line per product |
| updatedAt | `Instant` | `updated_at DATETIMEOFFSET(3)` | updated on every change (FR-012) |
| version | `long` | `version BIGINT` (`@Version`) | optimistic locking (two tabs) |

### CartLine (entity within the aggregate)

| Field | Domain type | Column | Rules |
|---|---|---|---|
| productId | `long` | `product_id BIGINT` | part of PK `(cart_id, product_id)`; no FK (other BC) |
| quantity | `int` | `quantity INT` | `1..99` (CHECK) |
| priceWhenAdded | `Money` | `price_when_added_minor BIGINT` | only for detecting a price change (R-09); never for pricing |
| addedAt | `Instant` | `added_at DATETIMEOFFSET(3)` | display order |

**Aggregate rules** (R-10, unit tests):

| Operation | Behavior |
|---|---|
| `add(product, n)` | `n ∈ 1..99`; existing line → `quantity += n` (FR-007); if the result > `product.maxPurchasable` → `QuantityExceedsLimit(max)` and no change (US2-3, FR-008); product `UNAVAILABLE` → `ProductUnavailable` (US2-4) |
| `changeQuantity(productId, n)` | `n = 0` → remove the line (US3-4); `n > max` → set `max` and return `QuantityCapped(max)` (US3-3); `n < 0` → error |
| `remove(productId)` | removes the line; no line → no-op (idempotent) |
| `clear()` | removes all lines (FR-009, FR-021) |
| `acceptPrices(prices)` | overwrites `priceWhenAdded` with current prices (R-09) |

### PricedCart (read model, not persisted)

Result of `CartService.price(guestId)` built from `CatalogQueryFacade` data — returned by the API
and by `CartQueryFacade` to the `order` BC:

| Field | Description |
|---|---|
| `lines[]` | `productId, name, imageUrl, unitPrice (current), quantity, lineTotal, status, maxQuantity, priceChanged, previousPrice?, quantityExceedsStock` |
| `itemCount` | sum of `quantity` over all lines (header counter, FR-013) |
| `total` | sum of `lineTotal` of available lines (FR-010) |
| `canPlaceOrder` | non-empty ∧ every line available ∧ `quantity ≤ maxQuantity` (FR-014) |
| `problems[]` | `PRICE_CHANGED`, `PRODUCT_UNAVAILABLE`, `QUANTITY_EXCEEDS_STOCK` with `productId` |

---

## BC `order`

### Order (aggregate)

| Field | Domain type | Column | Rules |
|---|---|---|---|
| id | `OrderId(UUID)` | `id UNIQUEIDENTIFIER` PK | |
| number | `OrderNumber` | `number VARCHAR(14)` | UNIQUE; `ORD-` + 10 Crockford Base32 characters (R-15) |
| guestId | `GuestId` | `guest_id UNIQUEIDENTIFIER` | NOT NULL; owner (FR-023); indexed |
| customer | `CustomerDetails` | columns `email`, `full_name` | see VO |
| shippingAddress | `ShippingAddress` | `street`, `postal_code`, `city`, `country` | see VO |
| lines | `List<OrderLine>` | table `order_line` | immutable after creation (FR-017), min. 1 |
| total | `Money` | `total_minor BIGINT` | = Σ line totals (FR-024), shipping 0 PLN |
| status | `OrderStatus` | `status VARCHAR(30)` | see the state machine |
| createdAt | `Instant` | `created_at DATETIMEOFFSET(3)` | |
| paidAt | `Instant?` | `paid_at DATETIMEOFFSET(3)` NULL | set on `PAID`/`NEEDS_REVIEW` after payment |
| reviewReason | `String?` | `review_reason NVARCHAR(200)` NULL | e.g. `INSUFFICIENT_STOCK`, `AMOUNT_MISMATCH` |
| version | `long` | `version BIGINT` (`@Version`) | |

The table is named `orders` (`ORDER` is a reserved word in SQL).

### Value Objects

| VO | Fields | Invariants (FR-015, R-25) |
|---|---|---|
| `CustomerDetails` | `email`, `fullName` | valid email, ≤ 254; full name 2–100 characters |
| `ShippingAddress` | `streetAndNumber`, `postalCode`, `city`, `country` | street 3–120; code `^\d{2}-\d{3}$`; city 2–60; `country = "PL"` |
| `OrderNumber` | `value` | format `ORD-[0-9A-HJKMNP-TV-Z]{10}` |

### OrderLine (entity within the aggregate, immutable)

| Field | Column | Rules |
|---|---|---|
| lineNo | `line_no INT` | part of PK `(order_id, line_no)` |
| productId | `product_id BIGINT` | informational reference (no FK) — for decreasing stock |
| name | `name NVARCHAR(200)` | copy at the time of ordering |
| unitPrice | `unit_price_minor BIGINT` | `> 0` |
| quantity | `quantity INT` | `1..99` |
| lineTotal (derived) | — | `unitPrice × quantity` |

### `OrderStatus` state machine

```text
                    ┌──────────── PaymentConfirmed ∧ amount OK ∧ stock OK ──────────► PAID
                    │
AWAITING_PAYMENT ───┼──────── PaymentConfirmed ∧ (no stock ∨ wrong amount) ─────────► NEEDS_REVIEW
                    │
                    └──────── PaymentFailed (expired/failed) ∨ Stripe unavailable ─► PAYMENT_FAILED

PAYMENT_FAILED ── PaymentConfirmed (late) ──► NEEDS_REVIEW
PAID, NEEDS_REVIEW — final states in the PoC
```

| From → To | Trigger | Effects in the same transaction |
|---|---|---|
| `AWAITING…` → `PAID` | confirmation event, `amount_total == total`, `currency == pln`, sufficient stock | `paidAt = now`; `CatalogCommandFacade.decreaseStock`; `CartCommandFacade.clear(guestId)`; `OutboxEvent(OrderPaid)` (FR-021, R-17) |
| `AWAITING…` → `NEEDS_REVIEW` | confirmation, but insufficient stock or amount mismatch | `paidAt = now`; `reviewReason`; cart cleared; stock **not** changed |
| `AWAITING…` → `PAYMENT_FAILED` | `checkout.session.expired` / `async_payment_failed` / session creation error | cart **not** changed (FR-022) |
| `PAYMENT_FAILED` → `NEEDS_REVIEW` | late payment confirmation | `reviewReason = CONFIRMED_AFTER_FAILURE` |
| `PAID` → `PAID` | repeated confirmation (different `event.id`, same session) | no effects — domain idempotency (FR-020) |
| other | — | `IllegalStatusTransition` (unit test) |

Mapping of status to UI label (frontend, presentation only; exact Polish copy in the translation
files): `AWAITING_PAYMENT` → "Payment being verified" (confirmation page, US4-6) / "Awaiting
payment"; `PAID` → "Paid"; `PAYMENT_FAILED` → "Payment failed"; `NEEDS_REVIEW` → "Needs review".

### Event `OrderPaidEvent` (Outbox payload)

`number`, `paidAt`, `total{minor, currency}`, `lines[{name, quantity, unitPriceMinor}]`,
`customer{fullName}`, `shippingAddress{…}`. No email (data minimization for Trello — added only if
the `fulfillment` feature justifies the need).

---

## BC `payment`

### Payment (aggregate)

| Field | Domain type | Column | Rules |
|---|---|---|---|
| id | `PaymentId(UUID)` | `id UNIQUEIDENTIFIER` PK | used in the idempotency key (R-11) |
| orderId | `UUID` | `order_id UNIQUEIDENTIFIER` | NOT NULL; indexed; no FK (other BC) |
| guestId | `GuestId` | `guest_id UNIQUEIDENTIFIER` | for expiring the guest's previous sessions (R-13) |
| amount | `Money` | `amount_minor BIGINT` | = order total |
| providerSessionId | `String?` | `stripe_session_id VARCHAR(255)` | UNIQUE (filtered, NOT NULL) |
| providerPaymentId | `String?` | `stripe_payment_intent_id VARCHAR(255)` NULL | from the webhook |
| paymentUrl | `String?` | `payment_url NVARCHAR(1000)` NULL | |
| status | `PaymentStatus` | `status VARCHAR(20)` | `CREATED → OPEN → CONFIRMED \| FAILED \| EXPIRED` |
| createdAt / confirmedAt | `Instant` | `DATETIMEOFFSET(3)` | `confirmed_at` NULL until confirmed |

Transitions: `CREATED → OPEN` (session created), `CREATED → FAILED` (Stripe unavailable),
`OPEN → CONFIRMED` (webhook completed/paid), `OPEN → EXPIRED` (webhook expired or our `expire` on a
retry), `OPEN → FAILED` (async_payment_failed). `CONFIRMED` is final.

### Table `processed_stripe_event`

| Column | Type | Constraints |
|---|---|---|
| `event_id` | `VARCHAR(255)` | PK — deduplication (R-12, FR-020) |
| `type` | `VARCHAR(100)` | NOT NULL |
| `processed_at` | `DATETIMEOFFSET(3)` | NOT NULL |

### Domain port `PaymentGateway` (Principle V)

| Operation | Input | Output |
|---|---|---|
| `createSession` | `paymentId`, `orderNumber`, lines (name, price, quantity), email, return URLs | `PaymentSession{providerSessionId, url}` or `GatewayUnavailable` |
| `expire` | `providerSessionId` | `EXPIRED` / `ALREADY_PAID` |

### Domain port `ProviderEventVerifier` (Principle V)

| Operation | Input | Output |
|---|---|---|
| `verify` | raw body (`String`), signature header (`String`) | SDK-independent `ProviderConfirmation{eventId, type, sessionId, paymentIntentId, amount, currency, paid, livemode, providerCreatedAt}`, or the domain exception `InvalidEventSignature` (missing/invalid signature, tolerance exceeded) |

The port is a `public interface` in `payment/domain/`; the Stripe implementation
`StripeWebhookVerifier` (`Webhook.constructEvent`) lives in `payment/infrastructure/stripe/`.
`WebhookHandlingService` (application) depends only on the port, so signature verification,
the `livemode` check and deduplication all happen in one service and every outcome can be
reported through `PaymentMetrics`. The controller only passes the raw body and the header.

### Events published by `payment` (public records in the BC's root package)

| Event | Fields | Consumer |
|---|---|---|
| `PaymentConfirmedEvent` | `orderId, amountMinor, currency, confirmedAt` | `order` (synchronously, same transaction — R-02) |
| `PaymentFailedEvent` | `orderId, reason` | `order` |

---

## Facades (public BC API)

| Facade | Operations | Consumers |
|---|---|---|
| `CatalogQueryFacade` | `getForPricing(Set<Long> ids) → Map<Long, PricingProductDto>` | `cart`, `order` |
| `CatalogCommandFacade` | `decreaseStock(List<StockLineDto>) → StockDecreaseResult` | `order` |
| `CartQueryFacade` | `price(GuestId) → PricedCartDto` | `order` |
| `CartCommandFacade` | `clear(GuestId)` | `order` |
| `PaymentFacade` | `start(StartPaymentDto) → StartedPaymentDto`, `expireOpen(GuestId) → ExpiryResult` | `order` |
| `OrderQueryFacade` | `get(OrderNumber, GuestId) → Optional<OrderDto>` | (future `fulfillment` feature) |

REST controllers use their own BC's `*Service`, not other BCs' facades.

---

## Observability (US5, R-26–R-34)

Observability **adds no tables or columns** and does not change domain entities. It adds metrics
ports in the `application/` layer (R-27) — the only way application services report business
events. Full catalog of names and labels: [contracts/metrics.md](contracts/metrics.md).

### Metrics ports (`public interface` in `<bc>/application/`, package-private adapter in `<bc>/infrastructure/metrics/`)

| Port | Operations | Called by | Recorded |
|---|---|---|---|
| `CartMetrics` | `addedToCart()` | `CartService` after a successful add | after commit |
| `OrderMetrics` | `orderCreated()`; `summaryMismatch(Set<MismatchKind>)`; `orderCompleted(OrderStatus target, Money total, Duration timeToPayment)` | `PlaceOrderService` (TX1; on `409`), `PaymentEventsListener` (status transition) | after commit (`409` — immediately, no transaction) |
| `PaymentMetrics` | `webhook(WebhookOutcome)`; `payment(PaymentOutcome)`; `confirmationDelay(Duration)` | `WebhookHandlingService` — all outcomes, including signature/`livemode` rejections reported by the `ProviderEventVerifier` port | after commit; rejections — immediately |

Stripe call metrics (`shop.stripe.*`) are recorded directly by the `StripePaymentGateway` adapter
(`MeterRegistry` in `infrastructure/`), Outbox gauges by `shared/infrastructure/metrics/OutboxMetrics`,
migrations by `shared/infrastructure/metrics/FlywayMetrics`. The helper
`shared/infrastructure/metrics/AfterCommit` defers recording to `afterCommit` (or runs immediately
when there is no active transaction).

`orderCompleted` counts **transitions** to the target status: a late confirmation
(`PAYMENT_FAILED → NEEDS_REVIEW`) increments the `NEEDS_REVIEW` counter, while the earlier
`PAYMENT_FAILED` stays counted. A repeated confirmation `PAID → PAID` is not a transition — the
counter does not change (FR-020).

### Label enums (the only allowed values — FR-031)

| Enum | Package | Values → label |
|---|---|---|
| `OrderStatus` (existing) | `order/domain` | target states only: `PAID`, `PAYMENT_FAILED`, `NEEDS_REVIEW` → `status` |
| `MismatchKind` | `order/application` | `PRICE`, `AVAILABILITY`, `CONTENTS` → `kind` |
| `WebhookOutcome` | `payment/application` | `PROCESSED`, `DUPLICATE`, `REJECTED_SIGNATURE`, `REJECTED_LIVEMODE`, `IGNORED` → `outcome` (lower case) |
| `PaymentOutcome` | `payment/application` | `SUCCEEDED`, `DECLINED`, `CANCELED` → `outcome` (lower case) |
| `StripeOperation`, `CallOutcome` | `payment/infrastructure/stripe` | `CREATE_SESSION`, `EXPIRE_SESSION`; `SUCCESS`, `ERROR`, `TIMEOUT` → `operation`, `outcome` |

`MismatchKind` is computed by `PlaceOrderService` when comparing the confirmed summary with a fresh
pricing (R-14): a different unit price → `PRICE`; an unavailable line or quantity > stock →
`AVAILABILITY`; a different set of products or quantities → `CONTENTS`.

### Gauge queries

| Gauge | Query | Index | Refresh |
|---|---|---|---|
| `shop.outbox.pending`, `shop.outbox.oldest` | `SELECT COUNT(*), MIN(created_at) FROM outbox_event WHERE sent_at IS NULL` | existing `(sent_at, created_at)` | result cached for 15 s |
| `shop.flyway.migrations{state}` | `Flyway.info().all()` grouped by state | — | once, after `ApplicationReadyEvent` |
