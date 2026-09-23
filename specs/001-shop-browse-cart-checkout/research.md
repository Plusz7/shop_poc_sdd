# Research: Przeglądanie sklepu, koszyk i płatność

**Feature**: `001-shop-browse-cart-checkout` | **Date**: 2026-09-23 | **Plan**: [plan.md](plan.md)

Każda decyzja w formacie: **Decision / Rationale / Alternatives considered** (R-01–R-25:
ścieżka zakupowa; R-26–R-34: obserwowalność, US5). Po tej fazie
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
  - **Stripe niedostępny**: timeouty klienta HTTP (connect 5 s, read 10 s), 2 ponowienia
    (bezpieczne dzięki kluczowi idempotencji). Ponowienia wykonuje adapter (`maxNetworkRetries=0`
    w SDK, backoff 0,5 s / 1 s, tylko dla błędów połączenia, timeoutu, `5xx` i `429`), żeby dało
    się je policzyć w metryce `shop.stripe.ponowienia` (FR-028, R-27). Po porażce zamówienie → `PLATNOSC_NIEUDANA`,
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
  port `OutboxEventPublisher` (w `shared/domain/outbox`, bez adnotacji frameworkowych)
  z adapterem `JpaOutboxEventPublisher` (w `shared/infrastructure/outbox`) zapisujący w tej samej transakcji
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

---

# Obserwowalność (US5, FR-025–FR-034, SC-009–SC-012)

## R-26. Stos metryk i endpoint zarządzania (FR-025, FR-026, FR-030, FR-031)

- **Decision**: Spring Boot Actuator (już w zależnościach) + Micrometer z
  `io.micrometer:micrometer-registry-prometheus` (wersja z BOM Boot 4). Actuator działa na
  **osobnym porcie zarządzania** `management.server.port=8081`, z wystawionymi wyłącznie
  endpointami `health` i `prometheus` (`management.endpoints.web.exposure.include=health,prometheus`).
  Port `8080` (API) nie wystawia `/actuator/**`, a proxy Vite przekazuje tylko `/api` i `/images`
  na `8080`, więc klient sklepu nie ma drogi do metryk. Wspólna etykieta
  `management.metrics.tags.application=${spring.application.name}` (= `shop`). Histogram
  `http.server.requests`: `percentiles-histogram=true` + SLO `100ms, 300ms, 500ms, 1s, 2s`
  (p50/p95/p99 liczone w Prometheusie przez `histogram_quantile`). Etykieta `uri` to szablon
  ścieżki ze Spring MVC (`/api/produkty/{id}`); nieznane adresy trafiają do `uri="UNKNOWN"`/`"/**"`
  — Boot nie tworzy serii per adres. Dodatkowo `MeterFilter.maximumAllowableTags(
  "http.server.requests", "uri", 50, …)` jako bezpiecznik kardynalności. Health: probes
  `liveness`/`readiness` (`management.endpoint.health.probes.enabled=true`), readiness obejmuje
  `db`; szczegóły odpowiedzi health `never` (bez danych konfiguracji). JVM, GC, wątki
  i HikariCP (`hikaricp_connections_*`) — metryki wbudowane Boot. Migracje: własny gauge
  `shop.flyway.migracje{stan}` wyliczany raz po starcie z `Flyway.info()` (Boot nie publikuje
  metryk Flyway).
- **Rationale**: Actuator + Micrometer to standard Boot — zero własnego formatu, a metryki
  techniczne z FR-030 dostajemy bez kodu. Osobny port spełnia FR-025 bez Spring Security
  (Zasada VII) — izolację zapewnia topologia (port nieproksowany i niepublikowany), a test
  integracyjny sprawdza, że `:8080/actuator/prometheus` zwraca `404`.
- **Alternatives considered**: Spring Security z Basic auth dla `/actuator` — dodatkowy
  komponent i sekret tylko po to, by chronić endpoint osiągalny z sieci wewnętrznej;
  OpenTelemetry SDK + Collector — mocniejsze (także ślady), ale spec wyłącza tracing, a Collector
  to kolejny kontener; eksport push (Pushgateway) — spec zakłada pull.

## R-27. Metryki biznesowe a granice BC (FR-027, FR-028, Zasada III)

- **Decision**: każdy BC, który mierzy zdarzenia biznesowe, ma **port metryk** w warstwie
  `application/` — `public interface MetrykiKoszyka`, `MetrykiZamowien`, `MetrykiPlatnosci` —
  z metodami w języku domeny (`dodanoDoKoszyka(int ilosc)`, `zamowienieUtworzone()`,
  `zamowienieZakonczone(StatusZamowienia, Pieniadze, Duration czasDoOplacenia)`,
  `potwierdzenieOperatora(WynikPotwierdzenia)`, `rozbieznoscPodsumowania()`, …). Adapter
  `Micrometer<Port>` jest package-private w `<bc>/infrastructure/metrics/`. Serwisy aplikacyjne
  wołają port po udanym przypadku użycia; **domena nie wie o metrykach**. Zapis metryk
  dotyczących zmian w transakcji jest odkładany do **po commicie** przez wspólny helper
  `shared/infrastructure/metrics/PoCommicie` (`TransactionSynchronization.afterCommit`; bez
  aktywnej transakcji — natychmiast), więc wycofana transakcja nie zawyża liczników (SC-010).
  Metryki wywołań Stripe (czas, wynik `sukces|blad|timeout`, ponowienia) rejestruje bezpośrednio
  adapter `StripeBramkaPlatnosci` (to już warstwa `infrastructure`). ArchUnit dostaje nową regułę:
  `io.micrometer..` wolno importować tylko z `..infrastructure..`.
- **Rationale**: metryki to szczegół infrastruktury — port w `application/` zachowuje
  Dependency Inversion z `AGENTS.md`, a osobny port na BC trzyma Interface Segregation
  (koszyk nie widzi metod płatności). Odkładanie do commitu daje liczby zgodne z bazą.
- **Alternatives considered**: `MeterRegistry` wstrzykiwany wprost do `*Service` — prościej,
  ale liczniki rosną też przy rollbacku i serwisy zależą od biblioteki; nasłuchiwanie
  zdarzeń Springa `@TransactionalEventListener(AFTER_COMMIT)` — wymagałoby nowych zdarzeń
  (`PozycjaDodanaEvent`, `ZamowienieUtworzoneEvent`) tylko na potrzeby metryk; jeden wspólny
  port w `shared` — łamie ISP i wiąże `shared` z pojęciami wszystkich BC; aspekty AOP — ukryta
  logika, trudne do testowania.

## R-28. Metryki płatności odrzuconych i anulowanych (FR-027, US5-3)

- **Decision**: odrzucenie karty w hostowanym Checkout **nie kończy sesji** — klient może
  spróbować inną kartą, a Stripe nie wysyła żadnego `checkout.session.*`. Dlatego webhook
  subskrybuje dodatkowo `payment_intent.payment_failed`, obsługiwane **wyłącznie metrycznie**
  (licznik `shop.platnosci{wynik="odrzucona"}`) — bez zmiany `Platnosc` i `Zamowienie`,
  z deduplikacją `event.id` jak każde zdarzenie. Mapowanie wyników:
  `checkout.session.completed` (paid) / `async_payment_succeeded` → `udana`;
  `payment_intent.payment_failed` / `async_payment_failed` → `odrzucona`;
  `checkout.session.expired` (także po naszym `expire` przy ponownej próbie) → `anulowana`.
- **Rationale**: bez tego zdarzenia dashboard płatności nigdy nie pokazałby płatności
  odrzuconej kartą `4000 0000 0000 0002` (US5-3), bo sesja wygasa dopiero po 30 min jako
  „anulowana". Brak zmian stanu zachowuje FR-022 (koszyk i sesja nietknięte).
- **Alternatives considered**: oznaczanie płatności jako `NIEUDANA` po pierwszym odrzuceniu —
  blokowałoby ponowną próbę w tej samej sesji Stripe; liczenie powrotów przez `cancel_url` —
  klient nie musi wracać, a powrót ≠ odrzucenie.

## R-29. SLO „Opłacone ≤ 30 s" i wykrywanie braku webhooków (FR-034, SC-007)

- **Decision**: backend nie wie o udanej płatności, dopóki nie dostanie webhooka (FR-019),
  więc alert „zamówienie oczekuje z udaną płatnością > 30 s" realizujemy dwiema regułami:
  (a) **opóźnienie potwierdzenia** — histogram `shop.platnosc.opoznienie.potwierdzenia`
  = czas od `event.created` (Stripe) do commitu obsługi webhooka; alert, gdy p95 > 30 s
  przez 5 min; (b) **brak potwierdzeń** — zamówienia powstają, a webhooki nie przychodzą:
  `increase(shop_zamowienia_utworzone_total[15m]) > 0 and increase(shop_platnosc_webhook_total[15m]) == 0`
  przez 5 min (np. zatrzymany `stripe-cli`, zły `whsec_`). Czas od utworzenia zamówienia do
  opłacenia (FR-028) to osobny histogram `shop.zamowienie.czas.do.oplacenia` — informacyjny,
  bo obejmuje czas, w którym klient wpisuje dane karty.
- **Rationale**: dwie reguły pokrywają oba sposoby złamania SC-007 (webhook spóźniony albo
  w ogóle nieobecny) bez odpytywania Stripe.
- **Alternatives considered**: okresowe odpytywanie Stripe o sesje oczekujących zamówień —
  nowy job i ruch do API tylko dla alertu; alert na wiek zamówień `OCZEKUJE_NA_PLATNOSC` —
  fałszywe alarmy, bo sesja legalnie trwa do 30 min.

## R-30. Metryki Outboxa i alert bez joba wysyłki (FR-029, FR-034, R-17)

- **Decision**: gauge'e `shop.outbox.oczekujace` (liczba `outbox_event` z `wyslano IS NULL`)
  i `shop.outbox.najstarsze` (wiek najstarszego z nich w sekundach) liczone jednym zapytaniem
  `SELECT COUNT(*), MIN(utworzono) … WHERE wyslano IS NULL` (istniejący indeks
  `(wyslano, utworzono)`), z wynikiem buforowanym 15 s, żeby każdy odczyt Prometheusa nie szedł
  do bazy. Reguła alertu `OutboxZalegly` (najstarsze > 5 min) **powstaje teraz**, ale w tej
  funkcji nie ma joba wysyłki (R-17), więc po każdym opłaconym zamówieniu przejdzie w „firing"
  po 5 min. Dlatego reguła ma etykietę `wymaga="realizacja"` i adnotację wyjaśniającą, a
  dashboard pokazuje ją w osobnym panelu „oczekuje na funkcję realizacja". Test SC-011
  (`promtool test rules`) udowadnia przejście `firing → resolved` na danych syntetycznych;
  w quickstart stan `resolved` wymusza się ręcznym `UPDATE outbox_event SET wyslano = …`.
- **Rationale**: FR-029/FR-034 wymagają metryki i reguły teraz; ukrycie reguły do czasu
  `realizacja` łamałoby spec, a milczące „firing" bez wyjaśnienia byłoby mylące.
- **Alternatives considered**: odłożenie reguły do funkcji `realizacja` — niezgodne z FR-034;
  job oznaczający zdarzenia jako wysłane bez handlera — fałszowałby metrykę i łamał R-17.

## R-31. Prometheus i Grafana lokalnie (FR-032, FR-033, SC-009)

- **Decision**: `compose.yaml` (profil domyślny, więc `docker compose up -d` podnosi je razem
  z SQL Server) dostaje:
  - `prometheus` (`prom/prometheus`, wersja przypięta w compose) z
    `--storage.tsdb.retention.time=15d`, `scrape_interval`/`evaluation_interval` 15 s, scrape
    `host.docker.internal:8081/actuator/prometheus` (backend działa na hoście — R-22;
    `extra_hosts: host.docker.internal:host-gateway` dla Linuksa), reguły z
    `observability/prometheus/rules/*.yml`. Port `127.0.0.1:9090`.
  - `grafana` (`grafana/grafana`, wersja przypięta) z provisioningiem z repozytorium:
    `observability/grafana/provisioning/datasources/prometheus.yaml` (źródło danych z
    `uid: prometheus`), `…/provisioning/dashboards/shop.yaml` (provider plików) i dashboardy
    JSON w `observability/grafana/dashboards/` (`http.json`, `sciezka-zakupowa.json`,
    `platnosci-integracje.json`, `jvm-baza.json` — FR-033 a–d). Hasło admina
    `GF_SECURITY_ADMIN_PASSWORD: ${GRAFANA_ADMIN_PASSWORD:?Ustaw GRAFANA_ADMIN_PASSWORD w .env}`
    — brak zmiennej przerywa `compose up`; `GF_USERS_ALLOW_SIGN_UP=false`,
    `GF_AUTH_ANONYMOUS_ENABLED=false`. Port `127.0.0.1:3000`.
  - Alerty to **reguły Prometheusa** (widoczne w Prometheus `/alerts` i w Grafanie jako alerty
    źródła danych); bez Alertmanagera (wysyłka powiadomień poza zakresem — spec).
  - Dashboardy odwołują się tylko do metryk z [contracts/metrics.md](contracts/metrics.md);
    wszystkie zapytania liczników używają `rate`/`increase` (odporność na restart — edge case).
- **Rationale**: reguły Prometheusa są plikami YAML testowalnymi `promtool test rules` w CI
  (SC-011), a provisioning plików spełnia „bez ręcznego klikania". Porty na `127.0.0.1` —
  narzędzia operatora nie są wystawiane w sieci lokalnej.
- **Alternatives considered**: alerty zarządzane przez Grafanę (unified alerting) — trudniej
  testować w CI i eksportować deterministycznie; Grafana Cloud — konto zewnętrzne i sekret;
  backend w kontenerze obok Prometheusa — wolniejsza pętla dev (R-22).

## R-32. Identyfikator korelacji w logach (Assumptions spec)

- **Decision**: filtr `KorelacjaFilter` w `shared/api` (przed `GoscIdFilter`) przyjmuje
  nagłówek `X-Request-Id`, jeśli pasuje do `^[A-Za-z0-9-]{8,64}$`, w przeciwnym razie generuje
  UUID; wkłada go do MDC pod kluczem `requestId`, zwraca w nagłówku odpowiedzi i czyści MDC po
  żądaniu. Wzorzec logu: `logging.pattern.level=%5p [%X{requestId:-}]`. Obsługa webhooka dodaje
  do MDC `stripeEventId` (identyfikator zdarzenia Stripe nie jest daną osobową). ID korelacji
  **nie** trafia do metryk (FR-031).
- **Rationale**: wystarczy, by powiązać wpis w logu z incydentem widocznym na dashboardzie
  (spec), bez tracingu. Walidacja nagłówka chroni logi przed wstrzyknięciem znaków.
- **Alternatives considered**: Micrometer Tracing (traceId/spanId) — spec wyłącza tracing;
  bez przyjmowania nagłówka z zewnątrz — traci się korelację z proxy/testem E2E.

## R-33. Brak danych osobowych i kontrola kardynalności (FR-031, SC-012)

- **Decision**: katalog metryk w [contracts/metrics.md](contracts/metrics.md) jest zamkniętą
  listą: każda etykieta ma wyliczalny zbiór wartości (enumy Javy przekazywane do portów, nigdy
  `String` z danych wejściowych). Test `MetrykiEndpointIT` (Testcontainers) wykonuje pełną
  ścieżkę (koszyk → zamówienie → webhook udany, zduplikowany, sfałszowany), pobiera
  `:8081/actuator/prometheus` i sprawdza: (1) brak adresu e-mail, imienia/nazwiska, adresu,
  numeru `ZAM-…`, `cs_test_…`, `pi_…`, `sk_…`, `whsec_…` (wzorce regex); (2) każda metryka
  `shop_*` ma etykietę `application="shop"`; (3) zbiór nazw `shop_*` jest równy liście
  z kontraktu; (4) `:8080/actuator/prometheus` → `404`.
- **Rationale**: SC-012 wymaga testu automatycznego; porównanie z kontraktem pilnuje, że
  dashboardy i reguły odwołują się do istniejących metryk.
- **Alternatives considered**: tylko code review — nieegzekwowalne; `MeterFilter` usuwający
  nieznane etykiety w runtime — maskuje błąd zamiast go zgłosić.

## R-34. Testy obserwowalności i narzut (SC-010, SC-011, SC-012)

- **Decision**:

  | Poziom | Narzędzie | Zakres |
  |---|---|---|
  | Jednostkowe adapterów metryk | `SimpleMeterRegistry`, bez Springa | nazwy, etykiety i wartości dla każdej metody portu |
  | Integracyjne `*Service` | istniejące testy + asercje na `MeterRegistry` | licznik rośnie po commicie, **nie** rośnie po rollbacku (SC-010) |
  | Endpoint metryk | `MetrykiEndpointIT` | R-33 |
  | Reguły alertów | `promtool check rules` + `promtool test rules observability/prometheus/tests/*.test.yml` (obraz `prom/prometheus` w CI) | każda reguła z FR-034 ma przypadek `firing` i `resolved` (SC-011) |
  | Dashboardy | `scripts/check-dashboards.mjs` w CI | poprawny JSON, `datasource.uid = prometheus`, każda metryka `shop_*` z zapytań istnieje w kontrakcie |
  | Narzut | `WydajnoscKatalogIT` (T153) z metrykami (konfiguracja jak w produkcji) i raz z `management.metrics.enable.all=false` | różnica p95 < 5% lub < 5 ms (próg szumu) — SC-012 |
  | Ręcznie / demo | [quickstart.md](quickstart.md), US5 | dashboardy po `compose up` (SC-009), alerty wywołane na żywo |

- **Rationale**: `promtool` testuje reguły deterministycznie i bez czekania 5 minut, więc
  SC-011 jest weryfikowalne w CI; test po rollbacku chroni zgodność liczb z bazą.
- **Alternatives considered**: E2E czekające na „firing" w działającym Prometheusie —
  minuty na test i niestabilność; brak testów dashboardów — literówka w nazwie metryki daje
  pusty panel bez błędu.
