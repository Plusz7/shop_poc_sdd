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

2. Zależności (SQL Server + utworzenie bazy `shop`):

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

   Oczekiwane: start na `:8080`. Bez `STRIPE_SECRET_KEY` start kończy się błędem
   „Brak wymaganej konfiguracji: shop.stripe.secret-key” — bez wartości sekretów w logu.

5. Frontend (proxy `/api` i `/images` → `:8080`):

   ```bash
   npm --prefix frontend ci
   ```

   ```bash
   npm --prefix frontend run dev
   ```

   Sklep: <http://localhost:5173>. Swagger UI (tylko profil `local`): <http://localhost:8080/swagger-ui.html>.

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

## 4. Testy automatyczne

| Poziom | Polecenie | Wymaga |
|---|---|---|
| Backend: jednostkowe + integracyjne + kontraktowe + architektura | `./backend/mvnw -f backend/pom.xml verify` | Docker (Testcontainers); bez sieci do Stripe |
| Frontend: jednostkowe | `npm --prefix frontend test` | — |
| Frontend: typy z kontraktu aktualne | `npm --prefix frontend run api:types -- --check` | — |
| E2E (4 ścieżki P1) | `npm --prefix frontend run e2e` | stack z sekcji 2 uruchomiony, `STRIPE_*` testowe |
| Skan sekretów | `gitleaks detect --no-banner` | — |

Oczekiwane: wszystko zielone; test wydajności na seedzie 500 produktów mieści się w budżecie
(R-24, SC-003).
