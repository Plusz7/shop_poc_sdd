# Implementation Plan: Przeglądanie sklepu, koszyk i płatność

**Branch**: `001-shop-browse-cart-checkout` | **Date**: 2026-09-23 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/001-shop-browse-cart-checkout/spec.md`

## Summary

Pierwsza funkcja PoC sklepu: pełna ścieżka zakupowa gościa — przeglądanie katalogu
(kategorie, wyszukiwanie bez diakrytyków, filtr ceny, sortowanie, paginacja w URL),
koszyk po stronie serwera powiązany z przeglądarką ciasteczkiem, edycja koszyka z wyceną
wyłącznie z katalogu oraz zamówienie opłacane przez **hostowany Stripe Checkout**.
Zamówienie staje się „Opłacone” tylko po zweryfikowanym, zdeduplikowanym webhooku Stripe,
który w jednej transakcji zmniejsza stan, czyści koszyk i zapisuje do Outboxa zdarzenie
`ZamowienieOplaconeEvent` dla przyszłej integracji z Trello.

Technicznie: modularny monolit Spring Boot 4 (Java 21) z BC `katalog`, `koszyk`,
`zamowienie`, `platnosc` + `shared`, SQL Server z Flyway, SPA React + Vite + TypeScript
korzystająca z kontraktu OpenAPI (API-first). Szczegóły decyzji: [research.md](research.md).

**Obserwowalność (US5, P2)**: Actuator + Micrometer wystawiają metryki Prometheus na osobnym
porcie zarządzania `8081` (niedostępnym przez proxy frontendu). Metryki biznesowe zgłaszają
serwisy aplikacyjne przez porty `Metryki*` w każdym BC, a zapis następuje dopiero po commicie.
Adapter Stripe mierzy wywołania i ponowienia, a webhook — wyniki potwierdzeń i opóźnienie
względem Stripe. `docker compose up -d` podnosi też Prometheusa (retencja 15 dni, 9 reguł
alertów testowanych `promtool`) i Grafanę z 4 dashboardami z provisioningu. Logi mają
identyfikator korelacji `X-Request-Id`. Katalog metryk: [contracts/metrics.md](contracts/metrics.md);
decyzje R-26–R-34.

## Technical Context

**Language/Version**: Java 21 (backend), TypeScript 5.x strict (frontend)

**Primary Dependencies**: Spring Boot 4.0 (Web MVC, Data JPA/Hibernate 7, Validation,
Actuator), Flyway (+ moduł SQL Server), `com.stripe:stripe-java`, springdoc-openapi (linia
zgodna z Boot 4), ArchUnit, `micrometer-registry-prometheus`; React 19, Vite, React Router,
TanStack Query, React Hook Form + Zod, `openapi-typescript` + `openapi-fetch`;
obserwowalność lokalna: Prometheus i Grafana (kontenery, wersje przypięte w `compose.yaml`)

**Storage**: SQL Server 2022 (kolacja kolumny `produkt.nazwa` = `Polish_100_CI_AI`),
schemat przez Flyway; statyczne zdjęcia seeda w zasobach backendu

**Testing**: JUnit Jupiter + AssertJ (domena), `@SpringBootTest` + Testcontainers
(`MSSQLServerContainer`, `stripe-mock`), WireMock (awarie Stripe), ArchUnit, test zgodności
OpenAPI; Vitest + Testing Library + MSW (frontend); Playwright + Stripe CLI (E2E);
`SimpleMeterRegistry` (adaptery metryk), `promtool test rules` (alerty), `scripts/check-dashboards.mjs`
(dashboardy vs kontrakt metryk)

**Target Platform**: backend — JVM w kontenerze Linux / lokalnie Windows/macOS;
frontend — nowoczesne przeglądarki desktop i mobile (od 360 px)

**Project Type**: aplikacja webowa — backend REST (modularny monolit) + SPA

**Performance Goals**: lista, wyszukiwanie i koszyk < 1 s dla klienta przy ≥ 500 produktach
(SC-003; budżet backendu 300 ms); status „Opłacone” ≤ 30 s od potwierdzenia Stripe (SC-007);
dashboardy z danymi ≤ 2 min po starcie (SC-009); zdarzenia w metrykach ≤ 1 min (SC-010);
narzut metryk na p95 < 5% (SC-012)

**Constraints**: kwoty w groszach (`long`), bez `double`/`float`; tylko klucze testowe
Stripe (`sk_test_`), żaden klucz Stripe we frontendzie; brak wywołań zewnętrznych wewnątrz
transakcji; brak logowania danych osobowych i sekretów; interfejs po polsku; metryki bez
danych osobowych i z etykietami o zamkniętym zbiorze wartości; endpoint metryk tylko na porcie
zarządzania; zbieranie metryk nigdy nie blokuje ścieżki zakupowej

**Scale/Scope**: PoC — ~500 produktów, kilkanaście kategorii, 5 ekranów, 4 BC, pojedyncza
instancja backendu, ruch demonstracyjny; 14 metryk domenowych, 9 reguł alertów, 4 dashboardy

Brak pozycji „NEEDS CLARIFICATION” — wszystkie wybory rozstrzygnięte w research.md (R-01–R-34).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Zasada | Wymaganie | Jak plan je spełnia | Pre | Post |
|---|---|---|---|---|
| **I. Sekrety** | env/`@ConfigurationProperties`, `.env` w `.gitignore`, `.env.example`, brak `sk_` we froncie, walidacja przy starcie, gitleaks | `StripeProperties` z `@Validated` + wzorce `sk_test_`/`whsec_` blokujące start; `.env.example` rozszerzony o `STRIPE_*`, `DB_PASSWORD`, `APP_BASE_URL`; frontend nie dostaje **żadnego** klucza (hostowany Checkout); gitleaks w pre-commit i CI; maskowanie PII w logach (R-20). Obserwowalność: hasło Grafany tylko z `GRAFANA_ADMIN_PASSWORD` (brak → `compose up` przerwany), bez `admin/admin` i anonimowego dostępu; metryki bez sekretów i PII, sprawdzane testem `MetrykiEndpointIT` (R-31, R-33) | ✅ | ✅ |
| **II. Płatności** | Stripe Checkout/Elements, kwota z serwera, grosze, tylko webhook z podpisem, idempotencja `event.id` + klucz idempotencji, tylko tryb testowy | Hostowany Checkout (SAQ A); kwota z niezmiennych pozycji zamówienia wycenionych serwerowo; `Pieniadze(long grosze)`; `Webhook.constructEvent` + odrzucenie `livemode`; tabela `przetworzone_zdarzenie_stripe` w tej samej transakcji; `Idempotency-Key: checkout-{platnoscId}`; porównanie `amount_total` z sumą (R-11, R-12, R-14) | ✅ | ✅ |
| **III. Modularny monolit DDD** | BC z warstwami i Facade, komunikacja przez Facade/zdarzenia, domena bez adnotacji, frontend przez OpenAPI bez reguł biznesowych | BC `katalog`, `koszyk`, `zamowienie`, `platnosc` + `shared`; brak cyklu dzięki zdarzeniom `platnosc → zamowienie`; ArchUnit egzekwuje granice; kontrakt `contracts/openapi.yaml`, frontend tylko prezentuje statusy/flagi z API (R-02, R-03, R-19). BC `realizacja` — osobna funkcja (odstępstwo od „minimalnego zestawu” tylko czasowe — zob. Complexity Tracking). Metryki: porty `Metryki*` w `application/` każdego BC, adaptery Micrometer w `infrastructure/metrics/`; domena bez zmian; nowa reguła ArchUnit — `io.micrometer..` tylko w `..infrastructure..` (R-27) | ✅ | ✅ |
| **IV. Ścieżki marketplace** | 4 ścieżki P1 niezależnie testowalne, blokady ilości, pokazywanie zmiany ceny | US1–US4 zmapowane na trasy i endpointy (`contracts/frontend-routes.md`); reguły ilości w agregacie `Koszyk`; `cenaZmieniona` + `409 PODSUMOWANIE_NIEAKTUALNE`; E2E na każdą ścieżkę | ✅ | ✅ |
| **V. Integracje odizolowane** | port w `domain/`, adapter w `infrastructure/`, Outbox, timeouty/ponowienia, Trello nie blokuje zakupu | Port `BramkaPlatnosci` + adapter Stripe; timeouty 5 s/10 s, 2 ponowienia; wywołania Stripe poza transakcją; `outbox_event` zapisywany atomowo z opłaceniem; brak jakiejkolwiek zależności ścieżki zakupu od Trello (R-13, R-17). Wywołania Stripe mierzone w adapterze (czas, wynik, ponowienia — ponawia adapter, nie SDK); zbieranie metryk metodą pull, więc awaria Prometheusa/Grafany nie dotyka ścieżki zakupu (R-26, R-27) | ✅ | ✅ |
| **VI. Testy** | jednostkowe domeny bez Springa, integracyjne `*Service` z Testcontainers, kontraktowe adapterów, webhook z poprawnym i sfałszowanym podpisem, E2E P1, brak prawdziwych sekretów | Macierz testów w R-21 i `contracts/stripe-webhook.md`; E2E Playwright + Stripe CLI z sekretami z CI. Obserwowalność: macierz w R-34 — jednostkowe adapterów metryk, asercje liczników po commicie i rollbacku w testach `*Service`, `MetrykiEndpointIT`, `promtool test rules` dla każdej reguły (SC-011) | ✅ | ✅ |
| **VII. YAGNI** | każdy dodatkowy komponent uzasadniony | Bez RabbitMQ, cache, Spring Security, Spring Session, Elasticsearch, Alertmanagera, OpenTelemetry Collectora i tracingu. Stripe CLI (narzędzie dev/E2E). **Prometheus + Grafana** — nowe kontenery, uzasadnione wprost w spec (US5, FR-032, wyraźne życzenie właściciela) — wpis w Complexity Tracking | ✅ | ✅ |
| Stos i proces | Java 21, Boot 4, SQL Server, React+Vite (zatwierdzany tutaj), OpenAPI, `docker compose up`, PR z bramkami | **Zatwierdzamy React 19 + Vite + TypeScript**; `compose.yaml`; CI: build, testy, gitleaks, audyt zależności `osv-scanner` (Maven + npm) i `npm audit` (R-18, R-22); `docker compose up -d` podnosi też Prometheusa i Grafanę; CI dodatkowo: `promtool test rules`, `check-dashboards.mjs` (R-31, R-34) | ✅ | ✅ |

**Wynik bramki**: PASS (przed Phase 0 i po Phase 1) z 2 udokumentowanymi, czasowymi
odstępstwami i 2 uzasadnionymi dodatkami dla US5 (zob. Complexity Tracking).

**Uwagi do zgodności z `AGENTS.md`** (konwencje kodu, nie naruszenia):

- `AGENTS.md` wymienia RabbitMQ w stosie — konstytucja (Zasada VII i „Asynchroniczność”)
  ma pierwszeństwo: RabbitMQ nie jest dodawany, bo nie ma konsumenta poza procesem (R-17).
- Konwencja `WniosekProcessor`/walidacji XML z `AGENTS.md` pochodzi z innej domeny i nie
  dotyczy sklepu. Zalecana aktualizacja `AGENTS.md` (sekcje „opis projektu” i przykłady)
  w osobnym PR — zgodnie z Governance konstytucji.
- `realizacja` z minimalnego zestawu BC powstanie w funkcji integracji z Trello; ta funkcja
  dostarcza dla niej jedynie zdarzenie w Outboxie (zgodnie z założeniem spec).
- `AGENTS.md` wymaga package-private `*Service`, ale `api/` i `application/` to różne pakiety
  Javy, więc kontroler nie widziałby serwisu. Serwisy aplikacyjne są `public`, a ich użycie
  poza własnym BC blokuje ArchUnit (T012). Aktualizacja `AGENTS.md` w osobnym PR.

## Project Structure

### Documentation (this feature)

```text
specs/001-shop-browse-cart-checkout/
├── spec.md
├── plan.md                  # ten plik
├── research.md              # Phase 0 — decyzje R-01…R-34 (R-26…R-34: obserwowalność)
├── data-model.md            # Phase 1 — encje, tabele, maszyny stanów, fasady, porty metryk
├── quickstart.md            # Phase 1 — uruchomienie i scenariusze walidacyjne (US1–US5)
├── contracts/
│   ├── openapi.yaml         # kontrakt REST (API-first) + nagłówek X-Request-Id
│   ├── stripe-webhook.md    # kontrakt integracji Stripe (wychodzące + webhook + metryki)
│   ├── metrics.md           # katalog metryk, reguły alertów, dashboardy (US5)
│   └── frontend-routes.md   # trasy SPA, parametry URL, stany widoków
├── checklists/
│   └── requirements.md
└── tasks.md                 # Phase 2 — /speckit-tasks (do regeneracji: nie obejmuje jeszcze US5)
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
    │   │   │   ├── domain/                  # Pieniadze, GoscId, outbox/OutboxEventPublisher (port)
    │   │   │   ├── api/                     # KorelacjaFilter (X-Request-Id → MDC), GoscIdFilter (ciasteczko shop_guest), GlobalExceptionHandler (ProblemDetail)
    │   │   │   └── infrastructure/
    │   │   │       ├── outbox/              # OutboxEventJpaEntity, JpaOutboxEventPublisher (adapter)
    │   │   │       ├── metrics/             # PoCommicie, OutboxMetryki, FlywayMetryki, MetrykiConfig (MeterFilter: limit uri)
    │   │   │       └── config/              # AppProperties (APP_BASE_URL), maskowanie PII
    │   │   ├── katalog/
    │   │   │   ├── KatalogQueryFacade.java, KatalogCommandFacade.java  # + public record DTO
    │   │   │   ├── domain/                  # Produkt, Kategoria, StatusDostepnosci, KryteriaWyszukiwania, ProduktRepository
    │   │   │   ├── application/             # KatalogService
    │   │   │   ├── infrastructure/persistence/  # ProduktJpaEntity, KategoriaJpaEntity, ProduktJpaRepository, adaptery repozytoriów
    │   │   │   └── api/                     # KatalogController
    │   │   ├── koszyk/
    │   │   │   ├── KoszykQueryFacade.java, KoszykCommandFacade.java
    │   │   │   ├── domain/                  # Koszyk, PozycjaKoszyka, wyjątki domenowe, KoszykRepository
    │   │   │   ├── application/             # KoszykService (wycena przez KatalogQueryFacade), MetrykiKoszyka (port)
    │   │   │   ├── infrastructure/persistence/
    │   │   │   ├── infrastructure/metrics/  # MicrometerMetrykiKoszyka
    │   │   │   └── api/                     # KoszykController
    │   │   ├── zamowienie/
    │   │   │   ├── ZamowienieQueryFacade.java, ZamowienieOplaconeEvent.java
    │   │   │   ├── domain/                  # Zamowienie, PozycjaZamowienia, StatusZamowienia, DaneKlienta, AdresDostawy, NumerZamowienia, ZamowienieRepository
    │   │   │   ├── application/             # ZlozZamowienieService, ObslugaPlatnosciListener, MetrykiZamowien (port), RodzajRozbieznosci
    │   │   │   ├── infrastructure/persistence/
    │   │   │   ├── infrastructure/metrics/  # MicrometerMetrykiZamowien
    │   │   │   └── api/                     # ZamowienieController
    │   │   └── platnosc/
    │   │       ├── PlatnoscFacade.java, PlatnoscPotwierdzonaEvent.java, PlatnoscNieudanaEvent.java
    │   │       ├── domain/                  # Platnosc, StatusPlatnosci, BramkaPlatnosci (port), PotwierdzenieOperatora, PlatnoscRepository
    │   │       ├── application/             # PlatnoscService, ObslugaWebhookaService, MetrykiPlatnosci (port), WynikWebhooka, WynikPlatnosci
    │   │       ├── infrastructure/
    │   │       │   ├── persistence/         # PlatnoscJpaEntity, PrzetworzoneZdarzenieJpaEntity
    │   │       │   ├── metrics/             # MicrometerMetrykiPlatnosci
    │   │       │   └── stripe/              # StripeProperties, StripeBramkaPlatnosci (+ ponowienia i metryki wywołań), StripeWebhookVerifier
    │   │       └── api/                     # StripeWebhookController
    │   └── resources/
    │       ├── application.yaml, application-local.yaml   # + management.server.port=8081, exposure health,prometheus, histogramy (R-26)
    │       ├── openapi/shop-api.yaml        # kopia contracts/openapi.yaml
    │       ├── db/migration/                # V1__katalog.sql, V2__koszyk.sql, V3__zamowienie.sql, V4__platnosc.sql, V5__outbox.sql
    │       ├── db/seed/                     # R__seed_katalog.sql (profil local/e2e)
    │       └── static/images/
    └── test/java/com/project/custom/
        ├── ArchitectureTest.java, OpenApiContractTest.java, MetrykiEndpointIT.java
        ├── support/                         # IntegrationTest (Testcontainers), StripeWebhookSigner, MetrykiAssert
        ├── katalog/  koszyk/  zamowienie/  platnosc/   # domain/ (unit), application/ (integration), infrastructure/ (contract)

frontend/
├── package.json, vite.config.ts, tsconfig.json, playwright.config.ts
├── src/
│   ├── api/                 # schema.d.ts (generowany z contracts/openapi.yaml), client.ts
│   ├── app/                 # router, QueryClientProvider, Layout z nagłówkiem i licznikiem koszyka
│   ├── features/
│   │   ├── katalog/         # KatalogPage, ProduktPage, Filtry, useFiltryZUrl
│   │   ├── koszyk/          # KoszykPage, PozycjaKoszyka, useKoszyk (mutacje + invalidate)
│   │   └── zamowienie/      # ZamowieniePage (formularz + podsumowanie), PotwierdzeniePage (polling)
│   └── shared/              # formatPln, komponenty UI, toasty
└── tests/
    ├── unit/                # Vitest + Testing Library + MSW
    └── e2e/                 # Playwright: przegladanie, koszyk-dodawanie, koszyk-edycja, platnosc

observability/
├── prometheus/
│   ├── prometheus.yml       # scrape host.docker.internal:8081, interwał 15 s, rule_files
│   ├── rules/shop.yml       # 9 reguł z contracts/metrics.md §4
│   └── tests/shop.test.yml  # promtool: firing + resolved dla każdej reguły
└── grafana/
    ├── provisioning/
    │   ├── datasources/prometheus.yaml   # uid: prometheus
    │   └── dashboards/shop.yaml          # provider plików → folder „Sklep”
    └── dashboards/          # http.json, sciezka-zakupowa.json, platnosci-integracje.json, jvm-baza.json

scripts/check-dashboards.mjs # walidacja dashboardów i reguł względem contracts/metrics.md
compose.yaml                 # sqlserver (+ init bazy), prometheus, grafana; profil "stripe": stripe-cli listen
.env.example                 # + STRIPE_SECRET_KEY, STRIPE_WEBHOOK_SECRET, DB_PASSWORD, APP_BASE_URL, GRAFANA_ADMIN_PASSWORD
.pre-commit-config.yaml      # gitleaks
.github/workflows/ci.yml     # backend verify, frontend test/lint/typecheck, gitleaks, audyt zależności, promtool, check-dashboards, E2E (gdy są sekrety)
```

**Structure Decision**: aplikacja webowa w monorepo — `backend/` (jeden moduł Maven,
modularny monolit z pakietem na BC wg `AGENTS.md`) i `frontend/` (SPA). Granice BC
egzekwuje ArchUnit, a nie moduły budowania (R-01, R-03). Kontrakt `contracts/openapi.yaml`
jest współdzielony: kopia w zasobach backendu i źródło typów frontendu.

## Przepływy kluczowe (skrót)

1. **Złożenie zamówienia** (`POST /api/zamowienia`): `KoszykQueryFacade.wycen` → porównanie
   z potwierdzonym podsumowaniem (409 przy różnicy) → `PlatnoscFacade.wygasOtwarte(gosc)` →
   **TX1**: zapis `Zamowienie(OCZEKUJE_NA_PLATNOSC)` + `Platnosc(UTWORZONA)` →
   **Stripe** `checkout.sessions.create` (poza TX) → **TX2**: `Platnosc(OTWARTA, sessionId, url)`
   lub `Zamowienie(PLATNOSC_NIEUDANA)` + 503 → `201 {numer, urlPlatnosci}`.
2. **Webhook** (`POST /api/platnosci/stripe/webhook`): weryfikacja podpisu → **TX**:
   dedup `event.id` → `Platnosc(POTWIERDZONA)` → `PlatnoscPotwierdzonaEvent` →
   `zamowienie`: kontrola kwoty → `KatalogCommandFacade.zmniejszStan` →
   `OPLACONE` | `WYMAGA_WYJASNIENIA` → `KoszykCommandFacade.wyczysc` → `OutboxEvent` → commit → `200`.
3. **Powrót klienta** (`/zamowienie/{numer}`): tylko odczyt i polling statusu — żadnej zmiany stanu.
4. **Metryki** (przekrojowo): serwis aplikacyjny → port `Metryki*` → adapter Micrometer →
   `PoCommicie` (zapis dopiero po commicie; rollback nic nie liczy) → rejestr → `:8081/actuator/prometheus`
   ← Prometheus co 15 s → reguły alertów i dashboardy Grafany. Webhook: wynik weryfikacji/deduplikacji
   → `shop.platnosc.webhook`, a po commicie `shop.platnosci` i opóźnienie względem `event.created`.

## Kolejność realizacji (wejście dla `/speckit-tasks`)

1. **Fundament**: szkielet `backend/` i `frontend/`, `compose.yaml`, Flyway, `shared`
   (`Pieniadze`, `GoscId`, filtr ciasteczka, obsługa błędów), `StripeProperties` z walidacją,
   `.env.example`, gitleaks, CI, ArchitectureTest, kontrakt w zasobach i typy frontendu.
2. **US1** katalog: migracja + seed, wyszukiwanie z kolacją, API, strony listy i karty.
3. **US2** koszyk — dodawanie: agregat `Koszyk`, API POST/GET, licznik w nagłówku.
4. **US3** koszyk — edycja: PUT/DELETE, wycena ze zmianą ceny/dostępności, akceptacja cen.
5. **US4** zamówienie i płatność: `zamowienie` + `platnosc`, adapter Stripe, webhook,
   Outbox, strony zamówienia i potwierdzenia.
6. **US5** obserwowalność — dwie części:
   - **Fundament** (w fazie 1 razem z resztą szkieletu, żeby porty metryk istniały, zanim
     powstaną serwisy): Actuator na `8081`, rejestr Prometheus, `PoCommicie`, `KorelacjaFilter`,
     reguła ArchUnit dla `io.micrometer`, kontenery Prometheus/Grafana z provisioningiem.
   - **Właściwa US5** (po US4): porty i adaptery `Metryki*` wpięte w serwisy US2–US4, metryki
     Stripe i webhooka (w tym `payment_intent.payment_failed`), gauge'e Outboxa i Flyway,
     reguły + testy `promtool`, 4 dashboardy, `MetrykiEndpointIT`, `check-dashboards.mjs`.
7. **Domknięcie**: E2E 4 ścieżek, test wydajności na 500 produktach (także narzut metryk),
   przejście `quickstart.md` z sekcją US5.

US1–US3 nie wymagają konta ani połączenia ze Stripe i mogą być demonstrowane niezależnie
(Zasada IV) — aplikacja startuje z atrapami kluczy w formacie `sk_test_…`/`whsec_…`
(walidacja formatu z Zasad I/II obowiązuje zawsze).

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Brak BC `realizacja` z minimalnego zestawu (Zasada III) | Spec wyłącza integrację z Trello do osobnej funkcji; ta funkcja dostarcza tylko zdarzenie `ZamowienieOplaconeEvent` w Outboxie | Pusty pakiet `realizacja` bez przypadków użycia to martwy kod (Zasada VII); BC powstanie w funkcji integracji z Trello |
| Outbox bez joba wysyłki (Zasada V: „wysyłka przez job z ponawianiem”) | Nie ma jeszcze odbiorcy zdarzeń; zapis w jednej transakcji gwarantuje, że zdarzenie nie zginie (R-17) | Job bez handlerów to martwy kod; `OutboxEventSchedulerJob` powstanie razem z handlerem Trello w funkcji `realizacja` |
| Nowe kontenery Prometheus + Grafana (Zasada VII) | US5, FR-032/FR-033 i założenie spec („wyraźne życzenie właściciela projektu”): dashboardy i alerty bez ręcznej konfiguracji | Same metryki Actuatora bez Prometheusa — brak historii, zapytań `histogram_quantile` i reguł alertów; Grafana Cloud — zewnętrzne konto i sekret; Alertmanager — wysyłka powiadomień poza zakresem spec |
| Reguła `OutboxZalegly` stale aktywna lokalnie do czasu funkcji `realizacja` (R-30) | FR-034 wymaga reguły teraz, a brak joba wysyłki (R-17) oznacza, że zdarzenia rzeczywiście czekają | Odłożenie reguły — niezgodne z FR-034; job oznaczający zdarzenia jako wysłane bez handlera — fałszuje metrykę; reguła oznaczona `wymaga="realizacja"` i opisana w dashboardzie |
