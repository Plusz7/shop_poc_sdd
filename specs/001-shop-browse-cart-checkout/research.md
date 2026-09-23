# Research: Przeglądanie sklepu, koszyk i płatność

**Feature**: `001-shop-browse-cart-checkout` | **Date**: 2026-09-23 | **Plan**: [plan.md](plan.md)

Każda decyzja w formacie: **Decision / Rationale / Alternatives considered**. Po tej fazie
w Technical Context planu nie zostaje żadne „NEEDS CLARIFICATION".

---

## R-01. Układ repozytorium i narzędzie budowania

- **Decision**: monorepo z dwoma aplikacjami: `backend/` (jeden moduł Maven z wrapperem
  `mvnw`, Java 21, Spring Boot 4.0) i `frontend/` (React + Vite + TypeScript, npm). W katalogu
  głównym `compose.yaml` z zależnościami lokalnymi.
- **Rationale**: konstytucja (Zasada III) wymaga osobnego frontendu komunikującego się przez
  REST. Jeden moduł Maven wystarcza dla modularnego monolitu — granice BC egzekwujemy
  testem architektury (R-03), a nie modułami Maven. `.gitignore` ma już sekcję Maven.
- **Alternatives considered**: multi-module Maven (moduł na BC) — mocniejsze granice, ale
  narzut konfiguracji nieuzasadniony w PoC (Zasada VII); Gradle — równoważny, Maven wybrany
  dla zgodności z typowym stackiem Spring w zespole.

## R-02. Bounded Contexts i komunikacja między nimi

- **Decision**: w tej funkcji powstają BC `katalog`, `koszyk`, `zamowienie`, `platnosc` oraz
  pakiet `shared` (wspólne jądro: `Pieniadze`, `GoscId`, Outbox). BC `realizacja` (Trello)
  jest poza zakresem — powstanie w osobnej funkcji jako konsument zdarzenia
  `ZamowienieOplaconeEvent`. Kierunki zależności (wyłącznie przez `*Facade`):

  ```text
  koszyk ──► katalog            (wycena pozycji, dostępność)
  zamowienie ──► koszyk, katalog, platnosc
  platnosc ──(PlatnoscPotwierdzonaEvent / PlatnoscNieudanaEvent)──► zamowienie
  ```

  `platnosc` NIE zależy od `zamowienie`. Wynik weryfikowanego webhooka `platnosc` publikuje
  jako zdarzenie domenowe (Spring `ApplicationEventPublisher`), które `zamowienie` obsługuje
  **synchronicznie w tej samej transakcji** (`@TransactionalEventListener(phase = BEFORE_COMMIT)`
  lub zwykły `@EventListener` wywołany w transakcji webhooka).
- **Rationale**: eliminuje cykl `zamowienie ⇄ platnosc`. Synchroniczna obsługa w jednej
  transakcji daje atomowość: deduplikacja `event.id` + zmiana statusu + zmniejszenie stanu
  + wyczyszczenie koszyka + zapis `OutboxEvent` albo wszystko, albo nic (FR-019–FR-021, SC-005).
  Wszystko to jest w jednej bazie, więc nie potrzeba sagi.
- **Alternatives considered**: `platnosc` wywołuje `ZamowienieCommandFacade` — tworzy cykl
  zależności; asynchroniczna obsługa przez Outbox/RabbitMQ — zbędna złożoność i ryzyko
  przekroczenia SC-007 bez zysku; osobny proces „realizacji płatności" — przerost formy.

## R-03. Egzekwowanie granic BC

- **Decision**: test architektury ArchUnit (`ArchitectureTest`) uruchamiany w `mvn verify`:
  (1) klasy BC A nie importują nic z BC B poza jego pakietem głównym (`*Facade`, publiczne
  DTO/rekordy zdarzeń); (2) `domain/` nie zależy od `org.springframework..`, `jakarta.persistence..`,
  `com.stripe..`; (3) brak `@Autowired` na polach; (4) brak zależności od `ApplicationContext`.
- **Rationale**: w jednym module Maven tylko test architektury zamienia reguły `AGENTS.md`
  i Zasady III w bramkę PR. ArchUnit dokładnie oddaje konwencję „Facade w korzeniu BC,
  warstwy w podpakietach", której domyślne konwencje Spring Modulith nie odwzorowują
  (Modulith traktuje podpakiety jako wewnętrzne, a `domain/*Repository` ma być publiczne).
- **Alternatives considered**: Spring Modulith `ApplicationModules.verify()` — wymagałby
  nagięcia struktury pakietów; tylko code review — nieegzekwowalne.

## R-04. Baza danych, migracje i dane przykładowe

- **Decision**: SQL Server 2022 (obraz `mcr.microsoft.com/mssql/server:2022-latest`)
  lokalnie w `compose.yaml` i w testach przez Testcontainers (`MSSQLServerContainer`).
  Schemat zarządzany przez Flyway (`db/migration/V*.sql`); `spring.jpa.hibernate.ddl-auto=validate`.
  Dane przykładowe (kategorie, ≥ 500 produktów generowanych rekursywnym CTE, zdjęcia)
  w osobnej lokalizacji `db/seed` dołączanej tylko profilem `local`/`e2e`.
- **Rationale**: stack z `AGENTS.md`; Flyway daje powtarzalny schemat w testach i lokalnie;
  seed poza produkcyjną ścieżką migracji. 500+ produktów jest potrzebne do weryfikacji SC-003.
- **Alternatives considered**: Liquibase — cięższy DSL bez korzyści; `ddl-auto=update` —
  nieprzewidywalny schemat; H2 w testach — łamie Zasadę VI (prawdziwa baza) i nie ma kolacji
  polskiej z R-05.

## R-05. Wyszukiwanie bez rozróżniania wielkości liter i diakrytyków (FR-003)

- **Decision**: kolumna `produkt.nazwa` z kolacją `Polish_100_CI_AI`; zapytanie
  `WHERE nazwa LIKE :wzorzec ESCAPE '\'` z wzorcem `%fraza%`, gdzie znaki `% _ [ \` frazy są
  escapowane, a fraza przycinana do 100 znaków i `trim()`owana (edge case o długiej frazie).
  Parametr zawsze bindowany (bez konkatenacji SQL).
- **Rationale**: kolacja CI_AI robi całą pracę po stronie bazy (`łódź` = `LODZ`), bez
  dodatkowej kolumny znormalizowanej. Dla ~500–kilku tysięcy wierszy pełny skan z `LIKE '%…%'`
  mieści się głęboko poniżej 1 s (SC-003).
- **Alternatives considered**: kolumna `nazwa_znormalizowana` utrzymywana w kodzie — więcej
  kodu, ryzyko rozjazdu; Full-Text Search SQL Server — wymaga dodatkowej konfiguracji
  kontenera i nie wspiera „fragmentu słowa"; Elasticsearch — sprzeczne z Zasadą VII.

## R-06. Filtrowanie, sortowanie, paginacja (FR-001, FR-004)

- **Decision**: `GET /api/produkty` z parametrami `kategoria`, `q`, `cenaOd`, `cenaDo`
  (w groszach), `sort ∈ {cena_asc, cena_desc, nazwa_asc}` (biała lista, domyślnie `nazwa_asc`),
  `strona` (od 0), `rozmiar` (domyślnie 24, max 48). Stabilny drugi klucz sortowania `id`.
  Frontend odwzorowuje te same parametry 1:1 w query stringu URL (`/?kategoria=…&q=…&strona=2`),
  więc link do wyników jest współdzielny. Lista zwraca tylko produkty z `aktywny = 1`.
- **Rationale**: jedno źródło prawdy o stanie filtrów (URL), brak stanu w pamięci frontendu;
  biała lista sortowania chroni przed wstrzyknięciem nazw kolumn.
- **Alternatives considered**: paginacja kursorowa — zbędna przy 500 produktach i utrudnia
  „skok do strony N"; przekazywanie `Pageable.sort` wprost — ujawnia nazwy kolumn.

## R-07. Reprezentacja pieniędzy (FR-024, Zasada II)

- **Decision**: obiekt wartości `Pieniadze(long grosze, Currency waluta)` w `shared/domain`,
  waluta zawsze `PLN`; w bazie kolumny `BIGINT` z sufiksem `_grosze`; w API pola
  `…Grosze: integer` (int64). Formatowanie „1 234,56 zł" wyłącznie we frontendzie
  (`Intl.NumberFormat('pl-PL', {style:'currency', currency:'PLN'})`).
- **Rationale**: Stripe przyjmuje `unit_amount` w groszach — zero konwersji, zero zaokrągleń.
  Konstytucja zakazuje `double`/`float`.
- **Alternatives considered**: `BigDecimal` + `DECIMAL(12,2)` — dopuszczalne, ale wymaga
  konwersji do groszy przy Stripe i pilnowania skali; `javax.money` (Moneta) — zbędna zależność.

## R-08. Identyfikacja gościa i przechowywanie koszyka (FR-006, FR-012)

- **Decision**: koszyk trzymany **po stronie serwera** w bazie, powiązany z identyfikatorem
  gościa. Identyfikator to losowy UUIDv4 w ciasteczku `shop_guest`: `HttpOnly`, `Secure`
  (poza `local`), `SameSite=Lax`, `Path=/`, `Max-Age=30 dni`, odnawiane przy każdej zmianie
  koszyka. Ciasteczko wydaje backend przy pierwszym żądaniu `/api/koszyk*` / `/api/zamowienia*`
  (filtr `GoscIdFilter`). W dev frontend woła API przez proxy Vite (to samo origin), więc
  nie ma CORS ani ciasteczek cross-site.
- **Rationale**: FR-010 wymaga, by sumy liczył serwer z aktualnych cen — koszyk w bazie
  jest naturalny. Ten sam identyfikator gościa służy jako „właściciel" zamówienia (R-15)
  i pozwala wyczyścić koszyk po webhooku, nawet gdy klient zamknął kartę (edge case).
  `HttpOnly` chroni przed kradzieżą przez XSS; `SameSite=Lax` blokuje wysyłanie ciasteczka
  w cross-site POST (ochrona CSRF bez Spring Security).
- **Alternatives considered**: koszyk w `localStorage` — serwer nie widzi koszyka przy
  webhooku (nie da się go wyczyścić), a wycena i tak musi iść przez serwer; sesja HTTP
  (`JSESSIONID`) — domyślnie wygasa po 30 min, a trwała sesja wymaga Spring Session; JWT
  z zawartością koszyka — przerost i limit rozmiaru ciasteczka.
- **Retencja**: FR-012 mówi „co najmniej 30 dni" — w PoC koszyki nie są usuwane.
  Job czyszczący stare koszyki to świadomie odłożone rozszerzenie.

## R-09. Wycena koszyka i informacja o zmianie ceny (FR-010, FR-011)

- **Decision**: `PozycjaKoszyka` przechowuje `produktId`, `ilosc` i `cenaPrzyDodaniuGrosze`
  (wyłącznie informacyjnie). Każdy odczyt koszyka wycenia pozycje przez
  `KatalogQueryFacade.pobierzDoWyceny(ids)` i zwraca per pozycję: aktualną cenę, wartość,
  status `DOSTEPNY | OSTATNIE_SZTUKI | NIEDOSTEPNY`, `maksIlosc = min(stan, 99)`,
  flagi `cenaZmieniona` (z poprzednią ceną) i `iloscPrzekraczaStan`. Suma koszyka = suma
  wartości pozycji dostępnych; `moznaZamowic = niepusty && wszystkie dostępne w wymaganej ilości`.
  Po wyświetleniu zmiany ceny klient ją „akceptuje" przez `POST /api/koszyk/akceptuj-ceny`,
  co nadpisuje `cenaPrzyDodaniuGrosze` aktualnymi cenami.
- **Rationale**: ceny z przeglądarki nie mają znaczenia (FR-010, Zasada II), a informacja
  o zmianie nie znika przypadkiem przy odświeżeniu, tylko po świadomym potwierdzeniu.
- **Alternatives considered**: zamrożenie ceny w koszyku — sprzeczne z FR-010; porównanie
  po stronie frontendu z cache — frontend nie może zawierać reguł cenowych (Zasada III).

## R-10. Reguły ilości (FR-007, FR-008, edge case o niepoprawnej ilości)

- **Decision**: reguły w encji domenowej `Koszyk`: `dodaj(produkt, ilosc)` scala z istniejącą
  pozycją; ilość musi być liczbą całkowitą 1..99 (`0` w `zmienIlosc` = usunięcie pozycji);
  dodanie/zmiana ponad `min(stan, 99)` → wyjątek domenowy `IloscPrzekraczaLimit(maks)`.
  Zachowania różnią się świadomie wg spec: **dodanie** ponad limit jest **blokowane** (US2-3),
  a **zmiana ilości** w koszyku jest **ograniczana do dostępnego stanu** z komunikatem (US3-3).
  API zwraca w obu przypadkach informację o `maksIlosc`. Walidacja typu (`-1`, `1.5`, `abc`)
  na DTO przez Bean Validation (`@Min(0) @Max(99)` na `Integer`) → `400` z błędem pola.
  Optymistyczne blokowanie `@Version` na koszyku chroni przed równoległymi zmianami z dwóch kart.
- **Rationale**: logika w domenie testowalna jednostkowo bez Springa (Zasada VI).
- **Alternatives considered**: jednolite „przycinanie" także przy dodawaniu — niezgodne z US2-3.

## R-11. Integracja płatności: Stripe Checkout (FR-018, Zasada II)

- **Decision**: **Stripe Checkout Session w trybie hostowanym** (`mode=payment`,
  `payment_method_types=[card]`, `currency=pln`, `line_items` z `price_data.unit_amount`
  w groszach wyliczonymi z niezmiennych pozycji zamówienia, `customer_email`,
  `client_reference_id = zamowienie.id`, `metadata.zamowienieId`, `expires_at = teraz + 30 min`
  (minimum Stripe), `success_url = {APP_BASE_URL}/zamowienie/{numer}?session_id={CHECKOUT_SESSION_ID}`,
  `cancel_url = {APP_BASE_URL}/koszyk?platnosc=anulowana`). Backend zwraca `session.url`,
  frontend robi `window.location.assign(url)`. SDK `com.stripe:stripe-java` wyłącznie w
  `platnosc/infrastructure/stripe`, za portem domenowym `BramkaPlatnosci`.
  Wywołanie tworzące sesję ma nagłówek `Idempotency-Key = "checkout-" + platnoscId`.
- **Rationale**: spec wprost opisuje przekierowanie do strony operatora (US4-3). Hostowany
  Checkout = zakres PCI SAQ A, zero kodu formularza karty, a frontend **nie potrzebuje nawet
  klucza publishable** — żaden klucz Stripe nie trafia do przeglądarki. Stripe sam pokazuje
  błąd karty odrzuconej i pozwala ponowić próbę na swojej stronie.
- **Alternatives considered**: Payment Element (osadzony) — lepszy UX, ale wymaga
  `pk_test_…` we frontendzie, obsługi 3DS i stanów formularza — więcej pracy bez wartości
  dla PoC; Payment Links — brak kontroli nad kwotą per zamówienie.

## R-12. Webhook Stripe: weryfikacja, idempotencja, zgodność kwoty (FR-019, FR-020, SC-004/005)

- **Decision**: endpoint `POST /api/platnosci/stripe/webhook` przyjmuje surowe ciało
  (`@RequestBody String`) i nagłówek `Stripe-Signature`; weryfikacja
  `Webhook.constructEvent(payload, signature, webhookSecret)` (tolerancja 300 s). Brak/błędny
  podpis → `400`, bez żadnych zmian. Obsługiwane typy:

  | Zdarzenie Stripe | Warunek | Efekt |
  |---|---|---|
  | `checkout.session.completed` | `payment_status = paid` | `PlatnoscPotwierdzonaEvent` |
  | `checkout.session.async_payment_succeeded` | — | `PlatnoscPotwierdzonaEvent` |
  | `checkout.session.expired` | — | `PlatnoscNieudanaEvent` |
  | `checkout.session.async_payment_failed` | — | `PlatnoscNieudanaEvent` |
  | pozostałe | — | `200`, ignorowane |

  W jednej transakcji: `INSERT` do `przetworzone_zdarzenie_stripe(event_id PK)` — naruszenie
  klucza = duplikat → `200` bez efektów; potem aktualizacja `Platnosc` i publikacja zdarzenia
  domenowego obsługiwanego synchronicznie przez `zamowienie` (R-02). Przed oznaczeniem jako
  opłacone `zamowienie` porównuje `amount_total` i `currency` z sumą zamówienia — niezgodność
  → status `WYMAGA_WYJASNIENIA`. Webhook odpowiada `2xx` tylko po commicie; błąd techniczny →
  `500`, co uruchamia ponowienia po stronie Stripe.
- **Rationale**: dokładnie Zasada II i edge case'y „powtórzone" i „sfałszowane" potwierdzenie.
  Deduplikacja w tej samej transakcji co efekt eliminuje okno wyścigu przy równoległych
  dostarczeniach tego samego zdarzenia.
- **Alternatives considered**: deduplikacja po statusie zamówienia — nie chroni przed wyścigiem
  i nie obejmuje zdarzeń bez zmiany statusu; oznaczanie „opłacone" na `success_url` — zakazane
  (FR-019).

## R-13. Anulowanie, ponowna próba, operator niedostępny (FR-022, US4-5, edge case'y)

- **Decision**:
  - **Anulowanie** (klient wraca przez `cancel_url`): zamówienie zostaje `OCZEKUJE_NA_PLATNOSC`,
    koszyk nietknięty (nigdy nie był czyszczony przed płatnością), frontend pokazuje komunikat
    na podstawie `?platnosc=anulowana`. Status `PLATNOSC_NIEUDANA` nadaje webhook
    `checkout.session.expired` (maks. 30 min później).
  - **Ponowna próba**: każde „Przejdź do płatności" tworzy **nowe** zamówienie i sesję.
    Przed utworzeniem nowej sesji backend wygasza (`sessions.expire`) otwarte sesje
    poprzednich oczekujących zamówień tego gościa — chroni przed podwójną zapłatą z dwóch
    kart. Jeśli sesja jest już `complete`, nowa próba jest odrzucana komunikatem
    „Zamówienie zostało już opłacone".
  - **Stripe niedostępny**: timeouty klienta HTTP (connect 5 s, read 10 s), `maxNetworkRetries=2`
    (bezpieczne dzięki kluczowi idempotencji). Po porażce zamówienie → `PLATNOSC_NIEUDANA`,
    API `503` z kodem `PLATNOSC_NIEDOSTEPNA`, koszyk nietknięty.
  - Wywołania Stripe są wykonywane **poza** transakcją bazodanową (transakcja 1: utworzenie
    zamówienia i płatności; wywołanie Stripe; transakcja 2: zapis `stripeSessionId`/URL lub
    oznaczenie niepowodzenia) — Zasada V i zakaz wywołań zewnętrznych w transakcji z `AGENTS.md`.
- **Rationale**: najprostsze zachowanie spełniające FR-022 i SC-006; koszyk jest czyszczony
  wyłącznie przez zweryfikowany webhook.
- **Alternatives considered**: ponowne użycie tego samego zamówienia — komplikuje
  niezmienność pozycji przy zmianie koszyka; endpoint „porzuć zamówienie" na `cancel_url` —
  dodatkowy kod, a `expired` i tak przychodzi.

## R-14. Ponowna weryfikacja cen przed płatnością (FR-016, US4-7)

- **Decision**: `POST /api/zamowienia` przesyła, obok danych klienta, **potwierdzone
  podsumowanie** widziane przez klienta: lista `{produktId, ilosc, cenaJednostkowaGrosze}`
  i `sumaGrosze`. Backend wycenia koszyk od nowa; jeśli cokolwiek się różni (cena, ilość,
  skład, dostępność) → `409 PODSUMOWANIE_NIEAKTUALNE` z aktualnym podsumowaniem; zamówienie
  nie powstaje. Wartości z frontendu służą **wyłącznie do porównania** — kwota zamówienia
  i Stripe zawsze pochodzi z wyceny serwera.
- **Rationale**: klient zatwierdza dokładnie to, za co zapłaci; brak zaufania do cen z frontu.
- **Alternatives considered**: token/hasz wersji koszyka — mniej czytelny błąd dla UI;
  brak porównania — łamie FR-016.

## R-15. Dostęp do strony potwierdzenia (FR-023, edge case z cudzym numerem)

- **Decision**: numer zamówienia jest nieprzewidywalny: `ZAM-` + 10 znaków Crockford Base32
  z `SecureRandom` (np. `ZAM-7K2Q9M4XTB`), unikalny indeks. `GET /api/zamowienia/{numer}`
  zwraca dane tylko, gdy `zamowienie.goscId == GoscId z ciasteczka`; w przeciwnym razie `404`
  (nie `403` — nie ujawniamy istnienia zamówienia).
- **Rationale**: dwie warstwy — brak możliwości zgadnięcia numeru i powiązanie z przeglądarką
  klienta. Wystarczające dla zakupów gościa bez kont.
- **Alternatives considered**: numer sekwencyjny — enumerowalny; osobny token w URL —
  wyciekałby w historii/Refererze, a ciasteczko już istnieje.

## R-16. Zmniejszenie stanu i sprzedaż ostatniej sztuki (FR-021, edge case)

- **Decision**: `KatalogCommandFacade.zmniejszStan(List<PozycjaStanu>)` zwraca wynik
  `ZMNIEJSZONO | NIEWYSTARCZAJACY_STAN` i działa „wszystko albo nic" w transakcji webhooka:
  (1) odczyt stanów wszystkich produktów z blokadą `WITH (UPDLOCK, ROWLOCK)` w kolejności `id`
  (brak zakleszczeń), (2) jeśli którykolwiek stan < ilość → nic nie zmienia i zwraca
  `NIEWYSTARCZAJACY_STAN`, (3) w przeciwnym razie `UPDATE` wszystkich pozycji. Przy
  `NIEWYSTARCZAJACY_STAN` zamówienie otrzymuje `WYMAGA_WYJASNIENIA` — płatność pozostaje
  potwierdzona, koszyk jest czyszczony, zdarzenie `ZamowienieOplaconeEvent` nie powstaje.
- **Rationale**: atomowe, krótkie blokady tylko na czas transakcji webhooka, bez rezerwacji
  (zgodnie z założeniem spec); brak wyjątku oznacza, że deduplikacja zdarzenia Stripe
  i zmiana statusu commitują się razem.
- **Alternatives considered**: rezerwacja stanu na czas płatności — wprost poza zakresem spec;
  `SELECT … FOR UPDATE` + zapis — dłuższe blokady bez korzyści.

## R-17. Outbox i RabbitMQ (Zasada V, VII)

- **Decision**: w tej funkcji powstaje tabela `outbox_event` oraz komponent
  `OutboxEventPublisher` (w `shared/infrastructure/outbox`) zapisujący w tej samej transakcji
  co oznaczenie zamówienia jako opłaconego zdarzenie `ZamowienieOplaconeEvent`
  (JSON: numer, pozycje, suma, dane dostawy — do przyszłej karty Trello). **Wysyłka**
  (`OutboxEventSchedulerJob` i handler Trello) należy do funkcji `realizacja`.
  **RabbitMQ nie jest używany** — nie ma konsumenta poza procesem.
- **Rationale**: spec zobowiązuje tę funkcję wyłącznie do dostarczenia zdarzenia; Outbox
  gwarantuje, że zdarzenie nie zginie. Broker bez konsumenta łamie Zasadę VII.
- **Alternatives considered**: job wysyłki już teraz bez handlerów — martwy kod;
  RabbitMQ — brak potrzeby w PoC.

## R-18. Frontend

- **Decision**: React 19 + Vite + TypeScript (strict), React Router (tryb data/`createBrowserRouter`),
  TanStack Query do stanu serwerowego (licznik koszyka w nagłówku = zapytanie `['koszyk']`
  unieważniane po każdej mutacji — FR-013), formularz zamówienia: React Hook Form + Zod
  (walidacja UX; serwer waliduje ponownie), style: CSS Modules z responsywnym layoutem,
  bez biblioteki komponentów. Typy API generowane `openapi-typescript` z kontraktu,
  klient `openapi-fetch`. Testy: Vitest + Testing Library; E2E: Playwright.
- **Rationale**: domyślny wybór z konstytucji; TanStack Query rozwiązuje cache i odświeżanie
  bez własnego store'a; generowane typy wiążą frontend z kontraktem OpenAPI (Zasada III).
- **Alternatives considered**: Next.js — SSR zbędny i rozmywa granicę frontend/backend;
  Redux — nadmiarowy przy stanie w pełni serwerowym; Tailwind/MUI — kwestia gustu, pominięte
  dla minimalnych zależności.

## R-19. Kontrakt API (Zasada III)

- **Decision**: **API-first**. Źródło prawdy: `specs/001-shop-browse-cart-checkout/contracts/openapi.yaml`,
  kopiowany przy implementacji do `backend/src/main/resources/openapi/shop-api.yaml`.
  Backend wystawia go przez springdoc (`/v3/api-docs`, Swagger UI tylko w profilu `local`);
  kontrolery pisane ręcznie (package-private, zgodnie z `AGENTS.md`), a test kontraktowy
  (`OpenApiContractTest`) porównuje ścieżki/metody/kody odpowiedzi wygenerowane przez
  springdoc z plikiem kontraktu. Frontend generuje typy z tego samego pliku (`npm run api:types`).
  Błędy w formacie RFC 9457 `application/problem+json` (`ProblemDetail`) z polami `kod`
  i `bledy[]`.
- **Rationale**: kontrakt dostępny przed kodem (frontend i backend mogą iść równolegle),
  a jednocześnie brak generowanych publicznych interfejsów kontrolerów, które łamałyby
  zasadę package-private.
- **Alternatives considered**: openapi-generator (interfejsy Spring) — publiczne generowane
  typy i adnotacje w obcym stylu; czyste code-first — kontrakt powstaje dopiero po kodzie.

## R-20. Sekrety i konfiguracja (Zasada I)

- **Decision**: `@ConfigurationProperties("shop.stripe") @Validated record StripeProperties(
  @NotBlank @Pattern("sk_test_.+") String secretKey, @NotBlank @Pattern("whsec_.+") String webhookSecret,
  Duration connectTimeout, Duration readTimeout, URI apiBase)` (`apiBase` opcjonalny,
  domyślnie `https://api.stripe.com` — nadpisywany w testach na `stripe-mock`/WireMock) — brak lub klucz `sk_live_` blokuje start
  z komunikatem bez wartości. Wartości ze zmiennych środowiskowych `STRIPE_SECRET_KEY`,
  `STRIPE_WEBHOOK_SECRET`, `DB_PASSWORD`, `APP_BASE_URL`; `application.yaml` zawiera tylko
  `${…}` bez domyślnych wartości dla sekretów. `.env.example` rozszerzony o nowe zmienne;
  `spring-dotenv` NIE jest używany — `compose.yaml` i IDE/`mvnw` czytają `.env` jawnie
  (skrypt `scripts/run-backend.ps1`/`.sh`). Maskowanie: e-mail i adres logowane jako skrót
  (`d***@g***.com`), nigdy klucze. Gitleaks: hook pre-commit (`.pre-commit-config.yaml`)
  i krok w CI (`.github/workflows/ci.yml`).
- **Rationale**: bezpośrednie wymagania Zasady I i II (blokada klucza produkcyjnego).
- **Alternatives considered**: Vault/Azure Key Vault — przerost w PoC; domyślne wartości
  w `application.yaml` — ryzyko przypadkowego commitu.

## R-21. Strategia testów (Zasada VI)

- **Decision**:

  | Poziom | Narzędzia | Zakres |
  |---|---|---|
  | Jednostkowe domeny | JUnit Jupiter + AssertJ, bez Springa/Mockito | `Koszyk` (scalanie, limity, suma), `Zamowienie` (przejścia statusów), `Pieniadze`, `AdresDostawy` (kod NN-NNN), escapowanie frazy |
  | Integracyjne BC | `@SpringBootTest` + Testcontainers `MSSQLServerContainer` (współdzielony, `@ServiceConnection`) | każdy przypadek użycia `*Service`: listowanie/wyszukiwanie (w tym diakrytyki), CRUD koszyka, utworzenie zamówienia, obsługa webhooka (duplikat, sfałszowany podpis, niezgodna kwota, brak stanu) |
  | Kontraktowe adapterów | `stripe-mock` (Testcontainers `GenericContainer`) dla tworzenia/wygaszania sesji; WireMock dla błędów sieci/timeoutów; webhook: ciało podpisane testowym `whsec_` wygenerowanym w teście | `StripeBramkaPlatnosci`, `StripeWebhookController` |
  | Kontrakt API | `OpenApiContractTest` | zgodność springdoc z `shop-api.yaml` |
  | Architektura | ArchUnit | reguły z R-03 |
  | Frontend | Vitest + Testing Library + MSW | komponenty, stan filtrów w URL |
  | E2E | Playwright na stacku `compose` + `stripe listen --forward-to` (Stripe test mode, karty `4242 4242 4242 4242` i `4000 0000 0000 0002`) | 4 ścieżki P1 |

  E2E ze Stripe uruchamiane w CI tylko gdy ustawione sekrety `STRIPE_TEST_*` (w PR z forków
  pomijane); pozostałe poziomy działają bez sieci.
- **Rationale**: pokrywa każdą pozycję Zasady VI i `AGENTS.md`.
- **Alternatives considered**: mockowanie SDK Stripe Mockito — nie testuje serializacji
  i nagłówków; E2E bez prawdziwego Stripe — nie udowadnia SC-004/SC-007.

## R-22. Uruchomienie lokalne (SC-008)

- **Decision**: `docker compose up -d` podnosi SQL Server (z healthcheckiem i tworzeniem bazy
  `shop`) oraz — w profilu compose `stripe` — kontener `stripe/stripe-cli` z
  `listen --forward-to http://host.docker.internal:8080/api/platnosci/stripe/webhook`.
  Backend: `./mvnw spring-boot:run -Dspring-boot.run.profiles=local`; frontend: `npm run dev`
  (port 5173, proxy `/api` i `/images` → 8080). Szczegóły w [quickstart.md](quickstart.md).
- **Rationale**: jedno polecenie dla zależności, szybka pętla dev dla aplikacji.
- **Alternatives considered**: całość w compose (backend+frontend w kontenerach) — wolniejsza
  pętla dev; może zostać dodana jako profil `full` dla demo.

## R-23. Zdjęcia produktów

- **Decision**: statyczne pliki w `backend/src/main/resources/static/images/` (kilkanaście
  zdjęć współdzielonych przez produkty z seeda), serwowane pod `/images/**`; tabela
  `produkt_zdjecie(produkt_id, url, kolejnosc)`, zdjęcie główne = `kolejnosc = 0`.
  Frontend: `loading="lazy"`, stałe proporcje (brak layout shift).
- **Rationale**: brak panelu admina (poza zakresem) — obrazy są częścią seeda.
- **Alternatives considered**: blob storage/CDN — zbędne w PoC; zewnętrzne URL-e placeholderów
  — zależność od cudzego serwisu w testach E2E.

## R-24. Wydajność (SC-003)

- **Decision**: indeksy `produkt(kategoria_id, aktywny, cena_grosze)`, `produkt(aktywny, cena_grosze)`,
  `pozycja_koszyka(koszyk_id)`, unikalny `koszyk(gosc_id)`; lista produktów pobiera zdjęcie
  główne jednym zapytaniem (projekcja DTO, bez N+1); `spring.jpa.open-in-view=false`.
  Weryfikacja: test integracyjny na seedzie 500 produktów z asercją czasu odpowiedzi
  (budżet 300 ms na zapytanie backendu, reszta na render).
- **Rationale**: przy tej skali wystarczą indeksy i brak N+1; cache jest zbędny (Zasada VII).
- **Alternatives considered**: cache (Caffeine/Redis) — przedwczesna optymalizacja.

## R-25. Walidacja danych zamówienia (FR-015)

- **Decision**: DTO `ZlozZamowienieRequest` z Bean Validation: `email` (`@Email`, maks. 254),
  `imieNazwisko` (2–100), `ulicaINumer` (3–120), `kodPocztowy` (`^\d{2}-\d{3}$`),
  `miejscowosc` (2–60), `kraj` stały `PL` (nieprzyjmowany z frontu). Obiekty wartości
  `Email`, `AdresDostawy` w domenie `zamowienie` powtarzają niezmienniki. Błędy per pole
  w `ProblemDetail.bledy[] = {pole, komunikat}` — frontend wyświetla je przy konkretnych
  polach (US4-2).
- **Rationale**: walidacja na granicy + niezmienniki domeny; frontend waliduje tylko dla UX.
- **Alternatives considered**: walidacja wyłącznie we frontendzie — niedopuszczalna.
