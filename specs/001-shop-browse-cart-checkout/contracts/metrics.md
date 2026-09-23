# Kontrakt: metryki, reguły alertów i dashboardy (US5)

**Feature**: `001-shop-browse-cart-checkout` | [plan.md](../plan.md) | decyzje: R-26–R-34 w
[research.md](../research.md)

Ten plik jest **źródłem prawdy** dla nazw i etykiet metryk. `MetrykiEndpointIT` porównuje
zbiór metryk `shop_*` z tabelą w sekcji 2, a `scripts/check-dashboards.mjs` sprawdza,
że dashboardy i reguły odwołują się wyłącznie do metryk z tego pliku (R-33, R-34).

## 1. Endpoint

| Element | Wartość |
|---|---|
| Adres | `http://<host>:8081/actuator/prometheus` (port zarządzania, **nie** `8080`) |
| Inne endpointy na `8081` | `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness` |
| Na porcie `8080` | `/actuator/**` → `404` |
| Format | Prometheus text exposition (Micrometer) |
| Scrape | Prometheus co 15 s, job `shop-backend` |
| Wspólna etykieta | `application="shop"` na każdej metryce |

## 2. Metryki domenowe `shop_*`

Nazwy w notacji Micrometera (w kodzie) → nazwa w Prometheusie. Wszystkie kombinacje etykiet
są **rejestrowane z wartością 0 przy starcie**, aby `increase()` i reguły typu „brak zdarzeń”
działały od pierwszej minuty.

| Micrometer | Prometheus | Typ | Etykiety (zamknięty zbiór) | Kiedy rośnie | FR |
|---|---|---|---|---|---|
| `shop.koszyk.dodania` | `shop_koszyk_dodania_total` | counter | — | po commicie dodania pozycji do koszyka (`POST /api/koszyk/pozycje` → 2xx) | FR-027 |
| `shop.zamowienia.utworzone` | `shop_zamowienia_utworzone_total` | counter | — | po commicie TX1 utworzenia zamówienia | FR-027 |
| `shop.zamowienia.rozbieznosci` | `shop_zamowienia_rozbieznosci_total` | counter | `rodzaj`: `CENA`, `DOSTEPNOSC`, `SKLAD` | `409 PODSUMOWANIE_NIEAKTUALNE` (jedna inkrementacja na rodzaj wykryty w żądaniu) | FR-027, FR-016 |
| `shop.zamowienia.zakonczone` | `shop_zamowienia_zakonczone_total` | counter | `status`: `OPLACONE`, `PLATNOSC_NIEUDANA`, `WYMAGA_WYJASNIENIA` | po commicie zmiany statusu na docelowy | FR-027 |
| `shop.zamowienia.oplacone.wartosc` (base unit `pln`) | `shop_zamowienia_oplacone_wartosc_pln_total` | counter | — | po commicie `OPLACONE`, o `suma.grosze / 100` (tylko prezentacja — nie służy do obliczeń pieniężnych) | FR-027 |
| `shop.zamowienie.czas.do.oplacenia` | `shop_zamowienie_czas_do_oplacenia_seconds_{bucket,count,sum}` | timer (histogram; kubełki 30 s, 1 min, 2 min, 5 min, 10 min, 30 min) | — | po commicie `OPLACONE`: `oplacono − utworzono` | FR-028 |
| `shop.platnosci` | `shop_platnosci_total` | counter | `wynik`: `udana`, `odrzucona`, `anulowana` | mapowanie zdarzeń Stripe — R-28 | FR-027 |
| `shop.platnosc.webhook` | `shop_platnosc_webhook_total` | counter | `wynik`: `przetworzone`, `zduplikowane`, `odrzucony_podpis`, `odrzucony_livemode`, `zignorowane` | każde żądanie na webhook, wg wyniku kroku weryfikacji/deduplikacji ([stripe-webhook.md](stripe-webhook.md)) | FR-028 |
| `shop.platnosc.opoznienie.potwierdzenia` | `shop_platnosc_opoznienie_potwierdzenia_seconds_{bucket,count,sum}` | timer (histogram; kubełki 1, 5, 10, 30, 60, 120 s) | — | po commicie obsługi `przetworzone` zdarzeń zmieniających status: `teraz − event.created` | FR-028, SC-007 |
| `shop.stripe.wywolania` | `shop_stripe_wywolania_seconds_{bucket,count,sum}` | timer (histogram; kubełki 100 ms, 300 ms, 1 s, 3 s, 10 s) | `operacja`: `utworz_sesje`, `wygas_sesje`; `wynik`: `sukces`, `blad`, `timeout` | każda **próba** wywołania Stripe w adapterze | FR-028 |
| `shop.stripe.ponowienia` | `shop_stripe_ponowienia_total` | counter | `operacja`: jw. | każda ponowna próba (adapter ponawia sam, `maxNetworkRetries=0` w SDK) | FR-028 |
| `shop.outbox.oczekujace` | `shop_outbox_oczekujace` | gauge | — | liczba `outbox_event` z `wyslano IS NULL` (bufor 15 s) | FR-029 |
| `shop.outbox.najstarsze` (base unit `seconds`) | `shop_outbox_najstarsze_seconds` | gauge | — | wiek najstarszego niewysłanego zdarzenia; `0`, gdy brak | FR-029 |
| `shop.flyway.migracje` | `shop_flyway_migracje` | gauge | `stan`: `success`, `failed`, `pending` | liczba migracji w danym stanie, wyliczana raz po starcie | FR-030 |

**Zakazane w etykietach i nazwach** (FR-031, test SC-012): e-mail, imię i nazwisko, adres,
numer zamówienia (`ZAM-…`), identyfikatory Stripe (`cs_…`, `pi_…`, `evt_…`), `GoscId`,
identyfikator korelacji, fraza wyszukiwania, klucze (`sk_…`, `whsec_…`).

## 3. Metryki wbudowane (Spring Boot / Micrometer / Prometheus)

| Metryka | Źródło | Użycie |
|---|---|---|
| `http_server_requests_seconds_{bucket,count,sum}` z etykietami `method`, `uri` (szablon), `status`, `outcome`, `exception` | Spring MVC; `percentiles-histogram=true`, SLO 100 ms/300 ms/500 ms/1 s/2 s | dashboard HTTP, alerty 5xx i p95 (FR-026) |
| `jvm_memory_*`, `jvm_gc_*`, `jvm_threads_*` | Micrometer JVM | dashboard JVM (FR-030) |
| `hikaricp_connections_{active,idle,pending}`, `hikaricp_connections_acquire_seconds_*` | HikariCP | dashboard bazy (FR-030) |
| `process_uptime_seconds`, `process_cpu_usage`, `application_ready_time_seconds` | Boot | dashboard JVM |
| `up{job="shop-backend"}` | Prometheus | alert niedostępności |

Stan liveness/readiness: `/actuator/health/{liveness,readiness}` na `8081` (readiness obejmuje
`db`); Prometheus mierzy dostępność przez `up`.

## 4. Reguły alertów (`observability/prometheus/rules/shop.yml`)

Grupa `shop`, `interval: 15s`. Każda reguła ma etykiety `application="shop"`, `waga`
(`krytyczny` | `ostrzezenie`) oraz adnotacje `summary` (po polsku) i `dashboard` (link do
panelu). Każda ma przypadek `firing` i `resolved` w `observability/prometheus/tests/shop.test.yml`
(SC-011).

| Alert | Wyrażenie (PromQL) | `for` | Waga | FR-034 |
|---|---|---|---|---|
| `WysokiOdsetekBledow5xx` | `sum(rate(http_server_requests_seconds_count{application="shop",status=~"5.."}[5m])) / sum(rate(http_server_requests_seconds_count{application="shop"}[5m])) > 0.05` | 5m | krytyczny | 5xx > 5% |
| `WolneOdpowiedziKataloguKoszyka` | `histogram_quantile(0.95, sum by (le, uri) (rate(http_server_requests_seconds_bucket{application="shop",uri=~"/api/produkty\|/api/koszyk"}[5m]))) > 1` | 5m | ostrzezenie | p95 > 1 s (SC-003) |
| `SfalszowanePotwierdzeniePlatnosci` | `increase(shop_platnosc_webhook_total{wynik="odrzucony_podpis"}[5m]) > 0` | 0m | krytyczny | potwierdzenie z błędnym podpisem |
| `BledyOperatoraPlatnosci` | `sum(rate(shop_stripe_wywolania_seconds_count{wynik!="sukces"}[5m])) / sum(rate(shop_stripe_wywolania_seconds_count[5m])) > 0.2` | 5m | krytyczny | błędy/timeouty > 20% |
| `OpoznionePotwierdzeniaPlatnosci` | `histogram_quantile(0.95, sum by (le) (rate(shop_platnosc_opoznienie_potwierdzenia_seconds_bucket[5m]))) > 30` | 5m | krytyczny | „Opłacone” > 30 s (SC-007, R-29a) |
| `BrakPotwierdzenPlatnosci` | `(sum(increase(shop_zamowienia_utworzone_total[15m])) > 0) and on() (sum(increase(shop_platnosc_webhook_total{wynik=~"przetworzone\|zduplikowane"}[15m])) == 0)` | 5m | krytyczny | jw. — brak webhooków (R-29b) |
| `ZamowienieWymagaWyjasnienia` | `increase(shop_zamowienia_zakonczone_total{status="WYMAGA_WYJASNIENIA"}[10m]) > 0` | 0m | ostrzezenie | pojawienie się „Wymaga wyjaśnienia” |
| `OutboxZalegly` | `shop_outbox_najstarsze_seconds > 300` | 0m | ostrzezenie, dodatkowo `wymaga="realizacja"` | najstarsze zdarzenie > 5 min (R-30: w tej funkcji odpala po każdym opłaconym zamówieniu — brak joba wysyłki) |
| `SklepNiedostepny` | `up{job="shop-backend"} == 0` | 1m | krytyczny | brak odczytu metryk przez 1 min |

(`\|` w tabeli to `|` w PromQL.)

## 5. Dashboardy (`observability/grafana/dashboards/*.json`, FR-033)

Źródło danych: `uid: prometheus`. Zmienna dashboardu `$application` (domyślnie `shop`).
Liczniki zawsze przez `rate`/`increase` (edge case: restart zeruje liczniki).

| Plik | Tytuł | Panele (minimum) |
|---|---|---|
| `http.json` | Sklep — HTTP | żądania/s per `uri`; odsetek 4xx i 5xx; p50/p95/p99 per `uri` (`histogram_quantile`); tabela top 5 najwolniejszych `uri`; stan alertów `WysokiOdsetekBledow5xx`, `WolneOdpowiedziKataloguKoszyka` |
| `sciezka-zakupowa.json` | Sklep — ścieżka zakupowa | lejek: dodania do koszyka → zamówienia utworzone → `OPLACONE` (`increase` w wybranym zakresie); zamówienia wg `status`; płatności wg `wynik` (udana/odrzucona/anulowana osobnymi seriami); wartość opłaconych zamówień w PLN; rozbieżności wg `rodzaj` |
| `platnosci-integracje.json` | Sklep — płatności i integracje | wywołania Stripe wg `operacja`/`wynik`; p95 czasu wywołań; ponowienia; webhooki wg `wynik` (w tym `odrzucony_podpis`); p95 opóźnienia potwierdzenia vs próg 30 s; rozkład czasu do opłacenia; Outbox: oczekujące i najstarsze (panel opisany „oczekuje na funkcję realizacja”) |
| `jvm-baza.json` | Sklep — JVM i baza | heap/non-heap; pauzy GC; wątki; CPU procesu; uptime; HikariCP active/idle/pending i czas pozyskania połączenia; migracje Flyway wg `stan`; `up` |

## 6. Nagłówek korelacji (R-32)

Każda odpowiedź API (`8080`) zawiera `X-Request-Id`. Żądanie może go podać (`^[A-Za-z0-9-]{8,64}$`),
inaczej backend generuje UUID. Wartość jest w logach (`[requestId]`), **nigdy** w metrykach.
Nagłówek nie zmienia ścieżek, metod ani kodów odpowiedzi, więc `openapi.yaml` opisuje go jako
wspólny nagłówek odpowiedzi (`components.headers.X-Request-Id`), bez zmian w operacjach.
