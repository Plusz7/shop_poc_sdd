# Contract: Stripe integration (Checkout Session + webhook)

**Feature**: `001-shop-browse-cart-checkout` | Decisions: [research.md](../research.md) R-11–R-13

## 1. Outgoing calls (backend → Stripe)

Made only by the `payment/infrastructure/stripe/StripePaymentGateway` adapter, **outside the
database transaction**, with connect 5 s / read 10 s timeouts and 2 retries performed by the adapter
(`maxNetworkRetries = 0` in the SDK; the same `Idempotency-Key`; only connection errors, timeout,
`5xx`, `429`). Every attempt is measured in `shop.stripe.calls`, every retry in `shop.stripe.retries`
([metrics.md](metrics.md), R-13, R-27).

### `POST /v1/checkout/sessions`

| Parameter | Value |
|---|---|
| `mode` | `payment` |
| `payment_method_types[]` | `card` |
| `line_items[i].price_data.currency` | `pln` |
| `line_items[i].price_data.unit_amount` | unit price of the order line in grosze |
| `line_items[i].price_data.product_data.name` | name from the order line |
| `line_items[i].quantity` | quantity from the order line |
| `customer_email` | email from the order |
| `client_reference_id` | `order.id` |
| `metadata[orderId]`, `metadata[paymentId]`, `metadata[number]` | identifiers for correlation |
| `expires_at` | now + 30 min |
| `success_url` | `${APP_BASE_URL}/orders/{number}?session_id={CHECKOUT_SESSION_ID}` |
| `cancel_url` | `${APP_BASE_URL}/cart?payment=canceled` |
| `Idempotency-Key` header | `checkout-{paymentId}` |

**Invariant (SC-004)**: `Σ unit_amount × quantity == order.total`. Checked by an assertion in the
adapter before the call and by a contract test.

**Errors**: `APIConnectionException`, timeout, `5xx`, `RateLimitException` → the port returns
`GatewayUnavailable` → API `503 PAYMENT_UNAVAILABLE`. `InvalidRequestException` / `AuthenticationException`
→ configuration error, log (without the key) + `500`.

### `POST /v1/checkout/sessions/{id}/expire`

Called on a payment retry for open sessions of the same guest's previous pending orders (R-13).
A response with the error "session is not open" and `status = complete` → `ALREADY_PAID` →
API `409 ORDER_ALREADY_PAID`.

## 2. Webhook (Stripe → backend)

**Endpoint**: `POST /api/payments/stripe/webhook` (in the Stripe Dashboard / `stripe listen` only the
events from the table below are subscribed).

### Verification (in this order)

1. Read the raw body as a `String` (no Jackson deserialization).
2. `Webhook.constructEvent(payload, header "Stripe-Signature", STRIPE_WEBHOOK_SECRET)`,
   tolerance 300 s. Error → `400`, `WARN` log with the error type (without the body and header).
3. `event.livemode == true` → `400` (test mode only, Principle II).
4. Transaction: `INSERT processed_stripe_event(event_id)`; duplicate key → `200` with no effects.
5. Handling per the table, commit, `200`.

### Handled events

| `event.type` | Condition | `Payment` change | Domain event | Effect in `order` |
|---|---|---|---|---|
| `checkout.session.completed` | `payment_status == "paid"` | `OPEN → CONFIRMED`, store `payment_intent` | `PaymentConfirmedEvent(amount = amount_total, currency = currency)` | `PAID` or `NEEDS_REVIEW` (see data-model) |
| `checkout.session.completed` | `payment_status != "paid"` | no change | — | — (waits for `async_payment_*`) |
| `checkout.session.async_payment_succeeded` | — | `→ CONFIRMED` | `PaymentConfirmedEvent` | as above |
| `checkout.session.async_payment_failed` | — | `→ FAILED` | `PaymentFailedEvent` | `PAYMENT_FAILED` |
| `checkout.session.expired` | — | `→ EXPIRED` | `PaymentFailedEvent` | `PAYMENT_FAILED` (if still awaiting) |
| `payment_intent.payment_failed` | — | no change (the session is still open, the customer can use another card) | — | — (metric only, R-28) |
| other | — | — | — | `200`, ignored |

### Metrics (R-28, R-29, [metrics.md](metrics.md))

| Handling outcome | `shop.payment.webhook{outcome}` | `shop.payments{outcome}` | Other |
|---|---|---|---|
| step 2 failed | `rejected_signature` | — | immediately (outside a transaction) |
| step 3 (`livemode`) | `rejected_livemode` | — | immediately |
| step 4: duplicate `event.id` | `duplicate` | — | after the transaction ends |
| `checkout.session.completed` (paid) / `async_payment_succeeded` | `processed` | `succeeded` | after commit: `shop.payment.confirmation.delay` = now − `event.created` |
| `payment_intent.payment_failed` / `async_payment_failed` | `processed` | `declined` | after commit |
| `checkout.session.expired` | `processed` | `canceled` | after commit |
| `completed` with `payment_status != "paid"`, unknown session, other types | `ignored` | — | after commit |

Webhook handling puts `stripeEventId` into the MDC (logs), never into metrics (FR-031).

Correlation: `data.object.id` (session id) → `Payment.providerSessionId`. Unknown session →
`200` + `WARN` log (e.g. an event from another test environment on the same account).

### Guarantees

| Guarantee | Mechanism | Test |
|---|---|---|
| A forged confirmation does not change the status (SC-005) | step 2 | integration: body with an invalid signature → `400`, status unchanged |
| A repeated confirmation is processed once (FR-020) | step 4 + idempotent status transition | integration: the same event 2× (also concurrently) → stock decreased once |
| Charged amount = order total (SC-004) | comparing `amount_total`/`currency` with `order.total` | integration: mismatched amount → `NEEDS_REVIEW` |
| Payment only through the webhook (FR-019) | `success_url` triggers no state change | E2E: visiting `success_url` without a webhook → "Payment being verified" |
| "Paid" status ≤ 30 s after confirmation (SC-007) | synchronous handling within the webhook request | E2E with `stripe listen` |

Signature in tests: the body is signed in the test with HMAC-SHA256 using a test secret
`whsec_test_…` set in the test profile (not a secret from the Stripe account).
