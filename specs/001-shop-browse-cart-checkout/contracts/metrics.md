# Contract: metrics, alert rules and dashboards (US5)

**Feature**: `001-shop-browse-cart-checkout` | [plan.md](../plan.md) | decisions: R-26–R-34 in
[research.md](../research.md)

This file is the **source of truth** for metric names and labels. `MetricsEndpointIT` compares the
set of `shop_*` metrics with the table in section 2, and `scripts/check-dashboards.mjs` checks that
dashboards and rules refer only to metrics from this file (R-33, R-34).

## 1. Endpoint

| Item | Value |
|---|---|
| Address | `http://<host>:8081/actuator/prometheus` (management port, **not** `8080`) |
| Other endpoints on `8081` | `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness` |
| On port `8080` | `/actuator/**` → `404` |
| Format | Prometheus text exposition (Micrometer) |
| Scrape | Prometheus every 15 s, job `shop-backend` |
| Common label | `application="shop"` on every metric |

## 2. Domain metrics `shop_*`

Names in Micrometer notation (in code) → name in Prometheus. All label combinations are
**registered with value 0 at startup**, so that `increase()` and "no events" rules work from the
first minute.

| Micrometer | Prometheus | Type | Labels (closed set) | Increments when | FR |
|---|---|---|---|---|---|
| `shop.cart.additions` | `shop_cart_additions_total` | counter | — | after commit of adding a line to the cart (`POST /api/cart/lines` → 2xx) | FR-027 |
| `shop.orders.placed` | `shop_orders_placed_total` | counter | — | after commit of the order-creating TX1 | FR-027 |
| `shop.orders.mismatches` | `shop_orders_mismatches_total` | counter | `kind`: `PRICE`, `AVAILABILITY`, `CONTENTS` | `409 SUMMARY_OUTDATED` (one increment per kind detected in the request) | FR-027, FR-016 |
| `shop.orders.completed` | `shop_orders_completed_total` | counter | `status`: `PAID`, `PAYMENT_FAILED`, `NEEDS_REVIEW` | after commit of a status change to a target state | FR-027 |
| `shop.orders.paid.value` (base unit `pln`) | `shop_orders_paid_value_pln_total` | counter | — | after commit of `PAID`, by `total.minor / 100` (presentation only — never used for monetary calculations) | FR-027 |
| `shop.order.time.to.payment` | `shop_order_time_to_payment_seconds_{bucket,count,sum}` | timer (histogram; buckets 30 s, 1 min, 2 min, 5 min, 10 min, 30 min) | — | after commit of `PAID`: `paidAt − createdAt` | FR-028 |
| `shop.payments` | `shop_payments_total` | counter | `outcome`: `succeeded`, `declined`, `canceled` | mapping of Stripe events — R-28 | FR-027 |
| `shop.payment.webhook` | `shop_payment_webhook_total` | counter | `outcome`: `processed`, `duplicate`, `rejected_signature`, `rejected_livemode`, `ignored` | every webhook request, by the outcome of the verification/deduplication step ([stripe-webhook.md](stripe-webhook.md)) | FR-028 |
| `shop.payment.confirmation.delay` | `shop_payment_confirmation_delay_seconds_{bucket,count,sum}` | timer (histogram; buckets 1, 5, 10, 30, 60, 120 s) | — | after commit of handling `processed` status-changing events: `now − event.created` | FR-028, SC-007 |
| `shop.stripe.calls` | `shop_stripe_calls_seconds_{bucket,count,sum}` | timer (histogram; buckets 100 ms, 300 ms, 1 s, 3 s, 10 s) | `operation`: `create_session`, `expire_session`; `outcome`: `success`, `error`, `timeout` | every Stripe call **attempt** in the adapter | FR-028 |
| `shop.stripe.retries` | `shop_stripe_retries_total` | counter | `operation`: as above | every retry (the adapter retries itself, `maxNetworkRetries=0` in the SDK) | FR-028 |
| `shop.outbox.pending` | `shop_outbox_pending` | gauge | — | number of `outbox_event` rows with `sent_at IS NULL` (15 s cache) | FR-029 |
| `shop.outbox.oldest` (base unit `seconds`) | `shop_outbox_oldest_seconds` | gauge | — | age of the oldest unsent event; `0` when none | FR-029 |
| `shop.flyway.migrations` | `shop_flyway_migrations` | gauge | `state`: `success`, `failed`, `pending` | number of migrations in a given state, computed once after startup | FR-030 |

Names must not end with a suffix reserved by OpenMetrics (`_created`, `_total`, `_info`, `_bucket`, `_count`, `_sum`): the Prometheus client strips it from the base name (`shop.orders.created` would be exported as `shop_orders_total`), hence `shop.orders.placed`.

**Forbidden in labels and names** (FR-031, test SC-012): email, full name, address, order number
(`ORD-…`), Stripe identifiers (`cs_…`, `pi_…`, `evt_…`), `GuestId`, correlation identifier, search
phrase, keys (`sk_…`, `whsec_…`).

## 3. Built-in metrics (Spring Boot / Micrometer / Prometheus)

| Metric | Source | Usage |
|---|---|---|
| `http_server_requests_seconds_{bucket,count,sum}` with labels `method`, `uri` (template), `status`, `outcome`, `exception` | Spring MVC; `percentiles-histogram=true`, SLO 100 ms/300 ms/500 ms/1 s/2 s | HTTP dashboard, 5xx and p95 alerts (FR-026) |
| `jvm_memory_*`, `jvm_gc_*`, `jvm_threads_*` | Micrometer JVM | JVM dashboard (FR-030) |
| `hikaricp_connections_{active,idle,pending}`, `hikaricp_connections_acquire_seconds_*` | HikariCP | database dashboard (FR-030) |
| `process_uptime_seconds`, `process_cpu_usage`, `application_ready_time_seconds` | Boot | JVM dashboard |
| `up{job="shop-backend"}` | Prometheus | unavailability alert |

Liveness/readiness state: `/actuator/health/{liveness,readiness}` on `8081` (readiness includes
`db`); Prometheus measures availability through `up`.

## 4. Alert rules (`observability/prometheus/rules/shop.yml`)

Group `shop`, `interval: 15s`. Every rule has the labels `application="shop"`, `severity`
(`critical` | `warning`) and the annotations `summary` (in English) and `dashboard` (link to the
panel). Each has a `firing` and a `resolved` case in `observability/prometheus/tests/shop.test.yml`
(SC-011).

| Alert | Expression (PromQL) | `for` | Severity | FR-034 |
|---|---|---|---|---|
| `HighServerErrorRate` | `sum(rate(http_server_requests_seconds_count{application="shop",status=~"5.."}[5m])) / sum(rate(http_server_requests_seconds_count{application="shop"}[5m])) > 0.05` | 5m | critical | 5xx > 5% |
| `SlowCatalogOrCartResponses` | `histogram_quantile(0.95, sum by (le, uri) (rate(http_server_requests_seconds_bucket{application="shop",uri=~"/api/products\|/api/cart"}[5m]))) > 1` | 5m | warning | p95 > 1 s (SC-003) |
| `ForgedPaymentConfirmation` | `increase(shop_payment_webhook_total{outcome="rejected_signature"}[5m]) > 0` | 0m | critical | confirmation with an invalid signature |
| `PaymentProviderErrors` | `sum(rate(shop_stripe_calls_seconds_count{outcome!="success"}[5m])) / sum(rate(shop_stripe_calls_seconds_count[5m])) > 0.2` | 5m | critical | errors/timeouts > 20% |
| `DelayedPaymentConfirmations` | `histogram_quantile(0.95, sum by (le) (rate(shop_payment_confirmation_delay_seconds_bucket[5m]))) > 30` | 5m | critical | "Paid" > 30 s (SC-007, R-29a) |
| `MissingPaymentConfirmations` | `(sum(increase(shop_orders_placed_total[15m])) > 0) and on() (sum(increase(shop_payment_webhook_total{outcome=~"processed\|duplicate"}[15m])) == 0)` | 5m | critical | as above — no webhooks (R-29b) |
| `OrderNeedsReview` | `increase(shop_orders_completed_total{status="NEEDS_REVIEW"}[10m]) > 0` | 0m | warning | a "Needs review" order appears |
| `OutboxBacklog` | `shop_outbox_oldest_seconds > 300` | 0m | warning, plus `requires="fulfillment"` | oldest event > 5 min (R-30: in this feature it fires after every paid order — no dispatch job) |
| `ShopDown` | `up{job="shop-backend"} == 0` | 1m | critical | no metrics scrape for 1 min |

(`\|` in the table is `|` in PromQL.)

## 5. Dashboards (`observability/grafana/dashboards/*.json`, FR-033)

Data source: `uid: prometheus`. Dashboard variable `$application` (default `shop`).
Counters always through `rate`/`increase` (edge case: a restart resets counters).

| File | Title | Panels (minimum) |
|---|---|---|
| `http.json` | Shop — HTTP | requests/s per `uri`; 4xx and 5xx rate; p50/p95/p99 per `uri` (`histogram_quantile`); table of the top 5 slowest `uri`; alert state of `HighServerErrorRate`, `SlowCatalogOrCartResponses` |
| `purchase-funnel.json` | Shop — purchase funnel | funnel: cart additions → orders created → `PAID` (`increase` over the selected range); orders by `status`; payments by `outcome` (succeeded/declined/canceled as separate series); value of paid orders in PLN; mismatches by `kind` |
| `payments-integrations.json` | Shop — payments and integrations | Stripe calls by `operation`/`outcome`; p95 call duration; retries; webhooks by `outcome` (including `rejected_signature`); p95 confirmation delay vs the 30 s threshold; time-to-payment distribution; Outbox: pending and oldest (panel labeled "waiting for the fulfillment feature") |
| `jvm-database.json` | Shop — JVM and database | heap/non-heap; GC pauses; threads; process CPU; uptime; HikariCP active/idle/pending and connection acquisition time; Flyway migrations by `state`; `up` |

## 6. Correlation header (R-32)

Every API response (`8080`) contains `X-Request-Id`. A request may supply it
(`^[A-Za-z0-9-]{8,64}$`), otherwise the backend generates a UUID. The value appears in logs
(`[requestId]`), **never** in metrics. The header does not change paths, methods or response codes,
so `openapi.yaml` describes it as a shared response header (`components.headers.X-Request-Id`),
without changes to operations.
