# Identifier glossary (PL → EN)

Canonical English names for every identifier that appeared in the original Polish specs of
`001-shop-browse-cart-checkout`. Use exactly these names in code, API, database, metrics and docs.
Add new terms here before using them.

## Bounded contexts / packages

| PL | EN |
|---|---|
| `katalog` | `catalog` |
| `koszyk` | `cart` |
| `zamowienie` | `order` (package); SQL table `orders` (`ORDER` is reserved) |
| `platnosc` | `payment` |
| `realizacja` | `fulfillment` |
| `shared` | `shared` |

## Shared kernel

| PL | EN |
|---|---|
| `Pieniadze(long grosze, waluta)`, `plus`, `razy(int)` | `Money(long minor, currency)`, `plus`, `times(int)` — `minor` = grosze |
| `GoscId`, cookie `shop_guest` | `GuestId`, cookie `shop_guest` |
| `outbox_event`: `typ`, `agregat_id`, `ladunek`, `utworzono`, `wyslano`, `proby` | `type`, `aggregate_id`, `payload`, `created_at`, `sent_at`, `attempts` |
| `KorelacjaFilter`, `GoscIdFilter` | `CorrelationFilter`, `GuestIdFilter` |
| `PoCommicie`, `OutboxMetryki`, `FlywayMetryki`, `MetrykiConfig` | `AfterCommit`, `OutboxMetrics`, `FlywayMetrics`, `MetricsConfig` |
| `AppProperties` | `AppProperties` |
| maskowanie danych osobowych | `PiiMasking` (in `shared.domain`) |

## Catalog

| PL | EN |
|---|---|
| `Kategoria`, `KategoriaId`; `nazwa`, `slug`, `kolejnosc` | `Category`, `CategoryId`; `name`, `slug`, `display_order` |
| `Produkt`, `ProduktId`; `nazwa`, `opis`, `cena` (`cena_grosze`), `kategoriaId`, `stan`, `aktywny`, `zdjecia`, `wersja` | `Product`, `ProductId`; `name`, `description`, `price` (`price_minor`), `categoryId`, `stock`, `active`, `images`, `version` |
| `Zdjecie`, table `produkt_zdjecie` | `ProductImage`, table `product_image` |
| `StatusDostepnosci`: `DOSTEPNY`, `OSTATNIE_SZTUKI`, `NIEDOSTEPNY` | `AvailabilityStatus`: `AVAILABLE`, `LOW_STOCK`, `UNAVAILABLE` |
| `statusDostepnosci()`, `maksDoKupienia()`, `czyMoznaZmniejszyc(n)`, `zmniejszStan(n)` | `availabilityStatus()`, `maxPurchasable()`, `canDecreaseStock(n)`, `decreaseStock(n)` |
| `KryteriaWyszukiwania`: `kategoriaSlug`, `fraza`, `cenaOd`, `cenaDo`, `sortowanie`, `strona`, `rozmiar` | `SearchCriteria`: `categorySlug`, `query`, `minPrice`, `maxPrice`, `sort`, `page`, `size` |
| sort `CENA_ROSNACO`, `CENA_MALEJACO`, `NAZWA` | `PRICE_ASC`, `PRICE_DESC`, `NAME` |
| `KatalogQueryFacade.pobierzDoWyceny` → `ProduktDoWycenyDto` | `CatalogQueryFacade.getForPricing` → `PricingProductDto` |
| `KatalogCommandFacade.zmniejszStan(List<PozycjaStanuDto>)` → `WynikZmniejszeniaStanu` (`ZMNIEJSZONO`, `NIEWYSTARCZAJACY_STAN`) | `CatalogCommandFacade.decreaseStock(List<StockLineDto>)` → `StockDecreaseResult` (`DECREASED`, `INSUFFICIENT_STOCK`) |
| `KatalogService`, `KatalogController`, `ProduktRepository`, `ProduktJpaEntity`, `KategoriaJpaEntity`, `ProduktJpaRepository` | `CatalogService`, `CatalogController`, `ProductRepository`, `ProductJpaEntity`, `CategoryJpaEntity`, `ProductJpaRepository` |

## Cart

| PL | EN |
|---|---|
| `Koszyk`, `KoszykId`; `goscId`, `pozycje`, `zmieniono`, `wersja` | `Cart`, `CartId`; `guestId`, `lines`, `updated_at`, `version` |
| `PozycjaKoszyka`, table `pozycja_koszyka`; `produktId`, `ilosc`, `cenaPrzyDodaniu`, `dodano` | `CartLine`, table `cart_line`; `productId`, `quantity`, `priceWhenAdded` (`price_when_added_minor`), `added_at` |
| `dodaj`, `zmienIlosc`, `usun`, `wyczysc`, `akceptujCeny` | `add`, `changeQuantity`, `remove`, `clear`, `acceptPrices` |
| `IloscPrzekraczaLimit`, `ProduktNiedostepny`, `IloscOgraniczona` | `QuantityExceedsLimit`, `ProductUnavailable`, `QuantityCapped` |
| `WycenionyKoszyk`, `KoszykService.wycen` | `PricedCart`, `CartService.price` |
| fields `pozycje[]`: `nazwa`, `zdjecieUrl`, `cenaJednostkowa`, `ilosc`, `wartosc`, `status`, `maksIlosc`, `cenaZmieniona`, `poprzedniaCena`, `iloscPrzekraczaStan` | `lines[]`: `name`, `imageUrl`, `unitPrice`, `quantity`, `lineTotal`, `status`, `maxQuantity`, `priceChanged`, `previousPrice`, `quantityExceedsStock` |
| `liczbaSztuk`, `suma`, `moznaZamowic`, `problemy[]` | `itemCount`, `total`, `canPlaceOrder`, `problems[]` |
| `CENA_ZMIENIONA`, `PRODUKT_NIEDOSTEPNY`, `ILOSC_PRZEKRACZA_STAN` | `PRICE_CHANGED`, `PRODUCT_UNAVAILABLE`, `QUANTITY_EXCEEDS_STOCK` |
| `KoszykQueryFacade.wycen` → `WycenionyKoszykDto`, `KoszykCommandFacade.wyczysc` | `CartQueryFacade.price` → `PricedCartDto`, `CartCommandFacade.clear` |
| `KoszykService`, `KoszykController`, `KoszykRepository` | `CartService`, `CartController`, `CartRepository` |

## Order

| PL | EN |
|---|---|
| `Zamowienie`, `ZamowienieId`, table `zamowienie` | `Order`, `OrderId`, table `orders` |
| `numer`, `NumerZamowienia`, prefix `ZAM-` | `number`, `OrderNumber`, prefix `ORD-` |
| `klient`/`DaneKlienta`: `email`, `imieNazwisko` (`imie_nazwisko`) | `customer`/`CustomerDetails`: `email`, `fullName` (`full_name`) |
| `adres`/`AdresDostawy`: `ulicaINumer`, `kodPocztowy`, `miejscowosc`, `kraj` | `shippingAddress`/`ShippingAddress`: `streetAndNumber`, `postalCode`, `city`, `country` |
| `PozycjaZamowienia`, table `pozycja_zamowienia`; `lp`, `cenaJednostkowa`, `wartosc` | `OrderLine`, table `order_line`; `lineNo`, `unitPrice` (`unit_price_minor`), `lineTotal` |
| `suma`, `utworzono`, `oplacono`, `powodWyjasnienia` | `total` (`total_minor`), `createdAt`, `paidAt`, `reviewReason` |
| `StatusZamowienia`: `OCZEKUJE_NA_PLATNOSC`, `OPLACONE`, `PLATNOSC_NIEUDANA`, `WYMAGA_WYJASNIENIA` | `OrderStatus`: `AWAITING_PAYMENT`, `PAID`, `PAYMENT_FAILED`, `NEEDS_REVIEW` |
| `NIEWYSTARCZAJACY_STAN`, `NIEZGODNA_KWOTA`, `POTWIERDZENIE_PO_NIEPOWODZENIU` | `INSUFFICIENT_STOCK`, `AMOUNT_MISMATCH`, `CONFIRMED_AFTER_FAILURE` |
| `NiedozwolonePrzejscieStatusu` | `IllegalStatusTransition` |
| `ZamowienieOplaconeEvent` (outbox type `ZamowienieOplacone`) | `OrderPaidEvent` (outbox type `OrderPaid`) |
| `ZlozZamowienieService`, `ObslugaPlatnosciListener`, `ZamowienieController`, `ZamowienieRepository` | `PlaceOrderService`, `PaymentEventsListener`, `OrderController`, `OrderRepository` |
| `ZamowienieQueryFacade.pobierz` → `ZamowienieDto` | `OrderQueryFacade.get` → `OrderDto` |

## Payment

| PL | EN |
|---|---|
| `Platnosc`, `PlatnoscId`; `zamowienieId`, `kwota`, `operatorSesjaId`, `operatorPlatnoscId`, `urlPlatnosci`, `potwierdzono` | `Payment`, `PaymentId`; `orderId`, `amount` (`amount_minor`), `providerSessionId`, `providerPaymentId`, `paymentUrl`, `confirmedAt` |
| `StatusPlatnosci`: `UTWORZONA`, `OTWARTA`, `POTWIERDZONA`, `NIEUDANA`, `WYGASZONA` | `PaymentStatus`: `CREATED`, `OPEN`, `CONFIRMED`, `FAILED`, `EXPIRED` |
| table `przetworzone_zdarzenie_stripe` (`typ`, `przetworzono`) | table `processed_stripe_event` (`type`, `processed_at`) |
| `BramkaPlatnosci`: `utworzSesje`, `wygas` → `SesjaPlatnosci`, `BramkaNiedostepna`, `JUZ_OPLACONA` | `PaymentGateway`: `createSession`, `expire` → `PaymentSession`, `GatewayUnavailable`, `ALREADY_PAID` |
| `PotwierdzenieOperatora{eventId, typ, sesjaId, paymentIntentId, kwota, waluta, oplacona}` | `ProviderConfirmation{eventId, type, sessionId, paymentIntentId, amount, currency, paid}` |
| `PlatnoscPotwierdzonaEvent{zamowienieId, kwotaGrosze, waluta, potwierdzono}`, `PlatnoscNieudanaEvent{zamowienieId, powod}` | `PaymentConfirmedEvent{orderId, amountMinor, currency, confirmedAt}`, `PaymentFailedEvent{orderId, reason}` |
| `PlatnoscFacade.rozpocznij(RozpocznijPlatnoscDto)` → `RozpoczetaPlatnoscDto`, `wygasOtwarte` → `WynikWygaszenia` | `PaymentFacade.start(StartPaymentDto)` → `StartedPaymentDto`, `expireOpen` → `ExpiryResult` |
| `PlatnoscService`, `ObslugaWebhookaService`, `PlatnoscRepository` | `PaymentService`, `WebhookHandlingService`, `PaymentRepository` |
| `PlatnoscJpaEntity`, `PrzetworzoneZdarzenieJpaEntity` | `PaymentJpaEntity`, `ProcessedEventJpaEntity` |
| `StripeBramkaPlatnosci`, `PolitykaPonowienStripe` | `StripePaymentGateway`, `StripeRetryPolicy` |
| — (webhook verification port, added after translation) | `ProviderEventVerifier.verify(payload, signatureHeader)` → `ProviderConfirmation`; exception `InvalidEventSignature`; Stripe adapter `StripeWebhookVerifier` |
| Stripe metadata `zamowienieId`, `platnoscId`, `numer`; `Idempotency-Key: checkout-{platnoscId}` | `orderId`, `paymentId`, `number`; `checkout-{paymentId}` |

## Metrics ports and label enums

| PL | EN |
|---|---|
| `MetrykiKoszyka.dodanoDoKoszyka()` | `CartMetrics.addedToCart()` |
| `MetrykiZamowien`: `zamowienieUtworzone()`, `rozbieznoscPodsumowania(Set<RodzajRozbieznosci>)`, `zamowienieZakonczone(status, suma, czasDoOplacenia)` | `OrderMetrics`: `orderCreated()`, `summaryMismatch(Set<MismatchKind>)`, `orderCompleted(status, total, timeToPayment)` |
| `MetrykiPlatnosci`: `webhook(WynikWebhooka)`, `platnosc(WynikPlatnosci)`, `opoznieniePotwierdzenia(Duration)` | `PaymentMetrics`: `webhook(WebhookOutcome)`, `payment(PaymentOutcome)`, `confirmationDelay(Duration)` |
| `MicrometerMetryki{Koszyka,Zamowien,Platnosci}` | `MicrometerCartMetrics`, `MicrometerOrderMetrics`, `MicrometerPaymentMetrics` |
| `RodzajRozbieznosci`: `CENA`, `DOSTEPNOSC`, `SKLAD` (label `rodzaj`) | `MismatchKind`: `PRICE`, `AVAILABILITY`, `CONTENTS` (label `kind`) |
| `WynikWebhooka`: `PRZETWORZONE`, `ZDUPLIKOWANE`, `ODRZUCONY_PODPIS`, `ODRZUCONY_LIVEMODE`, `ZIGNOROWANE` | `WebhookOutcome`: `PROCESSED`, `DUPLICATE`, `REJECTED_SIGNATURE`, `REJECTED_LIVEMODE`, `IGNORED` (label `outcome`, lower case) |
| `WynikPlatnosci`: `UDANA`, `ODRZUCONA`, `ANULOWANA` | `PaymentOutcome`: `SUCCEEDED`, `DECLINED`, `CANCELED` (label `outcome`, lower case) |
| `OperacjaStripe`: `UTWORZ_SESJE`, `WYGAS_SESJE`; `WynikWywolania`: `SUKCES`, `BLAD`, `TIMEOUT` | `StripeOperation`: `CREATE_SESSION`, `EXPIRE_SESSION`; `CallOutcome`: `SUCCESS`, `ERROR`, `TIMEOUT` (labels `operation`, `outcome`) |
| label `stan` (Flyway) | `state` |

## Metric names

| PL (Micrometer) | EN (Micrometer → Prometheus) |
|---|---|
| `shop.koszyk.dodania` | `shop.cart.additions` → `shop_cart_additions_total` |
| `shop.zamowienia.utworzone` | `shop.orders.created` → `shop_orders_created_total` |
| `shop.zamowienia.rozbieznosci` | `shop.orders.mismatches` → `shop_orders_mismatches_total` |
| `shop.zamowienia.zakonczone` | `shop.orders.completed` → `shop_orders_completed_total` |
| `shop.zamowienia.oplacone.wartosc` | `shop.orders.paid.value` → `shop_orders_paid_value_pln_total` |
| `shop.zamowienie.czas.do.oplacenia` | `shop.order.time.to.payment` → `shop_order_time_to_payment_seconds_*` |
| `shop.platnosci` | `shop.payments` → `shop_payments_total` |
| `shop.platnosc.webhook` | `shop.payment.webhook` → `shop_payment_webhook_total` |
| `shop.platnosc.opoznienie.potwierdzenia` | `shop.payment.confirmation.delay` → `shop_payment_confirmation_delay_seconds_*` |
| `shop.stripe.wywolania` | `shop.stripe.calls` → `shop_stripe_calls_seconds_*` |
| `shop.stripe.ponowienia` | `shop.stripe.retries` → `shop_stripe_retries_total` |
| `shop.outbox.oczekujace` | `shop.outbox.pending` → `shop_outbox_pending` |
| `shop.outbox.najstarsze` | `shop.outbox.oldest` → `shop_outbox_oldest_seconds` |
| `shop.flyway.migracje` | `shop.flyway.migrations` → `shop_flyway_migrations` |

## Alerts and dashboards

| PL | EN |
|---|---|
| labels `waga` (`krytyczny`/`ostrzezenie`), `wymaga="realizacja"` | `severity` (`critical`/`warning`), `requires="fulfillment"` |
| `WysokiOdsetekBledow5xx` | `HighServerErrorRate` |
| `WolneOdpowiedziKataloguKoszyka` | `SlowCatalogOrCartResponses` |
| `SfalszowanePotwierdzeniePlatnosci` | `ForgedPaymentConfirmation` |
| `BledyOperatoraPlatnosci` | `PaymentProviderErrors` |
| `OpoznionePotwierdzeniaPlatnosci` | `DelayedPaymentConfirmations` |
| `BrakPotwierdzenPlatnosci` | `MissingPaymentConfirmations` |
| `ZamowienieWymagaWyjasnienia` | `OrderNeedsReview` |
| `OutboxZalegly` | `OutboxBacklog` |
| `SklepNiedostepny` | `ShopDown` |
| Grafana folder „Sklep” | `Shop` |
| `sciezka-zakupowa.json`, `platnosci-integracje.json`, `jvm-baza.json` | `purchase-funnel.json`, `payments-integrations.json`, `jvm-database.json` |

## REST API and error codes

| PL | EN |
|---|---|
| `/api/kategorie`, `/api/produkty`, `/api/produkty/{id}` | `/api/categories`, `/api/products`, `/api/products/{id}` |
| `/api/koszyk`, `/api/koszyk/pozycje[/{produktId}]`, `/api/koszyk/akceptuj-ceny` | `/api/cart`, `/api/cart/lines[/{productId}]`, `/api/cart/accept-prices` |
| `/api/zamowienia`, `/api/zamowienia/{numer}` | `/api/orders`, `/api/orders/{number}` |
| `/api/platnosci/stripe/webhook` | `/api/payments/stripe/webhook` |
| query `kategoria`, `q`, `cenaOd`, `cenaDo`, `sort` (`nazwa_asc`, `cena_asc`, `cena_desc`), `strona`, `rozmiar` | `category`, `q`, `minPrice`, `maxPrice`, `sort` (`name_asc`, `price_asc`, `price_desc`), `page`, `size` |
| `PODSUMOWANIE_NIEAKTUALNE` | `SUMMARY_OUTDATED` |
| `PLATNOSC_NIEDOSTEPNA` | `PAYMENT_UNAVAILABLE` |
| `ZAMOWIENIE_JUZ_OPLACONE` | `ORDER_ALREADY_PAID` |
| `ILOSC_PRZEKRACZA_LIMIT`, `ILOSC_OGRANICZONA` | `QUANTITY_EXCEEDS_LIMIT`, `QUANTITY_CAPPED` |
| `BLAD_WALIDACJI`, `bledy[].pole`, `komunikaty[].kod` | `VALIDATION_ERROR`, `errors[].field`, `messages[].code` |
| `NIE_ZNALEZIONO`, `KOSZYK_NIE_DO_ZAMOWIENIA`, `KONFLIKT_WSPOLBIEZNOSCI`, `BLAD_WEWNETRZNY` | `NOT_FOUND`, `CART_NOT_ORDERABLE`, `CONCURRENCY_CONFLICT`, `INTERNAL_ERROR` |
| Problem fields `kod`, `maksIlosc`, `bledy` | `code`, `maxQuantity`, `errors` |
| JSON amount suffix `*Grosze` (`cenaGrosze`, `sumaGrosze`, …) | `*Minor` (`priceMinor`, `totalMinor`, …) |

## OpenAPI schemas and operations

| PL | EN |
|---|---|
| tags `katalog`, `koszyk`, `zamowienie`, `platnosc` | `catalog`, `cart`, `order`, `payment` |
| `listaKategorii`, `szukajProduktow`, `kartaProduktu` | `listCategories`, `searchProducts`, `getProduct` |
| `pobierzKoszyk`, `wyczyscKoszyk`, `dodajDoKoszyka`, `zmienIlosc`, `usunPozycje`, `akceptujCeny` | `getCart`, `clearCart`, `addToCart`, `changeQuantity`, `removeLine`, `acceptPrices` |
| `zlozZamowienie`, `pobierzZamowienie`, `webhookStripe` | `placeOrder`, `getOrder`, `stripeWebhook` |
| `Kategoria`, `Zdjecie`, `ProduktNaLiscie`, `StronaProduktow`, `ProduktSzczegoly` | `Category`, `Image`, `ProductSummary`, `ProductSearchResult`, `ProductDetails` |
| `StronaProduktow` fields `produkty`, `strona`, `rozmiar`, `lacznie`, `liczbaStron` | `products`, `page`, `size`, `totalElements`, `totalPages` |
| `maksDoKoszyka`, `zdjecie`, `zdjecia`, `opis`, `kategoria` | `maxAddable`, `image`, `images`, `description`, `category` |
| `DodajPozycjeRequest`, `ZmienIloscRequest`, `PozycjaKoszyka`, `Komunikat{kod, tresc}`, `Koszyk` | `AddLineRequest`, `ChangeQuantityRequest`, `CartLine`, `Message{code, text}`, `Cart` |
| `PotwierdzonaPozycja`, `ZlozZamowienieRequest`, `potwierdzonePodsumowanie` | `ConfirmedLine`, `PlaceOrderRequest`, `confirmedSummary` |
| `RozpoczetaPlatnosc{numer, urlPlatnosci}` | `StartedPayment{number, paymentUrl}` |
| `PozycjaZamowienia`, `Zamowienie`, `BladPola{pole, komunikat}`, `ProblemZKoszykiem` | `OrderLine`, `Order`, `FieldError{field, message}`, `ProblemWithCart` |
| responses `BladWalidacji`, `NieZnaleziono` | `ValidationError`, `NotFound` |

## Frontend

| PL | EN |
|---|---|
| routes `/produkt/:id`, `/koszyk`, `/zamowienie`, `/zamowienie/:numer` | `/product/:id`, `/cart`, `/checkout`, `/orders/:number` |
| `?platnosc=anulowana` | `?payment=canceled` |
| `KatalogPage`, `ProduktPage`, `Filtry`, `useFiltryZUrl` | `CatalogPage`, `ProductPage`, `Filters`, `useUrlFilters` |
| `KoszykPage`, `PozycjaKoszyka`, `useKoszyk`, query key `['koszyk']` | `CartPage`, `CartLineItem`, `useCart`, `['cart']` |
| `ZamowieniePage`, `PotwierdzeniePage` | `CheckoutPage`, `OrderConfirmationPage` |
| `features/katalog|koszyk|zamowienie` | `features/catalog|cart|checkout` |
| E2E `przegladanie`, `koszyk-dodawanie`, `koszyk-edycja`, `platnosc` | `browsing`, `cart-add`, `cart-edit`, `payment` |

## Files, migrations, tests

| PL | EN |
|---|---|
| `V1__katalog.sql` … `V4__platnosc.sql`, `R__seed_katalog.sql` | `V1__catalog.sql`, `V2__cart.sql`, `V3__order.sql`, `V4__payment.sql`, `R__seed_catalog.sql` |
| `MetrykiEndpointIT`, `MetrykiAssert`, `WydajnoscKatalogIT` | `MetricsEndpointIT`, `MetricsAssert`, `CatalogPerformanceIT` |
