---

description: "Implementation task list: shop browsing, cart and checkout"
---

# Tasks: Shop Browsing, Cart and Checkout

**Input**: Design documents from `/specs/001-shop-browse-cart-checkout/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md),
[data-model.md](data-model.md), [contracts/](contracts/), [quickstart.md](quickstart.md)

**Tests**: REQUIRED — the constitution (Principle VI) and `AGENTS.md` mandate domain unit tests
(without Spring), integration tests for every `*Service` (Testcontainers, real database), contract
tests for the Stripe adapters (`stripe-mock`/WireMock), webhook tests with a valid and a forged
signature, and E2E (Playwright) for every P1 path. For US5 (R-34): metrics adapter unit tests
(`SimpleMeterRegistry`), counter assertions after commit and rollback in `*Service` tests,
`MetricsEndpointIT`, `promtool test rules` and `scripts/check-dashboards.mjs`. Tests are written
BEFORE the implementation and must fail first.

**Organization**: Tasks are grouped by user story (US1–US4 — P1, US5 — P2), so that each can be
delivered and demonstrated independently (Principle IV). US1–US3 do not require a Stripe account
or connection — the application starts with dummy keys in the `sk_test_…`/`whsec_…` format
(the format validation from Principles I/II always applies).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependencies on unfinished tasks)
- **[Story]**: the user story the task belongs to (US1, US2, US3, US4, US5)
- Every task gives the exact file path

## Path conventions

- Backend (code): `backend/src/main/java/com/project/custom/<bc>/...`
- Backend (resources): `backend/src/main/resources/...`
- Backend (tests): `backend/src/test/java/com/project/custom/<bc>/{domain,application,infrastructure}/...`
- Frontend: `frontend/src/...`, tests: `frontend/tests/unit/...`, `frontend/tests/e2e/...`
- Observability: `observability/prometheus/...`, `observability/grafana/...`, `scripts/check-dashboards.mjs`
- Visibility rules (`AGENTS.md`, refined in plan.md): `*Facade` = `public interface` in the BC root
  + package-private implementation in `application/`; `domain/*Repository` = `public interface`;
  `*Service` = `public` (required by Java, because `api/` is a different package), but used ONLY
  within its own BC — enforced by ArchUnit (T012, rule 6); `*Controller`, `*JpaEntity`,
  repository adapters = package-private; DTOs through a facade = `public record` with the `Dto`
  suffix; constructor injection only.
- Language (constitution 1.1.0): all code, identifiers, comments, logs and exception messages in
  English; names per `.claude/skills/english-only/glossary.md`. Polish text only as customer-facing
  UI copy in `frontend/src/i18n/pl.json` and in seed data.

---

## Phase 1: Setup (shared infrastructure)

**Purpose**: The `backend/` + `frontend/` monorepo skeleton, local dependencies, secrets, CI gates.

- [X] T001 Create the Maven project in `backend/pom.xml` (Java 21, Spring Boot 4.0: web, data-jpa, validation, actuator; `flyway-core` + `flyway-sqlserver`; `mssql-jdbc`; `com.stripe:stripe-java`; `io.micrometer:micrometer-registry-prometheus` (version from the Boot BOM, R-26); springdoc-openapi of the line compatible with Boot 4; test: `spring-boot-starter-test`, `spring-boot-testcontainers`, `testcontainers` `mssqlserver` and `junit-jupiter`, `archunit-junit5`, `wiremock-standalone`, AssertJ) together with the wrapper `backend/mvnw`, `backend/mvnw.cmd`, `backend/.mvn/wrapper/maven-wrapper.properties`; add `backend/osv-scanner.toml` (empty, with a comment on the exception format: vulnerability identifier + justification)
- [X] T002 Create the entry class `backend/src/main/java/com/project/custom/ShopApplication.java` (`@SpringBootApplication`, `@ConfigurationPropertiesScan`) and the empty BC packages `shared`, `catalog`, `cart`, `order`, `payment` per the structure in plan.md
- [X] T003 [P] Initialize the frontend in `frontend/package.json`, `frontend/vite.config.ts` (port 5173, proxy `/api` and `/images` → `http://localhost:8080`), `frontend/tsconfig.json` (`strict: true`), `frontend/index.html`; dependencies: React 19, React Router, TanStack Query, React Hook Form, Zod, `openapi-fetch`; dev: `openapi-typescript`, Vitest, Testing Library, MSW, Playwright, ESLint, Prettier
- [X] T004 [P] Create `compose.yaml` in the root directory: service `sqlserver` (`mcr.microsoft.com/mssql/server:2022-latest`, `MSSQL_SA_PASSWORD=${DB_PASSWORD}`, healthcheck, a one-off container/script creating the `shop` database) and service `stripe-cli` in the `stripe` profile (`stripe/stripe-cli`, `listen --forward-to http://host.docker.internal:8080/api/payments/stripe/webhook`, key from `STRIPE_SECRET_KEY`)
- [X] T005 [P] Extend `.env.example` with empty `STRIPE_SECRET_KEY` (description: `sk_test_…` only), `STRIPE_WEBHOOK_SECRET` (`whsec_…` from `stripe listen`), `DB_PASSWORD`, `APP_BASE_URL=http://localhost:5173`, optional `STRIPE_API_BASE`; in a comment: "To work on US1–US3 without a Stripe account, dummies are enough: `STRIPE_SECRET_KEY=sk_test_dummy`, `STRIPE_WEBHOOK_SECRET=whsec_dummy` (payment will return 503)"; confirm that `.env` is in `.gitignore`
- [X] T006 [P] Add the gitleaks hook in `.pre-commit-config.yaml` (Principle I)
- [X] T007 [P] Add backend run scripts that read `.env` explicitly (no `spring-dotenv`, R-20) in `scripts/run-backend.ps1` and `scripts/run-backend.sh` (`local` profile)
- [X] T008 [P] Configure frontend linting and formatting in `frontend/eslint.config.js` and `frontend/.prettierrc`; scripts `lint`, `typecheck`, `test`, `e2e`, `api:types` in `frontend/package.json`
- [X] T009 Copy the contract `specs/001-shop-browse-cart-checkout/contracts/openapi.yaml` to `backend/src/main/resources/openapi/shop-api.yaml` and generate the frontend types `frontend/src/api/schema.d.ts` with the `api:types` script (`openapi-typescript`, with a `--check` mode comparing against the contract)
- [X] T010 Create the CI pipeline in `.github/workflows/ci.yml`: `./backend/mvnw -f backend/pom.xml verify`, `npm --prefix frontend ci` + `lint` + `typecheck` + `test` + `api:types -- --check`, `gitleaks detect`, dependency audit: `osv-scanner scan source --lockfile backend/pom.xml --lockfile frontend/package-lock.json` (fail at CVSS ≥ 7.0, exceptions only through `backend/osv-scanner.toml` with a justification) and `npm --prefix frontend audit --audit-level=high`, an E2E job run only when the `STRIPE_TEST_*` secrets are set

---

## Phase 2: Foundational (blocking prerequisites)

**Purpose**: Shared kernel, configuration with secret validation, error handling, guest
identification, test infrastructure and the SPA shell — required by ALL stories.

**⚠️ CRITICAL**: No user story can start before this phase is complete.

### Foundation tests

- [X] T011 [P] Unit test for `Money` (minor ≥ 0, currency PLN, `plus`, `times(int)`, equality by value, rejection of negative values) in `backend/src/test/java/com/project/custom/shared/domain/MoneyTest.java`
- [X] T012 [P] ArchUnit architecture test (R-03): (1) classes of BC A import nothing from BC B except its root package (`*Facade`, public DTO/event records); `shared.domain..` and `shared.api..` (only `GuestId`/resolver) are allowed for all BCs; `shared.infrastructure..` — only for `shared` and Spring configuration, with two exceptions, both limited to the infrastructure layer of a BC: `shared.infrastructure.metrics.AfterCommit` may be used from `<bc>.infrastructure.metrics..` (R-27), and `shared.infrastructure.config.AppProperties` from `<bc>.infrastructure..` (the Stripe adapter builds `success_url`/`cancel_url` from `baseUrl`, T102); `application..` and `domain..` never use `shared.infrastructure..` (PII masking lives in `shared.domain`, T019); (2) `..domain..` does not depend on `org.springframework..`, `jakarta.persistence..`, `com.stripe..`; (3) no field `@Autowired`; (4) no dependency on `ApplicationContext`; (5) `com.stripe..` used only in `payment.infrastructure.stripe..`; (6) classes from `<bc>.application..` and `<bc>.infrastructure..` are used only by classes of the same BC; (7) `..application..` does not depend on `..infrastructure..` (DIP); (8) `io.micrometer..` imported only from `..infrastructure..` (R-27) — in `backend/src/test/java/com/project/custom/ArchitectureTest.java`
- [X] T013 [P] Application startup test: missing `shop.stripe.secret-key`, an `sk_live_…` key or a `webhook-secret` without the `whsec_` prefix → the context does not start, with the message "Missing required configuration: shop.stripe.secret-key" and WITHOUT the secret value in the exception/log; plus a case that loads the real `application.yaml` with the `STRIPE_SECRET_KEY` environment variable absent → the same "Missing required configuration: shop.stripe.secret-key" message (guards the empty-default placeholder from T015) — in `backend/src/test/java/com/project/custom/payment/infrastructure/stripe/StripePropertiesValidationTest.java` (`ApplicationContextRunner`)
- [ ] T014 [P] Guest filter test: the first `/api/cart` request sets the `shop_guest` cookie (UUIDv4, `HttpOnly`, `SameSite=Lax`, `Path=/`, `Max-Age=2592000`, `Secure` outside the `local` profile), a subsequent request with the cookie keeps the same `GuestId`, an invalid UUID in the cookie → a new cookie, `/api/products` requests do not get the cookie — in `backend/src/test/java/com/project/custom/shared/api/GuestIdFilterTest.java`

### Foundation implementation

- [X] T015 Create `backend/src/main/resources/application.yaml` (datasource with `${DB_URL}`/`${DB_USER}`/`${DB_PASSWORD}` without defaults for secrets; `spring.jpa.hibernate.ddl-auto=validate`; `spring.jpa.open-in-view=false`; Flyway `classpath:db/migration`; `shop.stripe.secret-key=${STRIPE_SECRET_KEY:}`, `shop.stripe.webhook-secret=${STRIPE_WEBHOOK_SECRET:}` (empty default on purpose — an unresolved placeholder would be bound as the literal `${…}` and fail the format check instead of reporting the missing variable, R-20), `connect-timeout=5s`, `read-timeout=10s`, `api-base=${STRIPE_API_BASE:https://api.stripe.com}` — no `max-network-retries`, because the adapter retries (R-13); `shop.app.base-url=${APP_BASE_URL}`; springdoc `api-docs` enabled, Swagger UI disabled; `spring.application.name=shop`; Actuator per R-26: `management.server.port=8081`, `management.endpoints.web.exposure.include=health,prometheus`, `management.endpoint.health.probes.enabled=true`, `management.endpoint.health.show-details=never`, `management.metrics.tags.application=${spring.application.name}`, `management.metrics.distribution.percentiles-histogram.http.server.requests=true`, `management.metrics.distribution.slo.http.server.requests=100ms,300ms,500ms,1s,2s`; `logging.pattern.level=%5p [%X{requestId:-}]` (R-32)) and `backend/src/main/resources/application-local.yaml` (Flyway additionally `classpath:db/seed`, Swagger UI enabled, cookie without `Secure`)
- [X] T016 [P] Implement the `Money` VO (`long minor ≥ 0`, `Currency currency = PLN`, `plus`, `times(int)`, no `double`/`float`) in `backend/src/main/java/com/project/custom/shared/domain/Money.java`
- [X] T017 [P] Implement the `GuestId` VO (UUID, never empty, `fromString` with validation) in `backend/src/main/java/com/project/custom/shared/domain/GuestId.java`
- [X] T018 [P] Implement `AppProperties` (`@ConfigurationProperties("shop.app")`, `@Validated`, `@NotNull URI baseUrl`, cookie parameters) in `backend/src/main/java/com/project/custom/shared/infrastructure/config/AppProperties.java`
- [X] T019 [P] Implement masking of personal data for logs (`d***@g***.com`, address → abbreviation; never keys) as a framework-free `public final class PiiMasking` with static pure functions (no state) in `backend/src/main/java/com/project/custom/shared/domain/PiiMasking.java` — in `shared.domain`, because application services of other BCs use it (T112) and ArchUnit rules 1 and 7 forbid them `shared.infrastructure..`
- [X] T020 [P] Implement `StripeProperties` as `@ConfigurationProperties("shop.stripe") record StripeProperties(String secretKey, String webhookSecret, Duration connectTimeout, Duration readTimeout, @DefaultValue("https://api.stripe.com") URI apiBase)` (R-20) — **without** a `maxNetworkRetries` field: retrying is the adapter's policy, and the SDK always gets `0` (R-13, T102). Validation in the compact constructor throws `IllegalArgumentException` with messages WITHOUT values: missing/empty `secretKey` → "Missing required configuration: shop.stripe.secret-key"; a `secretKey` not matching `^sk_test_.+` (e.g. `sk_live_…`) → "shop.stripe.secret-key must be a test key (sk_test_…)"; likewise `webhookSecret` (`^whsec_.+`, "Missing required configuration: shop.stripe.webhook-secret"); `connectTimeout`/`readTimeout` null or ≤ 0 → an error with the property name. Do NOT use `@Validated` + `@Pattern`: a Bean Validation error contains `rejected value [sk_live_…]`, which breaks T013. Override `toString()` masking both secrets (`sk_test_***`, `whsec_***`) — the record's default `toString()` would print them to the log. Add `StripePropertiesFailureAnalyzer` (`AbstractFailureAnalyzer<BindException>` for the `StripeProperties` target, `@Order(Ordered.HIGHEST_PRECEDENCE)`, registered in `backend/src/main/resources/META-INF/spring.factories`), which reports only the property name and the cause message — the standard `BindFailureAnalyzer` prints `Value: "…"`. Files: `backend/src/main/java/com/project/custom/payment/infrastructure/stripe/StripeProperties.java`, `backend/src/main/java/com/project/custom/payment/infrastructure/stripe/StripePropertiesFailureAnalyzer.java`
- [ ] T021 Implement the `GuestIdFilter` filter (`shop_guest` cookie per R-08, only the `/api/cart*` and `/api/orders*` paths, renewing `Max-Age` on a cart change, storing `GuestId` in a request attribute) in `backend/src/main/java/com/project/custom/shared/api/GuestIdFilter.java` and the controller argument resolver in `backend/src/main/java/com/project/custom/shared/api/GuestIdArgumentResolver.java`, registered in `backend/src/main/java/com/project/custom/shared/api/WebConfig.java` (which also maps the `/images/**` resources → `classpath:/static/images/`)
- [ ] T022 Implement global RFC 9457 error handling (`ProblemDetail` + the fields `code` and `errors[] = {field, message}`; codes per the contract: `VALIDATION_ERROR`, `NOT_FOUND`, `QUANTITY_EXCEEDS_LIMIT`, `PRODUCT_UNAVAILABLE`, `SUMMARY_OUTDATED`, `CART_NOT_ORDERABLE`, `ORDER_ALREADY_PAID`, `PAYMENT_UNAVAILABLE`, `CONCURRENCY_CONFLICT`, `INTERNAL_ERROR`; `MethodArgumentNotValidException`/`HttpMessageNotReadableException`/parameter type error → `400 VALIDATION_ERROR`; `OptimisticLockingFailureException` → `409 CONCURRENCY_CONFLICT`; no secrets or personal data in responses) in `backend/src/main/java/com/project/custom/shared/api/GlobalExceptionHandler.java` and `backend/src/main/java/com/project/custom/shared/api/ErrorCode.java`
- [ ] T023 Create integration test support: `backend/src/test/java/com/project/custom/support/IntegrationTest.java` (annotation/base class `@SpringBootTest` + a shared `MSSQLServerContainer` with `@ServiceConnection`, cleaning tables between tests) and `backend/src/test/resources/application-test.yaml` (test `sk_test_dummy`, a `whsec_test_…` generated for tests, `APP_BASE_URL=http://localhost:5173`, `api-base` set by the tests, `management.server.port: 0` — a random management port, so tests do not collide with a local backend on `8081`)
- [ ] T024 [P] Create the API client `frontend/src/api/client.ts` (`openapi-fetch` with the `schema.d.ts` types, `credentials: 'same-origin'`, parsing `application/problem+json` into the `Problem` type with `code` and `errors[]`)
- [ ] T025 [P] Create `frontend/src/shared/formatPln.ts` (`Intl.NumberFormat('pl-PL', {style: 'currency', currency: 'PLN'})` from grosze) and the test `frontend/tests/unit/shared/formatPln.test.ts`
- [ ] T026 [P] Create the toast and banner system in `frontend/src/shared/toast/ToastProvider.tsx`, `frontend/src/shared/toast/useToast.ts` and `frontend/src/shared/Banner.tsx` (accessible: `role="status"`/`role="alert"`)
- [ ] T027 Create the application shell: `frontend/src/main.tsx`, `frontend/src/app/router.tsx` (`createBrowserRouter`, routes `/`, `/product/:id`, `/cart`, `/checkout`, `/orders/:number`, `*` → `frontend/src/app/NotFoundPage.tsx`), `frontend/src/app/queryClient.ts`, `frontend/src/app/Layout.tsx` with the header (logo/link `/`, a slot for the search box and the cart counter), responsive base styles from 360 px in `frontend/src/app/Layout.module.css`, and the Polish UI copy file `frontend/src/i18n/pl.json` with a typed `t(key, params?)` helper in `frontend/src/i18n/t.ts` (components never hard-code UI text)
- [ ] T028 [P] Configure frontend unit tests: `frontend/vitest.config.ts`, `frontend/tests/unit/setup.ts` (Testing Library + MSW), `frontend/tests/unit/msw/handlers.ts`, `frontend/tests/unit/renderWithProviders.tsx` (router + QueryClient + toasts)
- [ ] T029 [P] Configure Playwright in `frontend/playwright.config.ts` (baseURL `http://localhost:5173`, desktop and mobile 360 px projects) and the helpers `frontend/tests/e2e/fixtures.ts` (cart reset through a new browser context, a helper that changes a product's price/stock through SQL in the E2E database)

**Checkpoint**: `mvnw verify` passes (ArchitectureTest, secret validation, guest filter), `npm test` passes, the SPA starts with an empty layout.

---

## Phase 3: User Story 1 — Browsing and Searching Products (Priority: P1) 🎯 MVP

**Goal**: The customer browses the catalog: a paginated list (24/page), categories, case- and
diacritic-insensitive search, price filter, sorting — all in the URL; a product page with
description, images, price and availability (FR-001–FR-005).

**Independent Test**: On a seed of ≥ 500 products: `/` shows 24 products + categories; selecting a
category and searching for `LODZ` finds "łódź"; the 50–200 PLN filter + "price descending" changes
the URL and the results; a copied URL gives the same results; the phrase `%_[zzz` → "No results";
page 2 keeps the filters; a product page with stock 2 → "Last items" (quickstart 1.1–1.8). No cart.

### Tests for User Story 1 ⚠️ (first, must fail)

- [ ] T030 [P] [US1] Unit test for `Product`: availability status (`!active` or `stock = 0` → `UNAVAILABLE`; `1 ≤ stock ≤ 3` → `LOW_STOCK`; `stock > 3` → `AVAILABLE`), `maxPurchasable() = min(stock, 99)`, `canDecreaseStock(n)`, `decreaseStock(n)` throws when `n > stock` — in `backend/src/test/java/com/project/custom/catalog/domain/ProductTest.java`
- [ ] T031 [P] [US1] Unit test for `SearchCriteria`: phrase `trim()` and truncation to 100 characters, escaping the characters `%`, `_`, `[`, `\` into a `LIKE … ESCAPE '\'` pattern, `minPrice ≤ maxPrice` (otherwise swapped), `page ≥ 0`, `size ∈ [1, 48]` (default 24), sorting from the allow-list `PRICE_ASC | PRICE_DESC | NAME` — in `backend/src/test/java/com/project/custom/catalog/domain/SearchCriteriaTest.java`
- [ ] T032 [P] [US1] Integration test for `CatalogService` + `GET /api/categories`, `GET /api/products`, `GET /api/products/{id}` (MockMvc): the list returns only `active = 1` with the main image (`display_order 0`); `category` filter (slug); `q=LODZ` finds "Łódź …" (collation `Polish_100_CI_AI`); `q=%_[zzz` → an empty page without an error; `minPrice`/`maxPrice` in grosze; `sort=price_desc|price_asc|name_asc` with the stable secondary key `id`; pagination `page`/`size`, `size=49` or `sort=xyz` → `400 VALIDATION_ERROR`; a non-existent or inactive product page → `404 NOT_FOUND` — in `backend/src/test/java/com/project/custom/catalog/application/CatalogServiceIT.java`
- [ ] T033 [P] [US1] Unit test for the URL filter state hook: reading/writing `category`, `q`, `minPrice`/`maxPrice` (PLN in the UI ↔ grosze in the URL/API), `sort` (default `name_asc` omitted from the URL), `page` (URL from 1 ↔ API from 0); changing a filter resets `page` to 1, changing the page keeps the filters — in `frontend/tests/unit/catalog/useUrlFilters.test.tsx`
- [ ] T034 [P] [US1] Component test for `CatalogPage` (MSW): tiles with image, name, price and availability label; category navigation; the "No results" state with a "Clear filters" button navigating to `/` — in `frontend/tests/unit/catalog/CatalogPage.test.tsx`
- [ ] T035 [P] [US1] E2E test of the browsing path (quickstart 1.1–1.8) in `frontend/tests/e2e/browsing.spec.ts`

### Implementation for User Story 1

- [ ] T036 [US1] Create the migration `backend/src/main/resources/db/migration/V1__catalog.sql`: `category(id BIGINT IDENTITY PK, name NVARCHAR(80) NOT NULL UNIQUE, slug VARCHAR(80) NOT NULL UNIQUE, display_order INT)`; `product(id BIGINT IDENTITY PK, name NVARCHAR(200) COLLATE Polish_100_CI_AI NOT NULL, description NVARCHAR(4000), price_minor BIGINT NOT NULL CHECK (price_minor > 0), category_id BIGINT NOT NULL FK → category, stock INT NOT NULL CHECK (stock >= 0), active BIT NOT NULL, version BIGINT NOT NULL)`; `product_image(product_id BIGINT FK → product, display_order INT CHECK (display_order >= 0), url NVARCHAR(300) NOT NULL, alt NVARCHAR(200) NOT NULL, PK (product_id, display_order))`; indexes `product(category_id, active, price_minor)`, `product(active, price_minor)`, `product(active, name)`
- [ ] T037 [P] [US1] Create the seed `backend/src/main/resources/db/seed/R__seed_catalog.sql` (a dozen or so categories with `[a-z0-9-]+` slugs; ≥ 500 products generated with a recursive CTE, each with ≥ 1 image; fixed products for the scenarios: a name containing "łódź", a product with `stock = 2`, `stock = 3`, `stock = 5`, `stock = 0`, a product with `active = 0`, prices in the 50–200 PLN range; product and category names may be Polish — seed data) and a dozen or so images in `backend/src/main/resources/static/images/`
- [ ] T038 [P] [US1] Create the domain types `CategoryId`, `Category`, `ProductId`, `ProductImage`, `AvailabilityStatus` in `backend/src/main/java/com/project/custom/catalog/domain/` (no framework annotations)
- [ ] T039 [US1] Implement the `Product` aggregate (`availabilityStatus()`, `maxPurchasable()`, `canDecreaseStock(n)`, `decreaseStock(n)`) in `backend/src/main/java/com/project/custom/catalog/domain/Product.java` (depends on T038)
- [ ] T040 [P] [US1] Implement the `SearchCriteria` VO and the `ProductSort` enum per the rules from T031 in `backend/src/main/java/com/project/custom/catalog/domain/SearchCriteria.java` and `backend/src/main/java/com/project/custom/catalog/domain/ProductSort.java`
- [ ] T041 [US1] Define the ports `ProductRepository` (`search(SearchCriteria) → ResultPage<ProductListItem>`, `findActive(ProductId)`) and `CategoryRepository` (`findAll()` by `display_order`, `findBySlug`) in `backend/src/main/java/com/project/custom/catalog/domain/ProductRepository.java` and `backend/src/main/java/com/project/custom/catalog/domain/CategoryRepository.java` (list read model `ProductListItem`, `ResultPage` in the same package)
- [ ] T042 [US1] Create the JPA entities `CategoryJpaEntity`, `ProductJpaEntity` (`@Version version`), `ProductImageJpaEntity` and `CategoryJpaRepository`, `ProductJpaRepository` in `backend/src/main/java/com/project/custom/catalog/infrastructure/persistence/`
- [ ] T043 [US1] Implement the adapters `ProductRepositoryAdapter` (a query with `name LIKE :pattern ESCAPE '\'` with a bound parameter, filter `active = 1`, sort allow-list + `id`, a DTO projection with the main image in a single query — no N+1, R-24) and `CategoryRepositoryAdapter` in `backend/src/main/java/com/project/custom/catalog/infrastructure/persistence/` with JPA ↔ domain mapping in `CatalogPersistenceMapper.java`
- [ ] T044 [US1] Implement `CatalogService` (`listCategories`, `search(criteria)`, `productDetails(productId)` → `NotFound` for inactive/non-existent) in `backend/src/main/java/com/project/custom/catalog/application/CatalogService.java`
- [ ] T045 [US1] Implement `CatalogController` (`GET /api/categories`, `GET /api/products` with the parameters `category`, `q`, `minPrice`, `maxPrice`, `sort ∈ {name_asc, price_asc, price_desc}`, `page ≥ 0`, `size 1..48` default 24; `GET /api/products/{id}`) mapping to the contract schemas `Category`, `ProductSearchResult`, `ProductSummary`, `ProductDetails`, in `backend/src/main/java/com/project/custom/catalog/api/CatalogController.java` and `backend/src/main/java/com/project/custom/catalog/api/CatalogApiMapper.java`
- [ ] T046 [P] [US1] Create the query hooks `useCategories`, `useProducts(filters)`, `useProduct(id)` (TanStack Query) in `frontend/src/features/catalog/api.ts`
- [ ] T047 [P] [US1] Implement `useUrlFilters` per `contracts/frontend-routes.md` in `frontend/src/features/catalog/useUrlFilters.ts`
- [ ] T048 [P] [US1] Create the components `AvailabilityLabel.tsx` ("Available" / "Last items" / "Unavailable" derived only from the API `status` field), `ProductTile.tsx` (image with `loading="lazy"` and a fixed aspect ratio, name, price, label, link to `/product/:id`), `Pagination.tsx` in `frontend/src/features/catalog/`
- [ ] T049 [US1] Create `Filters.tsx` (category navigation, price from–to in PLN, sorting) and `CatalogPage.tsx` (2/3/4-column grid, empty state "No results" + "Clear filters", loading/error states) in `frontend/src/features/catalog/` with styles in `CatalogPage.module.css`
- [ ] T050 [US1] Create `ProductPage.tsx` (name, image gallery, description, price, availability label, 404 → "Product not found") in `frontend/src/features/catalog/ProductPage.tsx`
- [ ] T051 [US1] Wire up the header search box (submitted with Enter/button → `/?q=…`, resets `page`) in `frontend/src/app/SearchBox.tsx` and `frontend/src/app/Layout.tsx`; register the routes `/` and `/product/:id` in `frontend/src/app/router.tsx`

**Checkpoint**: US1 works on its own — quickstart 1.1–1.8 and `browsing.spec.ts` green.

---

## Phase 4: User Story 2 — Adding Products to the Cart (Priority: P1)

**Goal**: A guest adds a product from the product page (with a quantity) or from the list (1 item);
lines are merged; adding above `min(stock, 99)` is blocked with information about the maximum; an
unavailable product has a disabled button; the cart survives a refresh (30-day cookie); the header
counter (FR-006–FR-008, FR-012, FR-013).

**Independent Test**: Add 2 items from the product page → confirmation and counter +2; add the same
product from the list → one line with quantity 3; a product with stock 3 and 2 in the cart → adding 2
is blocked with "You can add at most 1 item"; an unavailable product → disabled button; a refresh
keeps the cart (quickstart 2.1–2.5).

### Tests for User Story 2 ⚠️

- [ ] T052 [P] [US2] Unit test for the `Cart` aggregate — adding: `n ∈ 1..99`, an existing line → `quantity += n` (one line, FR-007), a result > `maxPurchasable` → `QuantityExceedsLimit(max)` with no state change (US2-3), a product `UNAVAILABLE` → `ProductUnavailable` (US2-4), `priceWhenAdded` stored for information only, `updatedAt` updated — in `backend/src/test/java/com/project/custom/cart/domain/CartTest.java`
- [ ] T053 [P] [US2] Unit test for cart pricing `CartPricing` (line total = current price × quantity, `itemCount` = sum of quantities, `total` = sum of the line totals of available lines) in `backend/src/test/java/com/project/custom/cart/domain/CartPricingTest.java`
- [ ] T054 [P] [US2] Integration test for `CartService` + `GET /api/cart`, `POST /api/cart/lines` (MockMvc): the first GET without a cookie → an empty cart and a new `shop_guest`; adding 2 items → a line with the catalog price (a price in the request body is ignored, FR-010); adding again merges; exceeding the limit → `409 QUANTITY_EXCEEDS_LIMIT` with `maxQuantity`; an unavailable/inactive product → `409 PRODUCT_UNAVAILABLE`; a non-existent one → `404`; `quantity` `0`, `-1`, `100`, `1.5`, `"abc"` → `400 VALIDATION_ERROR`; the cart read again with the same cookie keeps its contents; another cookie → another cart — in `backend/src/test/java/com/project/custom/cart/application/CartServiceIT.java`
- [ ] T055 [P] [US2] Component test for adding (MSW): on the product page, choosing a quantity and "Add to cart" → a confirmation toast and a refreshed counter; `409 QUANTITY_EXCEEDS_LIMIT` → toast "You can add at most N items"; status `UNAVAILABLE` → a button with `aria-disabled` and an explanation — in `frontend/tests/unit/cart/AddToCart.test.tsx`
- [ ] T056 [P] [US2] E2E test of the adding path (quickstart 2.1–2.5, including a page refresh) in `frontend/tests/e2e/cart-add.spec.ts`

### Implementation for User Story 2

- [ ] T057 [US2] Create the migration `backend/src/main/resources/db/migration/V2__cart.sql`: `cart(id UNIQUEIDENTIFIER PK, guest_id UNIQUEIDENTIFIER NOT NULL UNIQUE, updated_at DATETIMEOFFSET(3) NOT NULL, version BIGINT NOT NULL)`; `cart_line(cart_id UNIQUEIDENTIFIER FK → cart, product_id BIGINT NOT NULL /* no FK — other BC */, quantity INT NOT NULL CHECK (quantity BETWEEN 1 AND 99), price_when_added_minor BIGINT NOT NULL, added_at DATETIMEOFFSET(3) NOT NULL, PK (cart_id, product_id))`; index `cart_line(cart_id)`
- [ ] T058 [P] [US2] Add the catalog facade for other BCs: `public interface CatalogQueryFacade` (`getForPricing(Set<Long> ids) → Map<Long, PricingProductDto>`) and `public record PricingProductDto(productId, name, imageUrl, priceMinor, stock, active, status, maxQuantity)` in `backend/src/main/java/com/project/custom/catalog/CatalogQueryFacade.java` and `backend/src/main/java/com/project/custom/catalog/PricingProductDto.java`; package-private implementation (a single query by the set of ids, also returns inactive ones) in `backend/src/main/java/com/project/custom/catalog/application/CatalogQueryFacadeImpl.java`
- [ ] T059 [P] [US2] Create the cart domain types: `CartId`, `CartLine` (`productId`, `quantity 1..99`, `priceWhenAdded`, `addedAt`), `CartProduct` (a view of the product in the cart's language: id, price, status, max), the exceptions `QuantityExceedsLimit(max)` and `ProductUnavailable` in `backend/src/main/java/com/project/custom/cart/domain/`
- [ ] T060 [US2] Implement the `Cart` aggregate with the operation `add(CartProduct, n)` per the rules in data-model.md in `backend/src/main/java/com/project/custom/cart/domain/Cart.java` (depends on T059)
- [ ] T061 [US2] Implement the read model `PricedCart` / `PricedLine` and the domain service `CartPricing` (for now: current price, line total, status, `maxQuantity`, `itemCount`, `total`) in `backend/src/main/java/com/project/custom/cart/domain/CartPricing.java` and `backend/src/main/java/com/project/custom/cart/domain/PricedCart.java`
- [ ] T062 [US2] Define the port `CartRepository` (`findByGuest(GuestId)`, `save(Cart)`) in `backend/src/main/java/com/project/custom/cart/domain/CartRepository.java`; create `CartJpaEntity` (`@Version`), `CartLineJpaEntity`, `CartJpaRepository` and a package-private `CartRepositoryAdapter` with mapping in `backend/src/main/java/com/project/custom/cart/infrastructure/persistence/`
- [ ] T063 [US2] Implement `CartService` (`price(GuestId)` through `CatalogQueryFacade`, `add(GuestId, productId, quantity)` — the cart is created lazily, a single transaction) in `backend/src/main/java/com/project/custom/cart/application/CartService.java`
- [ ] T064 [US2] Implement `CartController` (`GET /api/cart`, `POST /api/cart/lines` with `AddLineRequest{productId, quantity: @NotNull @Min(1) @Max(99) Integer}`) mapping to the contract's `Cart` schema, and the mapping of domain exceptions to `409 QUANTITY_EXCEEDS_LIMIT`/`409 PRODUCT_UNAVAILABLE` (with `maxQuantity`) in `backend/src/main/java/com/project/custom/cart/api/CartController.java` and `backend/src/main/java/com/project/custom/cart/api/CartApiMapper.java`
- [ ] T065 [US2] Create the hooks `useCart` (query `['cart']`) and `useAddToCart` (mutation + `invalidateQueries(['cart'])`, handling `409` → toast) in `frontend/src/features/cart/useCart.ts`
- [ ] T066 [US2] Create `CartCounter.tsx` (link `/cart` with `itemCount`) in `frontend/src/features/cart/CartCounter.tsx` and put it in the header `frontend/src/app/Layout.tsx`
- [ ] T067 [US2] Create `AddToCartButton.tsx` (a variant with quantity selection `1..maxQuantity` for the product page and a "1 item" variant for the list; disabled with `aria-disabled` for `UNAVAILABLE`) in `frontend/src/features/cart/AddToCartButton.tsx` and use it in `frontend/src/features/catalog/ProductPage.tsx` and `frontend/src/features/catalog/ProductTile.tsx`
- [ ] T068 [US2] Create a read-only cart view `CartPage.tsx` (line list: image, name, unit price, quantity, line total; total; empty state) in `frontend/src/features/cart/CartPage.tsx` and register the `/cart` route in `frontend/src/app/router.tsx`

**Checkpoint**: US1 + US2 work independently — quickstart 2.1–2.5 and `cart-add.spec.ts` green.

---

## Phase 5: User Story 3 — Viewing and Editing the Cart (Priority: P1)

**Goal**: Changing a quantity (capped at the stock, with a message), removing lines, clearing the
cart, totals calculated by the backend, notices about price changes and loss of availability,
blocking checkout (FR-009–FR-011, FR-014 for the cart part).

**Independent Test**: A cart with 2 products: quantity 1 → 3 recalculates the totals; quantity 50
with stock 5 → 5 + "Only 5 items available"; 0/"Remove" removes the line; "Clear cart" → "Your cart
is empty"; a price change in the database → struck-through old price + banner and "I understand";
`stock = 0` → the line is "Unavailable", the checkout button disabled; `quantity` `1.5`/`-1`/`"abc"`
→ 400 (quickstart 3.1–3.8).

### Tests for User Story 3 ⚠️

- [ ] T069 [P] [US3] Extend the `Cart` test with editing: `changeQuantity(productId, 0)` removes the line; `n > max` → sets `max` and returns `QuantityCapped(max)`; `n < 0` → an error; `remove` of a non-existent line → no-op; `clear()` removes everything; `acceptPrices(prices)` overwrites `priceWhenAdded` — in `backend/src/test/java/com/project/custom/cart/domain/CartTest.java`
- [ ] T070 [P] [US3] Extend the `CartPricing` test: `priceChanged` + `previousPrice` when the catalog price ≠ `priceWhenAdded`; an inactive/`stock = 0`/removed product → `status = UNAVAILABLE` and excluded from the total; `quantityExceedsStock`; `canPlaceOrder = non-empty ∧ every line available ∧ quantity ≤ maxQuantity`; `problems[]` with the codes `PRICE_CHANGED`, `PRODUCT_UNAVAILABLE`, `QUANTITY_EXCEEDS_STOCK` — in `backend/src/test/java/com/project/custom/cart/domain/CartPricingTest.java`
- [ ] T071 [P] [US3] Integration test for cart editing (MockMvc): `PUT /api/cart/lines/{productId}` (change, capping at stock with `messages[code=QUANTITY_CAPPED]`, `0` removes, `1.5`/`-1`/`"abc"`/`100` → `400 VALIDATION_ERROR`, no line → `404`); `DELETE /api/cart/lines/{productId}` (idempotent); `DELETE /api/cart`; `POST /api/cart/accept-prices` clears the `priceChanged` flags; a price/stock change in the database is reflected in `GET /api/cart`; a concurrent change from two "tabs" → `409 CONCURRENCY_CONFLICT` — in `backend/src/test/java/com/project/custom/cart/application/CartEditingIT.java`
- [ ] T072 [P] [US3] Component test for `CartPage` (MSW) for the states from `contracts/frontend-routes.md`: empty cart; `priceChanged` (struck-through price, banner, "I understand" → `accept-prices`); `UNAVAILABLE` (greyed out, only "Remove"); `canPlaceOrder=false` (disabled button with a hint); `QUANTITY_CAPPED` → toast "Only N items available" — in `frontend/tests/unit/cart/CartPage.test.tsx`
- [ ] T073 [P] [US3] E2E test of the cart editing path (quickstart 3.1–3.7, price/stock changes through the SQL helper from `fixtures.ts`) in `frontend/tests/e2e/cart-edit.spec.ts`

### Implementation for User Story 3

- [ ] T074 [US3] Add the operations `changeQuantity`, `remove`, `clear`, `acceptPrices` and the result `QuantityCapped(max)` to the `Cart` aggregate in `backend/src/main/java/com/project/custom/cart/domain/Cart.java` and `backend/src/main/java/com/project/custom/cart/domain/QuantityCapped.java`
- [ ] T075 [US3] Extend `CartPricing`/`PricedCart` with `priceChanged`, `previousPrice`, `quantityExceedsStock`, `canPlaceOrder`, `problems[]` in `backend/src/main/java/com/project/custom/cart/domain/CartPricing.java`
- [ ] T076 [US3] Add the use cases `changeQuantity`, `remove`, `clear`, `acceptPrices` to `CartService` (each returns a freshly priced summary + messages) in `backend/src/main/java/com/project/custom/cart/application/CartService.java`
- [ ] T077 [US3] Add the endpoints `PUT /api/cart/lines/{productId}` (`ChangeQuantityRequest{quantity: @NotNull @Min(0) @Max(99) Integer}`), `DELETE /api/cart/lines/{productId}`, `DELETE /api/cart`, `POST /api/cart/accept-prices` and the `messages[]` mapping in `backend/src/main/java/com/project/custom/cart/api/CartController.java` and `backend/src/main/java/com/project/custom/cart/api/CartApiMapper.java`
- [ ] T078 [US3] Add the mutations `useChangeQuantity`, `useRemoveLine`, `useClearCart`, `useAcceptPrices` (each invalidates `['cart']`, a toast for `QUANTITY_CAPPED`) in `frontend/src/features/cart/useCart.ts`
- [ ] T079 [US3] Create `CartLineItem.tsx` (a quantity field validating an integer 0–99, "Remove", current price + struck-through `previousPriceMinor`, the "Unavailable" state) in `frontend/src/features/cart/CartLineItem.tsx`
- [ ] T080 [US3] Extend `CartPage.tsx` with editing: the "Price has changed" banner + "I understand", "Clear cart", a summary with the total from the API, a "Proceed to checkout" button (link `/checkout`, disabled when `canPlaceOrder=false`, with a hint), the empty state "Your cart is empty" + a link to the shop in `frontend/src/features/cart/CartPage.tsx` and `frontend/src/features/cart/CartPage.module.css`

**Checkpoint**: US1–US3 work independently of Stripe — quickstart 3.1–3.8 and `cart-edit.spec.ts` green.

---

## Phase 6: User Story 4 — Placing an Order and Paying Online (Priority: P1)

**Goal**: Details form + summary → price re-verification (409 on a change) → an
`AWAITING_PAYMENT` order + a Stripe Checkout session (outside the transaction, with an idempotency
key) → redirect; payment only through a verified, deduplicated webhook, which in a single
transaction decreases stock, clears the cart and writes `OrderPaidEvent` to the Outbox; a
confirmation page with polling; a failed/canceled payment does not touch the cart
(FR-014–FR-024, SC-004–SC-007).

**Independent Test**: A cart with available products → form (an invalid code `12345` and an empty
email block it) → Stripe with amount = total → card `4242…` → "Payment being verified" →
"Paid" within ≤ 30 s, counter 0, stock decreased; card `4000…0002` + return → banner, cart
untouched; a webhook retry has no effect; a forged signature → 400; someone else's number → 404;
Stripe unavailable → 503 and cart untouched (quickstart 4.1–4.11).

### Tests for User Story 4 ⚠️

- [ ] T081 [P] [US4] Unit test for the order VOs: `CustomerDetails` (valid email, ≤ 254; full name 2–100 characters), `ShippingAddress` (street 3–120; code `^\d{2}-\d{3}$`; city 2–60; `country = "PL"`), `OrderNumber` (format `ORD-[0-9A-HJKMNP-TV-Z]{10}`, a generator based on `SecureRandom`) — in `backend/src/test/java/com/project/custom/order/domain/OrderValueObjectsTest.java`
- [ ] T082 [P] [US4] Unit test for the `Order` state machine: `AWAITING_PAYMENT → PAID` (amount and currency match, stock OK; `paidAt` set), `→ NEEDS_REVIEW` (`INSUFFICIENT_STOCK`, `AMOUNT_MISMATCH`), `→ PAYMENT_FAILED`, `PAYMENT_FAILED → NEEDS_REVIEW` (`CONFIRMED_AFTER_FAILURE`), `PAID → PAID` with no effects, the rest → `IllegalStatusTransition`; `total = Σ unitPrice × quantity`, min. 1 line, lines immutable — in `backend/src/test/java/com/project/custom/order/domain/OrderTest.java`
- [ ] T083 [P] [US4] Unit test for `Payment`: `CREATED → OPEN | FAILED`, `OPEN → CONFIRMED | EXPIRED | FAILED`, `CONFIRMED` is final, a repeated confirmation is idempotent — in `backend/src/test/java/com/project/custom/payment/domain/PaymentTest.java`
- [ ] T084 [P] [US4] Create the webhook signing helper (HMAC-SHA256 with a test `whsec_test_…`, header `Stripe-Signature: t=…,v1=…`, event templates `checkout.session.completed|async_payment_succeeded|async_payment_failed|expired` with `amount_total`, `currency`, `payment_status`, `livemode`) in `backend/src/test/java/com/project/custom/support/StripeWebhookSigner.java`
- [ ] T085 [P] [US4] Contract test for the adapter against `stripe-mock` (Testcontainers `GenericContainer`): `createSession` sends `mode=payment`, `payment_method_types[]=card`, `currency=pln`, `unit_amount` in grosze, `quantity`, `customer_email`, `client_reference_id`, `metadata[orderId|paymentId|number]`, `expires_at ≈ now + 30 min`, `success_url`/`cancel_url` per the contract, header `Idempotency-Key: checkout-{paymentId}`; the assertion `Σ unit_amount × quantity == total` rejects inconsistent data; `expire` returns `EXPIRED` — in `backend/src/test/java/com/project/custom/payment/infrastructure/stripe/StripePaymentGatewayContractTest.java`
- [ ] T086 [P] [US4] Adapter failure test on WireMock (retry policy from T102): read timeout, dropped connection, `5xx`, `429` → `GatewayUnavailable` after exactly 2 retries — WireMock records exactly 3 requests, all with the same `Idempotency-Key: checkout-{paymentId}` (proof that the SDK with `maxNetworkRetries=0` adds no attempts of its own); delays of 0.5 s and 1 s recorded by a test `Sleeper` without real waiting; `503` → `200` on the second attempt → `PaymentSession` after 1 retry; `400`/`401` → exactly 1 request, no retries, a configuration error without the key in the message/log; an `expire` response "session is not open" with `status=complete` → `ALREADY_PAID` — in `backend/src/test/java/com/project/custom/payment/infrastructure/stripe/StripePaymentGatewayFailureTest.java`
- [ ] T087 [P] [US4] Integration test for `PlaceOrderService` + `POST /api/orders` (adapter pointing at `stripe-mock`/WireMock): valid data and a current summary → `201 {number, paymentUrl}`, an `AWAITING_PAYMENT` order with immutable lines, `Payment` `OPEN` with `stripe_session_id`, cart UNTOUCHED; a price/quantity/contents/availability difference → `409 SUMMARY_OUTDATED` with the current `cart` and no order; an empty cart or `canPlaceOrder=false` → `409 CART_NOT_ORDERABLE`; field errors (`postalCode=12345`, empty email) → `400 VALIDATION_ERROR` with `errors[].field`; Stripe unavailable → `503 PAYMENT_UNAVAILABLE`, order `PAYMENT_FAILED`, cart untouched; a second attempt expires the open session of the guest's previous order; a previous session `complete` → `409 ORDER_ALREADY_PAID`; no Stripe call inside an open transaction — in `backend/src/test/java/com/project/custom/order/application/PlaceOrderServiceIT.java`
- [ ] T088 [P] [US4] Integration test for the webhook `POST /api/payments/stripe/webhook` + `WebhookHandlingService`: a valid `completed/paid` → `200`, `Payment CONFIRMED`, order `PAID`, product stock decreased, the guest's cart empty, one `outbox_event` row of type `OrderPaid` (payload without the email); an invalid/missing signature → `400` and no changes; `livemode=true` → `400`; the same `event.id` 2× (also concurrently) → one processing, stock decreased once; another `event.id` for the same session → no effects; mismatched `amount_total`/`currency` → `NEEDS_REVIEW` (`AMOUNT_MISMATCH`) without a stock change; insufficient stock → `NEEDS_REVIEW` (`INSUFFICIENT_STOCK`), cart cleared, no Outbox event; `completed` with `payment_status != paid` → no change; `async_payment_succeeded` → as paid; `expired`/`async_payment_failed` → `PAYMENT_FAILED`, cart untouched; a late confirmation after a failure → `NEEDS_REVIEW`; an unknown session → `200`; another event type → `200` ignored — in `backend/src/test/java/com/project/custom/payment/application/WebhookHandlingServiceIT.java`
- [ ] T089 [P] [US4] Integration test for `GET /api/orders/{number}`: the owner (the same `shop_guest`) sees the number, lines, total and status; another cookie, no cookie or a non-existent number → `404 NOT_FOUND` (no distinction); reading does not change state — in `backend/src/test/java/com/project/custom/order/application/OrderQueryIT.java`
- [ ] T090 [P] [US4] Integration test for `CatalogCommandFacade.decreaseStock`: all or nothing with many lines, `INSUFFICIENT_STOCK` without changes, concurrent decreases of the last item → exactly one `DECREASED` — in `backend/src/test/java/com/project/custom/catalog/application/DecreaseStockIT.java`
- [ ] T091 [P] [US4] Component test for `CheckoutPage` (MSW): Zod errors next to fields with `aria-describedby`; `400` → messages per `errors[].field`; `409 SUMMARY_OUTDATED` → the summary replaced with `problem.cart` + banner; `503` → "Payment temporarily unavailable, please try again shortly"; `201` → `window.location.assign(paymentUrl)` — in `frontend/tests/unit/checkout/CheckoutPage.test.tsx`
- [ ] T092 [P] [US4] Component test for `OrderConfirmationPage` (MSW, fake timers): `AWAITING_PAYMENT` → "Payment being verified" and polling every 2 s, after 60 s "Verification is taking longer — refresh the page later"; `PAID` → "Thank you! Order paid" + invalidation of `['cart']`; `PAYMENT_FAILED` → "Payment failed" + a link to the cart; `NEEDS_REVIEW` → "Payment received — we will contact you about fulfillment"; `404` → "Order not found" — in `frontend/tests/unit/checkout/OrderConfirmationPage.test.tsx`
- [ ] T093 [P] [US4] E2E test of the payment path with Stripe test mode and `stripe listen` (card `4242 4242 4242 4242` → "Paid" within ≤ 30 s and counter 0; card `4000 0000 0000 0002` + return → banner and cart untouched; visiting `success_url` without a webhook → "Payment being verified"; form validation; a price change before "Pay" → re-confirmation) in `frontend/tests/e2e/payment.spec.ts`

### Implementation for User Story 4 — schema and shared kernel

- [ ] T094 [US4] Create the migration `backend/src/main/resources/db/migration/V3__order.sql`: `orders(id UNIQUEIDENTIFIER PK, number VARCHAR(14) NOT NULL UNIQUE, guest_id UNIQUEIDENTIFIER NOT NULL /* index */, email NVARCHAR(254) NOT NULL, full_name NVARCHAR(100) NOT NULL, street NVARCHAR(120) NOT NULL, postal_code CHAR(6) NOT NULL, city NVARCHAR(60) NOT NULL, country CHAR(2) NOT NULL, total_minor BIGINT NOT NULL, status VARCHAR(30) NOT NULL, created_at DATETIMEOFFSET(3) NOT NULL, paid_at DATETIMEOFFSET(3) NULL, review_reason NVARCHAR(200) NULL, version BIGINT NOT NULL)`; `order_line(order_id FK → orders, line_no INT, product_id BIGINT NOT NULL /* no FK */, name NVARCHAR(200) NOT NULL, unit_price_minor BIGINT NOT NULL CHECK (> 0), quantity INT NOT NULL CHECK (BETWEEN 1 AND 99), PK (order_id, line_no))`
- [ ] T095 [P] [US4] Create the migration `backend/src/main/resources/db/migration/V4__payment.sql`: `payment(id UNIQUEIDENTIFIER PK, order_id UNIQUEIDENTIFIER NOT NULL /* index, no FK */, guest_id UNIQUEIDENTIFIER NOT NULL, amount_minor BIGINT NOT NULL, stripe_session_id VARCHAR(255) NULL, stripe_payment_intent_id VARCHAR(255) NULL, payment_url NVARCHAR(1000) NULL, status VARCHAR(20) NOT NULL, created_at DATETIMEOFFSET(3) NOT NULL, confirmed_at DATETIMEOFFSET(3) NULL)` with a filtered unique index `stripe_session_id WHERE stripe_session_id IS NOT NULL`; `processed_stripe_event(event_id VARCHAR(255) PK, type VARCHAR(100) NOT NULL, processed_at DATETIMEOFFSET(3) NOT NULL)`
- [ ] T096 [P] [US4] Create the migration `backend/src/main/resources/db/migration/V5__outbox.sql`: `outbox_event(id UNIQUEIDENTIFIER PK, type NVARCHAR(100) NOT NULL, aggregate_id NVARCHAR(50) NOT NULL, payload NVARCHAR(MAX) NOT NULL, created_at DATETIMEOFFSET(3) NOT NULL, sent_at DATETIMEOFFSET(3) NULL, attempts INT NOT NULL DEFAULT 0)` with the index `(sent_at, created_at)`
- [ ] T097 [US4] Implement the Outbox (write only — dispatch belongs to the `fulfillment` feature, R-17): the port `public interface OutboxEventPublisher` (no framework annotations) in `backend/src/main/java/com/project/custom/shared/domain/outbox/OutboxEventPublisher.java`; `OutboxEventJpaEntity`, `OutboxEventJpaRepository` and a package-private `JpaOutboxEventPublisher` writing JSON in the current transaction (`Propagation.MANDATORY`) in `backend/src/main/java/com/project/custom/shared/infrastructure/outbox/`

### Implementation for User Story 4 — catalog and cart facades

- [ ] T098 [P] [US4] Add `public interface CatalogCommandFacade` (`decreaseStock(List<StockLineDto>) → StockDecreaseResult` with the values `DECREASED | INSUFFICIENT_STOCK`) and the DTO records in `backend/src/main/java/com/project/custom/catalog/`; extend the `ProductRepository` port with `lockForUpdate(Set<ProductId>) → List<Product>` (`id` order) and `saveAll(List<Product>)`; an "all or nothing" implementation (the check and `Product.decreaseStock` in the domain) in `backend/src/main/java/com/project/custom/catalog/application/CatalogCommandFacadeImpl.java`; the `WITH (UPDLOCK, ROWLOCK)` query in `ProductRepositoryAdapter` / `ProductJpaRepository` in `backend/src/main/java/com/project/custom/catalog/infrastructure/persistence/`
- [ ] T099 [P] [US4] Add `public interface CartQueryFacade` (`price(GuestId) → PricedCartDto`) and `public interface CartCommandFacade` (`clear(GuestId)` in the current transaction) with the `PricedCartDto` record in `backend/src/main/java/com/project/custom/cart/`; package-private implementations delegating to `CartService` in `backend/src/main/java/com/project/custom/cart/application/CartFacadeImpl.java`

### Implementation for User Story 4 — BC `payment`

- [ ] T100 [P] [US4] Create the payment domain: `PaymentId`, `PaymentStatus`, the `Payment` aggregate (transitions from data-model.md), the port `PaymentGateway` (`createSession(SessionRequest) → PaymentSession | GatewayUnavailable`, `expire(providerSessionId) → EXPIRED | ALREADY_PAID`), `SessionRequest`, `PaymentSession`, `GatewayUnavailable`, `ExpiryResult`, `ProviderConfirmation{eventId, type, sessionId, paymentIntentId, amount, currency, paid, livemode}`, the port `ProviderEventVerifier` (`verify(String payload, String signatureHeader) → ProviderConfirmation`, throws the domain exception `InvalidEventSignature` — Principle V, data-model.md), the ports `PaymentRepository` and `ProcessedEventRepository` (`register(eventId, type) → boolean` — `false` on a duplicate) in `backend/src/main/java/com/project/custom/payment/domain/`
- [ ] T101 [US4] Create payment persistence: `PaymentJpaEntity`, `ProcessedEventJpaEntity`, JPA repositories and package-private adapters (a duplicate `event_id` PK → `false` in the same transaction) in `backend/src/main/java/com/project/custom/payment/infrastructure/persistence/`
- [ ] T102 [US4] Implement the package-private adapter `StripePaymentGateway` implementing the `PaymentGateway` port, together with the client configuration and the retry policy (R-13, `contracts/stripe-webhook.md` §1):
  - **Client** (`StripeClientConfig`): `StripeClient.builder()` from `StripeProperties` — `setApiKey(secretKey)`, `setApiBase(apiBase)`, `setConnectTimeout(connectTimeout)`, `setReadTimeout(readTimeout)` and **`setMaxNetworkRetries(0)`** — the SDK does not retry, so that every attempt can be counted in `shop.stripe.retries` (US5).
  - **Retries in the adapter** (`StripeRetryPolicy`, package-private, constants from the contract): max. 2 retries (3 attempts), delays of 0.5 s and 1 s through an injected `Sleeper` (`Thread.sleep` by default, a fake in tests). Only these are retried: `APIConnectionException` (including a timeout — cause `SocketTimeoutException`/`HttpTimeoutException`), `StripeException` with `statusCode ≥ 500` and `RateLimitException` (`429`). Every attempt carries the same `RequestOptions.setIdempotencyKey("checkout-{paymentId}")`. After the attempts are exhausted → `GatewayUnavailable`.
  - **No retries**: `InvalidRequestException`/`AuthenticationException`/`PermissionException` → a configuration error (`500`), logged without the key and without customer data.
  - **Attempt classification**: the enums `StripeOperation {CREATE_SESSION, EXPIRE_SESSION}` and `CallOutcome {SUCCESS, ERROR, TIMEOUT}` (data-model.md); on a retry a `WARN` log with the operation, the attempt number and the outcome. Metrics are added by T141 (US5), without changing this logic.
  - **`createSession`**: parameters per the contract (`mode=payment`, `payment_method_types[]=card`, `currency=pln`, `unit_amount` in grosze, `quantity`, line name, `customer_email`, `client_reference_id`, `metadata[orderId|paymentId|number]`, `expires_at` = now + 30 min from an injected `Clock`, `success_url`/`cancel_url` from `AppProperties.baseUrl`). Before the call, the assertion `Σ unit_amount × quantity == total`.
  - **`expire`**: `sessions.expire` with the same retry policy; the error "session is not open" → `sessions.retrieve`: `status=complete` → `ALREADY_PAID`, `status=expired` → `EXPIRED`.
  - Files in `backend/src/main/java/com/project/custom/payment/infrastructure/stripe/`: `StripePaymentGateway.java`, `StripeClientConfig.java`, `StripeRetryPolicy.java`, `Sleeper.java`, `StripeOperation.java`, `CallOutcome.java`.
- [ ] T103 [US4] Implement the package-private adapter `StripeWebhookVerifier` implementing the `ProviderEventVerifier` port (`Webhook.constructEvent(payload, signature, webhookSecret)`, tolerance 300 s; `SignatureVerificationException` → `InvalidEventSignature` without the body or header in the message; an SDK-independent mapping to `ProviderConfirmation`) in `backend/src/main/java/com/project/custom/payment/infrastructure/stripe/StripeWebhookVerifier.java`
- [ ] T104 [US4] Create the BC's public API: `public interface PaymentFacade` (`start(StartPaymentDto) → StartedPaymentDto`, `expireOpen(GuestId) → ExpiryResultDto`), the DTO records and the events `public record PaymentConfirmedEvent(orderId, amountMinor, currency, confirmedAt)` and `public record PaymentFailedEvent(orderId, reason)` in `backend/src/main/java/com/project/custom/payment/`
- [ ] T105 [US4] Implement `PaymentService` implementing `PaymentFacade`: `start` = TX1 save `Payment(CREATED)` → call `PaymentGateway` OUTSIDE the transaction → TX2 `OPEN` with `sessionId`/URL or `FAILED` (through `TransactionTemplate`); `expireOpen` expires the guest's open sessions (`ALREADY_PAID` → a result for `409`) in `backend/src/main/java/com/project/custom/payment/application/PaymentService.java`
- [ ] T106 [US4] Implement `WebhookHandlingService` (input: the raw body and the `Stripe-Signature` header; verification through the `ProviderEventVerifier` port — never the Stripe adapter directly, ArchUnit rule 7 — before the transaction starts, `InvalidEventSignature` → a rejection exception; then a single transaction: `livemode` → rejection; `ProcessedEventRepository.register` — a duplicate → end with no effects; correlation by `providerSessionId`, an unknown session → `WARN` log; the `Payment` transition per the event table; publishing `PaymentConfirmedEvent`/`PaymentFailedEvent` through `ApplicationEventPublisher`, handled synchronously in the same transaction, R-02) in `backend/src/main/java/com/project/custom/payment/application/WebhookHandlingService.java`
- [ ] T107 [US4] Implement `StripeWebhookController` (`POST /api/payments/stripe/webhook`, `@RequestBody String` raw body + the `Stripe-Signature` header passed unchanged to `WebhookHandlingService` — the controller does no verification itself; a signature or `livemode` rejection from the service → `400` with a `WARN` log of the error type only, without the body and header; `200` only after commit; a technical error → `500`) in `backend/src/main/java/com/project/custom/payment/api/StripeWebhookController.java`; disable the `GuestIdFilter` filter for this path

### Implementation for User Story 4 — BC `order`

- [ ] T108 [P] [US4] Create the order domain: `OrderId`, `OrderStatus`, the VOs `CustomerDetails`, `ShippingAddress`, `OrderNumber` (+ `OrderNumberGenerator` based on `SecureRandom`, Crockford Base32), `OrderLine` (immutable), `IllegalStatusTransition`, `ReviewReason` in `backend/src/main/java/com/project/custom/order/domain/`
- [ ] T109 [US4] Implement the `Order` aggregate (creation with an immutable copy of the lines and `total = Σ line totals`, `AWAITING_PAYMENT`; `confirmPayment(amount, currency, stockResult)`, `markFailed()`, the state machine per data-model.md) and the `OrderRepository` port in `backend/src/main/java/com/project/custom/order/domain/Order.java` and `backend/src/main/java/com/project/custom/order/domain/OrderRepository.java`
- [ ] T110 [US4] Create order persistence: `OrderJpaEntity` (`@Version`, table `orders`), `OrderLineJpaEntity`, `OrderJpaRepository` and a package-private `OrderRepositoryAdapter` with mapping in `backend/src/main/java/com/project/custom/order/infrastructure/persistence/`
- [ ] T111 [US4] Create the BC's public API: `public record OrderPaidEvent(number, paidAt, total{minor, currency}, lines[{name, quantity, unitPriceMinor}], customer{fullName}, shippingAddress{…})` — WITHOUT the email; `public interface OrderQueryFacade` (`get(OrderNumber, GuestId) → Optional<OrderDto>`) with the `OrderDto` record and a package-private implementation in `backend/src/main/java/com/project/custom/order/`
- [ ] T112 [US4] Implement `PlaceOrderService`: `CartQueryFacade.price` → `canPlaceOrder` (otherwise `CART_NOT_ORDERABLE`) → comparison with the confirmed summary `{productId, quantity, unitPriceMinor}[]` + `totalMinor` (a difference → `SummaryOutdated` with the current cart) → `PaymentFacade.expireOpen(guest)` (`ALREADY_PAID` → `ORDER_ALREADY_PAID`) → TX save `Order` → `PaymentFacade.start` outside the transaction → on `GatewayUnavailable` TX `PAYMENT_FAILED` + `PAYMENT_UNAVAILABLE`; the amount always from server-side pricing; logs with PII masking through `shared.domain.PiiMasking` (T019) — in `backend/src/main/java/com/project/custom/order/application/PlaceOrderService.java`
- [ ] T113 [US4] Implement `PaymentEventsListener` (`@EventListener` within the webhook transaction): `PaymentConfirmedEvent` → check `amount`/`currency` against `total` → `CatalogCommandFacade.decreaseStock` (only when the amount matches) → `PAID` or `NEEDS_REVIEW` → `CartCommandFacade.clear(guestId)` → on `PAID` `OutboxEventPublisher` with `OrderPaidEvent`; a repeated confirmation → no effects; `PaymentFailedEvent` → `PAYMENT_FAILED` (cart untouched) in `backend/src/main/java/com/project/custom/order/application/PaymentEventsListener.java`
- [ ] T114 [US4] Implement `OrderController` (`POST /api/orders` with `PlaceOrderRequest`: `email @Email @Size(max=254)`, `fullName @Size(min=2,max=100)`, `streetAndNumber @Size(min=3,max=120)`, `postalCode @Pattern("^\\d{2}-\\d{3}$")`, `city @Size(min=2,max=60)`, `country` not accepted from the frontend, confirmed lines and `totalMinor` → `201 StartedPayment{number, paymentUrl}`, `409 ProblemWithCart`, `503`; `GET /api/orders/{number}` → `404` for someone else's/non-existent) in `backend/src/main/java/com/project/custom/order/api/OrderController.java` and `backend/src/main/java/com/project/custom/order/api/OrderApiMapper.java`

### Implementation for User Story 4 — frontend

- [ ] T115 [P] [US4] Create the Zod form schema (UX rules matching FR-015: email ≤ 254, full name 2–100, street 3–120, code `^\d{2}-\d{3}$`, city 2–60; country fixed to "Poland") in `frontend/src/features/checkout/checkoutSchema.ts`
- [ ] T116 [P] [US4] Create the hooks `usePlaceOrder` (mutation, handling `201` → `window.location.assign(paymentUrl)`, `400`/`409`/`503`) and `useOrder(number)` (polling every 2 s while `AWAITING_PAYMENT`, 60 s limit) in `frontend/src/features/checkout/api.ts`
- [ ] T117 [US4] Create `CheckoutPage.tsx` (React Hook Form + Zod, `<label>` labels, errors with `aria-describedby`, the summary from `GET /api/cart` sent as the confirmed lines, handling `SUMMARY_OUTDATED` with re-confirmation, `PAYMENT_UNAVAILABLE`, redirect to `/cart` when `canPlaceOrder=false`) in `frontend/src/features/checkout/CheckoutPage.tsx` and `frontend/src/features/checkout/CheckoutPage.module.css`
- [ ] T118 [US4] Create `OrderConfirmationPage.tsx` (number, lines, total, status only from the API per the state table in `contracts/frontend-routes.md`, invalidating `['cart']` after `PAID`, with no server-side state change whatsoever) in `frontend/src/features/checkout/OrderConfirmationPage.tsx`
- [ ] T119 [US4] Add the banner "The payment was not completed. Your cart is waiting." for `?payment=canceled` (with invalidation of `['cart']`) in `frontend/src/features/cart/CartPage.tsx`; register the routes `/checkout` and `/orders/:number` in `frontend/src/app/router.tsx`

**Checkpoint**: All four P1 paths work end-to-end — quickstart 4.1–4.11 and `payment.spec.ts` green.

---

## Phase 7: User Story 5 — Monitoring the Shop (Priority: P2)

**Goal**: Prometheus metrics on the internal port `8081` (HTTP, JVM, HikariCP, Flyway, business
metrics of the purchase path, Stripe calls, webhooks, Outbox), recorded only after commit;
`docker compose up -d` starts Prometheus with 9 alert rules and Grafana with 4 provisioned
dashboards; the `X-Request-Id` correlation identifier in logs (FR-025–FR-034, SC-009–SC-012).
Catalog of names and labels — only [contracts/metrics.md](contracts/metrics.md).

**Independent Test**: `docker compose up -d` + backend → Grafana has the folder "Shop" with 4
dashboards and data within ≤ 2 min; `:8080/actuator/prometheus` → `404`, `:8081` → `shop_*`
metrics with `application="shop"` and no PII; a `4242…` purchase → +1 created/`PAID`, sales value,
+1 `succeeded` within ≤ 1 min; card `4000…0002` → `declined`; a forged signature →
`rejected_signature` and the alert `ForgedPaymentConfirmation` "firing"; a stopped backend →
`ShopDown`; every `/api/**` response has an `X-Request-Id` present in the log (quickstart
5.1–5.12); in CI `promtool` shows `firing` and `resolved` for every rule.

**Dependencies**: requires US2–US4 (metrics wired into `CartService`, `PlaceOrderService`,
`PaymentEventsListener`, `WebhookHandlingService`, `StripePaymentGateway`). The Actuator
configuration, the Micrometer dependency and the ArchUnit rule for `io.micrometer` are already in
T001, T012, T015. The `observability/**` files (T132, T133, T143–T150) depend only on the contract
and can be created right after Phase 2.

### Tests for User Story 5 ⚠️

- [ ] T120 [P] [US5] Unit test for `AfterCommit` without a Spring context (manual `TransactionSynchronizationManager.initSynchronization()`): without active synchronization the action runs immediately; with synchronization — only in `afterCommit`; on `afterCompletion(STATUS_ROLLED_BACK)` — not at all; an exception in the action is logged and does not abort the commit — in `backend/src/test/java/com/project/custom/shared/infrastructure/metrics/AfterCommitTest.java`
- [ ] T121 [P] [US5] Test for `CorrelationFilter` (R-32): no header → a response with an `X-Request-Id` that is a UUID; a valid `X-Request-Id: abc-12345` → the same value in the response; an invalid one (7 characters, 65 characters, `<script>`, CR/LF) → a new UUID; during handling `MDC.get("requestId")` equals the header, after the request the MDC is cleared (also after an exception); the filter runs before `GuestIdFilter` — in `backend/src/test/java/com/project/custom/shared/api/CorrelationFilterTest.java`
- [ ] T122 [P] [US5] Unit test for the `MicrometerCartMetrics` adapter on `SimpleMeterRegistry` (no Spring, `AfterCommit` without a transaction → immediately): `addedToCart()` → `shop.cart.additions` +1; the counter registered with `0` after the adapter is created — in `backend/src/test/java/com/project/custom/cart/infrastructure/metrics/MicrometerCartMetricsTest.java`
- [ ] T123 [P] [US5] Unit test for the `MicrometerOrderMetrics` adapter (`SimpleMeterRegistry`): `orderCreated()` → `shop.orders.created` +1; `summaryMismatch({PRICE, CONTENTS})` → +1 for `kind=PRICE` and `kind=CONTENTS`, `AVAILABILITY` unchanged; `orderCompleted(PAID, 123.45 PLN, 90 s)` → `shop.orders.completed{status=PAID}` +1, `shop.orders.paid.value` +123.45 (base unit `pln`), the timer `shop.order.time.to.payment` with buckets 30 s, 1 min, 2 min, 5 min, 10 min, 30 min; `PAYMENT_FAILED`/`NEEDS_REVIEW` → only the status counter (no value and no time); all `kind` and `status` combinations registered with `0` at startup — in `backend/src/test/java/com/project/custom/order/infrastructure/metrics/MicrometerOrderMetricsTest.java`
- [ ] T124 [P] [US5] Unit test for the `MicrometerPaymentMetrics` adapter (`SimpleMeterRegistry`): `webhook(WebhookOutcome)` → `shop.payment.webhook{outcome}` with lower-case values `processed|duplicate|rejected_signature|rejected_livemode|ignored`; `payment(PaymentOutcome)` → `shop.payments{outcome=succeeded|declined|canceled}`; `confirmationDelay(12 s)` → the timer `shop.payment.confirmation.delay` with buckets 1, 5, 10, 30, 60, 120 s; all combinations registered with `0` — in `backend/src/test/java/com/project/custom/payment/infrastructure/metrics/MicrometerPaymentMetricsTest.java`
- [ ] T125 [P] [US5] Test for the Stripe adapter metrics (WireMock + `SimpleMeterRegistry`, a test `Sleeper`): success → 1 attempt in `shop.stripe.calls{operation=create_session,outcome=success}`, `shop.stripe.retries` unchanged; `503` ×3 → 3 attempts with `outcome=error` and `shop.stripe.retries{operation=create_session}` = 2; a read timeout → `outcome=timeout`; `400` → 1 `error` attempt, 0 retries; `expire` → `operation=expire_session`; a timer with buckets 100 ms, 300 ms, 1 s, 3 s, 10 s; all `operation × outcome` combinations registered with `0` — in `backend/src/test/java/com/project/custom/payment/infrastructure/stripe/StripePaymentGatewayMetricsTest.java`
- [ ] T126 [P] [US5] Create the metrics assertion helper `MetricsAssert` (reads a counter/timer from `MeterRegistry` by name and labels, `delta(() -> action)` returning the increment, a missing metric = `0`) in `backend/src/test/java/com/project/custom/support/MetricsAssert.java`
- [ ] T127 [US5] Extend `CartServiceIT` with metrics (uses `MetricsAssert` from T126): a successful `POST /api/cart/lines` → `shop.cart.additions` +1 after commit; `409 QUANTITY_EXCEEDS_LIMIT`, `409 PRODUCT_UNAVAILABLE`, `400` → unchanged; a forced rollback after the write (e.g. `CONCURRENCY_CONFLICT`) → unchanged (SC-010) — in `backend/src/test/java/com/project/custom/cart/application/CartServiceIT.java`
- [ ] T128 [US5] Extend `PlaceOrderServiceIT` with metrics: `201` → `shop.orders.created` +1; `409 SUMMARY_OUTDATED` with a changed price → `mismatches{kind=PRICE}` +1, with `stock = 0` → `AVAILABILITY`, with a different set of products → `CONTENTS` (one increment per detected kind), `created` unchanged; `503 PAYMENT_UNAVAILABLE` → `created` +1 (TX1 committed) and `completed{status=PAYMENT_FAILED}` +1; `409 CART_NOT_ORDERABLE`/`400` → unchanged — in `backend/src/test/java/com/project/custom/order/application/PlaceOrderServiceIT.java`
- [ ] T129 [US5] Extend `WebhookHandlingServiceIT` with metrics per the table in `contracts/stripe-webhook.md` §2 "Metrics": `completed/paid` → `webhook{processed}` +1, `payments{succeeded}` +1, `completed{PAID}` +1, `paid_value_pln` + total/100, `time.to.payment` and `confirmation.delay` (= now − `event.created`) recorded; the same `event.id` 2× → `duplicate` +1, the rest unchanged; an invalid signature → `rejected_signature` +1; `livemode=true` → `rejected_livemode` +1; `payment_intent.payment_failed` → `processed` +1 and `payments{declined}` +1 WITHOUT changing `Payment` and `Order` (R-28); `async_payment_failed` → `declined` + `completed{PAYMENT_FAILED}`; `expired` → `canceled`; `completed` with `payment_status != paid`, an unknown session, another type → `ignored`; insufficient stock → `completed{NEEDS_REVIEW}`; a late confirmation after a failure → `NEEDS_REVIEW` +1, while `PAYMENT_FAILED` stays unchanged; a forced rollback of the webhook transaction (an error in the listener → `500`) → `processed`, `succeeded`, `completed` unchanged — in `backend/src/test/java/com/project/custom/payment/application/WebhookHandlingServiceIT.java`
- [ ] T130 [P] [US5] Integration test for the gauges: `shop.outbox.pending` and `shop.outbox.oldest` — an empty Outbox → `0`/`0`; 2 rows with `sent_at IS NULL` (the oldest from 400 s ago) and 1 sent → `2`/`≈ 400`; the result cached for 15 s (injected `Clock`: a second change in the database is invisible before 15 s pass); `shop.flyway.migrations{state=success}` = the number of migrations `V1…V5`, `failed`/`pending` = `0` — in `backend/src/test/java/com/project/custom/shared/infrastructure/metrics/GaugeMetricsIT.java`
- [ ] T131 [P] [US5] Test `MetricsEndpointIT` (R-33, SC-012; `@SpringBootTest(webEnvironment = RANDOM_PORT)`, management port from `@LocalManagementPort`): runs the full path (add to cart → `POST /api/orders` against `stripe-mock` → a successful, a duplicate and a forged webhook), fetches `/actuator/prometheus` from the management port and checks: (1) no patterns of an email, full name, street, `ORD-[0-9A-Z]{10}`, `cs_test_`, `pi_`, `evt_`, `sk_`, `whsec_`, or the `shop_guest` and `X-Request-Id` values used in the test; (2) every `shop_*` series has `application="shop"`; (3) the set of `shop_*` names (without the `_bucket|_count|_sum|_max` suffixes) equals the list in `contracts/metrics.md` §2; (4) `GET :{API port}/actuator/prometheus` → `404`; (5) `/actuator/health/readiness` on the management port → `UP` without details — in `backend/src/test/java/com/project/custom/MetricsEndpointIT.java`
- [ ] T132 [P] [US5] Write `promtool` rule tests with a `firing` and a `resolved` case for each of the 9 rules from `contracts/metrics.md` §4 (synthetic `input_series`, `eval_time` after `for` elapses, expected labels `application`, `severity`, and for `OutboxBacklog` also `requires="fulfillment"`) in `observability/prometheus/tests/shop.test.yml`
- [ ] T133 [P] [US5] Create the validator `scripts/check-dashboards.mjs` (Node, no dependencies): parses the §2 table of `specs/001-shop-browse-cart-checkout/contracts/metrics.md` as the list of allowed `shop_*` metrics; for each `observability/grafana/dashboards/*.json` checks valid JSON, `datasource.uid = "prometheus"` in all panels and targets, that every `shop_*` metric in the expressions is on the list, and that counters (`_total`, `_count`) appear only inside `rate(`/`increase(`; the same for the expressions in `observability/prometheus/rules/*.yml`; exit code `1` with a list of errors

### Implementation for User Story 5 — shared kernel

- [ ] T134 [P] [US5] Implement `public final class AfterCommit` (`run(Runnable)`: with active transaction synchronization it registers `TransactionSynchronization.afterCommit`, without it runs immediately; an exception from the action is caught and logged at `WARN`, never aborting the purchase path) in `backend/src/main/java/com/project/custom/shared/infrastructure/metrics/AfterCommit.java`
- [ ] T135 [P] [US5] Implement `MetricsConfig` (`@Configuration`; a `MeterFilter.maximumAllowableTags("http.server.requests", "uri", 50, MeterFilter.deny())` bean as a cardinality safeguard, R-26) in `backend/src/main/java/com/project/custom/shared/infrastructure/metrics/MetricsConfig.java`
- [ ] T136 [P] [US5] Implement `CorrelationFilter` (`OncePerRequestFilter` for `/api/**`: the `X-Request-Id` header is accepted only when it matches `^[A-Za-z0-9-]{8,64}$`, otherwise `UUID.randomUUID()`; `MDC.put("requestId", …)`, the header in the response, `MDC.remove` in `finally`; the value never goes into metrics) in `backend/src/main/java/com/project/custom/shared/api/CorrelationFilter.java` and register it before `GuestIdFilter` (a `FilterRegistrationBean` with a lower `order`) in `backend/src/main/java/com/project/custom/shared/api/WebConfig.java`

### Implementation for User Story 5 — business metrics in the BCs

- [ ] T137 [US5] Add cart metrics: the port `public interface CartMetrics { void addedToCart(); }` in `backend/src/main/java/com/project/custom/cart/application/CartMetrics.java`; a package-private adapter `MicrometerCartMetrics` (the `shop.cart.additions` counter registered in the constructor, incremented through `AfterCommit`) in `backend/src/main/java/com/project/custom/cart/infrastructure/metrics/MicrometerCartMetrics.java`; the port call in `CartService.add` after a successful addition (inside the transaction — the adapter defers the recording to commit) in `backend/src/main/java/com/project/custom/cart/application/CartService.java`
- [ ] T138 [US5] Add order metrics: the enum `MismatchKind {PRICE, AVAILABILITY, CONTENTS}` and the port `public interface OrderMetrics` (`orderCreated()`, `summaryMismatch(Set<MismatchKind>)`, `orderCompleted(OrderStatus target, Money total, Duration timeToPayment)`) in `backend/src/main/java/com/project/custom/order/application/`; a package-private adapter `MicrometerOrderMetrics` (names, labels and buckets from `contracts/metrics.md` §2; the PLN value = `minor / 100.0` for presentation only; all combinations with `0` at startup; recording through `AfterCommit`) in `backend/src/main/java/com/project/custom/order/infrastructure/metrics/MicrometerOrderMetrics.java`; wiring: `PlaceOrderService` — `orderCreated()` in TX1, on `SummaryOutdated` compute the kinds (a different unit price → `PRICE`; an unavailable line or quantity > stock → `AVAILABILITY`; a different set of products or quantities → `CONTENTS`) and call `summaryMismatch` immediately (no transaction), on `GatewayUnavailable` `orderCompleted(PAYMENT_FAILED, …)` in the TX that marks the failure; `PaymentEventsListener` — `orderCompleted` only on an actual status transition (`PAID → PAID` counts nothing), `timeToPayment = paidAt − createdAt` — in `backend/src/main/java/com/project/custom/order/application/PlaceOrderService.java` and `backend/src/main/java/com/project/custom/order/application/PaymentEventsListener.java`
- [ ] T139 [US5] Extend `ProviderConfirmation` with `Instant providerCreatedAt` (from `event.created`) in `backend/src/main/java/com/project/custom/payment/domain/ProviderConfirmation.java`, and `StripeWebhookVerifier` with the mapping of this field and of the `payment_intent.payment_failed` type (no `sessionId`; for the metric only, R-28) in `backend/src/main/java/com/project/custom/payment/infrastructure/stripe/StripeWebhookVerifier.java`
- [ ] T140 [US5] Add payment metrics: the enums `WebhookOutcome {PROCESSED, DUPLICATE, REJECTED_SIGNATURE, REJECTED_LIVEMODE, IGNORED}`, `PaymentOutcome {SUCCEEDED, DECLINED, CANCELED}` and the port `public interface PaymentMetrics` (`webhook(WebhookOutcome)`, `payment(PaymentOutcome)`, `confirmationDelay(Duration)`) in `backend/src/main/java/com/project/custom/payment/application/`; a package-private adapter `MicrometerPaymentMetrics` (lower-case labels, buckets from the contract, `AfterCommit`) in `backend/src/main/java/com/project/custom/payment/infrastructure/metrics/MicrometerPaymentMetrics.java`; in `WebhookHandlingService` (which already verifies through the `ProviderEventVerifier` port, T106) report every outcome through the port: a signature/`livemode` rejection → `webhook(...)` immediately, before the exception mapped by the controller to `400`; a duplicate → `DUPLICATE`; outcome mapping per R-28 (`completed`/paid and `async_payment_succeeded` → `SUCCEEDED` + `confirmationDelay(now − providerCreatedAt)`; `payment_intent.payment_failed` and `async_payment_failed` → `DECLINED`; `expired` → `CANCELED`; `payment_intent.payment_failed` only deduplication + metric, no `Payment` change); the rest → `IGNORED`; `MDC.put("stripeEventId", …)` for the duration of handling — in `backend/src/main/java/com/project/custom/payment/application/WebhookHandlingService.java`
- [ ] T141 [US5] Add call metrics to `StripePaymentGateway` (an injected `MeterRegistry`; every attempt of the T102 retry loop → the timer `shop.stripe.calls{operation, outcome}` with buckets 100 ms, 300 ms, 1 s, 3 s, 10 s; every retry → `shop.stripe.retries{operation}`; labels from the `StripeOperation`/`CallOutcome` enums in lower case; all combinations with `0` at startup; no `AfterCommit` — the calls are outside a transaction) in `backend/src/main/java/com/project/custom/payment/infrastructure/stripe/StripePaymentGateway.java`
- [ ] T142 [P] [US5] Implement the gauges in `shared`: `OutboxMetrics` (`shop.outbox.pending` and `shop.outbox.oldest` in seconds, a single query `SELECT COUNT(*), MIN(created_at) FROM outbox_event WHERE sent_at IS NULL` through `OutboxEventJpaRepository`, the result cached for 15 s with an injected `Clock`, `0` when none) and `FlywayMetrics` (`shop.flyway.migrations{state=success|failed|pending}` from `Flyway.info().all()` once after `ApplicationReadyEvent`) in `backend/src/main/java/com/project/custom/shared/infrastructure/metrics/OutboxMetrics.java` and `backend/src/main/java/com/project/custom/shared/infrastructure/metrics/FlywayMetrics.java`

### Implementation for User Story 5 — Prometheus, Grafana, CI

- [ ] T143 [P] [US5] Add to `compose.yaml` the services `prometheus` (`prom/prometheus` with a pinned version, `--config.file=/etc/prometheus/prometheus.yml`, `--storage.tsdb.retention.time=15d`, volumes `./observability/prometheus:/etc/prometheus:ro` and a named one for data, `extra_hosts: host.docker.internal:host-gateway`, port `127.0.0.1:9090:9090`) and `grafana` (`grafana/grafana` with a pinned version, `GF_SECURITY_ADMIN_PASSWORD: ${GRAFANA_ADMIN_PASSWORD:?Set GRAFANA_ADMIN_PASSWORD in .env}`, `GF_USERS_ALLOW_SIGN_UP=false`, `GF_AUTH_ANONYMOUS_ENABLED=false`, volumes `./observability/grafana/provisioning:/etc/grafana/provisioning:ro` and `./observability/grafana/dashboards:/var/lib/grafana/dashboards:ro`, port `127.0.0.1:3000:3000`, `depends_on: prometheus`) in the default profile; in the `stripe-cli` service limit `listen` to `--events checkout.session.completed,checkout.session.async_payment_succeeded,checkout.session.async_payment_failed,checkout.session.expired,payment_intent.payment_failed` (R-28); add `GRAFANA_ADMIN_PASSWORD=` with the comment "required, do not use admin/admin" to `.env.example`
- [ ] T144 [P] [US5] Create `observability/prometheus/prometheus.yml` (`global.scrape_interval: 15s`, `evaluation_interval: 15s`; `rule_files: [/etc/prometheus/rules/*.yml]`; job `shop-backend` with `metrics_path: /actuator/prometheus` and the target `host.docker.internal:8081`)
- [ ] T145 [P] [US5] Create the 9 alert rules from `contracts/metrics.md` §4 verbatim (group `shop`, `interval: 15s`; expressions, `for` and `severity` from the table; label `application: shop`; annotations `summary` in English and `dashboard` with a link to the panel; `OutboxBacklog` with the label `requires: fulfillment` and an annotation about the missing dispatch job, R-30) in `observability/prometheus/rules/shop.yml`
- [ ] T146 [P] [US5] Create the Grafana provisioning: the Prometheus data source (`uid: prometheus`, `url: http://prometheus:9090`, `isDefault: true`, `editable: false`) in `observability/grafana/provisioning/datasources/prometheus.yaml` and the file provider (folder "Shop", `path: /var/lib/grafana/dashboards`, `allowUiUpdates: false`) in `observability/grafana/provisioning/dashboards/shop.yaml`
- [ ] T147 [P] [US5] Create the dashboard "Shop — HTTP" (variable `$application` defaulting to `shop`; requests/s per `uri`; 4xx and 5xx rate; p50/p95/p99 per `uri` through `histogram_quantile`; a table of the top 5 slowest `uri`; an alert state panel for `HighServerErrorRate`, `SlowCatalogOrCartResponses`) in `observability/grafana/dashboards/http.json`
- [ ] T148 [P] [US5] Create the dashboard "Shop — purchase funnel" (a funnel with `increase` over the dashboard range: `shop_cart_additions_total` → `shop_orders_created_total` → `shop_orders_completed_total{status="PAID"}`; orders by `status`; payments by `outcome` as separate `succeeded`/`declined`/`canceled` series; sales value from `shop_orders_paid_value_pln_total`; mismatches by `kind`) in `observability/grafana/dashboards/purchase-funnel.json`
- [ ] T149 [P] [US5] Create the dashboard "Shop — payments and integrations" (Stripe calls by `operation`/`outcome`; p95 call duration; retries; webhooks by `outcome` with `rejected_signature` highlighted; p95 confirmation delay with a 30 s threshold line; time-to-payment distribution; Outbox: pending and oldest in a panel labeled "waiting for the fulfillment feature") in `observability/grafana/dashboards/payments-integrations.json`
- [ ] T150 [P] [US5] Create the dashboard "Shop — JVM and database" (heap/non-heap, GC pauses, threads, `process_cpu_usage`, `process_uptime_seconds`, HikariCP `active`/`idle`/`pending` and connection acquisition time, `shop_flyway_migrations` by `state`, `up{job="shop-backend"}`) in `observability/grafana/dashboards/jvm-database.json`
- [ ] T151 [US5] Add an `observability` job to `.github/workflows/ci.yml`: `promtool check config` and `promtool check rules` plus `promtool test rules observability/prometheus/tests/shop.test.yml` (the `prom/prometheus` image with the same version as in `compose.yaml`, `--entrypoint promtool`) and `node scripts/check-dashboards.mjs`

**Checkpoint**: US5 works — quickstart 5.1–5.12 passes, `promtool test rules`,
`check-dashboards.mjs` and `MetricsEndpointIT` green; US1–US4 without regressions.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Contract, performance (including metrics overhead), security and whole-feature validation (SC-003, SC-008, SC-012).

- [ ] T152 [P] Implement `OpenApiContractTest` comparing the paths, methods and response codes from `/v3/api-docs` (springdoc) with `backend/src/main/resources/openapi/shop-api.yaml` in `backend/src/test/java/com/project/custom/OpenApiContractTest.java`
- [ ] T153 [P] Performance test on a seed of ≥ 500 products (list, search with a phrase, filter+sort, a cart with 10 lines — every backend query ≤ 300 ms after warm-up, no N+1 verified with the Hibernate query counter) and metrics overhead (R-34, SC-012): the same measurement with the production-like configuration and with `management.metrics.enable.all=false` — p95 difference < 5% or < 5 ms — in `backend/src/test/java/com/project/custom/catalog/application/CatalogPerformanceIT.java`
- [ ] T154 [P] Test for the absence of personal data and secrets in logs (capturing logs while placing an order and handling a webhook: no full email, address, `sk_test_`, `whsec_`; every entry from an API request contains `[requestId]`) in `backend/src/test/java/com/project/custom/shared/infrastructure/config/NoPiiInLogsIT.java`
- [ ] T155 [P] Accessibility and responsiveness review (360 px, 2/3/4 grid, keyboard focus, `aria-disabled` with an explanation, contrast) and fixes in `frontend/src/app/Layout.module.css`, `frontend/src/features/catalog/CatalogPage.module.css`, `frontend/src/features/cart/CartPage.module.css`, `frontend/src/features/checkout/CheckoutPage.module.css`
- [ ] T156 [P] Add `README.md` in the root directory (prerequisites, `.env` with `GRAFANA_ADMIN_PASSWORD`, `docker compose up -d`, running the backend/frontend, Grafana `http://127.0.0.1:3000` and Prometheus `http://127.0.0.1:9090` addresses, tests, gitleaks, `promtool`) with a link to `specs/001-shop-browse-cart-checkout/quickstart.md`
- [ ] T157 Complete the E2E job in `.github/workflows/ci.yml` (compose + backend `e2e` profile + `stripe listen` with CI secrets, running `npm --prefix frontend run e2e`, skipped when `STRIPE_TEST_*` are missing) and the profile `backend/src/main/resources/application-e2e.yaml` (seed as in `local`)
- [ ] T158 Run `gitleaks detect --no-banner` and the dependency audit locally (`osv-scanner scan source --lockfile backend/pom.xml --lockfile frontend/package-lock.json` as in T010 + `npm --prefix frontend audit --audit-level=high`); remove high/critical findings
- [ ] T159 Walk through all scenarios from `specs/001-shop-browse-cart-checkout/quickstart.md` manually (sections 3 and 4, including US5 5.1–5.12) and record the result in the PR description

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: no dependencies — start immediately.
- **Foundational (Phase 2)**: after Setup — BLOCKS all stories.
- **US1 (Phase 3)**: after Foundational.
- **US2 (Phase 4)**: after Foundational; needs products in the database (`V1__catalog.sql` + the seed from US1: T036, T037) and the catalog entities/queries (T038–T043) for `CatalogQueryFacade` (T058). The adding UI (T067) plugs into the US1 pages (T048, T050).
- **US3 (Phase 5)**: after US2 (extends the `Cart` aggregate, `CartService`, `CartController`, `CartPage`).
- **US4 (Phase 6)**: after US3 (uses `canPlaceOrder`, `problems[]` and `clear`); the `payment` backend (T100–T107) can be built in parallel with US1–US3 after Phase 2, because it depends only on `shared`.
- **US5 (Phase 7)**: after US4 — metrics are wired into the US2–US4 services (T137 → `CartService`, T138 → `PlaceOrderService`/`PaymentEventsListener`, T140 → `WebhookHandlingService`, T141 → `StripePaymentGateway` from T102) and extend their IT tests (T127–T129). The shared kernel (T120–T121, T134–T136) and the whole Prometheus/Grafana configuration (T132–T133, T143–T150) depend only on Phase 2 and the `contracts/metrics.md` contract — they can be built in parallel with US1–US4.
- **Polish (Phase 8)**: after all stories (T153 also measures the metrics overhead from US5).

### User Story Dependencies

```text
Phase 1 ─► Phase 2 ─┬─► US1 ─► US2 ─► US3 ─► US4 ─► US5 (metrics in BCs) ─► Polish
                    ├─► (in parallel) US4: domain + Stripe adapter (T083–T086, T100–T103)
                    └─► (in parallel) US5: kernel + observability/** (T120–T121, T132–T136, T143–T150)
```

Every story is independently **testable and demonstrable** (Principle IV) on the seed and through
its own E2E; the dependencies are only "build" dependencies (shared aggregates and screens), not
functional ones.

### Within Each User Story

- Tests (⚠️) first — they must fail before the implementation.
- Migration → domain → repository port → JPA/adapter → `*Service` → facade/controller → frontend.
- Stripe calls always outside a transaction; webhook effects always in a single transaction.
- Commit after every task or logical group.

### Parallel Opportunities

- Phase 1: T003–T008 in parallel after T001–T002.
- Phase 2: tests T011–T014 in parallel; T016–T020 in parallel; frontend T024–T026, T028–T029 in parallel with the backend.
- US1: tests T030–T035 in parallel; T037, T038, T040 in parallel; frontend T046–T048 in parallel with backend T039–T045.
- US2: tests T052–T056 in parallel; T058 and T059 in parallel.
- US3: tests T069–T073 in parallel; the backend (T074–T077) in parallel with preparing the frontend components (T079).
- US4: tests T081–T093 in parallel; migrations T095–T096 in parallel; facades T098–T099 in parallel; the `payment` (T100) and `order` (T108) domains in parallel; frontend T115–T116 in parallel with the backend.
- US5: tests T120–T126 and T130–T133 in parallel; T127–T129 in parallel with each other after T126; kernel T134–T136 in parallel; T142 in parallel with T137–T141; the `observability/**` files T143–T150 all in parallel (each is a separate file).
- Polish: T152–T156 in parallel.

---

## Parallel Example: User Story 1

```bash
# US1 tests together:
Task: "Unit test for Product in backend/src/test/java/com/project/custom/catalog/domain/ProductTest.java"
Task: "Unit test for SearchCriteria in backend/src/test/java/com/project/custom/catalog/domain/SearchCriteriaTest.java"
Task: "Integration test for CatalogService in backend/src/test/java/com/project/custom/catalog/application/CatalogServiceIT.java"
Task: "Test for useUrlFilters in frontend/tests/unit/catalog/useUrlFilters.test.tsx"

# Seed, domain types and frontend in parallel:
Task: "Seed in backend/src/main/resources/db/seed/R__seed_catalog.sql"
Task: "Domain types in backend/src/main/java/com/project/custom/catalog/domain/"
Task: "Query hooks in frontend/src/features/catalog/api.ts"
Task: "useUrlFilters in frontend/src/features/catalog/useUrlFilters.ts"
```

## Parallel Example: User Story 4

```bash
# Domains of two BCs and migrations in parallel:
Task: "Payment domain in backend/src/main/java/com/project/custom/payment/domain/"
Task: "Order domain in backend/src/main/java/com/project/custom/order/domain/"
Task: "Migration V4__payment.sql"
Task: "Migration V5__outbox.sql"

# Facades of neighboring BCs in parallel:
Task: "CatalogCommandFacade in backend/src/main/java/com/project/custom/catalog/"
Task: "CartQueryFacade/CartCommandFacade in backend/src/main/java/com/project/custom/cart/"
```

## Parallel Example: User Story 5

```bash
# Observability configuration — independent of the code, right after Phase 2:
Task: "promtool tests in observability/prometheus/tests/shop.test.yml"
Task: "Alert rules in observability/prometheus/rules/shop.yml"
Task: "Grafana provisioning in observability/grafana/provisioning/"
Task: "HTTP dashboard in observability/grafana/dashboards/http.json"
Task: "Purchase funnel dashboard in observability/grafana/dashboards/purchase-funnel.json"
Task: "Validator scripts/check-dashboards.mjs"

# Metrics adapter tests — one per BC:
Task: "MicrometerCartMetricsTest in backend/src/test/java/com/project/custom/cart/infrastructure/metrics/"
Task: "MicrometerOrderMetricsTest in backend/src/test/java/com/project/custom/order/infrastructure/metrics/"
Task: "MicrometerPaymentMetricsTest in backend/src/test/java/com/project/custom/payment/infrastructure/metrics/"
```

---

## Implementation Strategy

### MVP First (User Story 1 only)

1. Phase 1: Setup.
2. Phase 2: Foundational (CRITICAL — blocks all stories).
3. Phase 3: US1 — catalog browsing.
4. **STOP and VALIDATE**: quickstart 1.1–1.8 + `browsing.spec.ts`.
5. Catalog demo.

### Incremental Delivery

1. Setup + Foundational → foundation ready (CI green).
2. US1 → browsing demo (MVP).
3. US2 → demo of adding to the cart and the counter.
4. US3 → demo of cart editing with price/availability changes (still without Stripe).
5. US4 → the full purchase path with a test payment and an event in the Outbox.
6. US5 → dashboards, alerts and PII-free metrics after `docker compose up -d` (SC-009–SC-012).
7. Polish → contract, performance and metrics overhead, PII-free logs, a quickstart walkthrough (SC-008).

### Parallel Team Strategy

1. The team completes Setup + Foundational together.
2. Then:
   - Dev A: US1 → US2 → US3 (catalog and cart, backend + frontend)
   - Dev B: US4 `payment` backend (domain, Stripe adapter, contract tests) right after Phase 2
   - Dev C: US4 frontend on MSW per the OpenAPI contract, then US5 `observability/**` (rules, `promtool` tests, dashboards) per `contracts/metrics.md`
3. US4 integration after US3 is finished; metrics in the BCs (T137–T142) after US4.

---

## Notes

- [P] = different files, no dependencies on unfinished tasks.
- [Story] maps a task to a story for traceability.
- No real secrets in tests — `sk_test_dummy` and a generated `whsec_test_…`; E2E with Stripe only with CI secrets.
- Amounts only in grosze (`long`/`BIGINT`), formatting only in the frontend.
- The frontend contains no price, availability or payment status rules — it only presents fields from the API.
- Avoid: vague tasks, conflicts in the same file, cross-story dependencies that break their independent testability.
