---
name: english-only
description: Pilnuje, żeby wszystko, co trafia do repozytorium shop_poc_sdd, było po angielsku — kod, nazwy (klasy, pakiety, BC, metody, zmienne, tabele, kolumny, endpointy, klucze JSON, i18n-klucze), komentarze, logi, komunikaty wyjątków, dokumentacja (specs/, docs/, README, konstytucja), zadania w tasks.md, karty Trello i commity. Użyj ZAWSZE przed tworzeniem lub edycją plików w repo, przy generowaniu spec/plan/tasks (speckit-*), przy przeglądzie kodu i gdy użytkownik pyta "po angielsku", "english", "nazewnictwo", "sprawdź język", "przetłumacz", "polskie nazwy".
---

# English-only w shop_poc_sdd

**Zasada:** wszystko, co ląduje w repozytorium albo w narzędziach zespołu, jest po angielsku.
Rozmowa z użytkownikiem na czacie zostaje po polsku — to jedyny wyjątek.

Ta zasada zastępuje starsze ustalenie „klasy domenowe po polsku” z `AGENTS.md` (konstytucja 1.1.0
i skill `commit` są już zgodne). Jeśli trafisz na taki konflikt — stosuj angielski i wspomnij
użytkownikowi o niespójności w jednej linijce, zamiast po cichu dostosowywać się do starego stylu.

## Co musi być po angielsku

| Obszar | Przykład ✅ | Nie ❌ |
|---|---|---|
| Pakiety / BC | `catalog`, `cart`, `order`, `payment`, `fulfillment` | `katalog`, `koszyk`, `zamowienie` |
| Klasy, rekordy, interfejsy | `Cart`, `CartLine`, `OrderPlacedEvent`, `CartFacade` | `Koszyk`, `PozycjaKoszyka`, `ZamowienieZlozoneEvent` |
| Metody, pola, zmienne, parametry | `addLine(productId, quantity)` | `dodajPozycje(idProduktu, ilosc)` |
| Testy | `shouldRejectQuantityAbove99()`, `CartServiceIT` | `powinienOdrzucic…`, `KoszykServiceIT` |
| Baza: tabele, kolumny, migracje | `cart_line`, `V3__create_order.sql` | `pozycja_koszyka` |
| REST, JSON, OpenAPI | `/api/payments/stripe/webhook`, `"totalAmount"` | `/api/platnosci/…`, `"sumaCalkowita"` |
| Kolejki, eventy, metryki, properties | `shop.orders.placed`, `shop_cart_lines_total` | `shop.zamowienia…` |
| Frontend: komponenty, hooki, pliki, klucze i18n | `CartPage.tsx`, `useCart`, `cart.empty` | `KoszykStrona.tsx` |
| Komentarze, Javadoc, TODO | `// Stripe is the source of truth` | `// Stripe jest źródłem prawdy` |
| Logi i komunikaty wyjątków | `"Cart line limit exceeded"` | `"Przekroczono limit"` |
| Dokumentacja: `specs/`, `docs/`, README, konstytucja, quickstart | całe zdania po angielsku | |
| `tasks.md`, checklisty, karty i komentarze Trello | `T037 Add Cart aggregate in …` | `T037 Dodaj agregat Koszyk …` |
| Commity, PR, nazwy gałęzi | `feat(cart): add line limit` | `feat(koszyk): …` |

**Wyjątki (dozwolony polski):**
- teksty widoczne dla klienta sklepu w UI (etykiety, komunikaty walidacji) — ale tylko jako
  **wartości** w plikach tłumaczeń (np. `pl.json`), nigdy jako klucze ani na sztywno w komponencie;
- dane testowe/seed, które udają polski katalog (`"Kubek ceramiczny"`), i cytaty wymagań
  prawnych, jeśli dosłowne brzmienie ma znaczenie — z angielskim komentarzem obok;
- rozmowa na czacie.

## Słownik domeny (używaj konsekwentnie)

| PL | EN |
|---|---|
| katalog / produkt / kategoria | catalog / product / category |
| koszyk / pozycja koszyka / ilość | cart / cart line / quantity |
| zamówienie / złożenie zamówienia | order / order placement (`placeOrder`) |
| płatność / zwrot | payment / refund |
| realizacja | fulfillment |
| suma częściowa / suma całkowita | subtotal / total |
| cena / kwota / waluta | price / amount / currency |
| gość / klient | guest / customer |
| dostępność / stan magazynowy | availability / stock |
| metryki / alerty | metrics / alerts |
| zdarzenie | event |
| fasada | facade |

Pełna lista identyfikatorów (klasy, tabele, endpointy, enumy, metryki, pliki) jest w
[glossary.md](glossary.md) — sprawdź ją przed nazwaniem czegokolwiek.

Nowe pojęcie spoza słownika: wybierz jedną nazwę, dopisz ją do `glossary.md` i trzymaj się jej wszędzie
(kod, API, baza, dokumentacja) — zero synonimów (`order` vs `purchase`).

## Przebieg

1. **Przed pisaniem** — nazwy bierz ze słownika. Tłumacząc polski opis wymagań na kod, tłumacz
   znaczenie, nie słowo w słowo (`realizacja` → `fulfillment`, nie `realization`).
2. **Podczas pisania** — dokumentacja, specyfikacje i zadania od razu po angielsku, także gdy
   użytkownik opisał je po polsku. Nie pytaj o zgodę na tłumaczenie — to jest reguła.
3. **Po zmianie** — uruchom skaner na dotkniętych plikach:
   ```bash
   bash .claude/skills/english-only/scripts/check-english.sh <pliki lub katalogi>
   ```
   Bez argumentów skanuje pliki zmienione względem `HEAD` (w tym nieśledzone).
   Popraw każde trafienie albo — gdy to dozwolony wyjątek — zostaw i powiedz dlaczego.
4. **Przy przeglądzie kodu / PR** — polskie nazwy i komentarze zgłaszaj jak każdy inny błąd.

## Edytowanie istniejących polskich plików

Starsze artefakty (np. `docs/kanban-trello.md`) mogą być jeszcze po polsku. Gdy je edytujesz:
- nowy lub zmieniany fragment pisz po angielsku;
- **nie tłumacz całego pliku przy okazji** — mieszana wersja to stan przejściowy, a masowe
  tłumaczenie to osobne zadanie (`docs: translate … to English`), które proponujesz użytkownikowi;
- zmiana nazw w kodzie (np. BC `koszyk` → `cart`) to refaktor w osobnym commicie
  `refactor(...)`, nigdy wymieszany z `feat`/`fix`.

## Checklista

- [ ] Żadnych polskich znaków (`ąćęłńóśźż`) w kodzie, nazwach plików, kluczach, komentarzach.
- [ ] Żadnych polskich słów bez diakrytyków w identyfikatorach (`koszyk`, `zamowienie`, `ilosc`).
- [ ] Nazwy zgodne ze słownikiem, bez synonimów.
- [ ] Dokumentacja, zadania i commit po angielsku.
- [ ] Polski tylko w dozwolonych wyjątkach.
