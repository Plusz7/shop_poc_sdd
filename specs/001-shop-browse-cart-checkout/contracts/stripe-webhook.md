# Kontrakt: integracja Stripe (Checkout Session + webhook)

**Feature**: `001-shop-browse-cart-checkout` | Decyzje: [research.md](../research.md) R-11–R-13

## 1. Wywołania wychodzące (backend → Stripe)

Wykonywane wyłącznie przez adapter `platnosc/infrastructure/stripe/StripeBramkaPlatnosci`,
**poza transakcją bazodanową**, z timeoutami connect 5 s / read 10 s i `maxNetworkRetries = 2`.

### `POST /v1/checkout/sessions`

| Parametr | Wartość |
|---|---|
| `mode` | `payment` |
| `payment_method_types[]` | `card` |
| `line_items[i].price_data.currency` | `pln` |
| `line_items[i].price_data.unit_amount` | cena jednostkowa pozycji zamówienia w groszach |
| `line_items[i].price_data.product_data.name` | nazwa z pozycji zamówienia |
| `line_items[i].quantity` | ilość z pozycji zamówienia |
| `customer_email` | e-mail z zamówienia |
| `client_reference_id` | `zamowienie.id` |
| `metadata[zamowienieId]`, `metadata[platnoscId]`, `metadata[numer]` | identyfikatory do korelacji |
| `expires_at` | teraz + 30 min |
| `success_url` | `${APP_BASE_URL}/zamowienie/{numer}?session_id={CHECKOUT_SESSION_ID}` |
| `cancel_url` | `${APP_BASE_URL}/koszyk?platnosc=anulowana` |
| nagłówek `Idempotency-Key` | `checkout-{platnoscId}` |

**Niezmiennik (SC-004)**: `Σ unit_amount × quantity == zamowienie.suma`. Sprawdzany asercją
w adapterze przed wywołaniem i testem kontraktowym.

**Błędy**: `APIConnectionException`, timeout, `5xx`, `RateLimitException` → port zwraca
`BramkaNiedostepna` → API `503 PLATNOSC_NIEDOSTEPNA`. `InvalidRequestException` / `AuthenticationException`
→ błąd konfiguracji, log (bez klucza) + `500`.

### `POST /v1/checkout/sessions/{id}/expire`

Wywoływane przy ponownej próbie płatności dla otwartych sesji poprzednich oczekujących
zamówień tego samego gościa (R-13). Odpowiedź z błędem „session is not open" i
`status = complete` → `JUZ_OPLACONA` → API `409 ZAMOWIENIE_JUZ_OPLACONE`.

## 2. Webhook (Stripe → backend)

**Endpoint**: `POST /api/platnosci/stripe/webhook` (w Stripe Dashboard / `stripe listen`
subskrybowane tylko zdarzenia z tabeli poniżej).

### Weryfikacja (w tej kolejności)

1. Odczyt surowego ciała jako `String` (bez deserializacji przez Jacksona).
2. `Webhook.constructEvent(payload, header "Stripe-Signature", STRIPE_WEBHOOK_SECRET)`,
   tolerancja 300 s. Błąd → `400`, log `WARN` z typem błędu (bez ciała i nagłówka).
3. `event.livemode == true` → `400` (tylko tryb testowy, Zasada II).
4. Transakcja: `INSERT przetworzone_zdarzenie_stripe(event_id)`; duplikat klucza → `200` bez efektów.
5. Obsługa wg tabeli, commit, `200`.

### Obsługiwane zdarzenia

| `event.type` | Warunek | Zmiana `Platnosc` | Zdarzenie domenowe | Efekt w `zamowienie` |
|---|---|---|---|---|
| `checkout.session.completed` | `payment_status == "paid"` | `OTWARTA → POTWIERDZONA`, zapis `payment_intent` | `PlatnoscPotwierdzonaEvent(kwota = amount_total, waluta = currency)` | `OPLACONE` lub `WYMAGA_WYJASNIENIA` (zob. data-model) |
| `checkout.session.completed` | `payment_status != "paid"` | bez zmian | — | — (czeka na `async_payment_*`) |
| `checkout.session.async_payment_succeeded` | — | `→ POTWIERDZONA` | `PlatnoscPotwierdzonaEvent` | jw. |
| `checkout.session.async_payment_failed` | — | `→ NIEUDANA` | `PlatnoscNieudanaEvent` | `PLATNOSC_NIEUDANA` |
| `checkout.session.expired` | — | `→ WYGASZONA` | `PlatnoscNieudanaEvent` | `PLATNOSC_NIEUDANA` (jeśli nadal oczekuje) |
| inne | — | — | — | `200`, ignorowane |

Korelacja: `data.object.id` (session id) → `Platnosc.operatorSesjaId`. Nieznana sesja →
`200` + log `WARN` (np. zdarzenie z innego środowiska testowego na tym samym koncie).

### Gwarancje

| Gwarancja | Mechanizm | Test |
|---|---|---|
| Sfałszowane potwierdzenie nie zmienia statusu (SC-005) | krok 2 | integracyjny: ciało z błędnym podpisem → `400`, status bez zmian |
| Powtórzone potwierdzenie przetwarzane raz (FR-020) | krok 4 + idempotentne przejście statusu | integracyjny: to samo zdarzenie 2× (także równolegle) → stan magazynowy zmniejszony raz |
| Kwota pobrana = suma zamówienia (SC-004) | porównanie `amount_total`/`currency` z `zamowienie.suma` | integracyjny: niezgodna kwota → `WYMAGA_WYJASNIENIA` |
| Opłacenie tylko przez webhook (FR-019) | `success_url` nie wywołuje żadnej zmiany stanu | E2E: wejście na `success_url` bez webhooka → „Płatność w trakcie weryfikacji" |
| Status „Opłacone" ≤ 30 s od potwierdzenia (SC-007) | synchroniczna obsługa w żądaniu webhooka | E2E z `stripe listen` |

Podpis w testach: ciało podpisywane w teście HMAC-SHA256 testowym sekretem `whsec_test_…`
ustawionym w profilu testowym (nie jest to sekret z konta Stripe).
