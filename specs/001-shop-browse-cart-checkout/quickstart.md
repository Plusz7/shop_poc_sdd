# Quickstart: walidacja funkcji „Przeglądanie sklepu, koszyk i płatność”

**Feature**: `001-shop-browse-cart-checkout` | [plan.md](plan.md) | [data-model.md](data-model.md) |
[contracts/](contracts/)

Przewodnik uruchomienia i sprawdzenia end-to-end (SC-008). Funkcja jest „gotowa”, gdy
wszystkie scenariusze z sekcji 3 dają oczekiwany wynik, a sekcja 4 przechodzi na zielono.

## 1. Wymagania wstępne

| Narzędzie | Wersja | Uwagi |
|---|---|---|
| JDK | 21 | backend (`mvnw` w repo, Maven instalować nie trzeba) |
| Node.js | 22 LTS | frontend, Playwright |
| Docker Desktop | aktualny | SQL Server 2022, `stripe-mock`/Testcontainers, Stripe CLI |
| Konto Stripe | tryb **testowy** | klucz `sk_test_…` z Dashboard → Developers → API keys |
| gitleaks + pre-commit | aktualne | `pre-commit install` po sklonowaniu (Zasada I) |

## 2. Konfiguracja i uruchomienie lokalne

1. Skopiuj `.env.example` do `.env` (plik jest w `.gitignore`) i uzupełnij:

   | Zmienna | Skąd |
   |---|---|
   | `STRIPE_SECRET_KEY` | `sk_test_…` z Dashboard (klucz `sk_live_` zablokuje start — R-20) |
   | `STRIPE_WEBHOOK_SECRET` | `whsec_…` wypisany przez `stripe listen` (krok 3) |
   | `DB_PASSWORD` | dowolne silne hasło dla lokalnego SQL Server |
   | `APP_BASE_URL` | `http://localhost:5173` |
   | `GRAFANA_ADMIN_PASSWORD` | dowolne silne hasło do lokalnej Grafany (bez niego `compose up` się zatrzyma — R-31) |

   Do pracy nad US1–US3 bez konta Stripe wystarczą atrapy `STRIPE_SECRET_KEY=sk_test_dummy`
   i `STRIPE_WEBHOOK_SECRET=whsec_dummy` — aplikacja wystartuje, a złożenie zamówienia zwróci
   „Płatność chwilowo niedostępna” (`503`). Kroku 3 wtedy nie wykonujesz.

2. Zależności (SQL Server + utworzenie bazy `shop`, Prometheus na `127.0.0.1:9090`, Grafana na
   `127.0.0.1:3000`):

   ```bash
   docker compose up -d
   ```

3. Przekazywanie webhooków Stripe do lokalnego backendu (profil compose `stripe`; przy pierwszym
   uruchomieniu skopiuj wypisany `whsec_…` do `.env`):

   ```bash
   docker compose --profile stripe up -d stripe-cli
   ```

4. Backend (Flyway tworzy schemat, profil `local` ładuje seed ≥ 500 produktów):

   ```bash
   ./backend/mvnw -f backend/pom.xml spring-boot:run -Dspring-boot.run.profiles=local
   ```

   Oczekiwane: start na `:8080` (API) i `:8081` (metryki i health — R-26). Bez `STRIPE_SECRET_KEY` start kończy się błędem
   „Brak wymaganej konfiguracji: shop.stripe.secret-key” — bez wartości sekretów w logu.

5. Frontend (proxy `/api` i `/images` → `:8080`):

   ```bash
   npm --prefix frontend ci
   ```

   ```bash
   npm --prefix frontend run dev
   ```

   Sklep: <http://localhost:5173>. Swagger UI (tylko profil `local`): <http://localhost:8080/swagger-ui.html>.
   Grafana: <http://localhost:3000> (login `admin`, hasło z `GRAFANA_ADMIN_PASSWORD`),
   Prometheus: <http://localhost:9090>.

## 3. Scenariusze walidacyjne (ręcznie lub przez E2E)

Karty testowe Stripe: **akceptowana** `4242 4242 4242 4242`, **odrzucana** `4000 0000 0000 0002`;
dowolna przyszła data, dowolny CVC.

### US1 — przeglądanie (FR-001–FR-005)

| # | Kroki | Oczekiwany wynik |
|---|---|---|
| 1.1 | Otwórz `/` | 24 produkty (zdjęcie, nazwa, cena, dostępność) + lista kategorii |
| 1.2 | Wybierz kategorię | tylko produkty tej kategorii; URL zawiera `?kategoria=…` |
| 1.3 | Szukaj `LODZ` (produkt seeda ma w nazwie „łódź”) | produkt znaleziony (wielkość liter i diakrytyki ignorowane) |
| 1.4 | Ustaw cenę 50–200 zł i „cena malejąco” | tylko ceny z przedziału, malejąco; parametry w URL |
| 1.5 | Skopiuj URL do nowej karty | identyczne wyniki (link współdzielny) |
| 1.6 | Szukaj `%_[zzz` | „Brak wyników” + „Wyczyść filtry”, brak błędu |
| 1.7 | Przejdź na stronę 2 | kolejne produkty, filtry zachowane |
| 1.8 | Otwórz produkt ze stanem 2 | karta z opisem, zdjęciami, etykietą „Ostatnie sztuki” |

### US2 — dodawanie do koszyka (FR-006–FR-008, FR-012, FR-013)

| # | Kroki | Oczekiwany wynik |
|---|---|---|
| 2.1 | Na karcie produktu ustaw ilość 2, „Dodaj do koszyka” | potwierdzenie, licznik w nagłówku +2 |
| 2.2 | Dodaj ten sam produkt z listy | jedna pozycja, ilość 3 |
| 2.3 | Produkt ze stanem 3, w koszyku 2 → dodaj 2 | blokada: „Możesz dodać maksymalnie 1 szt.” |
| 2.4 | Otwórz produkt niedostępny | przycisk nieaktywny, etykieta „Niedostępny” |
| 2.5 | Odśwież stronę / zamknij i otwórz przeglądarkę | koszyk zachowany (ciasteczko `shop_guest`, 30 dni) |

### US3 — edycja koszyka (FR-009–FR-011)

| # | Kroki | Oczekiwany wynik |
|---|---|---|
| 3.1 | Otwórz `/koszyk` z 2 produktami | ceny jednostkowe, ilości, wartości pozycji, suma |
| 3.2 | Zmień ilość 1 → 3 | wartość i suma przeliczone przez backend |
| 3.3 | Ustaw ilość 50 dla produktu ze stanem 5 | ilość = 5 + komunikat „Dostępnych jest tylko 5 szt.” |
| 3.4 | Ustaw 0 / kliknij „Usuń” | pozycja znika, suma przeliczona |
| 3.5 | „Wyczyść koszyk” | „Twój koszyk jest pusty”, brak przejścia do zamówienia |
| 3.6 | Zmień cenę produktu w bazie (`UPDATE produkt SET cena_grosze = …`), odśwież koszyk | nowa cena + przekreślona stara + baner zmiany ceny |
| 3.7 | Ustaw `stan = 0` produktu w koszyku, odśwież | pozycja „Niedostępny”, przycisk zamówienia nieaktywny |
| 3.8 | `PUT /api/koszyk/pozycje/{id}` z `{"ilosc": 1.5}` / `-1` / `"abc"` | `400 BLAD_WALIDACJI` |

### US4 — zamówienie i płatność (FR-014–FR-024)

| # | Kroki | Oczekiwany wynik |
|---|---|---|
| 4.1 | „Przejdź do zamówienia” | formularz + podsumowanie z sumą |
| 4.2 | Kod pocztowy `12345`, pusty e-mail | komunikaty przy polach, brak przekierowania |
| 4.3 | Poprawne dane → „Zapłać” | przekierowanie do Stripe Checkout; kwota = suma sklepu |
| 4.4 | Zapłać kartą `4242…` | strona `/zamowienie/ZAM-…`: „Płatność w trakcie weryfikacji” → „Opłacone” w ≤ 30 s; licznik koszyka 0; stan produktów zmniejszony |
| 4.5 | Nowe zamówienie, karta `4000…0002`, potem „Wróć” na stronie Stripe | Stripe pokazuje odrzucenie; po powrocie baner „Płatność nie została zakończona”, koszyk nietknięty, zamówienie nieopłacone |
| 4.6 | Zatrzymaj `stripe-cli`, zapłać `4242…` | strona potwierdzenia zostaje na „Płatność w trakcie weryfikacji”; po ponownym starcie `stripe-cli` i `stripe events resend <evt>` → „Opłacone” |
| 4.7 | Na etapie formularza zmień cenę produktu w bazie → „Zapłać” | brak przekierowania; zaktualizowane podsumowanie do ponownego zatwierdzenia |
| 4.8 | Otwórz `/zamowienie/{numer}` w innej przeglądarce (bez ciasteczka) | „Nie znaleziono zamówienia” |
| 4.9 | `stripe events resend <evt_completed>` dla opłaconego zamówienia | brak zmian; stan magazynowy zmniejszony tylko raz |
| 4.10 | `curl -X POST localhost:8080/api/platnosci/stripe/webhook -H "Stripe-Signature: t=1,v1=zly" -d '{}'` | `400`; żadne zamówienie nie zmienia statusu |
| 4.11 | Ustaw w `.env` nieosiągalny host Stripe (`STRIPE_API_BASE=http://localhost:9`), złóż zamówienie | „Płatność chwilowo niedostępna, spróbuj za chwilę”, koszyk nietknięty |

Weryfikacja stanu w bazie (opcjonalnie): statusy w tabeli `zamowienie`, zdarzenie
`ZamowienieOplacone` w `outbox_event` dla każdego opłaconego zamówienia, deduplikacja
w `przetworzone_zdarzenie_stripe` — zob. [data-model.md](data-model.md).

### US5 — monitorowanie (FR-025–FR-034, SC-009–SC-012)

Nazwy metryk, reguł i dashboardów: [contracts/metrics.md](contracts/metrics.md). Reguły
z `for: 5m` przechodzą w „firing” dopiero po 5 min — w CI sprawdza je `promtool` (sekcja 4).

| # | Kroki | Oczekiwany wynik |
|---|---|---|
| 5.1 | Po kroku 2 i 4 z sekcji 2 otwórz Grafanę → Dashboards | folder „Sklep” z 4 dashboardami; źródło danych Prometheus skonfigurowane; dane w ≤ 2 min (SC-009) |
| 5.2 | Prometheus → Status → Targets | `shop-backend` (`host.docker.internal:8081`) w stanie `UP` |
| 5.3 | `curl -s -o /dev/null -w "%{http_code}" localhost:8080/actuator/prometheus` | `404` — metryk nie ma na porcie API (FR-025, US5-7) |
| 5.4 | `curl -s localhost:8081/actuator/prometheus \| grep "^shop_"` | metryki z kontraktu, każda z `application="shop"`; brak e-maili, `ZAM-…`, `cs_test_…` (FR-031) |
| 5.5 | Wykonaj scenariusz 4.4 (zakup kartą `4242…`) | w ≤ 1 min dashboard „ścieżka zakupowa”: +1 zamówienie utworzone, +1 `OPLACONE`, wartość sprzedaży rośnie o sumę zamówienia; „płatności”: +1 `udana`, opóźnienie potwierdzenia < 30 s (US5-2, SC-010) |
| 5.6 | Wykonaj scenariusz 4.5 (karta `4000…0002`) | +1 płatność `odrzucona`, osobna seria od `udana` (US5-3, R-28) |
| 5.7 | Wykonaj scenariusz 4.10 (sfałszowany podpis) | webhook `odrzucony_podpis` +1; alert `SfalszowanePotwierdzeniePlatnosci` „firing” w Prometheus → Alerts w ≤ 30 s, „resolved” po ~5 min (US5-4) |
| 5.8 | Wykonaj scenariusz 4.11 (nieosiągalny Stripe) kilka razy w ciągu 5 min | panel wywołań Stripe: `wynik="blad"`/`timeout` i ponowienia; po 5 min `BledyOperatoraPlatnosci` „firing” (US5-6) |
| 5.9 | Zatrzymaj backend (Ctrl+C) | po ~1 min `SklepNiedostepny` „firing”; po ponownym starcie „resolved”, a wykresy liczników nie mają skoków (edge case: restart) |
| 5.10 | Zatrzymaj Prometheus i Grafanę (`docker compose stop prometheus grafana`), wykonaj scenariusz 2.1 | sklep działa bez zmian; po `docker compose start prometheus grafana` zbieranie wznawia się samo (edge case) |
| 5.11 | Po opłaconym zamówieniu odczekaj 5 min | `OutboxZalegly` „firing” z etykietą `wymaga="realizacja"` — oczekiwane w tej funkcji (R-30); `docker compose exec sqlserver … "UPDATE outbox_event SET wyslano = SYSDATETIMEOFFSET()"` → „resolved” po następnym odczycie |
| 5.12 | Odpowiedź dowolnego `/api/**` | nagłówek `X-Request-Id`; ta sama wartość w logu backendu przy tym żądaniu (R-32) |

## 4. Testy automatyczne

| Poziom | Polecenie | Wymaga |
|---|---|---|
| Backend: jednostkowe + integracyjne + kontraktowe + architektura | `./backend/mvnw -f backend/pom.xml verify` | Docker (Testcontainers); bez sieci do Stripe |
| Frontend: jednostkowe | `npm --prefix frontend test` | — |
| Frontend: typy z kontraktu aktualne | `npm --prefix frontend run api:types -- --check` | — |
| E2E (4 ścieżki P1) | `npm --prefix frontend run e2e` | stack z sekcji 2 uruchomiony, `STRIPE_*` testowe |
| Skan sekretów | `gitleaks detect --no-banner` | — |
| Reguły alertów (składnia + przypadki firing/resolved, SC-011) | `docker run --rm -v "$PWD/observability/prometheus:/p" --entrypoint promtool prom/prometheus test rules /p/tests/shop.test.yml` | Docker |
| Dashboardy odwołują się do metryk z kontraktu | `node scripts/check-dashboards.mjs` | Node.js |

Oczekiwane: wszystko zielone; test wydajności na seedzie 500 produktów mieści się w budżecie
(R-24, SC-003).
