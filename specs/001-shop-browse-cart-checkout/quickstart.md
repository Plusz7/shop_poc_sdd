# Quickstart: validating the "Shop Browsing, Cart and Checkout" feature

**Feature**: `001-shop-browse-cart-checkout` | [plan.md](plan.md) | [data-model.md](data-model.md) |
[contracts/](contracts/)

A guide to running and checking the feature end-to-end (SC-008). The feature is "done" when all
scenarios from section 3 give the expected result and section 4 is green.

UI texts in quotes are English renderings of the Polish UI copy (`frontend/src/i18n/pl.json`).

## 1. Prerequisites

| Tool | Version | Notes |
|---|---|---|
| JDK | 21 | backend (`mvnw` is in the repo, no need to install Maven) |
| Node.js | 22 LTS | frontend, Playwright |
| Docker Desktop | current | SQL Server 2022, `stripe-mock`/Testcontainers, Stripe CLI |
| Stripe account | **test** mode | `sk_test_…` key from Dashboard → Developers → API keys |
| gitleaks + pre-commit | current | `pre-commit install` after cloning (Principle I) |

## 2. Configuration and local run

1. Copy `.env.example` to `.env` (the file is in `.gitignore`) and fill in:

   | Variable | Source |
   |---|---|
   | `STRIPE_SECRET_KEY` | `sk_test_…` from the Dashboard (an `sk_live_` key blocks startup — R-20) |
   | `STRIPE_WEBHOOK_SECRET` | `whsec_…` printed by `stripe listen` (step 3) |
   | `DB_PASSWORD` | any strong password for the local SQL Server |
   | `APP_BASE_URL` | `http://localhost:5173` |
   | `GRAFANA_ADMIN_PASSWORD` | any strong password for the local Grafana (without it `compose up` stops — R-31) |

   To work on US1–US3 without a Stripe account, the dummies `STRIPE_SECRET_KEY=sk_test_dummy`
   and `STRIPE_WEBHOOK_SECRET=whsec_dummy` are enough — the application starts, and placing an
   order returns "Payment temporarily unavailable" (`503`). Skip step 3 in that case.

2. Dependencies (SQL Server + creation of the `shop` database, Prometheus on `127.0.0.1:9090`,
   Grafana on `127.0.0.1:3000`):

   ```bash
   docker compose up -d
   ```

3. Forwarding Stripe webhooks to the local backend (compose profile `stripe`; on the first run copy
   the printed `whsec_…` into `.env`):

   ```bash
   docker compose --profile stripe up -d stripe-cli
   ```

4. Backend (Flyway creates the schema, the `local` profile loads a seed of ≥ 500 products):

   ```bash
   ./backend/mvnw -f backend/pom.xml spring-boot:run -Dspring-boot.run.profiles=local
   ```

   Expected: starts on `:8080` (API) and `:8081` (metrics and health — R-26). Without
   `STRIPE_SECRET_KEY` startup fails with "Missing required configuration: shop.stripe.secret-key"
   — no secret values in the log.

5. Frontend (proxy `/api` and `/images` → `:8080`):

   ```bash
   npm --prefix frontend ci
   ```

   ```bash
   npm --prefix frontend run dev
   ```

   Shop: <http://localhost:5173>. Swagger UI (`local` profile only): <http://localhost:8080/swagger-ui.html>.
   Grafana: <http://localhost:3000> (login `admin`, password from `GRAFANA_ADMIN_PASSWORD`),
   Prometheus: <http://localhost:9090>.

## 3. Validation scenarios (manual or via E2E)

Stripe test cards: **accepted** `4242 4242 4242 4242`, **declined** `4000 0000 0000 0002`;
any future date, any CVC.

### US1 — browsing (FR-001–FR-005)

| # | Steps | Expected result |
|---|---|---|
| 1.1 | Open `/` | 24 products (image, name, price, availability) + category list |
| 1.2 | Select a category | only products from that category; the URL contains `?category=…` |
| 1.3 | Search for `LODZ` (a seed product has "łódź" in its name) | product found (letter case and diacritics ignored) |
| 1.4 | Set the price to 50–200 PLN and "price descending" | only prices within the range, descending; parameters in the URL |
| 1.5 | Copy the URL into a new tab | identical results (shareable link) |
| 1.6 | Search for `%_[zzz` | "No results" + "Clear filters", no error |
| 1.7 | Go to page 2 | next products, filters preserved |
| 1.8 | Open a product with stock 2 | product page with description, images, "Last items" label |

### US2 — adding to the cart (FR-006–FR-008, FR-012, FR-013)

| # | Steps | Expected result |
|---|---|---|
| 2.1 | On the product page set quantity 2, "Add to cart" | confirmation, header counter +2 |
| 2.2 | Add the same product from the list | one line, quantity 3 |
| 2.3 | Product with stock 3, 2 in the cart → add 2 | blocked: "You can add at most 1 item" |
| 2.4 | Open an unavailable product | button disabled, "Unavailable" label |
| 2.5 | Refresh the page / close and reopen the browser | cart kept (`shop_guest` cookie, 30 days) |

### US3 — editing the cart (FR-009–FR-011)

| # | Steps | Expected result |
|---|---|---|
| 3.1 | Open `/cart` with 2 products | unit prices, quantities, line totals, total |
| 3.2 | Change quantity 1 → 3 | line total and total recalculated by the backend |
| 3.3 | Set quantity 50 for a product with stock 5 | quantity = 5 + message "Only 5 items available" |
| 3.4 | Set 0 / click "Remove" | the line disappears, total recalculated |
| 3.5 | "Clear cart" | "Your cart is empty", no way to proceed to checkout |
| 3.6 | Change the product price in the database (`UPDATE product SET price_minor = …`), refresh the cart | new price + struck-through old price + price change banner |
| 3.7 | Set `stock = 0` for a product in the cart, refresh | line "Unavailable", checkout button disabled |
| 3.8 | `PUT /api/cart/lines/{id}` with `{"quantity": 1.5}` / `-1` / `"abc"` | `400 VALIDATION_ERROR` |

### US4 — order and payment (FR-014–FR-024)

| # | Steps | Expected result |
|---|---|---|
| 4.1 | "Proceed to checkout" | form + summary with the total |
| 4.2 | Postal code `12345`, empty email | messages next to the fields, no redirect |
| 4.3 | Valid data → "Pay" | redirect to Stripe Checkout; amount = shop total |
| 4.4 | Pay with the `4242…` card | page `/orders/ORD-…`: "Payment being verified" → "Paid" within ≤ 30 s; cart counter 0; product stock decreased |
| 4.5 | New order, card `4000…0002`, then "Back" on the Stripe page | Stripe shows the decline; after returning, the "The payment was not completed" banner, cart untouched, order unpaid |
| 4.6 | Stop `stripe-cli`, pay with `4242…` | the confirmation page stays at "Payment being verified"; after restarting `stripe-cli` and `stripe events resend <evt>` → "Paid" |
| 4.7 | At the form step change the product price in the database → "Pay" | no redirect; updated summary for re-confirmation |
| 4.8 | Open `/orders/{number}` in another browser (no cookie) | "Order not found" |
| 4.9 | `stripe events resend <evt_completed>` for a paid order | no change; stock decreased only once |
| 4.10 | `curl -X POST localhost:8080/api/payments/stripe/webhook -H "Stripe-Signature: t=1,v1=bad" -d '{}'` | `400`; no order changes status |
| 4.11 | Set an unreachable Stripe host in `.env` (`STRIPE_API_BASE=http://localhost:9`), place an order | "Payment temporarily unavailable, please try again shortly", cart untouched |

Database state check (optional): statuses in the `orders` table, an `OrderPaid` event in
`outbox_event` for every paid order, deduplication in `processed_stripe_event` — see
[data-model.md](data-model.md).

### US5 — monitoring (FR-025–FR-034, SC-009–SC-012)

Metric, rule and dashboard names: [contracts/metrics.md](contracts/metrics.md). Rules with
`for: 5m` go into "firing" only after 5 min — in CI they are checked by `promtool` (section 4).

| # | Steps | Expected result |
|---|---|---|
| 5.1 | After steps 2 and 4 of section 2, open Grafana → Dashboards | folder "Shop" with 4 dashboards; Prometheus data source configured; data within ≤ 2 min (SC-009) |
| 5.2 | Prometheus → Status → Targets | `shop-backend` (`host.docker.internal:8081`) in the `UP` state |
| 5.3 | `curl -s -o /dev/null -w "%{http_code}" localhost:8080/actuator/prometheus` | `404` — no metrics on the API port (FR-025, US5-7) |
| 5.4 | `curl -s localhost:8081/actuator/prometheus \| grep "^shop_"` | metrics from the contract, each with `application="shop"`; no emails, `ORD-…`, `cs_test_…` (FR-031) |
| 5.5 | Run scenario 4.4 (purchase with the `4242…` card) | within ≤ 1 min the "purchase funnel" dashboard shows: +1 order created, +1 `PAID`, sales value grows by the order total; "payments": +1 `succeeded`, confirmation delay < 30 s (US5-2, SC-010) |
| 5.6 | Run scenario 4.5 (card `4000…0002`) | +1 `declined` payment, a separate series from `succeeded` (US5-3, R-28) |
| 5.7 | Run scenario 4.10 (forged signature) | webhook `rejected_signature` +1; alert `ForgedPaymentConfirmation` "firing" in Prometheus → Alerts within ≤ 30 s, "resolved" after ~5 min (US5-4) |
| 5.8 | Run scenario 4.11 (unreachable Stripe) several times within 5 min | Stripe calls panel: `outcome="error"`/`timeout` and retries; after 5 min `PaymentProviderErrors` "firing" (US5-6) |
| 5.9 | Stop the backend (Ctrl+C) | after ~1 min `ShopDown` "firing"; after restarting "resolved", and counter charts have no spikes (edge case: restart) |
| 5.10 | Stop Prometheus and Grafana (`docker compose stop prometheus grafana`), run scenario 2.1 | the shop works unchanged; after `docker compose start prometheus grafana` collection resumes by itself (edge case) |
| 5.11 | After a paid order wait 5 min | `OutboxBacklog` "firing" with the label `requires="fulfillment"` — expected in this feature (R-30); `docker compose exec sqlserver … "UPDATE outbox_event SET sent_at = SYSDATETIMEOFFSET()"` → "resolved" after the next scrape |
| 5.12 | Response of any `/api/**` | `X-Request-Id` header; the same value in the backend log for that request (R-32) |

## 4. Automated tests

| Level | Command | Requires |
|---|---|---|
| Backend: unit + integration + contract + architecture | `./backend/mvnw -f backend/pom.xml verify` | Docker (Testcontainers); no network access to Stripe |
| Frontend: unit | `npm --prefix frontend test` | — |
| Frontend: types from the contract up to date | `npm --prefix frontend run api:types -- --check` | — |
| E2E (4 P1 paths) | `npm --prefix frontend run e2e` | stack from section 2 running, test `STRIPE_*` |
| Secret scan | `gitleaks detect --no-banner` | — |
| Alert rules (syntax + firing/resolved cases, SC-011) | `docker run --rm -v "$PWD/observability/prometheus:/p" --entrypoint promtool prom/prometheus test rules /p/tests/shop.test.yml` | Docker |
| Dashboards refer to metrics from the contract | `node scripts/check-dashboards.mjs` | Node.js |

Expected: everything green; the performance test on the 500-product seed fits the budget
(R-24, SC-003).
