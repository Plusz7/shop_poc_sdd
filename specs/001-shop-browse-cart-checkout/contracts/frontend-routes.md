# UI contract: routes and URL state (frontend SPA)

**Feature**: `001-shop-browse-cart-checkout` | API: [openapi.yaml](openapi.yaml)

The frontend presents data from the API and contains no price, availability or payment status rules
(Principle III). All amounts are formatted with
`Intl.NumberFormat('pl-PL', {style: 'currency', currency: 'PLN'})`.

UI texts below are English renderings; the exact Polish copy lives in the translation file
(`pl.json`) under the given meaning, and components refer to it by key only.

## Routes

| Route | View | Data source | Story / FR |
|---|---|---|---|
| `/` | Product list + category navigation + search box + filters | `GET /api/categories`, `GET /api/products` | US1, FR-001–FR-004 |
| `/product/:id` | Product page, quantity selection, "Add to cart" | `GET /api/products/{id}`, `POST /api/cart/lines` | US1-6, US2, FR-005 |
| `/cart` | Cart: lines, quantity change, removal, clearing, totals, messages | `/api/cart*` | US3, FR-009–FR-011 |
| `/checkout` | Details form + summary → redirect to Stripe | `GET /api/cart`, `POST /api/orders` | US4-1..3, US4-7 |
| `/orders/:number` | Confirmation with number, lines, total and status; polling every 2 s | `GET /api/orders/{number}` | US4-4, US4-6, FR-023 |
| `*` | 404 with a link to the shop | — | |

Header (every page): logo/link `/`, search box, link `/cart` with the counter `cart.itemCount`
(FR-013) — TanStack Query key `['cart']`, invalidated after every cart mutation and after returning
from Stripe.

## Product list URL parameters (FR-004 — a link to the results can be shared)

| URL parameter | API parameter | Notes |
|---|---|---|
| `category` | `category` | slug |
| `q` | `q` | search submitted with Enter/button |
| `minPrice`, `maxPrice` | same | in PLN in the UI, in grosze in the URL and API |
| `sort` | `sort` | `name_asc` (default, omitted from the URL), `price_asc`, `price_desc` |
| `page` | `page - 1` | numbered from 1 in the URL (friendly), from 0 in the API |

Changing a filter/sort resets `page` to 1; changing the page keeps the filters (US1-7).
Empty list → "No results" message + "Clear filters" button (navigates to `/`) (US1-5).

## Cart and order view states

| State from the API | Presentation |
|---|---|
| `cart.lines = []` | "Your cart is empty" + link to the shop; no checkout button (US3-5) |
| `line.priceChanged` | current price + struck-through `previousPriceMinor` + "Price has changed" banner and an "I understand" button → `POST /api/cart/accept-prices` (US3-6) |
| `line.status = UNAVAILABLE` | line greyed out, "Unavailable" label, only the "Remove" action (US3-7) |
| `cart.canPlaceOrder = false` | "Proceed to checkout" button disabled with a hint |
| `messages[code=QUANTITY_CAPPED]` | toast "Only N items available" (US3-3) |
| `409 QUANTITY_EXCEEDS_LIMIT` when adding | toast "You can add at most N items" (US2-3) |
| `?payment=canceled` on `/cart` | banner "The payment was not completed. Your cart is waiting." (US4-5) |
| `409 SUMMARY_OUTDATED` | summary replaced with `problem.cart`, banner "Prices or availability have changed — review and confirm again" (US4-7) |
| `400 VALIDATION_ERROR` | messages next to fields per `errors[].field` (US4-2) |
| `503 PAYMENT_UNAVAILABLE` | "Payment temporarily unavailable, please try again shortly" |
| order `AWAITING_PAYMENT` on `/orders/:number` | "Payment being verified" + spinner; after 60 s without a change: "Verification is taking longer — refresh the page later" (US4-6) |
| `PAID` | "Thank you! Order paid" + invalidation of `['cart']` (counter = 0) (US4-4) |
| `PAYMENT_FAILED` | "Payment failed" + link to the cart |
| `NEEDS_REVIEW` | "Payment received — we will contact you about fulfillment" |
| `404` | "Order not found" (no distinction between someone else's and non-existent) |

## Accessibility and responsiveness

Layout from 360 px (mobile) to desktop; product grid with 2/3/4 columns. Forms with `<label>`,
errors linked with `aria-describedby`, disabled buttons with `aria-disabled` and an explanation.
