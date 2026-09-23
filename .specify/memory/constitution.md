# Shop PoC Constitution

## Core Principles

### I. Bezpieczeństwo sekretów i kluczy API (NON-NEGOTIABLE)

- Sekrety (Stripe secret key, Stripe webhook signing secret, Trello API key/token, hasła do bazy)
  MUSZĄ być dostarczane wyłącznie przez zmienne środowiskowe lub menedżer sekretów
  i wstrzykiwane przez `@ConfigurationProperties`/`@Value`. NIGDY w kodzie, w
  `application.properties` commitowanym do repo, w testach ani w historii gita.
- Lokalnie sekrety żyją w pliku `.env` (lub `application-local.properties`), który MUSI być
  w `.gitignore`. Repozytorium zawiera jedynie `.env.example` z pustymi wartościami i opisem.
- Frontend NIGDY nie otrzymuje sekretnych kluczy. Dopuszczalny jest wyłącznie Stripe
  **publishable key** (`pk_...`); każda operacja wymagająca `sk_...` przechodzi przez backend.
- Sekrety NIE MOGĄ pojawiać się w logach, komunikatach błędów, odpowiedziach API ani
  w konfiguracji MCP commitowanej do repo (konfiguracja MCP odwołuje się do zmiennych
  środowiskowych).
- Aplikacja MUSI przy starcie zweryfikować obecność wymaganych sekretów i odmówić
  uruchomienia z czytelnym komunikatem (bez ujawniania wartości), jeśli ich brakuje.
- Repozytorium MUSI mieć skanowanie sekretów (np. `gitleaks`) jako hook pre-commit lub krok CI.
- Wyciek klucza = natychmiastowa rotacja klucza u dostawcy, niezależnie od etapu PoC.

Uzasadnienie: klucz Stripe daje dostęp do operacji finansowych, token Trello do danych
zespołu. Wyciek jest nieodwracalny, a koszt dyscypliny od pierwszego dnia jest minimalny.

### II. Płatności wyłącznie przez Stripe, serwer jako źródło prawdy

- Dane kart płatniczych NIGDY nie przechodzą przez nasz frontend ani backend — używamy
  Stripe Checkout lub Stripe Elements / Payment Element (zakres PCI DSS SAQ A).
- Kwota do zapłaty MUSI być wyliczana po stronie serwera z aktualnych cen katalogu
  i zawartości koszyka. Ceny i sumy przesłane z frontendu są ignorowane.
- Kwoty pieniężne MUSZĄ być reprezentowane jako liczby całkowite w jednostkach
  najmniejszych (grosze) lub `BigDecimal` z jawną walutą (domyślnie PLN). Zakaz `double`/`float`.
- Zamówienie staje się opłacone WYŁĄCZNIE na podstawie zweryfikowanego webhooka Stripe
  (weryfikacja podpisu `Stripe-Signature`), nigdy na podstawie przekierowania z frontendu.
- Obsługa webhooków MUSI być idempotentna (deduplikacja po `event.id`); wywołania tworzące
  płatność używają klucza idempotencji.
- W PoC obowiązuje wyłącznie **tryb testowy** Stripe (`sk_test_...`/`pk_test_...`); klucz
  produkcyjny w środowisku PoC jest błędem konfiguracji, który blokuje start aplikacji.

Uzasadnienie: płatność to najbardziej ryzykowna część sklepu — zaufanie do klienta lub
przekierowania prowadzi do zamówień „opłaconych" bez pieniędzy.

### III. Modularny monolit DDD z Bounded Contexts

- Backend jest modularnym monolitem zgodnym z `AGENTS.md`: każdy Bounded Context to osobny
  pakiet z warstwami `domain / application / infrastructure / api` i jedynym publicznym
  wejściem przez `*Facade`.
- Minimalny zestaw BC: `katalog` (produkty, kategorie, wyszukiwanie), `koszyk`,
  `zamowienie`, `platnosc` (adapter Stripe), `realizacja` (integracja z Trello).
- Komunikacja między BC wyłącznie przez Facade lub zdarzenia domenowe; zakaz sięgania do
  repozytoriów i encji innego BC.
- Encje domenowe są wolne od adnotacji frameworkowych; mapowanie na `*JpaEntity` w warstwie
  infrastruktury.
- Frontend jest osobną aplikacją komunikującą się z backendem wyłącznie przez udokumentowane
  REST API (kontrakt OpenAPI). Frontend nie zawiera reguł biznesowych dotyczących cen,
  dostępności ani statusu płatności — jedynie je prezentuje.

Uzasadnienie: wyraźne granice pozwalają rozwijać PoC w stronę produktu bez przepisywania
i izolują ryzykowne integracje od rdzenia domeny.

### IV. Kluczowe ścieżki zakupowe wzorowane na marketplace

Priorytetowe ścieżki użytkownika (P1) — każda MUSI być niezależnie testowalna i demonstrowalna:

1. **Przeglądanie sklepu** — lista produktów z paginacją, kategorie, wyszukiwanie po nazwie,
   filtrowanie (np. cena) i sortowanie; karta produktu ze zdjęciem, opisem, ceną i dostępnością.
2. **Dodawanie do koszyka** — z listy i z karty produktu, z wyborem ilości; natychmiastowa
   informacja zwrotna (licznik koszyka w nagłówku), bez wymuszania logowania.
3. **Edycja koszyka** — zmiana ilości, usuwanie pozycji, podgląd sum częściowych i sumy
   całkowitej przeliczanej przez backend; koszyk gościa przetrwa odświeżenie strony.
4. **Płatność** — przejście z koszyka do checkoutu, płatność Stripe, strona potwierdzenia
   oraz obsługa płatności nieudanej/anulowanej bez utraty koszyka.

Zasady UX inspirowane Allegro/eBay: dodanie produktu niedostępnego lub w ilości większej niż
stan jest blokowane z czytelnym komunikatem; zmiana ceny między dodaniem do koszyka
a płatnością jest pokazywana użytkownikowi przed zapłatą. Funkcje spoza P1 (opinie, licytacje,
konta sprzedawców, rekomendacje) są poza zakresem, dopóki P1 nie działa end-to-end.

Uzasadnienie: wartość PoC mierzymy tym, czy da się przejść pełną ścieżkę od przeglądania
do opłaconego zamówienia.

### V. Integracje zewnętrzne odizolowane i odporne na awarie

- Każda integracja (Stripe, Trello przez MCP/REST) jest schowana za portem (interfejsem
  w `domain/`) z adapterem w `infrastructure/`. Logika domenowa nie zna SDK dostawcy.
- Zdarzenia do systemów zewnętrznych (np. „zamówienie opłacone" → karta na tablicy Trello)
  są wysyłane asynchronicznie przez wzorzec **Outbox**: zapis zmiany domenowej i `OutboxEvent`
  w jednej transakcji, wysyłka przez job z ponawianiem.
- Awaria lub niedostępność Trello NIE MOŻE blokować ani wycofywać zakupu i płatności.
- Każde wywołanie zewnętrzne ma timeout, ograniczoną liczbę ponowień i logowanie błędu
  (bez sekretów i danych osobowych).
- Konfiguracja serwera MCP dla Trello odwołuje się do sekretów przez zmienne środowiskowe
  (Zasada I).

Uzasadnienie: systemy zewnętrzne zawodzą; klient nie może stracić zamówienia, bo kanban
jest chwilowo niedostępny.

### VI. Testy na każdym poziomie ryzyka

- Logika domenowa (sumy koszyka, reguły ilości i dostępności, przejścia statusów zamówienia)
  — testy jednostkowe bez kontekstu Springa.
- Każdy przypadek użycia w `*Service` — test integracyjny `@SpringBootTest` z Testcontainers
  (prawdziwa baza, bez mockowania repozytoriów).
- Adaptery Stripe i Trello — testy kontraktowe na atrapach (np. `stripe-mock`, WireMock);
  obsługa webhooka testowana z poprawnym i sfałszowanym podpisem.
- Każda ścieżka P1 z Zasady IV — co najmniej jeden test end-to-end przez UI
  (np. Playwright) z kartą testową Stripe.
- Testy NIGDY nie używają prawdziwych sekretów z repozytorium; sekrety testowe pochodzą ze
  zmiennych środowiskowych CI.

Uzasadnienie: koszyk i płatność to miejsca, gdzie błąd kosztuje pieniądze i zaufanie.

### VII. Prostota Proof of Concept (YAGNI)

- Budujemy najprostsze rozwiązanie spełniające ścieżki P1. Każdy dodatkowy komponent
  infrastrukturalny (broker wiadomości, cache, mikroserwis, Kubernetes) wymaga uzasadnienia
  w sekcji „Complexity Tracking" planu.
- Rezygnacje z zakresu (brak kont użytkowników, brak zwrotów, brak faktur) są dokumentowane
  w specyfikacji jako świadome założenia, a nie pomijane milcząco.
- Uproszczenia NIGDY nie dotyczą Zasad I i II — bezpieczeństwo sekretów i poprawność
  płatności obowiązują w PoC w pełnym zakresie.

Uzasadnienie: PoC ma szybko udowodnić wykonalność, ale bez długu, którego nie da się spłacić.

## Stos technologiczny i ograniczenia

- **Backend**: Java 21, Spring Boot 4.0, JPA/Hibernate, SQL Server (zgodnie z `AGENTS.md`);
  constructor injection, bez `@Autowired` na polach, bez statycznego stanu.
- **Asynchroniczność**: Outbox + job schedulera; RabbitMQ tylko jeśli plan wykaże potrzebę
  (Zasada VII).
- **Frontend**: SPA w TypeScript; domyślnie React + Vite — ostateczny wybór zatwierdzany
  w `/speckit-plan`. Responsywny layout (mobile i desktop).
- **Płatności**: Stripe (tryb testowy), Checkout Session lub Payment Element + webhooki.
- **Kanban**: Trello, integracja przez serwer MCP i/lub REST API za portem `realizacja`.
- **Kontrakt API**: OpenAPI generowane lub utrzymywane razem z kodem kontrolerów.
- **Uruchomienie lokalne**: jedno polecenie (np. `docker compose up`) podnosi bazę i zależności;
  wymagane sekrety opisane w `.env.example` i README.
- **Dane osobowe**: zbieramy wyłącznie dane niezbędne do zamówienia (e-mail, adres dostawy);
  nie logujemy ich w postaci jawnej.

## Proces wytwórczy i bramki jakości

- Praca prowadzona przez Spec Kit: `/speckit-specify` → `/speckit-clarify` → `/speckit-plan`
  → `/speckit-tasks` → `/speckit-implement`. Plan MUSI zawierać „Constitution Check"
  potwierdzający zgodność z Zasadami I–VII.
- Zmiany trafiają przez gałęzie funkcjonalne i Pull Request; bezpośredni push na `main`
  jest niedozwolony.
- Bramki PR (wszystkie MUSZĄ przejść): build, testy jednostkowe i integracyjne, skan sekretów,
  brak nowych ostrzeżeń bezpieczeństwa zależności o poziomie high/critical.
- Review PR MUSI sprawdzić: brak sekretów w diffie, wyliczanie kwot po stronie serwera,
  weryfikację podpisu webhooka, przestrzeganie granic BC.
- Funkcja jest „gotowa", gdy jej ścieżka P1 przechodzi test end-to-end i jest opisana
  w `quickstart.md` feature'a.

## Governance

- Konstytucja ma pierwszeństwo przed innymi praktykami projektu. `AGENTS.md` uzupełnia ją
  o szczegółowe konwencje kodu; w razie konfliktu obowiązuje konstytucja, a `AGENTS.md`
  należy zaktualizować.
- Zmiana konstytucji wymaga PR z opisem zmiany, uzasadnieniem, raportem wpływu (Sync Impact
  Report) i planem migracji dla istniejącego kodu, jeśli zmiana go dotyczy.
- Wersjonowanie semantyczne: MAJOR — usunięcie lub redefinicja zasady; MINOR — nowa zasada
  lub istotne rozszerzenie; PATCH — doprecyzowania i poprawki redakcyjne.
- Każdy plan i każdy PR weryfikuje zgodność z konstytucją; odstępstwo MUSI być jawnie
  uzasadnione w „Complexity Tracking". Odstępstwa od Zasad I i II są niedopuszczalne.
- Przegląd zgodności całego repozytorium odbywa się przy każdym kamieniu milowym PoC.

**Version**: 1.0.0 | **Ratified**: 2026-09-23 | **Last Amended**: 2026-09-23
