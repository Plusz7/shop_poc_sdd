# Data Model: Przeglądanie sklepu, koszyk i płatność

**Feature**: `001-shop-browse-cart-checkout` | **Date**: 2026-09-23 | **Plan**: [plan.md](plan.md)

Model podzielony na Bounded Contexts (Zasada III). Encje domenowe są wolne od adnotacji
frameworkowych; kolumny opisują tabele `*JpaEntity` w `infrastructure/persistence`.
Między BC **nie ma kluczy obcych** — odwołania przez identyfikator (np. `produkt_id`
w koszyku), spójność pilnowana przez fasady. Wszystkie kwoty: `BIGINT` w groszach, PLN (R-07).
Czasy: `DATETIMEOFFSET(3)` w UTC.

## Wspólne jądro (`shared`)

| Typ | Rodzaj | Opis / niezmienniki |
|---|---|---|
| `Pieniadze` | Value Object | `long grosze ≥ 0`, `waluta = PLN`; `plus`, `razy(int)`; porównanie po wartości |
| `GoscId` | Value Object | UUID z ciasteczka `shop_guest` (R-08); nigdy pusty |
| `OutboxEvent` | Encja infrastruktury | patrz tabela `outbox_event` |

### Tabela `outbox_event`

| Kolumna | Typ | Ograniczenia |
|---|---|---|
| `id` | `UNIQUEIDENTIFIER` | PK |
| `typ` | `NVARCHAR(100)` | NOT NULL, np. `ZamowienieOplacone` |
| `agregat_id` | `NVARCHAR(50)` | NOT NULL (numer zamówienia) |
| `ladunek` | `NVARCHAR(MAX)` | NOT NULL, JSON |
| `utworzono` | `DATETIMEOFFSET(3)` | NOT NULL |
| `wyslano` | `DATETIMEOFFSET(3)` | NULL — wypełnia job z funkcji `realizacja` |
| `proby` | `INT` | NOT NULL DEFAULT 0 |

Indeks: `(wyslano, utworzono)` — pod przyszły job wysyłki.

---

## BC `katalog`

### Kategoria

| Pole | Typ domenowy | Kolumna | Reguły |
|---|---|---|---|
| id | `KategoriaId(long)` | `id BIGINT IDENTITY` PK | |
| nazwa | `String` | `nazwa NVARCHAR(80)` | NOT NULL, unikalna |
| slug | `String` | `slug VARCHAR(80)` | NOT NULL, unikalny, `[a-z0-9-]+` — używany w URL (`?kategoria=elektronika`) |
| kolejnosc | `int` | `kolejnosc INT` | kolejność w nawigacji |

Płaska lista, bez podkategorii (założenie spec).

### Produkt (agregat)

| Pole | Typ domenowy | Kolumna | Reguły |
|---|---|---|---|
| id | `ProduktId(long)` | `id BIGINT IDENTITY` PK | |
| nazwa | `String` | `nazwa NVARCHAR(200) COLLATE Polish_100_CI_AI` | NOT NULL; kolacja dla FR-003 (R-05) |
| opis | `String` | `opis NVARCHAR(4000)` | |
| cena | `Pieniadze` | `cena_grosze BIGINT` | NOT NULL, `> 0` (CHECK) |
| kategoriaId | `KategoriaId` | `kategoria_id BIGINT` FK → `kategoria` | NOT NULL (FK w obrębie BC) |
| stan | `int` | `stan INT` | NOT NULL, `≥ 0` (CHECK) |
| aktywny | `boolean` | `aktywny BIT` | NOT NULL; nieaktywny = „usunięty z katalogu" |
| zdjecia | `List<Zdjecie>` | tabela `produkt_zdjecie` | min. 1; główne = `kolejnosc 0` |
| wersja | — | `wersja BIGINT` (`@Version`) | optymistyczne blokowanie |

**Status dostępności (wyliczany, FR-005)**:

| Warunek | `StatusDostepnosci` |
|---|---|
| `!aktywny` lub `stan = 0` | `NIEDOSTEPNY` |
| `1 ≤ stan ≤ 3` | `OSTATNIE_SZTUKI` |
| `stan > 3` | `DOSTEPNY` |

**Operacje domenowe**: `statusDostepnosci()`, `maksDoKupienia() = min(stan, 99)`,
`czyMoznaZmniejszyc(n)`, `zmniejszStan(n)` (rzuca, gdy `n > stan`).

### Tabela `produkt_zdjecie`

| Kolumna | Typ | Ograniczenia |
|---|---|---|
| `produkt_id` | `BIGINT` | FK → `produkt`, część PK |
| `kolejnosc` | `INT` | część PK, `≥ 0` |
| `url` | `NVARCHAR(300)` | NOT NULL, ścieżka względna `/images/...` |
| `alt` | `NVARCHAR(200)` | NOT NULL |

**Indeksy**: `produkt(kategoria_id, aktywny, cena_grosze)`, `produkt(aktywny, cena_grosze)`,
`produkt(aktywny, nazwa)` (R-24).

**Kryteria wyszukiwania (`KryteriaWyszukiwania`, VO)**: `kategoriaSlug?`, `fraza?`
(trim, maks. 100 znaków, znaki `LIKE` escapowane), `cenaOd?`, `cenaDo?` (`cenaOd ≤ cenaDo`,
inaczej zamiana/400), `sortowanie ∈ {CENA_ROSNACO, CENA_MALEJACO, NAZWA}`, `strona ≥ 0`,
`rozmiar ∈ [1, 48]` (domyślnie 24).

---

## BC `koszyk`

### Koszyk (agregat)

| Pole | Typ domenowy | Kolumna | Reguły |
|---|---|---|---|
| id | `KoszykId(UUID)` | `id UNIQUEIDENTIFIER` PK | |
| goscId | `GoscId` | `gosc_id UNIQUEIDENTIFIER` | NOT NULL, UNIQUE — jeden koszyk na przeglądarkę |
| pozycje | `List<PozycjaKoszyka>` | tabela `pozycja_koszyka` | maks. 1 pozycja na produkt |
| zmieniono | `Instant` | `zmieniono DATETIMEOFFSET(3)` | aktualizowane przy każdej zmianie (FR-012) |
| wersja | `long` | `wersja BIGINT` (`@Version`) | optymistyczne blokowanie (dwie karty) |

### PozycjaKoszyka (encja w agregacie)

| Pole | Typ domenowy | Kolumna | Reguły |
|---|---|---|---|
| produktId | `long` | `produkt_id BIGINT` | część PK `(koszyk_id, produkt_id)`; bez FK (inny BC) |
| ilosc | `int` | `ilosc INT` | `1..99` (CHECK) |
| cenaPrzyDodaniu | `Pieniadze` | `cena_przy_dodaniu_grosze BIGINT` | tylko do wykrywania zmiany ceny (R-09); nigdy do wyceny |
| dodano | `Instant` | `dodano DATETIMEOFFSET(3)` | kolejność wyświetlania |

**Reguły agregatu** (R-10, testy jednostkowe):

| Operacja | Zachowanie |
|---|---|
| `dodaj(produkt, n)` | `n ∈ 1..99`; istniejąca pozycja → `ilosc += n` (FR-007); jeśli wynik > `produkt.maksDoKupienia` → `IloscPrzekraczaLimit(maks)` i brak zmian (US2-3, FR-008); produkt `NIEDOSTEPNY` → `ProduktNiedostepny` (US2-4) |
| `zmienIlosc(produktId, n)` | `n = 0` → usuń pozycję (US3-4); `n > maks` → ustaw `maks` i zwróć `IloscOgraniczona(maks)` (US3-3); `n < 0` → błąd |
| `usun(produktId)` | usuwa pozycję; brak pozycji → no-op (idempotentne) |
| `wyczysc()` | usuwa wszystkie pozycje (FR-009, FR-021) |
| `akceptujCeny(ceny)` | nadpisuje `cenaPrzyDodaniu` aktualnymi cenami (R-09) |

### WycenionyKoszyk (read model, nie utrwalany)

Wynik `KoszykService.wycen(goscId)` z danych `KatalogQueryFacade` — zwracany przez API i przez
`KoszykQueryFacade` do BC `zamowienie`:

| Pole | Opis |
|---|---|
| `pozycje[]` | `produktId, nazwa, zdjecieUrl, cenaJednostkowa (aktualna), ilosc, wartosc, status, maksIlosc, cenaZmieniona, poprzedniaCena?, iloscPrzekraczaStan` |
| `liczbaSztuk` | suma `ilosc` wszystkich pozycji (licznik w nagłówku, FR-013) |
| `suma` | suma `wartosc` pozycji dostępnych (FR-010) |
| `moznaZamowic` | niepusty ∧ każda pozycja dostępna ∧ `ilosc ≤ maksIlosc` (FR-014) |
| `problemy[]` | `CENA_ZMIENIONA`, `PRODUKT_NIEDOSTEPNY`, `ILOSC_PRZEKRACZA_STAN` z `produktId` |

---

## BC `zamowienie`

### Zamowienie (agregat)

| Pole | Typ domenowy | Kolumna | Reguły |
|---|---|---|---|
| id | `ZamowienieId(UUID)` | `id UNIQUEIDENTIFIER` PK | |
| numer | `NumerZamowienia` | `numer VARCHAR(14)` | UNIQUE; `ZAM-` + 10 znaków Crockford Base32 (R-15) |
| goscId | `GoscId` | `gosc_id UNIQUEIDENTIFIER` | NOT NULL; właściciel (FR-023); indeks |
| klient | `DaneKlienta` | kolumny `email`, `imie_nazwisko` | patrz VO |
| adres | `AdresDostawy` | `ulica`, `kod_pocztowy`, `miejscowosc`, `kraj` | patrz VO |
| pozycje | `List<PozycjaZamowienia>` | tabela `pozycja_zamowienia` | niezmienne po utworzeniu (FR-017), min. 1 |
| suma | `Pieniadze` | `suma_grosze BIGINT` | = Σ wartości pozycji (FR-024), dostawa 0 zł |
| status | `StatusZamowienia` | `status VARCHAR(30)` | patrz maszyna stanów |
| utworzono | `Instant` | `utworzono DATETIMEOFFSET(3)` | |
| oplacono | `Instant?` | `oplacono DATETIMEOFFSET(3)` NULL | ustawiane przy `OPLACONE`/`WYMAGA_WYJASNIENIA` po płatności |
| powodWyjasnienia | `String?` | `powod_wyjasnienia NVARCHAR(200)` NULL | np. `NIEWYSTARCZAJACY_STAN`, `NIEZGODNA_KWOTA` |
| wersja | `long` | `wersja BIGINT` (`@Version`) | |

### Value Objects

| VO | Pola | Niezmienniki (FR-015, R-25) |
|---|---|---|
| `DaneKlienta` | `email`, `imieNazwisko` | email poprawny, ≤ 254; imię i nazwisko 2–100 znaków |
| `AdresDostawy` | `ulicaINumer`, `kodPocztowy`, `miejscowosc`, `kraj` | ulica 3–120; kod `^\d{2}-\d{3}$`; miejscowość 2–60; `kraj = "PL"` |
| `NumerZamowienia` | `wartosc` | format `ZAM-[0-9A-HJKMNP-TV-Z]{10}` |

### PozycjaZamowienia (encja w agregacie, niezmienna)

| Pole | Kolumna | Reguły |
|---|---|---|
| lp | `lp INT` | część PK `(zamowienie_id, lp)` |
| produktId | `produkt_id BIGINT` | referencja informacyjna (bez FK) — do zmniejszenia stanu |
| nazwa | `nazwa NVARCHAR(200)` | kopia z chwili zamówienia |
| cenaJednostkowa | `cena_jednostkowa_grosze BIGINT` | `> 0` |
| ilosc | `ilosc INT` | `1..99` |
| wartosc (wyliczana) | — | `cenaJednostkowa × ilosc` |

### Maszyna stanów `StatusZamowienia`

```text
                    ┌──────────── PlatnoscPotwierdzona ∧ kwota OK ∧ stan OK ───────────► OPLACONE
                    │
OCZEKUJE_NA_PLATNOSC ─┼──────── PlatnoscPotwierdzona ∧ (brak stanu ∨ zła kwota) ─────► WYMAGA_WYJASNIENIA
                    │
                    └──────── PlatnoscNieudana (expired/failed) ∨ Stripe niedostępny ─► PLATNOSC_NIEUDANA

PLATNOSC_NIEUDANA ── PlatnoscPotwierdzona (spóźniona) ──► WYMAGA_WYJASNIENIA
OPLACONE, WYMAGA_WYJASNIENIA — stany końcowe w PoC
```

| Z → Do | Wyzwalacz | Efekty w tej samej transakcji |
|---|---|---|
| `OCZEKUJE…` → `OPLACONE` | zdarzenie potwierdzenia, `amount_total == suma`, `currency == pln`, stan wystarczający | `oplacono = now`; `KatalogCommandFacade.zmniejszStan`; `KoszykCommandFacade.wyczysc(goscId)`; `OutboxEvent(ZamowienieOplacone)` (FR-021, R-17) |
| `OCZEKUJE…` → `WYMAGA_WYJASNIENIA` | potwierdzenie, ale stan niewystarczający lub niezgodna kwota | `oplacono = now`; `powodWyjasnienia`; koszyk czyszczony; stan **nie** zmieniany |
| `OCZEKUJE…` → `PLATNOSC_NIEUDANA` | `checkout.session.expired` / `async_payment_failed` / błąd tworzenia sesji | koszyk **nie** zmieniany (FR-022) |
| `PLATNOSC_NIEUDANA` → `WYMAGA_WYJASNIENIA` | spóźnione potwierdzenie płatności | `powodWyjasnienia = POTWIERDZENIE_PO_NIEPOWODZENIU` |
| `OPLACONE` → `OPLACONE` | powtórzone potwierdzenie (inny `event.id`, ta sama sesja) | brak efektów — idempotencja domenowa (FR-020) |
| inne | — | `NiedozwolonePrzejscieStatusu` (test jednostkowy) |

Mapowanie statusu na etykietę UI (frontend, tylko prezentacja): `OCZEKUJE_NA_PLATNOSC` →
„Płatność w trakcie weryfikacji" (strona potwierdzenia, US4-6) / „Oczekuje na płatność";
`OPLACONE` → „Opłacone"; `PLATNOSC_NIEUDANA` → „Płatność nieudana";
`WYMAGA_WYJASNIENIA` → „Wymaga wyjaśnienia".

### Zdarzenie `ZamowienieOplaconeEvent` (ładunek Outbox)

`numer`, `oplacono`, `suma{grosze, waluta}`, `pozycje[{nazwa, ilosc, cenaJednostkowaGrosze}]`,
`klient{imieNazwisko}`, `adres{…}`. Bez e-maila (minimalizacja danych dla Trello — dołączany
tylko, jeśli funkcja `realizacja` uzasadni potrzebę).

---

## BC `platnosc`

### Platnosc (agregat)

| Pole | Typ domenowy | Kolumna | Reguły |
|---|---|---|---|
| id | `PlatnoscId(UUID)` | `id UNIQUEIDENTIFIER` PK | używany w kluczu idempotencji (R-11) |
| zamowienieId | `UUID` | `zamowienie_id UNIQUEIDENTIFIER` | NOT NULL; indeks; bez FK (inny BC) |
| goscId | `GoscId` | `gosc_id UNIQUEIDENTIFIER` | do wygaszania poprzednich sesji gościa (R-13) |
| kwota | `Pieniadze` | `kwota_grosze BIGINT` | = suma zamówienia |
| operatorSesjaId | `String?` | `stripe_session_id VARCHAR(255)` | UNIQUE (filtrowany, NOT NULL) |
| operatorPlatnoscId | `String?` | `stripe_payment_intent_id VARCHAR(255)` NULL | z webhooka |
| urlPlatnosci | `String?` | `url_platnosci NVARCHAR(1000)` NULL | |
| status | `StatusPlatnosci` | `status VARCHAR(20)` | `UTWORZONA → OTWARTA → POTWIERDZONA \| NIEUDANA \| WYGASZONA` |
| utworzono / potwierdzono | `Instant` | `DATETIMEOFFSET(3)` | `potwierdzono` NULL do potwierdzenia |

Przejścia: `UTWORZONA → OTWARTA` (sesja utworzona), `UTWORZONA → NIEUDANA` (Stripe
niedostępny), `OTWARTA → POTWIERDZONA` (webhook completed/paid), `OTWARTA → WYGASZONA`
(webhook expired lub nasze `expire` przy ponownej próbie), `OTWARTA → NIEUDANA`
(async_payment_failed). `POTWIERDZONA` jest końcowa.

### Tabela `przetworzone_zdarzenie_stripe`

| Kolumna | Typ | Ograniczenia |
|---|---|---|
| `event_id` | `VARCHAR(255)` | PK — deduplikacja (R-12, FR-020) |
| `typ` | `VARCHAR(100)` | NOT NULL |
| `przetworzono` | `DATETIMEOFFSET(3)` | NOT NULL |

### Port domenowy `BramkaPlatnosci` (Zasada V)

| Operacja | Wejście | Wyjście |
|---|---|---|
| `utworzSesje` | `platnoscId`, `numerZamowienia`, pozycje (nazwa, cena, ilość), email, URL-e powrotu | `SesjaPlatnosci{operatorSesjaId, url}` lub `BramkaNiedostepna` |
| `wygas` | `operatorSesjaId` | `WYGASZONA` / `JUZ_OPLACONA` |

Weryfikacja podpisu webhooka jest w adapterze `infrastructure/stripe` i produkuje
niezależny od SDK `PotwierdzenieOperatora{eventId, typ, sesjaId, paymentIntentId, kwota, waluta, oplacona}`.

### Zdarzenia publikowane przez `platnosc` (publiczne rekordy w pakiecie głównym BC)

| Zdarzenie | Pola | Konsument |
|---|---|---|
| `PlatnoscPotwierdzonaEvent` | `zamowienieId, kwotaGrosze, waluta, potwierdzono` | `zamowienie` (synchronicznie, ta sama transakcja — R-02) |
| `PlatnoscNieudanaEvent` | `zamowienieId, powod` | `zamowienie` |

---

## Fasady (publiczne API BC)

| Fasada | Operacje | Konsumenci |
|---|---|---|
| `KatalogQueryFacade` | `pobierzDoWyceny(Set<Long> ids) → Map<Long, ProduktDoWycenyDto>` | `koszyk`, `zamowienie` |
| `KatalogCommandFacade` | `zmniejszStan(List<PozycjaStanuDto>) → WynikZmniejszeniaStanu` | `zamowienie` |
| `KoszykQueryFacade` | `wycen(GoscId) → WycenionyKoszykDto` | `zamowienie` |
| `KoszykCommandFacade` | `wyczysc(GoscId)` | `zamowienie` |
| `PlatnoscFacade` | `rozpocznij(RozpocznijPlatnoscDto) → RozpoczetaPlatnoscDto`, `wygasOtwarte(GoscId) → WynikWygaszenia` | `zamowienie` |
| `ZamowienieQueryFacade` | `pobierz(NumerZamowienia, GoscId) → Optional<ZamowienieDto>` | (przyszła funkcja `realizacja`) |

Kontrolery REST korzystają z `*Service` własnego BC, nie z fasad innych BC.
