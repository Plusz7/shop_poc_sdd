# Kontrakt UI: trasy i stan w URL (frontend SPA)

**Feature**: `001-shop-browse-cart-checkout` | API: [openapi.yaml](openapi.yaml)

Frontend prezentuje dane z API i nie zawiera reguł cen, dostępności ani statusu płatności
(Zasada III). Wszystkie kwoty formatowane `Intl.NumberFormat('pl-PL', {style: 'currency', currency: 'PLN'})`.

## Trasy

| Trasa | Widok | Źródło danych | Historia / FR |
|---|---|---|---|
| `/` | Lista produktów + nawigacja kategorii + wyszukiwarka + filtry | `GET /api/kategorie`, `GET /api/produkty` | US1, FR-001–FR-004 |
| `/produkt/:id` | Karta produktu, wybór ilości, „Dodaj do koszyka" | `GET /api/produkty/{id}`, `POST /api/koszyk/pozycje` | US1-6, US2, FR-005 |
| `/koszyk` | Koszyk: pozycje, zmiana ilości, usuwanie, wyczyszczenie, sumy, komunikaty | `/api/koszyk*` | US3, FR-009–FR-011 |
| `/zamowienie` | Formularz danych + podsumowanie → przekierowanie do Stripe | `GET /api/koszyk`, `POST /api/zamowienia` | US4-1..3, US4-7 |
| `/zamowienie/:numer` | Potwierdzenie z numerem, pozycjami, sumą i statusem; polling co 2 s | `GET /api/zamowienia/{numer}` | US4-4, US4-6, FR-023 |
| `*` | 404 z linkiem do sklepu | — | |

Nagłówek (każda strona): logo/link `/`, wyszukiwarka, link `/koszyk` z licznikiem
`koszyk.liczbaSztuk` (FR-013) — zapytanie TanStack Query `['koszyk']`, unieważniane po każdej
mutacji koszyka i po powrocie ze Stripe.

## Parametry URL listy produktów (FR-004 — link do wyników da się udostępnić)

| Parametr URL | Parametr API | Uwagi |
|---|---|---|
| `kategoria` | `kategoria` | slug |
| `q` | `q` | wyszukiwanie zatwierdzane Enterem/przyciskiem |
| `cenaOd`, `cenaDo` | te same | w UI w złotych, w URL i API w groszach |
| `sort` | `sort` | `nazwa_asc` (domyślny, pomijany w URL), `cena_asc`, `cena_desc` |
| `strona` | `strona - 1` | w URL numeracja od 1 (przyjazna), w API od 0 |

Zmiana filtra/sortowania resetuje `strona` do 1; zmiana strony zachowuje filtry (US1-7).
Pusta lista → komunikat „Brak wyników" + przycisk „Wyczyść filtry" (nawigacja do `/`) (US1-5).

## Stany widoku koszyka i zamówienia

| Stan z API | Prezentacja |
|---|---|
| `koszyk.pozycje = []` | „Twój koszyk jest pusty" + link do sklepu; brak przycisku zamówienia (US3-5) |
| `pozycja.cenaZmieniona` | cena aktualna + przekreślona `poprzedniaCenaGrosze` + baner „Cena zmieniła się" i przycisk „Rozumiem" → `POST /api/koszyk/akceptuj-ceny` (US3-6) |
| `pozycja.status = NIEDOSTEPNY` | pozycja wyszarzona, etykieta „Niedostępny", tylko akcja „Usuń" (US3-7) |
| `koszyk.moznaZamowic = false` | przycisk „Przejdź do zamówienia" nieaktywny z podpowiedzią |
| `komunikaty[kod=ILOSC_OGRANICZONA]` | toast „Dostępnych jest tylko N szt." (US3-3) |
| `409 ILOSC_PRZEKRACZA_LIMIT` przy dodawaniu | toast „Możesz dodać maksymalnie N szt." (US2-3) |
| `?platnosc=anulowana` na `/koszyk` | baner „Płatność nie została zakończona. Twój koszyk czeka." (US4-5) |
| `409 PODSUMOWANIE_NIEAKTUALNE` | podsumowanie zastąpione `problem.koszyk`, baner „Ceny lub dostępność zmieniły się — sprawdź i potwierdź ponownie" (US4-7) |
| `400 BLAD_WALIDACJI` | komunikaty przy polach wg `bledy[].pole` (US4-2) |
| `503 PLATNOSC_NIEDOSTEPNA` | „Płatność chwilowo niedostępna, spróbuj za chwilę" |
| zamówienie `OCZEKUJE_NA_PLATNOSC` na `/zamowienie/:numer` | „Płatność w trakcie weryfikacji" + spinner; po 60 s bez zmiany: „Weryfikacja trwa dłużej — odśwież stronę później" (US4-6) |
| `OPLACONE` | „Dziękujemy! Zamówienie opłacone" + unieważnienie `['koszyk']` (licznik = 0) (US4-4) |
| `PLATNOSC_NIEUDANA` | „Płatność nieudana" + link do koszyka |
| `WYMAGA_WYJASNIENIA` | „Płatność przyjęta — skontaktujemy się w sprawie realizacji" |
| `404` | „Nie znaleziono zamówienia" (bez rozróżnienia cudze/nieistniejące) |

## Dostępność i responsywność

Layout od 360 px (mobile) do desktopu; siatka produktów 2/3/4 kolumny. Formularze z `<label>`,
błędy powiązane `aria-describedby`, przyciski nieaktywne z `aria-disabled` i wyjaśnieniem.
