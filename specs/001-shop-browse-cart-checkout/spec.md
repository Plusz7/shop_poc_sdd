# Feature Specification: Shop Browsing, Cart and Checkout

**Feature Branch**: `001-shop-browse-cart-checkout`

**Created**: 2026-09-23

**Status**: Draft

**Input**: User description: "shop browsing, cart, cart editing, Stripe payment"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Browsing and Searching Products (Priority: P1)

A customer enters the shop and browses the offer: they see a list of products with an image,
name and price, can narrow it down to a category, search for a product by name, filter by a
price range and sort the results. After clicking a product they see its product page with a
description, price and availability information — similar to Allegro or eBay.

**Why this priority**: without a way to find a product there is no further purchase path; it is
also an independently demonstrable part of the shop.

**Independent Test**: can be fully tested against a sample catalog — searching for a product by
name, narrowing to a category and opening a product page delivers value (the customer knows what
they can buy and for how much) without a working cart.

**Acceptance Scenarios**:

1. **Given** the catalog contains products in several categories, **When** the customer opens the
   shop's home page, **Then** they see the first page of the product list (image, name, price,
   availability) and category navigation.
2. **Given** the customer is on the product list, **When** they select a category, **Then** the list
   shows only products from that category.
3. **Given** the customer types a phrase into the search box, **When** they submit the search,
   **Then** they see products whose name contains that phrase (case-insensitive).
4. **Given** the customer is viewing results, **When** they set a price range and sorting (price
   ascending, price descending, name), **Then** the list contains only products within the range,
   in the chosen order.
5. **Given** a search returns no results, **When** the list is displayed, **Then** the customer sees
   a "no results" message with an option to clear the filters.
6. **Given** the customer clicks a product on the list, **When** the product page opens, **Then** they
   see the name, images, description, price and availability (available / last items / unavailable).
7. **Given** the list has more products than fit on one page, **When** the customer goes to the next
   page, **Then** they see the following products with filters and sorting preserved.

---

### User Story 2 - Adding Products to the Cart (Priority: P1)

The customer adds the chosen product to the cart from the product page (with a quantity) or
directly from the list (1 item). They immediately get a confirmation, and the cart counter in
the header updates. They do not have to log in or create an account.

**Why this priority**: the cart is a precondition for a purchase; together with US1 it forms a
minimal, meaningful increment (the customer can assemble an order).

**Independent Test**: can be tested by adding products from the list and from the product page,
then checking the counter and the cart contents — also after a page refresh.

**Acceptance Scenarios**:

1. **Given** the product is available, **When** the customer selects quantity 2 on the product page
   and clicks "Add to cart", **Then** the cart contains that product with quantity 2, the customer
   sees a confirmation, and the header counter increases by 2.
2. **Given** the product is already in the cart, **When** the customer adds it again, **Then** the
   quantity of the existing line is summed (no second line is created).
3. **Given** the product has 3 items in stock and the customer has 2 in the cart, **When** they try
   to add another 2, **Then** adding is blocked with a message stating the maximum available quantity.
4. **Given** the product is unavailable, **When** the customer views its page or the list, **Then**
   the "Add to cart" button is disabled and the product is marked as unavailable.
5. **Given** the customer has added products to the cart, **When** they refresh the page or return to
   the shop in the same browser within 30 days, **Then** the cart keeps its contents.

---

### User Story 3 - Viewing and Editing the Cart (Priority: P1)

The customer opens the cart and sees all lines: image, name, unit price, quantity, line total and
the overall total. They can change a quantity, remove a line or clear the whole cart. Totals are
recalculated after every change.

**Why this priority**: the customer must be able to correct the order before paying; without this,
a quantity mistake ends in an abandoned purchase.

**Independent Test**: can be tested on a cart with several lines — changing a quantity, removing a
line and clearing the cart, verifying the totals.

**Acceptance Scenarios**:

1. **Given** the cart contains 2 different products, **When** the customer opens the cart, **Then**
   they see both lines with unit price, quantity, line total and the overall total.
2. **Given** a line has quantity 1, **When** the customer increases the quantity to 3, **Then** the
   line total and the overall total are recalculated using the current price.
3. **Given** the customer tries to set a quantity larger than the available stock, **When** they
   submit the change, **Then** the quantity is capped at the available stock with an appropriate
   message.
4. **Given** the customer reduces a line's quantity to 0 or clicks "Remove", **When** the change is
   saved, **Then** the line disappears from the cart and the total is recalculated.
5. **Given** the cart is empty, **When** the customer opens it, **Then** they see a "Your cart is
   empty" message and a link to browse the shop; proceeding to payment is unavailable.
6. **Given** a product's catalog price has changed since it was added to the cart, **When** the
   customer opens the cart, **Then** they see the current price and a clear notice of the price change.
7. **Given** a product in the cart has become unavailable, **When** the customer opens the cart,
   **Then** the line is marked as unavailable and payment cannot proceed until the customer removes it.

---

### User Story 4 - Placing an Order and Paying Online (Priority: P1)

From a non-empty cart the customer proceeds to checkout: they enter an email address and a
shipping address, see a summary (lines, amount to pay) and proceed to card payment with an
external payment provider. After a successful payment they see a confirmation page with the order
number, and the cart is emptied. If the payment fails or is canceled, the customer returns to the
shop with the cart intact and can try again.

**Why this priority**: payment closes the purchase path — without it the PoC does not prove that
the shop can sell.

**Independent Test**: can be tested on a cart with prepared products by making a test payment with
an accepted card and with a declined card, checking the order status and the cart state in both
cases.

**Acceptance Scenarios**:

1. **Given** the cart contains available products, **When** the customer clicks "Proceed to
   checkout", **Then** they see a details form (email, full name, shipping address) and an order
   summary with the amount to pay.
2. **Given** the customer entered an invalid email or skipped a required address field, **When** they
   try to continue, **Then** they see a message next to the specific field and do not proceed to
   payment.
3. **Given** the form is valid, **When** the customer proceeds to payment, **Then** they are
   redirected to the provider's payment page with an amount equal to the order total calculated by
   the shop.
4. **Given** the customer paid successfully, **When** the provider confirms the payment, **Then** the
   order has the status "Paid", product stock decreases by the purchased quantities, the customer
   sees a confirmation page with the order number, and the cart is empty.
5. **Given** the payment was declined or the customer canceled it, **When** they return to the shop,
   **Then** they see a notice about the failed payment, the cart keeps its previous contents, and the
   order is not marked as paid.
6. **Given** the customer returned to the confirmation page before the shop received the provider's
   confirmation, **When** the page loads, **Then** the customer sees the status "Payment being
   verified", which changes to "Paid" once the confirmation arrives.
7. **Given** a product's price or availability changed since the order was started, **When** the
   customer proceeds to payment, **Then** the shop stops the process and shows an updated summary
   for re-confirmation.

---

### User Story 5 - Monitoring the Shop (Priority: P2)

The team maintaining the shop (developer/operator) opens ready-made Grafana dashboards and sees
whether the shop works correctly: traffic, response times and errors, the purchase flow (carts,
orders, successful and failed payments), the state of the payment provider integration, and the
state of the application and the database. When something worrying happens (a growing number of
errors, slow responses, payment confirmations not arriving), the relevant alert rule goes into the
"firing" state without anyone having to watch the charts constantly.

**Why this priority**: a shop without observability runs "blind" — it is impossible to demonstrate
SC-003 and SC-007 or to diagnose a payment problem quickly. It does not block the purchase path
itself, hence P2.

**Independent Test**: after `docker compose up` and a few test purchases (one successful and one
declined), open Grafana and check that the dashboards show traffic, orders and payments matching
the actions performed; stop the database or send a forged payment confirmation and check that the
relevant alert changes state.

**Acceptance Scenarios**:

1. **Given** the local environment was started with a single command, **When** the developer opens
   Grafana, **Then** the Prometheus data source and the shop dashboards are already configured,
   without manual clicking.
2. **Given** a customer made a purchase, **When** the developer looks at the business dashboard,
   **Then** within 1 minute they see an increase in created and paid orders and in the value of paid
   orders in PLN.
3. **Given** a payment was declined, **When** the developer looks at the payments dashboard, **Then**
   they see it as a failed payment, distinguished from successful and canceled ones.
4. **Given** the shop received a payment confirmation with an invalid signature, **When** the
   developer looks at the payments dashboard, **Then** they see the rejected-confirmations counter,
   and the forged-confirmation alert rule goes into the "firing" state.
5. **Given** the server error rate exceeds the threshold for the configured duration, **When** the
   rule is evaluated, **Then** the "high error rate" alert goes into "firing" and returns to
   "resolved" once the problem is gone.
6. **Given** the payment provider is unavailable, **When** the developer looks at the integrations
   dashboard, **Then** they see a growing number of errors and timeouts in provider calls.
7. **Given** the application's metrics endpoint, **When** someone outside the internal network tries
   to read it, **Then** access is impossible (metrics are not publicly exposed).

---

### Edge Cases

- Two customers buy the last item of a product at the same time: availability is checked when
  proceeding to payment; if stock turns out to be insufficient after the payment is confirmed, the
  order gets the status "Needs review" (manual handling, out of PoC scope).
- The customer closes the browser tab during payment: the order stays "Awaiting payment"; the
  provider's confirmation still changes the status to "Paid", and the cart is emptied on the next
  visit.
- The payment provider sends the same confirmation several times: the order is marked as paid once,
  and stock is decreased only once.
- A payment confirmation is forged (does not come from the provider): the shop rejects it and does
  not change the order status.
- The customer manually enters another customer's order identifier in the page URL: the
  confirmation page does not reveal another customer's order data.
- The customer enters a quantity that is not a positive integer (e.g. -1, 1.5, "abc"): the value is
  rejected with a message.
- The payment provider is temporarily unavailable: the customer sees the message "Payment
  temporarily unavailable, please try again shortly", and the cart stays intact.
- A product removed from the catalog is still in the cart: the line is shown as unavailable (as in
  US3, scenario 7).
- A very long search phrase or special characters: search works without errors (the phrase is
  trimmed to 100 characters).
- Prometheus or Grafana is unavailable: the shop works normally — metric collection never blocks or
  slows down the purchase path; once Prometheus is back, collection resumes by itself (a gap in the
  data is acceptable).
- An application restart resets in-memory counters: dashboards and alerts are based on rates of
  change (`rate`/`increase`), so a restart causes no false spikes or alerts.
- A search with an arbitrary phrase or a request to a non-existent URL: metrics do not create a
  separate series per phrase/URL — labels have a bounded, known-in-advance set of values.

## Requirements *(mandatory)*

### Functional Requirements

**Catalog and browsing**

- **FR-001**: System MUST present a paginated product list (24 products per page by default)
  containing for each product: main image, name, price and availability status.
- **FR-002**: System MUST allow narrowing the list to a category.
- **FR-003**: System MUST allow searching products by a fragment of the name, ignoring letter case
  and Polish diacritics in both the query and the name.
- **FR-004**: System MUST allow filtering by a price range and sorting by price (ascending/descending)
  and by name; filters, sorting and page MUST be reflected in the page URL so that a link to the
  results can be shared.
- **FR-005**: System MUST display a product page with the name, images, description, price and
  availability status ("available", "last items" when stock ≤ 3, "unavailable" when stock is 0).

**Cart**

- **FR-006**: Customers MUST be able to add a product to the cart without logging in — from the
  product page with a chosen quantity or from the list with quantity 1.
- **FR-007**: System MUST merge a repeated addition of the same product into a single line with the
  summed quantity.
- **FR-008**: System MUST block adding or setting a quantity that exceeds the available stock or the
  limit of 99 items per line, stating the maximum possible quantity.
- **FR-009**: Customers MUST be able to change a line's quantity, remove a line and clear the whole
  cart.
- **FR-010**: System MUST calculate each line total and the cart total on the shop side based on
  current catalog prices; prices sent by the browser are irrelevant.
- **FR-011**: System MUST inform the customer about a price change or loss of availability of a
  product since it was added to the cart.
- **FR-012**: System MUST keep the customer's cart for at least 30 days after the last change in the
  same browser.
- **FR-013**: System MUST display the number of items in the cart in the header of every page.

**Order and payment**

- **FR-014**: System MUST allow proceeding to checkout only from a non-empty cart in which all lines
  are available in the required quantity.
- **FR-015**: System MUST collect from the customer: email address, full name, shipping address
  (street and number, postal code in NN-NNN format, city; country: Poland) and validate them before
  proceeding to payment.
- **FR-016**: System MUST re-verify prices and availability before proceeding to payment; on any
  discrepancy it MUST show an updated summary for re-confirmation.
- **FR-017**: System MUST create an order with a unique number, an immutable copy of the lines (name,
  unit price, quantity), the customer's details and the total, with the status "Awaiting payment".
- **FR-018**: System MUST take card payments on the external payment provider's page; card data is
  never entered into or stored by the shop.
- **FR-019**: System MUST mark an order as "Paid" only on the basis of a verified confirmation from
  the payment provider, not on the basis of the customer returning to the shop's page.
- **FR-020**: System MUST process repeated confirmations of the same payment only once.
- **FR-021**: After an order is paid, the system MUST decrease stock by the purchased quantities and
  empty the customer's cart; if stock is insufficient it MUST assign the status "Needs review".
- **FR-022**: For a declined or canceled payment the system MUST keep the cart contents, show the
  customer a message and allow another attempt.
- **FR-023**: System MUST display a confirmation page with the order number, the list of lines, the
  total and the current payment status, accessible only to the customer who placed the order.
- **FR-024**: All amounts MUST be presented in PLN with an accuracy of 1 grosz, and the order total
  MUST equal the sum of line totals (shipping cost in the PoC is 0 PLN).

**Observability (metrics, dashboards, alerts)**

- **FR-025**: System MUST expose Prometheus-format metrics on an endpoint available only internally
  (a separate management port or the container network), not reachable by shop customers.
- **FR-026**: System MUST measure for each HTTP endpoint (by path template, not the concrete URL):
  the number of requests, the response status and the response time distribution (a histogram that
  allows computing p50/p95/p99).
- **FR-027**: System MUST provide business metrics of the purchase path: additions to the cart,
  started orders, orders by target status ("Paid", "Payment failed", "Needs review"), payments by
  outcome (succeeded/declined/canceled), the total value of paid orders in PLN, and the number of
  price/availability discrepancies when proceeding to payment (FR-016).
- **FR-028**: System MUST provide payment provider integration metrics: duration and outcome of each
  call (success/error/timeout), the number of retries, the number of received confirmations split
  into processed, duplicate (FR-020) and rejected because of an invalid signature (edge case "forged
  confirmation"), and the time from order creation to payment.
- **FR-029**: System MUST provide Outbox metrics: the number of events waiting to be sent and the age
  of the oldest one.
- **FR-030**: System MUST provide technical metrics: JVM (memory, GC, threads), the database
  connection pool (active, pending, acquisition time), database migrations and the application
  health state (liveness/readiness).
- **FR-031**: Metrics MUST NOT contain personal data or secrets (email, full name, address, order or
  payment identifiers, keys) in names or labels; all labels MUST have a bounded, known-in-advance set
  of values. Every metric MUST have an `application` label identifying the shop.
- **FR-032**: The local environment MUST start Prometheus and Grafana together with the shop with a
  single command; the data source, dashboards and alert rules MUST be versioned in the repository
  and loaded automatically (provisioning), without manual configuration.
- **FR-033**: System MUST provide at least these dashboards: (a) HTTP overview — traffic, errors,
  p95/p99 latency per endpoint; (b) purchase funnel — cart → order → paid, payments by outcome,
  sales value; (c) payments and integrations — provider calls, confirmations by outcome, time to
  payment, Outbox; (d) JVM and database.
- **FR-034**: System MUST define alert rules for at least: a 5xx response rate > 5% for 5 minutes;
  p95 response time of the product list, search or cart > 1 s for 5 minutes (SC-003); any payment
  confirmation rejected because of its signature; a payment provider error/timeout rate > 20% for
  5 minutes; an "Awaiting payment" order with a successful payment for longer than 30 s (SC-007);
  an order appearing with "Needs review"; the oldest Outbox event older than 5 minutes; application
  unavailability (no metrics scrape for 1 minute).

### Key Entities *(include if feature involves data)*

- **Product**: an item offered for sale — name, description, price (PLN), images, category, stock,
  catalog active flag.
- **Category**: a group of products for navigation — name; a flat list in the PoC (no subcategories).
- **Cart**: a temporary set of lines tied to the customer's browser — lines, date of last change;
  does not require an account.
- **Cart line**: a product and a quantity; the price is always read from the current catalog.
- **Order**: a persisted purchase — number, customer details (email, full name, shipping address),
  immutable order lines, total, status ("Awaiting payment", "Paid", "Payment failed", "Needs
  review"), creation and payment dates.
- **Order line**: a copy of the product name, unit price and quantity at the time of ordering.
- **Payment**: an attempt to pay for an order with the provider — provider identifier, amount,
  status, confirmation date.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A new customer goes from entering the shop to a paid order for one found product in
  less than 3 minutes.
- **SC-002**: 90% of usability test participants (at least 5 people) independently find a given
  product, add it to the cart, change the quantity and pay for the order on the first attempt.
- **SC-003**: The product list, search results and cart are shown to the customer in under 1 second
  for a catalog of at least 500 products.
- **SC-004**: In 100% of test cases the amount charged by the payment provider equals the order total
  calculated by the shop.
- **SC-005**: 0 orders marked as "Paid" without a confirmation from the payment provider (including
  repeated and forged confirmations).
- **SC-006**: After a failed or canceled payment, 100% of carts keep their full contents.
- **SC-007**: The order status changes to "Paid" within 30 seconds of the provider confirming the
  payment.
- **SC-008**: All four paths (browsing, adding to cart, editing the cart, payment) can be
  demonstrated end-to-end in the local environment.
- **SC-009**: After starting the local environment with a single command, all dashboards from FR-033
  are available in Grafana and show data within 2 minutes, without manual configuration.
- **SC-010**: Every purchase path event performed in a test (add to cart, order, succeeded/declined
  payment, forged confirmation) is visible in the metrics within 1 minute, and the numbers match the
  performed actions in 100% of test cases.
- **SC-011**: Every alert rule from FR-034 has been triggered at least once in a test ("firing"
  state) and returned to "resolved" after the cause was removed.
- **SC-012**: 0 occurrences of personal data or secrets in the metrics endpoint response (verified by
  an automated test after running the full purchase path); the overhead of metric collection on p95
  response time is below 5%.

## Assumptions

- Purchases are made as a guest — customer accounts, login and order history are out of scope.
- The catalog (products, categories, images, stock) is filled with sample data; an admin panel for
  managing the catalog is out of scope.
- The payment provider is Stripe running in test mode only; the only payment method in the PoC is a
  card (other methods, e.g. BLIK, as a possible extension).
- A single currency (PLN), shipping within Poland only, 0 PLN shipping cost, no discount codes.
- Stock is not reserved for the duration of the payment; the risk of selling the last item to two
  customers is handled with the "Needs review" status (returns and refunds out of scope).
- Prices are gross prices; invoices, receipts and tax handling are out of scope.
- Order confirmation by email is out of scope — the customer sees the confirmation on the page.
- Passing paid orders to the kanban board (Trello) is a separate feature; this specification only
  provides the "order paid" event that feature will use.
- The user interface is in Polish and works in desktop and mobile browsers. UI texts quoted in this
  specification are English renderings; the exact Polish copy lives in the frontend translation files.
- The behavior reference (cart, counter, availability messages) is popular marketplaces (Allegro,
  eBay) to the extent described above.
- Observability is based on Prometheus (pull-based metric collection, 15-day local retention) and
  Grafana (dashboards); both run as containers in the local environment next to the shop. This is a
  deliberately added component, explicitly requested by the project owner.
- Alerts are defined and visible (Prometheus/Grafana), but notification delivery (email, Slack,
  Alertmanager with routing) is out of PoC scope.
- Frontend metrics (Web Vitals, JS errors), central log collection (e.g. Loki) and distributed
  tracing (e.g. Tempo) are out of scope — a possible extension. Application logs contain a request
  correlation identifier so they can be linked to an incident visible on a dashboard.
- Access to Grafana in the local environment is protected by an admin password supplied through an
  environment variable (not the default `admin/admin`).
