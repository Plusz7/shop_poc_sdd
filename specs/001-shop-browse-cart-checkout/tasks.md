---

description: "Lista zadań implementacyjnych: przeglądanie sklepu, koszyk i płatność"
---

# Tasks: Przeglądanie sklepu, koszyk i płatność

**Input**: Dokumenty projektowe z `/specs/001-shop-browse-cart-checkout/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md),
[data-model.md](data-model.md), [contracts/](contracts/), [quickstart.md](quickstart.md)

**Tests**: WYMAGANE — konstytucja (Zasada VI) i `AGENTS.md` nakazują testy jednostkowe domeny
(bez Springa), integracyjne każdego `*Service` (Testcontainers, prawdziwa baza), kontraktowe
adapterów Stripe (`stripe-mock`/WireMock), webhook z poprawnym i sfałszowanym podpisem
oraz E2E (Playwright) dla każdej ścieżki P1. Testy piszemy PRZED implementacją i muszą najpierw
nie przechodzić.

**Organization**: Zadania pogrupowane wg historii użytkownika (US1–US4, wszystkie P1), tak by
każdą dało się wdrożyć i zademonstrować niezależnie (Zasada IV). US1–US3 nie wymagają konta
ani połączenia ze Stripe — aplikacja startuje z atrapami kluczy w formacie `sk_test_…`/`whsec_…`
(walidacja formatu z Zasad I/II obowiązuje zawsze).

## Format: `[ID] [P?] [Story] Opis`

- **[P]**: można wykonywać równolegle (inne pliki, brak zależności od niedokończonych zadań)
- **[Story]**: historia użytkownika, do której należy zadanie (US1, US2, US3, US4)
- Każde zadanie podaje dokładną ścieżkę pliku

## Konwencje ścieżek

- Backend (kod): `backend/src/main/java/com/project/custom/<bc>/...`
- Backend (zasoby): `backend/src/main/resources/...`
- Backend (testy): `backend/src/test/java/com/project/custom/<bc>/{domain,application,infrastructure}/...`
- Frontend: `frontend/src/...`, testy: `frontend/tests/unit/...`, `frontend/tests/e2e/...`
- Reguły widoczności (`AGENTS.md`, doprecyzowane w plan.md): `*Facade` = `public interface` w korzeniu
  BC + package-private implementacja w `application/`; `domain/*Repository` = `public interface`;
  `*Service` = `public` (wymagane przez Javę, bo `api/` to inny pakiet), ale używane WYŁĄCZNIE
  wewnątrz własnego BC — egzekwuje ArchUnit (T012, reguła 6); `*Controller`, `*JpaEntity`,
  adaptery repozytoriów = package-private; DTO przez fasadę = `public record` z sufiksem `Dto`;
  tylko constructor injection.

---

## Phase 1: Setup (wspólna infrastruktura)

**Purpose**: Szkielet monorepo `backend/` + `frontend/`, zależności lokalne, sekrety, bramki CI.

- [ ] T001 Utwórz projekt Maven w `backend/pom.xml` (Java 21, Spring Boot 4.0: web, data-jpa, validation, actuator; `flyway-core` + `flyway-sqlserver`; `mssql-jdbc`; `com.stripe:stripe-java`; springdoc-openapi linii zgodnej z Boot 4; test: `spring-boot-starter-test`, `spring-boot-testcontainers`, `testcontainers` `mssqlserver` i `junit-jupiter`, `archunit-junit5`, `wiremock-standalone`, AssertJ) wraz z wrapperem `backend/mvnw`, `backend/mvnw.cmd`, `backend/.mvn/wrapper/maven-wrapper.properties`; dodaj `backend/osv-scanner.toml` (pusty, z komentarzem o formacie wyjątków: identyfikator podatności + uzasadnienie)
- [ ] T002 Utwórz klasę startową `backend/src/main/java/com/project/custom/ShopApplication.java` (`@SpringBootApplication`, `@ConfigurationPropertiesScan`) oraz puste pakiety BC `shared`, `katalog`, `koszyk`, `zamowienie`, `platnosc` zgodnie ze strukturą z plan.md
- [ ] T003 [P] Zainicjalizuj frontend w `frontend/package.json`, `frontend/vite.config.ts` (port 5173, proxy `/api` i `/images` → `http://localhost:8080`), `frontend/tsconfig.json` (`strict: true`), `frontend/index.html`; zależności: React 19, React Router, TanStack Query, React Hook Form, Zod, `openapi-fetch`; dev: `openapi-typescript`, Vitest, Testing Library, MSW, Playwright, ESLint, Prettier
- [ ] T004 [P] Utwórz `compose.yaml` w katalogu głównym: serwis `sqlserver` (`mcr.microsoft.com/mssql/server:2022-latest`, `MSSQL_SA_PASSWORD=${DB_PASSWORD}`, healthcheck, jednorazowy kontener/skrypt tworzący bazę `shop`) oraz serwis `stripe-cli` w profilu `stripe` (`stripe/stripe-cli`, `listen --forward-to http://host.docker.internal:8080/api/platnosci/stripe/webhook`, klucz z `STRIPE_SECRET_KEY`)
- [ ] T005 [P] Rozszerz `.env.example` o puste `STRIPE_SECRET_KEY` (opis: tylko `sk_test_…`), `STRIPE_WEBHOOK_SECRET` (`whsec_…` z `stripe listen`), `DB_PASSWORD`, `APP_BASE_URL=http://localhost:5173`, opcjonalny `STRIPE_API_BASE`; w komentarzu: „Do pracy nad US1–US3 bez konta Stripe wystarczą atrapy: `STRIPE_SECRET_KEY=sk_test_dummy`, `STRIPE_WEBHOOK_SECRET=whsec_dummy` (płatność zwróci 503)”; potwierdź, że `.env` jest w `.gitignore`
- [ ] T006 [P] Dodaj hook gitleaks w `.pre-commit-config.yaml` (Zasada I)
- [ ] T007 [P] Dodaj skrypty uruchomienia backendu czytające `.env` jawnie (bez `spring-dotenv`, R-20) w `scripts/run-backend.ps1` i `scripts/run-backend.sh` (profil `local`)
- [ ] T008 [P] Skonfiguruj lint i formatowanie frontendu w `frontend/eslint.config.js` i `frontend/.prettierrc`; skrypty `lint`, `typecheck`, `test`, `e2e`, `api:types` w `frontend/package.json`
- [ ] T009 Skopiuj kontrakt `specs/001-shop-browse-cart-checkout/contracts/openapi.yaml` do `backend/src/main/resources/openapi/shop-api.yaml` i wygeneruj typy frontendu `frontend/src/api/schema.d.ts` skryptem `api:types` (`openapi-typescript`, z trybem `--check` porównującym z kontraktem)
- [ ] T010 Utwórz pipeline CI w `.github/workflows/ci.yml`: `./backend/mvnw -f backend/pom.xml verify`, `npm --prefix frontend ci` + `lint` + `typecheck` + `test` + `api:types -- --check`, `gitleaks detect`, audyt zależności: `osv-scanner scan source --lockfile backend/pom.xml --lockfile frontend/package-lock.json` (fail przy CVSS ≥ 7.0, wyjątki tylko przez `backend/osv-scanner.toml` z uzasadnieniem) oraz `npm --prefix frontend audit --audit-level=high`, job E2E uruchamiany tylko gdy ustawione sekrety `STRIPE_TEST_*`

---

## Phase 2: Foundational (blokujące wymagania wstępne)

**Purpose**: Wspólne jądro, konfiguracja z walidacją sekretów, obsługa błędów, identyfikacja gościa,
infrastruktura testów i powłoka SPA — wymagane przez WSZYSTKIE historie.

**⚠️ CRITICAL**: Żadna historia użytkownika nie może się zacząć przed ukończeniem tej fazy.

### Testy fundamentu

- [ ] T011 [P] Test jednostkowy `Pieniadze` (grosze ≥ 0, waluta PLN, `plus`, `razy(int)`, równość po wartości, odrzucenie wartości ujemnych) w `backend/src/test/java/com/project/custom/shared/domain/PieniadzeTest.java`
- [ ] T012 [P] Test architektury ArchUnit (R-03): (1) klasy BC A nie importują z BC B nic poza jego pakietem głównym (`*Facade`, publiczne rekordy DTO/zdarzeń); `shared.domain..` i `shared.api..` (tylko `GoscId`/resolver) są dozwolone dla wszystkich BC; `shared.infrastructure..` — tylko dla `shared` i konfiguracji Springa; (2) `..domain..` nie zależy od `org.springframework..`, `jakarta.persistence..`, `com.stripe..`; (3) brak `@Autowired` na polach; (4) brak zależności od `ApplicationContext`; (5) `com.stripe..` używane tylko w `platnosc.infrastructure.stripe..`; (6) klasy z `<bc>.application..` i `<bc>.infrastructure..` są używane tylko przez klasy tego samego BC; (7) `..application..` nie zależy od `..infrastructure..` (DIP) — w `backend/src/test/java/com/project/custom/ArchitectureTest.java`
- [ ] T013 [P] Test startu aplikacji: brak `shop.stripe.secret-key`, klucz `sk_live_…` lub `webhook-secret` bez prefiksu `whsec_` → kontekst nie wstaje z komunikatem „Brak wymaganej konfiguracji: shop.stripe.secret-key” i BEZ wartości sekretu w wyjątku/logu, w `backend/src/test/java/com/project/custom/platnosc/infrastructure/stripe/StripePropertiesValidationTest.java` (`ApplicationContextRunner`)
- [ ] T014 [P] Test filtra gościa: pierwsze żądanie `/api/koszyk` nadaje ciasteczko `shop_guest` (UUIDv4, `HttpOnly`, `SameSite=Lax`, `Path=/`, `Max-Age=2592000`, `Secure` poza profilem `local`), kolejne żądanie z ciasteczkiem zachowuje ten sam `GoscId`, niepoprawny UUID w ciasteczku → nowe ciasteczko, żądania `/api/produkty` nie dostają ciasteczka — w `backend/src/test/java/com/project/custom/shared/api/GoscIdFilterTest.java`

### Implementacja fundamentu

- [ ] T015 Utwórz `backend/src/main/resources/application.yaml` (datasource z `${DB_URL}`/`${DB_USER}`/`${DB_PASSWORD}` bez wartości domyślnych dla sekretów; `spring.jpa.hibernate.ddl-auto=validate`; `spring.jpa.open-in-view=false`; Flyway `classpath:db/migration`; `shop.stripe.secret-key=${STRIPE_SECRET_KEY}`, `shop.stripe.webhook-secret=${STRIPE_WEBHOOK_SECRET}`, `connect-timeout=5s`, `read-timeout=10s`, `max-network-retries=2`, `api-base=${STRIPE_API_BASE:https://api.stripe.com}`; `shop.app.base-url=${APP_BASE_URL}`; springdoc `api-docs` włączone, Swagger UI wyłączone) oraz `backend/src/main/resources/application-local.yaml` (Flyway dodatkowo `classpath:db/seed`, Swagger UI włączone, ciasteczko bez `Secure`)
- [ ] T016 [P] Zaimplementuj VO `Pieniadze` (`long grosze ≥ 0`, `Currency waluta = PLN`, `plus`, `razy(int)`, bez `double`/`float`) w `backend/src/main/java/com/project/custom/shared/domain/Pieniadze.java`
- [ ] T017 [P] Zaimplementuj VO `GoscId` (UUID, nigdy pusty, `fromString` z walidacją) w `backend/src/main/java/com/project/custom/shared/domain/GoscId.java`
- [ ] T018 [P] Zaimplementuj `AppProperties` (`@ConfigurationProperties("shop.app")`, `@Validated`, `@NotNull URI baseUrl`, parametry ciasteczka) w `backend/src/main/java/com/project/custom/shared/infrastructure/config/AppProperties.java`
- [ ] T019 [P] Zaimplementuj maskowanie danych osobowych do logów (`d***@g***.com`, adres → skrót; nigdy klucze) w `backend/src/main/java/com/project/custom/shared/infrastructure/config/MaskowanieDanych.java`
- [ ] T020 [P] Zaimplementuj `StripeProperties` jako `@ConfigurationProperties("shop.stripe") @Validated record` z `@NotBlank @Pattern(regexp = "sk_test_.+") secretKey`, `@NotBlank @Pattern(regexp = "whsec_.+") webhookSecret`, `Duration connectTimeout`, `Duration readTimeout`, `int maxNetworkRetries`, `URI apiBase` (R-20); komunikaty walidacji bez wartości, w `backend/src/main/java/com/project/custom/platnosc/infrastructure/stripe/StripeProperties.java`
- [ ] T021 Zaimplementuj filtr `GoscIdFilter` (ciasteczko `shop_guest` wg R-08, tylko ścieżki `/api/koszyk*` i `/api/zamowienia*`, odnawianie `Max-Age` przy zmianie koszyka, zapis `GoscId` w atrybucie żądania) w `backend/src/main/java/com/project/custom/shared/api/GoscIdFilter.java` oraz resolver parametru kontrolera w `backend/src/main/java/com/project/custom/shared/api/GoscIdArgumentResolver.java` i rejestrację w `backend/src/main/java/com/project/custom/shared/api/WebConfig.java` (tamże mapowanie zasobów `/images/**` → `classpath:/static/images/`)
- [ ] T022 Zaimplementuj globalną obsługę błędów RFC 9457 (`ProblemDetail` + pola `kod` i `bledy[] = {pole, komunikat}`; kody wg kontraktu: `BLAD_WALIDACJI`, `NIE_ZNALEZIONO`, `ILOSC_PRZEKRACZA_LIMIT`, `PRODUKT_NIEDOSTEPNY`, `PODSUMOWANIE_NIEAKTUALNE`, `KOSZYK_NIE_DO_ZAMOWIENIA`, `ZAMOWIENIE_JUZ_OPLACONE`, `PLATNOSC_NIEDOSTEPNA`, `KONFLIKT_WSPOLBIEZNOSCI`, `BLAD_WEWNETRZNY`; `MethodArgumentNotValidException`/`HttpMessageNotReadableException`/błąd typu parametru → `400 BLAD_WALIDACJI`; `OptimisticLockingFailureException` → `409 KONFLIKT_WSPOLBIEZNOSCI`; brak sekretów i danych osobowych w odpowiedziach) w `backend/src/main/java/com/project/custom/shared/api/GlobalExceptionHandler.java` i `backend/src/main/java/com/project/custom/shared/api/KodBledu.java`
- [ ] T023 Utwórz wsparcie testów integracyjnych: `backend/src/test/java/com/project/custom/support/IntegrationTest.java` (adnotacja/klasa bazowa `@SpringBootTest` + współdzielony `MSSQLServerContainer` z `@ServiceConnection`, czyszczenie tabel między testami) oraz `backend/src/test/resources/application-test.yaml` (testowy `sk_test_dummy`, `whsec_test_…` generowany dla testów, `APP_BASE_URL=http://localhost:5173`, `api-base` wskazywany przez testy)
- [ ] T024 [P] Utwórz klienta API `frontend/src/api/client.ts` (`openapi-fetch` z typami `schema.d.ts`, `credentials: 'same-origin'`, parsowanie `application/problem+json` do typu `Problem` z `kod` i `bledy[]`)
- [ ] T025 [P] Utwórz `frontend/src/shared/formatPln.ts` (`Intl.NumberFormat('pl-PL', {style: 'currency', currency: 'PLN'})` z groszy) i test `frontend/tests/unit/shared/formatPln.test.ts`
- [ ] T026 [P] Utwórz system toastów i banerów w `frontend/src/shared/toast/ToastProvider.tsx`, `frontend/src/shared/toast/useToast.ts` i `frontend/src/shared/Baner.tsx` (dostępne: `role="status"`/`role="alert"`)
- [ ] T027 Utwórz powłokę aplikacji: `frontend/src/main.tsx`, `frontend/src/app/router.tsx` (`createBrowserRouter`, trasy `/`, `/produkt/:id`, `/koszyk`, `/zamowienie`, `/zamowienie/:numer`, `*` → `frontend/src/app/NotFoundPage.tsx`), `frontend/src/app/queryClient.ts`, `frontend/src/app/Layout.tsx` z nagłówkiem (logo/link `/`, miejsce na wyszukiwarkę i licznik koszyka) oraz responsywne style bazowe od 360 px w `frontend/src/app/Layout.module.css`
- [ ] T028 [P] Skonfiguruj testy jednostkowe frontendu: `frontend/vitest.config.ts`, `frontend/tests/unit/setup.ts` (Testing Library + MSW), `frontend/tests/unit/msw/handlers.ts`, `frontend/tests/unit/renderWithProviders.tsx` (router + QueryClient + toasty)
- [ ] T029 [P] Skonfiguruj Playwright w `frontend/playwright.config.ts` (baseURL `http://localhost:5173`, projekty desktop i mobile 360 px) i pomocnicze `frontend/tests/e2e/fixtures.ts` (reset koszyka przez nowy kontekst przeglądarki, pomocnik zmiany ceny/stanu produktu przez SQL w bazie E2E)

**Checkpoint**: `mvnw verify` przechodzi (ArchitectureTest, walidacja sekretów, filtr gościa), `npm test` przechodzi, SPA startuje z pustym layoutem.

---

## Phase 3: User Story 1 — Przeglądanie i wyszukiwanie produktów (Priority: P1) 🎯 MVP

**Goal**: Klient przegląda katalog: lista z paginacją (24/stronę), kategorie, wyszukiwanie bez
wielkości liter i diakrytyków, filtr ceny, sortowanie — wszystko w URL; karta produktu z opisem,
zdjęciami, ceną i dostępnością (FR-001–FR-005).

**Independent Test**: Na seedzie ≥ 500 produktów: `/` pokazuje 24 produkty + kategorie; wybór
kategorii i wyszukanie `LODZ` znajduje „łódź”; filtr 50–200 zł + „cena malejąco” zmienia URL
i wyniki; skopiowany URL daje te same wyniki; fraza `%_[zzz` → „Brak wyników”; strona 2 zachowuje
filtry; karta produktu ze stanem 2 → „Ostatnie sztuki” (quickstart 1.1–1.8). Bez koszyka.

### Testy dla User Story 1 ⚠️ (najpierw, muszą nie przechodzić)

- [ ] T030 [P] [US1] Test jednostkowy `Produkt`: status dostępności (`!aktywny` lub `stan = 0` → `NIEDOSTEPNY`; `1 ≤ stan ≤ 3` → `OSTATNIE_SZTUKI`; `stan > 3` → `DOSTEPNY`), `maksDoKupienia() = min(stan, 99)`, `czyMoznaZmniejszyc(n)`, `zmniejszStan(n)` rzuca przy `n > stan` — w `backend/src/test/java/com/project/custom/katalog/domain/ProduktTest.java`
- [ ] T031 [P] [US1] Test jednostkowy `KryteriaWyszukiwania`: fraza `trim()` i przycięcie do 100 znaków, escapowanie znaków `%`, `_`, `[`, `\` do wzorca `LIKE … ESCAPE '\'`, `cenaOd ≤ cenaDo` (inaczej zamiana), `strona ≥ 0`, `rozmiar ∈ [1, 48]` (domyślnie 24), sortowanie z białej listy `CENA_ROSNACO | CENA_MALEJACO | NAZWA` — w `backend/src/test/java/com/project/custom/katalog/domain/KryteriaWyszukiwaniaTest.java`
- [ ] T032 [P] [US1] Test integracyjny `KatalogService` + `GET /api/kategorie`, `GET /api/produkty`, `GET /api/produkty/{id}` (MockMvc): lista zwraca tylko `aktywny = 1` z głównym zdjęciem (`kolejnosc 0`); filtr `kategoria` (slug); `q=LODZ` znajduje „Łódź …” (kolacja `Polish_100_CI_AI`); `q=%_[zzz` → pusta strona bez błędu; `cenaOd`/`cenaDo` w groszach; `sort=cena_desc|cena_asc|nazwa_asc` ze stabilnym drugim kluczem `id`; paginacja `strona`/`rozmiar`, `rozmiar=49` lub `sort=xyz` → `400 BLAD_WALIDACJI`; karta produktu nieistniejącego lub nieaktywnego → `404 NIE_ZNALEZIONO` — w `backend/src/test/java/com/project/custom/katalog/application/KatalogServiceIT.java`
- [ ] T033 [P] [US1] Test jednostkowy hooka stanu filtrów w URL: odczyt/zapis `kategoria`, `q`, `cenaOd`/`cenaDo` (UI w złotych ↔ URL/API w groszach), `sort` (domyślny `nazwa_asc` pomijany w URL), `strona` (URL od 1 ↔ API od 0); zmiana filtra resetuje `strona` do 1, zmiana strony zachowuje filtry — w `frontend/tests/unit/katalog/useFiltryZUrl.test.tsx`
- [ ] T034 [P] [US1] Test komponentu `KatalogPage` (MSW): kafelki ze zdjęciem, nazwą, ceną i etykietą dostępności; nawigacja kategorii; stan „Brak wyników” z przyciskiem „Wyczyść filtry” nawigującym do `/` — w `frontend/tests/unit/katalog/KatalogPage.test.tsx`
- [ ] T035 [P] [US1] Test E2E ścieżki przeglądania (quickstart 1.1–1.8) w `frontend/tests/e2e/przegladanie.spec.ts`

### Implementacja User Story 1

- [ ] T036 [US1] Utwórz migrację `backend/src/main/resources/db/migration/V1__katalog.sql`: `kategoria(id BIGINT IDENTITY PK, nazwa NVARCHAR(80) NOT NULL UNIQUE, slug VARCHAR(80) NOT NULL UNIQUE, kolejnosc INT)`; `produkt(id BIGINT IDENTITY PK, nazwa NVARCHAR(200) COLLATE Polish_100_CI_AI NOT NULL, opis NVARCHAR(4000), cena_grosze BIGINT NOT NULL CHECK (cena_grosze > 0), kategoria_id BIGINT NOT NULL FK → kategoria, stan INT NOT NULL CHECK (stan >= 0), aktywny BIT NOT NULL, wersja BIGINT NOT NULL)`; `produkt_zdjecie(produkt_id BIGINT FK → produkt, kolejnosc INT CHECK (kolejnosc >= 0), url NVARCHAR(300) NOT NULL, alt NVARCHAR(200) NOT NULL, PK (produkt_id, kolejnosc))`; indeksy `produkt(kategoria_id, aktywny, cena_grosze)`, `produkt(aktywny, cena_grosze)`, `produkt(aktywny, nazwa)`
- [ ] T037 [P] [US1] Utwórz seed `backend/src/main/resources/db/seed/R__seed_katalog.sql` (kilkanaście kategorii ze slugami `[a-z0-9-]+`; ≥ 500 produktów generowanych rekursywnym CTE z ≥ 1 zdjęciem każdy; stałe produkty do scenariuszy: nazwa zawierająca „łódź”, produkt ze `stan = 2`, `stan = 3`, `stan = 5`, `stan = 0`, produkt `aktywny = 0`, ceny w przedziale 50–200 zł) oraz kilkanaście zdjęć w `backend/src/main/resources/static/images/`
- [ ] T038 [P] [US1] Utwórz typy domenowe `KategoriaId`, `Kategoria`, `ProduktId`, `Zdjecie`, `StatusDostepnosci` w `backend/src/main/java/com/project/custom/katalog/domain/` (bez adnotacji frameworkowych)
- [ ] T039 [US1] Zaimplementuj agregat `Produkt` (`statusDostepnosci()`, `maksDoKupienia()`, `czyMoznaZmniejszyc(n)`, `zmniejszStan(n)`) w `backend/src/main/java/com/project/custom/katalog/domain/Produkt.java` (zależy od T038)
- [ ] T040 [P] [US1] Zaimplementuj VO `KryteriaWyszukiwania` i enum `Sortowanie` wg reguł z T031 w `backend/src/main/java/com/project/custom/katalog/domain/KryteriaWyszukiwania.java` i `backend/src/main/java/com/project/custom/katalog/domain/Sortowanie.java`
- [ ] T041 [US1] Zdefiniuj porty `ProduktRepository` (`szukaj(KryteriaWyszukiwania) → StronaWynikow<ProduktNaLiscie>`, `znajdzAktywny(ProduktId)`) i `KategoriaRepository` (`wszystkie()` wg `kolejnosc`, `znajdzPoSlugu`) w `backend/src/main/java/com/project/custom/katalog/domain/ProduktRepository.java` i `backend/src/main/java/com/project/custom/katalog/domain/KategoriaRepository.java` (read model listy `ProduktNaLiscie`, `StronaWynikow` w tym samym pakiecie)
- [ ] T042 [US1] Utwórz encje JPA `KategoriaJpaEntity`, `ProduktJpaEntity` (`@Version wersja`), `ProduktZdjecieJpaEntity` oraz `KategoriaJpaRepository`, `ProduktJpaRepository` w `backend/src/main/java/com/project/custom/katalog/infrastructure/persistence/`
- [ ] T043 [US1] Zaimplementuj adaptery `ProduktRepositoryAdapter` (zapytanie z `nazwa LIKE :wzorzec ESCAPE '\'` z bindowanym parametrem, filtr `aktywny = 1`, biała lista sortowania + `id`, projekcja DTO ze zdjęciem głównym jednym zapytaniem — bez N+1, R-24) i `KategoriaRepositoryAdapter` w `backend/src/main/java/com/project/custom/katalog/infrastructure/persistence/` z mapowaniem JPA ↔ domena w `KatalogPersistenceMapper.java`
- [ ] T044 [US1] Zaimplementuj `KatalogService` (listaKategorii, szukaj(kryteria), karta(produktId) → `NieZnaleziono` dla nieaktywnych/nieistniejących) w `backend/src/main/java/com/project/custom/katalog/application/KatalogService.java`
- [ ] T045 [US1] Zaimplementuj `KatalogController` (`GET /api/kategorie`, `GET /api/produkty` z parametrami `kategoria`, `q`, `cenaOd`, `cenaDo`, `sort ∈ {nazwa_asc, cena_asc, cena_desc}`, `strona ≥ 0`, `rozmiar 1..48` domyślnie 24; `GET /api/produkty/{id}`) mapujący na schematy `Kategoria`, `StronaProduktow`, `ProduktNaLiscie`, `ProduktSzczegoly` z kontraktu, w `backend/src/main/java/com/project/custom/katalog/api/KatalogController.java` i `backend/src/main/java/com/project/custom/katalog/api/KatalogApiMapper.java`
- [ ] T046 [P] [US1] Utwórz hooki zapytań `useKategorie`, `useProdukty(filtry)`, `useProdukt(id)` (TanStack Query) w `frontend/src/features/katalog/api.ts`
- [ ] T047 [P] [US1] Zaimplementuj `useFiltryZUrl` wg `contracts/frontend-routes.md` w `frontend/src/features/katalog/useFiltryZUrl.ts`
- [ ] T048 [P] [US1] Utwórz komponenty `EtykietaDostepnosci.tsx` („Dostępny” / „Ostatnie sztuki” / „Niedostępny” wyłącznie z pola API `status`), `ProduktKafelek.tsx` (zdjęcie `loading="lazy"` o stałych proporcjach, nazwa, cena, etykieta, link do `/produkt/:id`), `Paginacja.tsx` w `frontend/src/features/katalog/`
- [ ] T049 [US1] Utwórz `Filtry.tsx` (nawigacja kategorii, cena od–do w złotych, sortowanie) i `KatalogPage.tsx` (siatka 2/3/4 kolumny, stan pusty „Brak wyników” + „Wyczyść filtry”, stany ładowania/błędu) w `frontend/src/features/katalog/` z stylami `KatalogPage.module.css`
- [ ] T050 [US1] Utwórz `ProduktPage.tsx` (nazwa, galeria zdjęć, opis, cena, etykieta dostępności, 404 → „Nie znaleziono produktu”) w `frontend/src/features/katalog/ProduktPage.tsx`
- [ ] T051 [US1] Podłącz wyszukiwarkę w nagłówku (zatwierdzanie Enterem/przyciskiem → `/?q=…`, reset `strona`) w `frontend/src/app/Wyszukiwarka.tsx` i `frontend/src/app/Layout.tsx`; zarejestruj trasy `/` i `/produkt/:id` w `frontend/src/app/router.tsx`

**Checkpoint**: US1 działa samodzielnie — quickstart 1.1–1.8 i `przegladanie.spec.ts` zielone.

---

## Phase 4: User Story 2 — Dodawanie produktów do koszyka (Priority: P1)

**Goal**: Gość dodaje produkt z karty (z ilością) lub z listy (1 szt.); pozycje się scalają;
dodanie ponad `min(stan, 99)` jest blokowane z informacją o maksimum; niedostępny produkt ma
nieaktywny przycisk; koszyk przetrwa odświeżenie (ciasteczko 30 dni); licznik w nagłówku
(FR-006–FR-008, FR-012, FR-013).

**Independent Test**: Dodaj 2 szt. z karty → potwierdzenie i licznik +2; dodaj ten sam z listy →
jedna pozycja z ilością 3; produkt ze stanem 3 i 2 w koszyku → dodanie 2 zablokowane „Możesz dodać
maksymalnie 1 szt.”; produkt niedostępny → przycisk nieaktywny; odświeżenie zachowuje koszyk
(quickstart 2.1–2.5).

### Testy dla User Story 2 ⚠️

- [ ] T052 [P] [US2] Test jednostkowy agregatu `Koszyk` — dodawanie: `n ∈ 1..99`, istniejąca pozycja → `ilosc += n` (jedna pozycja, FR-007), wynik > `maksDoKupienia` → `IloscPrzekraczaLimit(maks)` bez zmian stanu (US2-3), produkt `NIEDOSTEPNY` → `ProduktNiedostepny` (US2-4), `cenaPrzyDodaniu` zapamiętana tylko informacyjnie, `zmieniono` aktualizowane — w `backend/src/test/java/com/project/custom/koszyk/domain/KoszykTest.java`
- [ ] T053 [P] [US2] Test jednostkowy wyceny koszyka `WycenaKoszyka` (wartość pozycji = aktualna cena × ilość, `liczbaSztuk` = suma ilości, `suma` = suma wartości pozycji dostępnych) w `backend/src/test/java/com/project/custom/koszyk/domain/WycenaKoszykaTest.java`
- [ ] T054 [P] [US2] Test integracyjny `KoszykService` + `GET /api/koszyk`, `POST /api/koszyk/pozycje` (MockMvc): pierwszy GET bez ciasteczka → pusty koszyk i nowe `shop_guest`; dodanie 2 szt. → pozycja z ceną z katalogu (cena w ciele żądania ignorowana, FR-010); ponowne dodanie scala; przekroczenie limitu → `409 ILOSC_PRZEKRACZA_LIMIT` z `maksIlosc`; produkt niedostępny/nieaktywny → `409 PRODUKT_NIEDOSTEPNY`; nieistniejący → `404`; `ilosc` `0`, `-1`, `100`, `1.5`, `"abc"` → `400 BLAD_WALIDACJI`; koszyk odczytany ponownie z tym samym ciasteczkiem zachowuje zawartość; inne ciasteczko → inny koszyk — w `backend/src/test/java/com/project/custom/koszyk/application/KoszykServiceIT.java`
- [ ] T055 [P] [US2] Test komponentów dodawania (MSW): na karcie wybór ilości i „Dodaj do koszyka” → toast potwierdzenia i odświeżony licznik; `409 ILOSC_PRZEKRACZA_LIMIT` → toast „Możesz dodać maksymalnie N szt.”; status `NIEDOSTEPNY` → przycisk z `aria-disabled` i wyjaśnieniem — w `frontend/tests/unit/koszyk/DodajDoKoszyka.test.tsx`
- [ ] T056 [P] [US2] Test E2E ścieżki dodawania (quickstart 2.1–2.5, w tym odświeżenie strony) w `frontend/tests/e2e/koszyk-dodawanie.spec.ts`

### Implementacja User Story 2

- [ ] T057 [US2] Utwórz migrację `backend/src/main/resources/db/migration/V2__koszyk.sql`: `koszyk(id UNIQUEIDENTIFIER PK, gosc_id UNIQUEIDENTIFIER NOT NULL UNIQUE, zmieniono DATETIMEOFFSET(3) NOT NULL, wersja BIGINT NOT NULL)`; `pozycja_koszyka(koszyk_id UNIQUEIDENTIFIER FK → koszyk, produkt_id BIGINT NOT NULL /* bez FK — inny BC */, ilosc INT NOT NULL CHECK (ilosc BETWEEN 1 AND 99), cena_przy_dodaniu_grosze BIGINT NOT NULL, dodano DATETIMEOFFSET(3) NOT NULL, PK (koszyk_id, produkt_id))`; indeks `pozycja_koszyka(koszyk_id)`
- [ ] T058 [P] [US2] Dodaj fasadę katalogu dla innych BC: `public interface KatalogQueryFacade` (`pobierzDoWyceny(Set<Long> ids) → Map<Long, ProduktDoWycenyDto>`) i `public record ProduktDoWycenyDto(produktId, nazwa, zdjecieUrl, cenaGrosze, stan, aktywny, status, maksIlosc)` w `backend/src/main/java/com/project/custom/katalog/KatalogQueryFacade.java` i `backend/src/main/java/com/project/custom/katalog/ProduktDoWycenyDto.java`; package-private implementacja (jedno zapytanie po zbiorze id, zwraca też nieaktywne) w `backend/src/main/java/com/project/custom/katalog/application/KatalogQueryFacadeImpl.java`
- [ ] T059 [P] [US2] Utwórz typy domenowe koszyka: `KoszykId`, `PozycjaKoszyka` (`produktId`, `ilosc 1..99`, `cenaPrzyDodaniu`, `dodano`), `ProduktDoKoszyka` (widok produktu w języku koszyka: id, cena, status, maks), wyjątki `IloscPrzekraczaLimit(maks)` i `ProduktNiedostepny` w `backend/src/main/java/com/project/custom/koszyk/domain/`
- [ ] T060 [US2] Zaimplementuj agregat `Koszyk` z operacją `dodaj(ProduktDoKoszyka, n)` wg reguł z data-model.md w `backend/src/main/java/com/project/custom/koszyk/domain/Koszyk.java` (zależy od T059)
- [ ] T061 [US2] Zaimplementuj read model `WycenionyKoszyk` / `WycenionaPozycja` i usługę domenową `WycenaKoszyka` (na razie: aktualna cena, wartość, status, `maksIlosc`, `liczbaSztuk`, `suma`) w `backend/src/main/java/com/project/custom/koszyk/domain/WycenaKoszyka.java` i `backend/src/main/java/com/project/custom/koszyk/domain/WycenionyKoszyk.java`
- [ ] T062 [US2] Zdefiniuj port `KoszykRepository` (`znajdzPoGosciu(GoscId)`, `zapisz(Koszyk)`) w `backend/src/main/java/com/project/custom/koszyk/domain/KoszykRepository.java`; utwórz `KoszykJpaEntity` (`@Version`), `PozycjaKoszykaJpaEntity`, `KoszykJpaRepository` i package-private `KoszykRepositoryAdapter` z mapowaniem w `backend/src/main/java/com/project/custom/koszyk/infrastructure/persistence/`
- [ ] T063 [US2] Zaimplementuj `KoszykService` (`wycen(GoscId)` przez `KatalogQueryFacade`, `dodaj(GoscId, produktId, ilosc)` — koszyk tworzony leniwie, jedna transakcja) w `backend/src/main/java/com/project/custom/koszyk/application/KoszykService.java`
- [ ] T064 [US2] Zaimplementuj `KoszykController` (`GET /api/koszyk`, `POST /api/koszyk/pozycje` z `DodajPozycjeRequest{produktId, ilosc: @NotNull @Min(1) @Max(99) Integer}`) mapujący na schemat `Koszyk` z kontraktu oraz mapowanie wyjątków domenowych na `409 ILOSC_PRZEKRACZA_LIMIT`/`409 PRODUKT_NIEDOSTEPNY` (z `maksIlosc`) w `backend/src/main/java/com/project/custom/koszyk/api/KoszykController.java` i `backend/src/main/java/com/project/custom/koszyk/api/KoszykApiMapper.java`
- [ ] T065 [US2] Utwórz hooki `useKoszyk` (zapytanie `['koszyk']`) i `useDodajDoKoszyka` (mutacja + `invalidateQueries(['koszyk'])`, obsługa `409` → toast) w `frontend/src/features/koszyk/useKoszyk.ts`
- [ ] T066 [US2] Utwórz `LicznikKoszyka.tsx` (link `/koszyk` z `liczbaSztuk`) w `frontend/src/features/koszyk/LicznikKoszyka.tsx` i wstaw do nagłówka `frontend/src/app/Layout.tsx`
- [ ] T067 [US2] Utwórz `DodajDoKoszykaButton.tsx` (wariant z wyborem ilości `1..maksIlosc` dla karty i wariant „1 szt.” dla listy; nieaktywny z `aria-disabled` dla `NIEDOSTEPNY`) w `frontend/src/features/koszyk/DodajDoKoszykaButton.tsx` i użyj go w `frontend/src/features/katalog/ProduktPage.tsx` oraz `frontend/src/features/katalog/ProduktKafelek.tsx`
- [ ] T068 [US2] Utwórz podgląd koszyka tylko do odczytu `KoszykPage.tsx` (lista pozycji: zdjęcie, nazwa, cena jednostkowa, ilość, wartość; suma; stan pusty) w `frontend/src/features/koszyk/KoszykPage.tsx` i zarejestruj trasę `/koszyk` w `frontend/src/app/router.tsx`

**Checkpoint**: US1 + US2 działają niezależnie — quickstart 2.1–2.5 i `koszyk-dodawanie.spec.ts` zielone.

---

## Phase 5: User Story 3 — Przeglądanie i edycja koszyka (Priority: P1)

**Goal**: Zmiana ilości (z ograniczeniem do stanu i komunikatem), usuwanie pozycji, czyszczenie
koszyka, sumy liczone przez backend, informacja o zmianie ceny i utracie dostępności,
blokada przejścia do zamówienia (FR-009–FR-011, FR-014 w części koszyka).

**Independent Test**: Koszyk z 2 produktami: ilość 1 → 3 przelicza sumy; ilość 50 przy stanie 5
→ 5 + „Dostępnych jest tylko 5 szt.”; 0/„Usuń” usuwa pozycję; „Wyczyść koszyk” → „Twój koszyk
jest pusty”; zmiana ceny w bazie → przekreślona stara cena + baner i „Rozumiem”; `stan = 0` →
pozycja „Niedostępny”, przycisk zamówienia nieaktywny; `ilosc` `1.5`/`-1`/`"abc"` → 400
(quickstart 3.1–3.8).

### Testy dla User Story 3 ⚠️

- [ ] T069 [P] [US3] Rozszerz test `Koszyk` o edycję: `zmienIlosc(produktId, 0)` usuwa pozycję; `n > maks` → ustawia `maks` i zwraca `IloscOgraniczona(maks)`; `n < 0` → błąd; `usun` nieistniejącej pozycji → no-op; `wyczysc()` usuwa wszystko; `akceptujCeny(ceny)` nadpisuje `cenaPrzyDodaniu` — w `backend/src/test/java/com/project/custom/koszyk/domain/KoszykTest.java`
- [ ] T070 [P] [US3] Rozszerz test `WycenaKoszyka`: `cenaZmieniona` + `poprzedniaCena` gdy cena katalogu ≠ `cenaPrzyDodaniu`; produkt nieaktywny/`stan = 0`/usunięty → `status = NIEDOSTEPNY` i poza sumą; `iloscPrzekraczaStan`; `moznaZamowic = niepusty ∧ każda pozycja dostępna ∧ ilosc ≤ maksIlosc`; `problemy[]` z kodami `CENA_ZMIENIONA`, `PRODUKT_NIEDOSTEPNY`, `ILOSC_PRZEKRACZA_STAN` — w `backend/src/test/java/com/project/custom/koszyk/domain/WycenaKoszykaTest.java`
- [ ] T071 [P] [US3] Test integracyjny edycji koszyka (MockMvc): `PUT /api/koszyk/pozycje/{produktId}` (zmiana, ograniczenie do stanu z `komunikaty[kod=ILOSC_OGRANICZONA]`, `0` usuwa, `1.5`/`-1`/`"abc"`/`100` → `400 BLAD_WALIDACJI`, brak pozycji → `404`); `DELETE /api/koszyk/pozycje/{produktId}` (idempotentne); `DELETE /api/koszyk`; `POST /api/koszyk/akceptuj-ceny` czyści flagi `cenaZmieniona`; zmiana ceny/stanu w bazie odzwierciedlona w `GET /api/koszyk`; równoległa zmiana z dwóch „kart” → `409 KONFLIKT_WSPOLBIEZNOSCI` — w `backend/src/test/java/com/project/custom/koszyk/application/KoszykEdycjaIT.java`
- [ ] T072 [P] [US3] Test komponentu `KoszykPage` (MSW) dla stanów z `contracts/frontend-routes.md`: pusty koszyk; `cenaZmieniona` (przekreślona cena, baner, „Rozumiem” → `akceptuj-ceny`); `NIEDOSTEPNY` (wyszarzenie, tylko „Usuń”); `moznaZamowic=false` (przycisk nieaktywny z podpowiedzią); `ILOSC_OGRANICZONA` → toast „Dostępnych jest tylko N szt.” — w `frontend/tests/unit/koszyk/KoszykPage.test.tsx`
- [ ] T073 [P] [US3] Test E2E ścieżki edycji koszyka (quickstart 3.1–3.7, zmiana ceny/stanu przez pomocnik SQL z `fixtures.ts`) w `frontend/tests/e2e/koszyk-edycja.spec.ts`

### Implementacja User Story 3

- [ ] T074 [US3] Dodaj do agregatu `Koszyk` operacje `zmienIlosc`, `usun`, `wyczysc`, `akceptujCeny` oraz wynik `IloscOgraniczona(maks)` w `backend/src/main/java/com/project/custom/koszyk/domain/Koszyk.java` i `backend/src/main/java/com/project/custom/koszyk/domain/IloscOgraniczona.java`
- [ ] T075 [US3] Uzupełnij `WycenaKoszyka`/`WycenionyKoszyk` o `cenaZmieniona`, `poprzedniaCena`, `iloscPrzekraczaStan`, `moznaZamowic`, `problemy[]` w `backend/src/main/java/com/project/custom/koszyk/domain/WycenaKoszyka.java`
- [ ] T076 [US3] Dodaj do `KoszykService` przypadki użycia `zmienIlosc`, `usun`, `wyczysc`, `akceptujCeny` (każdy zwraca świeżo wycenione podsumowanie + komunikaty) w `backend/src/main/java/com/project/custom/koszyk/application/KoszykService.java`
- [ ] T077 [US3] Dodaj endpointy `PUT /api/koszyk/pozycje/{produktId}` (`ZmienIloscRequest{ilosc: @NotNull @Min(0) @Max(99) Integer}`), `DELETE /api/koszyk/pozycje/{produktId}`, `DELETE /api/koszyk`, `POST /api/koszyk/akceptuj-ceny` oraz mapowanie `komunikaty[]` w `backend/src/main/java/com/project/custom/koszyk/api/KoszykController.java` i `backend/src/main/java/com/project/custom/koszyk/api/KoszykApiMapper.java`
- [ ] T078 [US3] Dodaj mutacje `useZmienIlosc`, `useUsunPozycje`, `useWyczyscKoszyk`, `useAkceptujCeny` (każda unieważnia `['koszyk']`, toast dla `ILOSC_OGRANICZONA`) w `frontend/src/features/koszyk/useKoszyk.ts`
- [ ] T079 [US3] Utwórz `PozycjaKoszyka.tsx` (pole ilości z walidacją liczby całkowitej 0–99, „Usuń”, cena aktualna + przekreślona `poprzedniaCenaGrosze`, stan „Niedostępny”) w `frontend/src/features/koszyk/PozycjaKoszyka.tsx`
- [ ] T080 [US3] Rozbuduj `KoszykPage.tsx` o edycję: baner „Cena zmieniła się” + „Rozumiem”, „Wyczyść koszyk”, podsumowanie z sumą z API, przycisk „Przejdź do zamówienia” (link `/zamowienie`, nieaktywny gdy `moznaZamowic=false` z podpowiedzią), stan pusty „Twój koszyk jest pusty” + link do sklepu w `frontend/src/features/koszyk/KoszykPage.tsx` i `frontend/src/features/koszyk/KoszykPage.module.css`

**Checkpoint**: US1–US3 działają niezależnie od Stripe — quickstart 3.1–3.8 i `koszyk-edycja.spec.ts` zielone.

---

## Phase 6: User Story 4 — Złożenie zamówienia i płatność online (Priority: P1)

**Goal**: Formularz danych + podsumowanie → ponowna weryfikacja cen (409 przy zmianie) →
zamówienie `OCZEKUJE_NA_PLATNOSC` + sesja Stripe Checkout (poza transakcją, z kluczem
idempotencji) → przekierowanie; opłacenie wyłącznie przez zweryfikowany, zdeduplikowany webhook,
który w jednej transakcji zmniejsza stan, czyści koszyk i zapisuje `ZamowienieOplaconeEvent`
do Outboxa; strona potwierdzenia z pollingiem; płatność nieudana/anulowana nie narusza koszyka
(FR-014–FR-024, SC-004–SC-007).

**Independent Test**: Koszyk z dostępnymi produktami → formularz (błędny kod `12345` i pusty
e-mail blokują) → Stripe z kwotą = suma → karta `4242…` → „Płatność w trakcie weryfikacji” →
„Opłacone” ≤ 30 s, licznik 0, stan zmniejszony; karta `4000…0002` + powrót → baner, koszyk
nietknięty; ponowienie webhooka bez skutków; sfałszowany podpis → 400; cudzy numer → 404;
Stripe niedostępny → 503 i koszyk nietknięty (quickstart 4.1–4.11).

### Testy dla User Story 4 ⚠️

- [ ] T081 [P] [US4] Test jednostkowy VO zamówienia: `DaneKlienta` (email poprawny, ≤ 254; imię i nazwisko 2–100 znaków), `AdresDostawy` (ulica 3–120; kod `^\d{2}-\d{3}$`; miejscowość 2–60; `kraj = "PL"`), `NumerZamowienia` (format `ZAM-[0-9A-HJKMNP-TV-Z]{10}`, generator na `SecureRandom`) — w `backend/src/test/java/com/project/custom/zamowienie/domain/WartosciZamowieniaTest.java`
- [ ] T082 [P] [US4] Test jednostkowy maszyny stanów `Zamowienie`: `OCZEKUJE_NA_PLATNOSC → OPLACONE` (kwota i waluta zgodne, stan OK; `oplacono` ustawione), `→ WYMAGA_WYJASNIENIA` (`NIEWYSTARCZAJACY_STAN`, `NIEZGODNA_KWOTA`), `→ PLATNOSC_NIEUDANA`, `PLATNOSC_NIEUDANA → WYMAGA_WYJASNIENIA` (`POTWIERDZENIE_PO_NIEPOWODZENIU`), `OPLACONE → OPLACONE` bez efektów, pozostałe → `NiedozwolonePrzejscieStatusu`; `suma = Σ cenaJednostkowa × ilosc`, min. 1 pozycja, pozycje niezmienne — w `backend/src/test/java/com/project/custom/zamowienie/domain/ZamowienieTest.java`
- [ ] T083 [P] [US4] Test jednostkowy `Platnosc`: `UTWORZONA → OTWARTA | NIEUDANA`, `OTWARTA → POTWIERDZONA | WYGASZONA | NIEUDANA`, `POTWIERDZONA` końcowa, powtórne potwierdzenie idempotentne — w `backend/src/test/java/com/project/custom/platnosc/domain/PlatnoscTest.java`
- [ ] T084 [P] [US4] Utwórz pomocnika podpisu webhooków (HMAC-SHA256 testowym `whsec_test_…`, nagłówek `Stripe-Signature: t=…,v1=…`, szablony zdarzeń `checkout.session.completed|async_payment_succeeded|async_payment_failed|expired` z `amount_total`, `currency`, `payment_status`, `livemode`) w `backend/src/test/java/com/project/custom/support/StripeWebhookSigner.java`
- [ ] T085 [P] [US4] Test kontraktowy adaptera na `stripe-mock` (Testcontainers `GenericContainer`): `utworzSesje` wysyła `mode=payment`, `payment_method_types[]=card`, `currency=pln`, `unit_amount` w groszach, `quantity`, `customer_email`, `client_reference_id`, `metadata[zamowienieId|platnoscId|numer]`, `expires_at ≈ teraz + 30 min`, `success_url`/`cancel_url` wg kontraktu, nagłówek `Idempotency-Key: checkout-{platnoscId}`; asercja `Σ unit_amount × quantity == suma` odrzuca niezgodne dane; `wygas` zwraca `WYGASZONA` — w `backend/src/test/java/com/project/custom/platnosc/infrastructure/stripe/StripeBramkaPlatnosciContractTest.java`
- [ ] T086 [P] [US4] Test awarii adaptera na WireMock: timeout odczytu, zerwane połączenie, `5xx`, `429` → `BramkaNiedostepna` po ≤ 2 ponowieniach; `400`/`401` → błąd konfiguracji bez klucza w komunikacie/logu; odpowiedź `expire` „session is not open” ze `status=complete` → `JUZ_OPLACONA` — w `backend/src/test/java/com/project/custom/platnosc/infrastructure/stripe/StripeBramkaPlatnosciAwarieTest.java`
- [ ] T087 [P] [US4] Test integracyjny `ZlozZamowienieService` + `POST /api/zamowienia` (adapter wskazujący na `stripe-mock`/WireMock): poprawne dane i aktualne podsumowanie → `201 {numer, urlPlatnosci}`, zamówienie `OCZEKUJE_NA_PLATNOSC` z niezmiennymi pozycjami, `Platnosc` `OTWARTA` z `stripe_session_id`, koszyk NIETKNIĘTY; różnica ceny/ilości/składu/dostępności → `409 PODSUMOWANIE_NIEAKTUALNE` z aktualnym `koszyk` i bez zamówienia; pusty koszyk lub `moznaZamowic=false` → `409 KOSZYK_NIE_DO_ZAMOWIENIA`; błędy pól (`kodPocztowy=12345`, pusty e-mail) → `400 BLAD_WALIDACJI` z `bledy[].pole`; Stripe niedostępny → `503 PLATNOSC_NIEDOSTEPNA`, zamówienie `PLATNOSC_NIEUDANA`, koszyk nietknięty; druga próba wygasza otwartą sesję poprzedniego zamówienia gościa; poprzednia sesja `complete` → `409 ZAMOWIENIE_JUZ_OPLACONE`; brak wywołania Stripe wewnątrz otwartej transakcji — w `backend/src/test/java/com/project/custom/zamowienie/application/ZlozZamowienieServiceIT.java`
- [ ] T088 [P] [US4] Test integracyjny webhooka `POST /api/platnosci/stripe/webhook` + `ObslugaWebhookaService`: poprawny `completed/paid` → `200`, `Platnosc POTWIERDZONA`, zamówienie `OPLACONE`, stan produktów zmniejszony, koszyk gościa pusty, jeden wiersz `outbox_event` typu `ZamowienieOplacone` (ładunek bez e-maila); błędny/brak podpisu → `400` i brak zmian; `livemode=true` → `400`; to samo `event.id` 2× (także równolegle) → jedno przetworzenie, stan zmniejszony raz; inny `event.id` dla tej samej sesji → brak efektów; `amount_total`/`currency` niezgodne → `WYMAGA_WYJASNIENIA` (`NIEZGODNA_KWOTA`) bez zmiany stanu; niewystarczający stan → `WYMAGA_WYJASNIENIA` (`NIEWYSTARCZAJACY_STAN`), koszyk czyszczony, brak zdarzenia Outbox; `completed` z `payment_status != paid` → bez zmian; `async_payment_succeeded` → jak opłacone; `expired`/`async_payment_failed` → `PLATNOSC_NIEUDANA`, koszyk nietknięty; spóźnione potwierdzenie po niepowodzeniu → `WYMAGA_WYJASNIENIA`; nieznana sesja → `200`; inny typ zdarzenia → `200` ignorowane — w `backend/src/test/java/com/project/custom/platnosc/application/ObslugaWebhookaServiceIT.java`
- [ ] T089 [P] [US4] Test integracyjny `GET /api/zamowienia/{numer}`: właściciel (to samo `shop_guest`) widzi numer, pozycje, sumę i status; inne ciasteczko, brak ciasteczka lub nieistniejący numer → `404 NIE_ZNALEZIONO` (bez rozróżnienia); odczyt nie zmienia stanu — w `backend/src/test/java/com/project/custom/zamowienie/application/ZamowienieOdczytIT.java`
- [ ] T090 [P] [US4] Test integracyjny `KatalogCommandFacade.zmniejszStan`: wszystko albo nic przy wielu pozycjach, `NIEWYSTARCZAJACY_STAN` bez zmian, równoległe zmniejszenia ostatniej sztuki → dokładnie jedno `ZMNIEJSZONO` — w `backend/src/test/java/com/project/custom/katalog/application/ZmniejszStanIT.java`
- [ ] T091 [P] [US4] Test komponentu `ZamowieniePage` (MSW): błędy Zod przy polach z `aria-describedby`; `400` → komunikaty wg `bledy[].pole`; `409 PODSUMOWANIE_NIEAKTUALNE` → podsumowanie zastąpione `problem.koszyk` + baner; `503` → „Płatność chwilowo niedostępna, spróbuj za chwilę”; `201` → `window.location.assign(urlPlatnosci)` — w `frontend/tests/unit/zamowienie/ZamowieniePage.test.tsx`
- [ ] T092 [P] [US4] Test komponentu `PotwierdzeniePage` (MSW, fałszywe timery): `OCZEKUJE_NA_PLATNOSC` → „Płatność w trakcie weryfikacji” i polling co 2 s, po 60 s „Weryfikacja trwa dłużej — odśwież stronę później”; `OPLACONE` → „Dziękujemy! Zamówienie opłacone” + unieważnienie `['koszyk']`; `PLATNOSC_NIEUDANA` → „Płatność nieudana” + link do koszyka; `WYMAGA_WYJASNIENIA` → „Płatność przyjęta — skontaktujemy się w sprawie realizacji”; `404` → „Nie znaleziono zamówienia” — w `frontend/tests/unit/zamowienie/PotwierdzeniePage.test.tsx`
- [ ] T093 [P] [US4] Test E2E ścieżki płatności ze Stripe test mode i `stripe listen` (karta `4242 4242 4242 4242` → „Opłacone” ≤ 30 s i licznik 0; karta `4000 0000 0000 0002` + powrót → baner i koszyk nietknięty; wejście na `success_url` bez webhooka → „Płatność w trakcie weryfikacji”; walidacja formularza; zmiana ceny przed „Zapłać” → ponowne zatwierdzenie) w `frontend/tests/e2e/platnosc.spec.ts`

### Implementacja User Story 4 — schemat i wspólne jądro

- [ ] T094 [US4] Utwórz migrację `backend/src/main/resources/db/migration/V3__zamowienie.sql`: `zamowienie(id UNIQUEIDENTIFIER PK, numer VARCHAR(14) NOT NULL UNIQUE, gosc_id UNIQUEIDENTIFIER NOT NULL /* indeks */, email NVARCHAR(254) NOT NULL, imie_nazwisko NVARCHAR(100) NOT NULL, ulica NVARCHAR(120) NOT NULL, kod_pocztowy CHAR(6) NOT NULL, miejscowosc NVARCHAR(60) NOT NULL, kraj CHAR(2) NOT NULL, suma_grosze BIGINT NOT NULL, status VARCHAR(30) NOT NULL, utworzono DATETIMEOFFSET(3) NOT NULL, oplacono DATETIMEOFFSET(3) NULL, powod_wyjasnienia NVARCHAR(200) NULL, wersja BIGINT NOT NULL)`; `pozycja_zamowienia(zamowienie_id FK → zamowienie, lp INT, produkt_id BIGINT NOT NULL /* bez FK */, nazwa NVARCHAR(200) NOT NULL, cena_jednostkowa_grosze BIGINT NOT NULL CHECK (> 0), ilosc INT NOT NULL CHECK (BETWEEN 1 AND 99), PK (zamowienie_id, lp))`
- [ ] T095 [P] [US4] Utwórz migrację `backend/src/main/resources/db/migration/V4__platnosc.sql`: `platnosc(id UNIQUEIDENTIFIER PK, zamowienie_id UNIQUEIDENTIFIER NOT NULL /* indeks, bez FK */, gosc_id UNIQUEIDENTIFIER NOT NULL, kwota_grosze BIGINT NOT NULL, stripe_session_id VARCHAR(255) NULL, stripe_payment_intent_id VARCHAR(255) NULL, url_platnosci NVARCHAR(1000) NULL, status VARCHAR(20) NOT NULL, utworzono DATETIMEOFFSET(3) NOT NULL, potwierdzono DATETIMEOFFSET(3) NULL)` z filtrowanym unikalnym indeksem `stripe_session_id WHERE stripe_session_id IS NOT NULL`; `przetworzone_zdarzenie_stripe(event_id VARCHAR(255) PK, typ VARCHAR(100) NOT NULL, przetworzono DATETIMEOFFSET(3) NOT NULL)`
- [ ] T096 [P] [US4] Utwórz migrację `backend/src/main/resources/db/migration/V5__outbox.sql`: `outbox_event(id UNIQUEIDENTIFIER PK, typ NVARCHAR(100) NOT NULL, agregat_id NVARCHAR(50) NOT NULL, ladunek NVARCHAR(MAX) NOT NULL, utworzono DATETIMEOFFSET(3) NOT NULL, wyslano DATETIMEOFFSET(3) NULL, proby INT NOT NULL DEFAULT 0)` z indeksem `(wyslano, utworzono)`
- [ ] T097 [US4] Zaimplementuj Outbox (tylko zapis — wysyłka należy do funkcji `realizacja`, R-17): port `public interface OutboxEventPublisher` (bez adnotacji frameworkowych) w `backend/src/main/java/com/project/custom/shared/domain/outbox/OutboxEventPublisher.java`; `OutboxEventJpaEntity`, `OutboxEventJpaRepository` i package-private `JpaOutboxEventPublisher` zapisujący JSON w bieżącej transakcji (`Propagation.MANDATORY`) w `backend/src/main/java/com/project/custom/shared/infrastructure/outbox/`

### Implementacja User Story 4 — fasady katalogu i koszyka

- [ ] T098 [P] [US4] Dodaj `public interface KatalogCommandFacade` (`zmniejszStan(List<PozycjaStanuDto>) → WynikZmniejszeniaStanu` z wartościami `ZMNIEJSZONO | NIEWYSTARCZAJACY_STAN`) i rekordy DTO w `backend/src/main/java/com/project/custom/katalog/`; rozszerz port `ProduktRepository` o `zablokujDoZmiany(Set<ProduktId>) → List<Produkt>` (kolejność `id`) i `zapisz(List<Produkt>)`; implementacja „wszystko albo nic” (sprawdzenie i `Produkt.zmniejszStan` w domenie) w `backend/src/main/java/com/project/custom/katalog/application/KatalogCommandFacadeImpl.java`; zapytanie `WITH (UPDLOCK, ROWLOCK)` w `ProduktRepositoryAdapter` / `ProduktJpaRepository` w `backend/src/main/java/com/project/custom/katalog/infrastructure/persistence/`
- [ ] T099 [P] [US4] Dodaj `public interface KoszykQueryFacade` (`wycen(GoscId) → WycenionyKoszykDto`) i `public interface KoszykCommandFacade` (`wyczysc(GoscId)` w bieżącej transakcji) z rekordem `WycenionyKoszykDto` w `backend/src/main/java/com/project/custom/koszyk/`; package-private implementacje delegujące do `KoszykService` w `backend/src/main/java/com/project/custom/koszyk/application/KoszykFacadeImpl.java`

### Implementacja User Story 4 — BC `platnosc`

- [ ] T100 [P] [US4] Utwórz domenę płatności: `PlatnoscId`, `StatusPlatnosci`, agregat `Platnosc` (przejścia z data-model.md), port `BramkaPlatnosci` (`utworzSesje(ZadanieSesji) → SesjaPlatnosci | BramkaNiedostepna`, `wygas(operatorSesjaId) → WYGASZONA | JUZ_OPLACONA`), `ZadanieSesji`, `SesjaPlatnosci`, `BramkaNiedostepna`, `WynikWygaszenia`, `PotwierdzenieOperatora{eventId, typ, sesjaId, paymentIntentId, kwota, waluta, oplacona, livemode}`, porty `PlatnoscRepository` i `PrzetworzoneZdarzenieRepository` (`zarejestruj(eventId, typ) → boolean` — `false` przy duplikacie) w `backend/src/main/java/com/project/custom/platnosc/domain/`
- [ ] T101 [US4] Utwórz persystencję płatności: `PlatnoscJpaEntity`, `PrzetworzoneZdarzenieJpaEntity`, repozytoria JPA i package-private adaptery (duplikat PK `event_id` → `false` w tej samej transakcji) w `backend/src/main/java/com/project/custom/platnosc/infrastructure/persistence/`
- [ ] T102 [US4] Zaimplementuj adapter `StripeBramkaPlatnosci` (klient `StripeClient` z `StripeProperties`: `apiBase`, timeouty 5 s/10 s, `maxNetworkRetries=2`; parametry sesji i `Idempotency-Key: checkout-{platnoscId}` wg `contracts/stripe-webhook.md`; asercja `Σ unit_amount × quantity == suma`; mapowanie `APIConnectionException`/timeout/`5xx`/`RateLimitException` → `BramkaNiedostepna`, `InvalidRequestException`/`AuthenticationException` → błąd konfiguracji z logiem bez klucza; `expire` z obsługą `JUZ_OPLACONA`) w `backend/src/main/java/com/project/custom/platnosc/infrastructure/stripe/StripeBramkaPlatnosci.java` i `backend/src/main/java/com/project/custom/platnosc/infrastructure/stripe/StripeClientConfig.java`
- [ ] T103 [US4] Zaimplementuj `StripeWebhookVerifier` (`Webhook.constructEvent(payload, signature, webhookSecret)`, tolerancja 300 s; mapowanie na `PotwierdzenieOperatora` niezależne od SDK) w `backend/src/main/java/com/project/custom/platnosc/infrastructure/stripe/StripeWebhookVerifier.java`
- [ ] T104 [US4] Utwórz publiczne API BC: `public interface PlatnoscFacade` (`rozpocznij(RozpocznijPlatnoscDto) → RozpoczetaPlatnoscDto`, `wygasOtwarte(GoscId) → WynikWygaszeniaDto`), rekordy DTO oraz zdarzenia `public record PlatnoscPotwierdzonaEvent(zamowienieId, kwotaGrosze, waluta, potwierdzono)` i `public record PlatnoscNieudanaEvent(zamowienieId, powod)` w `backend/src/main/java/com/project/custom/platnosc/`
- [ ] T105 [US4] Zaimplementuj `PlatnoscService` implementujący `PlatnoscFacade`: `rozpocznij` = TX1 zapis `Platnosc(UTWORZONA)` → wywołanie `BramkaPlatnosci` POZA transakcją → TX2 `OTWARTA` z `sessionId`/URL albo `NIEUDANA` (przez `TransactionTemplate`); `wygasOtwarte` wygasza otwarte sesje gościa (`JUZ_OPLACONA` → wynik dla `409`) w `backend/src/main/java/com/project/custom/platnosc/application/PlatnoscService.java`
- [ ] T106 [US4] Zaimplementuj `ObslugaWebhookaService` (jedna transakcja: `livemode` → odrzucenie; `PrzetworzoneZdarzenieRepository.zarejestruj` — duplikat → koniec bez efektów; korelacja po `operatorSesjaId`, nieznana sesja → log `WARN`; przejście `Platnosc` wg tabeli zdarzeń; publikacja `PlatnoscPotwierdzonaEvent`/`PlatnoscNieudanaEvent` przez `ApplicationEventPublisher` obsługiwanych synchronicznie w tej samej transakcji, R-02) w `backend/src/main/java/com/project/custom/platnosc/application/ObslugaWebhookaService.java`
- [ ] T107 [US4] Zaimplementuj `StripeWebhookController` (`POST /api/platnosci/stripe/webhook`, `@RequestBody String` surowe ciało + nagłówek `Stripe-Signature`; błąd podpisu lub `livemode` → `400` z logiem `WARN` samego typu błędu, bez ciała i nagłówka; `200` tylko po commicie; błąd techniczny → `500`) w `backend/src/main/java/com/project/custom/platnosc/api/StripeWebhookController.java`; wyłącz filtr `GoscIdFilter` dla tej ścieżki

### Implementacja User Story 4 — BC `zamowienie`

- [ ] T108 [P] [US4] Utwórz domenę zamówienia: `ZamowienieId`, `StatusZamowienia`, VO `DaneKlienta`, `AdresDostawy`, `NumerZamowienia` (+ `GeneratorNumeruZamowienia` na `SecureRandom`, Crockford Base32), `PozycjaZamowienia` (niezmienna), `NiedozwolonePrzejscieStatusu`, `PowodWyjasnienia` w `backend/src/main/java/com/project/custom/zamowienie/domain/`
- [ ] T109 [US4] Zaimplementuj agregat `Zamowienie` (utworzenie z niezmienną kopią pozycji i `suma = Σ wartości`, `OCZEKUJE_NA_PLATNOSC`; `potwierdzPlatnosc(kwota, waluta, wynikStanu)`, `oznaczNieudana()`, maszyna stanów wg data-model.md) i port `ZamowienieRepository` w `backend/src/main/java/com/project/custom/zamowienie/domain/Zamowienie.java` i `backend/src/main/java/com/project/custom/zamowienie/domain/ZamowienieRepository.java`
- [ ] T110 [US4] Utwórz persystencję zamówienia: `ZamowienieJpaEntity` (`@Version`), `PozycjaZamowieniaJpaEntity`, `ZamowienieJpaRepository` i package-private `ZamowienieRepositoryAdapter` z mapowaniem w `backend/src/main/java/com/project/custom/zamowienie/infrastructure/persistence/`
- [ ] T111 [US4] Utwórz publiczne API BC: `public record ZamowienieOplaconeEvent(numer, oplacono, suma{grosze, waluta}, pozycje[{nazwa, ilosc, cenaJednostkowaGrosze}], klient{imieNazwisko}, adres{…})` — BEZ e-maila; `public interface ZamowienieQueryFacade` (`pobierz(NumerZamowienia, GoscId) → Optional<ZamowienieDto>`) z rekordem `ZamowienieDto` i package-private implementacją w `backend/src/main/java/com/project/custom/zamowienie/`
- [ ] T112 [US4] Zaimplementuj `ZlozZamowienieService`: `KoszykQueryFacade.wycen` → `moznaZamowic` (inaczej `KOSZYK_NIE_DO_ZAMOWIENIA`) → porównanie z potwierdzonym podsumowaniem `{produktId, ilosc, cenaJednostkowaGrosze}[]` + `sumaGrosze` (różnica → `PodsumowanieNieaktualne` z aktualnym koszykiem) → `PlatnoscFacade.wygasOtwarte(gosc)` (`JUZ_OPLACONA` → `ZAMOWIENIE_JUZ_OPLACONE`) → TX zapis `Zamowienie` → `PlatnoscFacade.rozpocznij` poza transakcją → przy `BramkaNiedostepna` TX `PLATNOSC_NIEUDANA` + `PLATNOSC_NIEDOSTEPNA`; kwota zawsze z wyceny serwera; logi z maskowaniem PII — w `backend/src/main/java/com/project/custom/zamowienie/application/ZlozZamowienieService.java`
- [ ] T113 [US4] Zaimplementuj `ObslugaPlatnosciListener` (`@EventListener` w transakcji webhooka): `PlatnoscPotwierdzonaEvent` → kontrola `kwota`/`waluta` z `suma` → `KatalogCommandFacade.zmniejszStan` (tylko przy zgodnej kwocie) → `OPLACONE` lub `WYMAGA_WYJASNIENIA` → `KoszykCommandFacade.wyczysc(goscId)` → przy `OPLACONE` `OutboxEventPublisher` z `ZamowienieOplaconeEvent`; powtórne potwierdzenie → brak efektów; `PlatnoscNieudanaEvent` → `PLATNOSC_NIEUDANA` (koszyk nietknięty) w `backend/src/main/java/com/project/custom/zamowienie/application/ObslugaPlatnosciListener.java`
- [ ] T114 [US4] Zaimplementuj `ZamowienieController` (`POST /api/zamowienia` z `ZlozZamowienieRequest`: `email @Email @Size(max=254)`, `imieNazwisko @Size(min=2,max=100)`, `ulicaINumer @Size(min=3,max=120)`, `kodPocztowy @Pattern("^\\d{2}-\\d{3}$")`, `miejscowosc @Size(min=2,max=60)`, `kraj` nieprzyjmowany z frontu, potwierdzone pozycje i `sumaGrosze` → `201 RozpoczetaPlatnosc{numer, urlPlatnosci}`, `409 ProblemZKoszykiem`, `503`; `GET /api/zamowienia/{numer}` → `404` dla cudzego/nieistniejącego) w `backend/src/main/java/com/project/custom/zamowienie/api/ZamowienieController.java` i `backend/src/main/java/com/project/custom/zamowienie/api/ZamowienieApiMapper.java`

### Implementacja User Story 4 — frontend

- [ ] T115 [P] [US4] Utwórz schemat Zod formularza (reguły UX odpowiadające FR-015: e-mail ≤ 254, imię i nazwisko 2–100, ulica 3–120, kod `^\d{2}-\d{3}$`, miejscowość 2–60; kraj stały „Polska”) w `frontend/src/features/zamowienie/zamowienieSchema.ts`
- [ ] T116 [P] [US4] Utwórz hooki `useZlozZamowienie` (mutacja, obsługa `201` → `window.location.assign(urlPlatnosci)`, `400`/`409`/`503`) i `usePobierzZamowienie(numer)` (polling co 2 s dopóki `OCZEKUJE_NA_PLATNOSC`, limit 60 s) w `frontend/src/features/zamowienie/api.ts`
- [ ] T117 [US4] Utwórz `ZamowieniePage.tsx` (React Hook Form + Zod, etykiety `<label>`, błędy z `aria-describedby`, podsumowanie z `GET /api/koszyk` przesyłane jako potwierdzone pozycje, obsługa `PODSUMOWANIE_NIEAKTUALNE` z ponownym zatwierdzeniem, `PLATNOSC_NIEDOSTEPNA`, przekierowanie do `/koszyk` gdy `moznaZamowic=false`) w `frontend/src/features/zamowienie/ZamowieniePage.tsx` i `frontend/src/features/zamowienie/ZamowieniePage.module.css`
- [ ] T118 [US4] Utwórz `PotwierdzeniePage.tsx` (numer, pozycje, suma, status wyłącznie z API wg tabeli stanów z `contracts/frontend-routes.md`, unieważnienie `['koszyk']` po `OPLACONE`, bez jakiejkolwiek zmiany stanu po stronie serwera) w `frontend/src/features/zamowienie/PotwierdzeniePage.tsx`
- [ ] T119 [US4] Dodaj baner „Płatność nie została zakończona. Twój koszyk czeka.” dla `?platnosc=anulowana` (z unieważnieniem `['koszyk']`) w `frontend/src/features/koszyk/KoszykPage.tsx`; zarejestruj trasy `/zamowienie` i `/zamowienie/:numer` w `frontend/src/app/router.tsx`

**Checkpoint**: Wszystkie cztery ścieżki P1 działają end-to-end — quickstart 4.1–4.11 i `platnosc.spec.ts` zielone.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Kontrakt, wydajność, bezpieczeństwo i walidacja całości (SC-003, SC-008).

- [ ] T120 [P] Zaimplementuj `OpenApiContractTest` porównujący ścieżki, metody i kody odpowiedzi z `/v3/api-docs` (springdoc) z `backend/src/main/resources/openapi/shop-api.yaml` w `backend/src/test/java/com/project/custom/OpenApiContractTest.java`
- [ ] T121 [P] Test wydajności na seedzie ≥ 500 produktów (lista, wyszukiwanie z frazą, filtr+sort, koszyk z 10 pozycjami — każde zapytanie backendu ≤ 300 ms po rozgrzaniu, brak N+1 weryfikowany licznikiem zapytań Hibernate) w `backend/src/test/java/com/project/custom/katalog/application/WydajnoscKatalogIT.java`
- [ ] T122 [P] Test braku danych osobowych i sekretów w logach (przechwycenie logów podczas złożenia zamówienia i webhooka: brak pełnego e-maila, adresu, `sk_test_`, `whsec_`) w `backend/src/test/java/com/project/custom/shared/infrastructure/config/LogiBezPiiIT.java`
- [ ] T123 [P] Przegląd dostępności i responsywności (360 px, siatka 2/3/4, fokus klawiatury, `aria-disabled` z wyjaśnieniem, kontrast) i poprawki w `frontend/src/app/Layout.module.css`, `frontend/src/features/katalog/KatalogPage.module.css`, `frontend/src/features/koszyk/KoszykPage.module.css`, `frontend/src/features/zamowienie/ZamowieniePage.module.css`
- [ ] T124 [P] Dodaj `README.md` w katalogu głównym (wymagania, `.env`, `docker compose up -d`, uruchomienie backendu/frontendu, testy, gitleaks) z odnośnikiem do `specs/001-shop-browse-cart-checkout/quickstart.md`
- [ ] T125 Uzupełnij job E2E w `.github/workflows/ci.yml` (compose + backend profil `e2e` + `stripe listen` z sekretami CI, uruchomienie `npm --prefix frontend run e2e`, pomijany gdy brak `STRIPE_TEST_*`) i profil `backend/src/main/resources/application-e2e.yaml` (seed jak `local`)
- [ ] T126 Uruchom `gitleaks detect --no-banner` i audyt zależności lokalnie (`osv-scanner scan source --lockfile backend/pom.xml --lockfile frontend/package-lock.json` jak w T010 + `npm --prefix frontend audit --audit-level=high`); usuń znaleziska high/critical
- [ ] T127 Przejdź ręcznie wszystkie scenariusze z `specs/001-shop-browse-cart-checkout/quickstart.md` (sekcje 3 i 4) i odnotuj wynik w opisie PR

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: brak zależności — start od razu.
- **Foundational (Phase 2)**: po Setup — BLOKUJE wszystkie historie.
- **US1 (Phase 3)**: po Foundational.
- **US2 (Phase 4)**: po Foundational; potrzebuje produktów w bazie (`V1__katalog.sql` + seed z US1: T036, T037) i encji/zapytań katalogu (T038–T043) dla `KatalogQueryFacade` (T058). UI dodawania (T067) wpina się w strony US1 (T048, T050).
- **US3 (Phase 5)**: po US2 (rozszerza agregat `Koszyk`, `KoszykService`, `KoszykController`, `KoszykPage`).
- **US4 (Phase 6)**: po US3 (korzysta z `moznaZamowic`, `problemy[]` i `wyczysc`); backend `platnosc` (T100–T107) może powstawać równolegle z US1–US3 po Phase 2, bo zależy tylko od `shared`.
- **Polish (Phase 7)**: po wszystkich historiach.

### User Story Dependencies

```text
Phase 1 ─► Phase 2 ─┬─► US1 ─► US2 ─► US3 ─► US4 ─► Polish
                    └─► (równolegle) US4: domena + adapter Stripe (T083–T086, T100–T103)
```

Każda historia jest niezależnie **testowalna i demonstrowalna** (Zasada IV) na seedzie i przez
własne E2E; zależności są wyłącznie „budowlane” (współdzielone agregaty i ekrany), nie funkcjonalne.

### Within Each User Story

- Testy (⚠️) najpierw — muszą nie przechodzić przed implementacją.
- Migracja → domena → port repozytorium → JPA/adapter → `*Service` → fasada/kontroler → frontend.
- Wywołania Stripe zawsze poza transakcją; efekty webhooka zawsze w jednej transakcji.
- Commit po każdym zadaniu lub logicznej grupie.

### Parallel Opportunities

- Phase 1: T003–T008 równolegle po T001–T002.
- Phase 2: testy T011–T014 równolegle; T016–T020 równolegle; frontend T024–T026, T028–T029 równolegle z backendem.
- US1: testy T030–T035 równolegle; T037, T038, T040 równolegle; frontend T046–T048 równolegle z backendem T039–T045.
- US2: testy T052–T056 równolegle; T058 i T059 równolegle.
- US3: testy T069–T073 równolegle; backend (T074–T077) równolegle z przygotowaniem komponentów frontendu (T079).
- US4: testy T081–T093 równolegle; migracje T095–T096 równolegle; fasady T098–T099 równolegle; domeny `platnosc` (T100) i `zamowienie` (T108) równolegle; frontend T115–T116 równolegle z backendem.
- Polish: T120–T124 równolegle.

---

## Parallel Example: User Story 1

```bash
# Testy US1 razem:
Task: "Test jednostkowy Produkt w backend/src/test/java/com/project/custom/katalog/domain/ProduktTest.java"
Task: "Test jednostkowy KryteriaWyszukiwania w backend/src/test/java/com/project/custom/katalog/domain/KryteriaWyszukiwaniaTest.java"
Task: "Test integracyjny KatalogService w backend/src/test/java/com/project/custom/katalog/application/KatalogServiceIT.java"
Task: "Test useFiltryZUrl w frontend/tests/unit/katalog/useFiltryZUrl.test.tsx"

# Seed, typy domenowe i frontend równolegle:
Task: "Seed w backend/src/main/resources/db/seed/R__seed_katalog.sql"
Task: "Typy domenowe w backend/src/main/java/com/project/custom/katalog/domain/"
Task: "Hooki zapytań w frontend/src/features/katalog/api.ts"
Task: "useFiltryZUrl w frontend/src/features/katalog/useFiltryZUrl.ts"
```

## Parallel Example: User Story 4

```bash
# Domeny dwóch BC i migracje równolegle:
Task: "Domena płatności w backend/src/main/java/com/project/custom/platnosc/domain/"
Task: "Domena zamówienia w backend/src/main/java/com/project/custom/zamowienie/domain/"
Task: "Migracja V4__platnosc.sql"
Task: "Migracja V5__outbox.sql"

# Fasady sąsiednich BC równolegle:
Task: "KatalogCommandFacade w backend/src/main/java/com/project/custom/katalog/"
Task: "KoszykQueryFacade/KoszykCommandFacade w backend/src/main/java/com/project/custom/koszyk/"
```

---

## Implementation Strategy

### MVP First (tylko User Story 1)

1. Phase 1: Setup.
2. Phase 2: Foundational (KRYTYCZNE — blokuje wszystkie historie).
3. Phase 3: US1 — przeglądanie katalogu.
4. **STOP i WALIDACJA**: quickstart 1.1–1.8 + `przegladanie.spec.ts`.
5. Demo katalogu.

### Incremental Delivery

1. Setup + Foundational → fundament gotowy (CI zielone).
2. US1 → demo przeglądania (MVP).
3. US2 → demo dodawania do koszyka i licznika.
4. US3 → demo edycji koszyka ze zmianą ceny/dostępności (wciąż bez Stripe).
5. US4 → pełna ścieżka zakupowa z płatnością testową i zdarzeniem w Outboxie.
6. Polish → kontrakt, wydajność, logi bez PII, przejście quickstart (SC-008).

### Parallel Team Strategy

1. Zespół razem kończy Setup + Foundational.
2. Następnie:
   - Dev A: US1 → US2 → US3 (katalog i koszyk, backend + frontend)
   - Dev B: US4 backend `platnosc` (domena, adapter Stripe, testy kontraktowe) od razu po Phase 2
   - Dev C: frontend US4 na MSW według kontraktu OpenAPI
3. Integracja US4 po zakończeniu US3.

---

## Notes

- [P] = inne pliki, brak zależności od niedokończonych zadań.
- [Story] mapuje zadanie na historię dla śledzenia.
- Żadnych prawdziwych sekretów w testach — `sk_test_dummy` i generowany `whsec_test_…`; E2E ze Stripe tylko z sekretami CI.
- Kwoty wyłącznie w groszach (`long`/`BIGINT`), formatowanie tylko we frontendzie.
- Frontend nie zawiera reguł cen, dostępności ani statusu płatności — tylko prezentuje pola z API.
- Unikaj: niejasnych zadań, konfliktów w tym samym pliku, zależności między historiami łamiących ich niezależną testowalność.
