# Feature Specification: Przeglądanie sklepu, koszyk i płatność

**Feature Branch**: `001-shop-browse-cart-checkout`

**Created**: 2026-09-23

**Status**: Draft

**Input**: User description: "przeglądanie sklepu, koszyk, edycja koszyka, płatność Stripe"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Przeglądanie i wyszukiwanie produktów (Priority: P1)

Klient wchodzi do sklepu i przegląda ofertę: widzi listę produktów ze zdjęciem, nazwą i ceną,
może zawęzić ją do kategorii, wyszukać produkt po nazwie, przefiltrować po przedziale cen
i posortować wyniki. Po kliknięciu produktu widzi jego kartę z opisem, ceną i informacją
o dostępności — podobnie jak na Allegro czy eBay.

**Why this priority**: bez możliwości znalezienia produktu nie ma żadnej dalszej ścieżki
zakupowej; to także samodzielnie demonstrowalny fragment sklepu.

**Independent Test**: można w pełni przetestować na przykładowym katalogu — wyszukanie
produktu po nazwie, zawężenie do kategorii i otwarcie karty produktu dostarcza wartość
(klient wie, co i za ile może kupić) bez działającego koszyka.

**Acceptance Scenarios**:

1. **Given** katalog zawiera produkty w kilku kategoriach, **When** klient otwiera stronę główną
   sklepu, **Then** widzi pierwszą stronę listy produktów (zdjęcie, nazwa, cena, dostępność)
   oraz nawigację po kategoriach.
2. **Given** klient jest na liście produktów, **When** wybiera kategorię, **Then** lista pokazuje
   wyłącznie produkty z tej kategorii.
3. **Given** klient wpisuje frazę w wyszukiwarkę, **When** zatwierdza wyszukiwanie, **Then**
   widzi produkty, których nazwa zawiera tę frazę (bez rozróżniania wielkości liter).
4. **Given** klient ogląda wyniki, **When** ustawia przedział cen i sortowanie (cena rosnąco,
   cena malejąco, nazwa), **Then** lista zawiera tylko produkty z przedziału w wybranej kolejności.
5. **Given** wyszukiwanie nie zwraca wyników, **When** lista się wyświetla, **Then** klient widzi
   komunikat „brak wyników" z możliwością wyczyszczenia filtrów.
6. **Given** klient klika produkt na liście, **When** otwiera się karta produktu, **Then** widzi
   nazwę, zdjęcia, opis, cenę i dostępność (dostępny / ostatnie sztuki / niedostępny).
7. **Given** lista ma więcej produktów niż mieści się na stronie, **When** klient przechodzi
   na kolejną stronę, **Then** widzi następne produkty z zachowaniem filtrów i sortowania.

---

### User Story 2 - Dodawanie produktów do koszyka (Priority: P1)

Klient dodaje wybrany produkt do koszyka z karty produktu (z wyborem ilości) lub bezpośrednio
z listy (1 sztuka). Od razu dostaje potwierdzenie, a licznik koszyka w nagłówku się aktualizuje.
Nie musi się logować ani zakładać konta.

**Why this priority**: koszyk jest warunkiem zakupu; razem z US1 tworzy minimalny, sensowny
przyrost (klient może skompletować zamówienie).

**Independent Test**: można przetestować, dodając produkty z listy i z karty, a następnie
sprawdzając licznik i zawartość koszyka — również po odświeżeniu strony.

**Acceptance Scenarios**:

1. **Given** produkt jest dostępny, **When** klient na karcie produktu wybiera ilość 2 i klika
   „Dodaj do koszyka", **Then** koszyk zawiera ten produkt w ilości 2, klient widzi potwierdzenie,
   a licznik w nagłówku rośnie o 2.
2. **Given** produkt jest już w koszyku, **When** klient dodaje go ponownie, **Then** ilość
   w istniejącej pozycji się sumuje (nie powstaje druga pozycja).
3. **Given** produkt ma na stanie 3 sztuki, a klient ma w koszyku 2, **When** próbuje dodać
   kolejne 2, **Then** dodanie jest zablokowane z komunikatem o maksymalnej dostępnej ilości.
4. **Given** produkt jest niedostępny, **When** klient ogląda kartę lub listę, **Then** przycisk
   „Dodaj do koszyka" jest nieaktywny, a produkt oznaczony jako niedostępny.
5. **Given** klient dodał produkty do koszyka, **When** odświeża stronę lub wraca do sklepu
   w tej samej przeglądarce w ciągu 30 dni, **Then** koszyk zachowuje swoją zawartość.

---

### User Story 3 - Przeglądanie i edycja koszyka (Priority: P1)

Klient otwiera koszyk i widzi wszystkie pozycje: zdjęcie, nazwę, cenę jednostkową, ilość
i wartość pozycji oraz sumę całkowitą. Może zmienić ilość, usunąć pozycję lub wyczyścić cały
koszyk. Sumy przeliczają się po każdej zmianie.

**Why this priority**: klient musi móc skorygować zamówienie przed płatnością; bez tego
pomyłka w ilości kończy się porzuceniem zakupu.

**Independent Test**: można przetestować na koszyku z kilkoma pozycjami — zmiana ilości,
usunięcie pozycji i wyczyszczenie koszyka, z weryfikacją sum.

**Acceptance Scenarios**:

1. **Given** koszyk zawiera 2 różne produkty, **When** klient otwiera koszyk, **Then** widzi
   obie pozycje z ceną jednostkową, ilością, wartością pozycji i sumą całkowitą.
2. **Given** pozycja ma ilość 1, **When** klient zwiększa ilość do 3, **Then** wartość pozycji
   i suma całkowita przeliczają się zgodnie z aktualną ceną.
3. **Given** klient próbuje ustawić ilość większą niż dostępny stan, **When** zatwierdza zmianę,
   **Then** ilość zostaje ograniczona do dostępnego stanu z odpowiednim komunikatem.
4. **Given** klient zmniejsza ilość pozycji do 0 lub klika „Usuń", **When** zmiana jest zapisana,
   **Then** pozycja znika z koszyka, a suma się przelicza.
5. **Given** koszyk jest pusty, **When** klient go otwiera, **Then** widzi komunikat „Twój koszyk
   jest pusty" i odnośnik do przeglądania sklepu; przejście do płatności jest niedostępne.
6. **Given** cena produktu w katalogu zmieniła się od momentu dodania do koszyka, **When** klient
   otwiera koszyk, **Then** widzi aktualną cenę oraz wyraźną informację o zmianie ceny.
7. **Given** produkt w koszyku stał się niedostępny, **When** klient otwiera koszyk, **Then**
   pozycja jest oznaczona jako niedostępna i nie da się przejść do płatności, dopóki jej nie usunie.

---

### User Story 4 - Złożenie zamówienia i płatność online (Priority: P1)

Klient z niepustego koszyka przechodzi do zamówienia: podaje adres e-mail i adres dostawy,
widzi podsumowanie (pozycje, suma do zapłaty) i przechodzi do płatności kartą u zewnętrznego
operatora płatności. Po udanej płatności widzi stronę potwierdzenia z numerem zamówienia,
a koszyk zostaje opróżniony. Jeśli płatność się nie uda lub zostanie anulowana, klient wraca
do sklepu z nienaruszonym koszykiem i może spróbować ponownie.

**Why this priority**: płatność domyka ścieżkę zakupową — bez niej PoC nie udowadnia, że sklep
może sprzedawać.

**Independent Test**: można przetestować na koszyku z przygotowanymi produktami, wykonując
płatność testową kartą akceptowaną i kartą odrzucaną, oraz sprawdzając status zamówienia
i stan koszyka w obu przypadkach.

**Acceptance Scenarios**:

1. **Given** koszyk zawiera dostępne produkty, **When** klient klika „Przejdź do zamówienia",
   **Then** widzi formularz danych (e-mail, imię i nazwisko, adres dostawy) i podsumowanie
   zamówienia z sumą do zapłaty.
2. **Given** klient podał niepoprawny e-mail lub pominął wymagane pole adresu, **When** próbuje
   przejść dalej, **Then** widzi komunikat przy konkretnym polu i nie przechodzi do płatności.
3. **Given** formularz jest poprawny, **When** klient przechodzi do płatności, **Then** zostaje
   przekierowany do strony płatności operatora z kwotą równą sumie zamówienia wyliczonej przez sklep.
4. **Given** klient zapłacił poprawnie, **When** operator potwierdzi płatność, **Then** zamówienie
   ma status „Opłacone", stan magazynowy produktów maleje o zakupione ilości, klient widzi stronę
   potwierdzenia z numerem zamówienia, a koszyk jest pusty.
5. **Given** płatność została odrzucona lub klient ją anulował, **When** wraca do sklepu, **Then**
   widzi informację o nieudanej płatności, koszyk ma dotychczasową zawartość, a zamówienie nie
   jest oznaczone jako opłacone.
6. **Given** klient wrócił na stronę potwierdzenia, zanim sklep otrzymał potwierdzenie od
   operatora, **When** strona się ładuje, **Then** klient widzi status „Płatność w trakcie
   weryfikacji", który zmienia się na „Opłacone" po otrzymaniu potwierdzenia.
7. **Given** od rozpoczęcia zamówienia zmieniła się cena lub dostępność produktu, **When** klient
   przechodzi do płatności, **Then** sklep zatrzymuje proces i pokazuje zaktualizowane
   podsumowanie do ponownego zatwierdzenia.

---

### Edge Cases

- Dwóch klientów jednocześnie kupuje ostatnią sztukę produktu: dostępność jest sprawdzana przy
  przejściu do płatności; jeśli po potwierdzeniu płatności stan okaże się niewystarczający,
  zamówienie otrzymuje status „Wymaga wyjaśnienia" (obsługa ręczna, poza zakresem PoC).
- Klient zamyka kartę przeglądarki w trakcie płatności: zamówienie pozostaje „Oczekuje na
  płatność"; potwierdzenie od operatora i tak zmieni status na „Opłacone", a koszyk zostanie
  opróżniony przy następnej wizycie.
- Operator płatności wysyła to samo potwierdzenie kilka razy: zamówienie jest oznaczane jako
  opłacone jednokrotnie, a stan magazynowy zmniejszany tylko raz.
- Potwierdzenie płatności jest sfałszowane (nie pochodzi od operatora): sklep je odrzuca
  i nie zmienia statusu zamówienia.
- Klient ręcznie podaje w adresie strony identyfikator cudzego zamówienia: strona potwierdzenia
  nie ujawnia danych zamówienia innego klienta.
- Klient wpisuje ilość niebędącą dodatnią liczbą całkowitą (np. -1, 1.5, „abc"): wartość jest
  odrzucana z komunikatem.
- Operator płatności jest chwilowo niedostępny: klient widzi komunikat „Płatność chwilowo
  niedostępna, spróbuj za chwilę", koszyk pozostaje nienaruszony.
- Produkt usunięty z katalogu, a nadal w koszyku: pozycja jest pokazana jako niedostępna
  (jak w US3, scenariusz 7).
- Bardzo długa fraza wyszukiwania lub znaki specjalne: wyszukiwanie działa bez błędu
  (fraza przycinana do 100 znaków).

## Requirements *(mandatory)*

### Functional Requirements

**Katalog i przeglądanie**

- **FR-001**: System MUST prezentować listę produktów z paginacją (domyślnie 24 produkty na stronę)
  zawierającą dla każdego produktu: zdjęcie główne, nazwę, cenę i status dostępności.
- **FR-002**: System MUST umożliwiać zawężenie listy do kategorii.
- **FR-003**: System MUST umożliwiać wyszukiwanie produktów po fragmencie nazwy, bez rozróżniania
  wielkości liter i polskich znaków diakrytycznych w zapytaniu i nazwie.
- **FR-004**: System MUST umożliwiać filtrowanie po przedziale cen oraz sortowanie wg ceny
  (rosnąco/malejąco) i nazwy; filtry, sortowanie i strona MUSZĄ być odzwierciedlone w adresie
  strony, tak aby link do wyników dało się udostępnić.
- **FR-005**: System MUST wyświetlać kartę produktu z nazwą, zdjęciami, opisem, ceną i statusem
  dostępności („dostępny", „ostatnie sztuki" przy stanie ≤ 3, „niedostępny" przy stanie 0).

**Koszyk**

- **FR-006**: Klienci MUST móc dodać produkt do koszyka bez logowania — z karty produktu
  z wybraną ilością lub z listy w ilości 1.
- **FR-007**: System MUST scalać ponowne dodanie tego samego produktu w jedną pozycję
  z zsumowaną ilością.
- **FR-008**: System MUST blokować dodanie lub ustawienie ilości przekraczającej dostępny stan
  lub limit 99 sztuk na pozycję, informując o maksymalnej możliwej ilości.
- **FR-009**: Klienci MUST móc zmienić ilość pozycji, usunąć pozycję oraz wyczyścić cały koszyk.
- **FR-010**: System MUST wyliczać wartość każdej pozycji i sumę koszyka po stronie sklepu
  na podstawie aktualnych cen z katalogu; ceny przesłane przez przeglądarkę nie mają znaczenia.
- **FR-011**: System MUST informować klienta o zmianie ceny lub utracie dostępności produktu
  od momentu dodania go do koszyka.
- **FR-012**: System MUST zachowywać koszyk klienta przez co najmniej 30 dni od ostatniej
  zmiany w tej samej przeglądarce.
- **FR-013**: System MUST wyświetlać w nagłówku każdej strony liczbę sztuk w koszyku.

**Zamówienie i płatność**

- **FR-014**: System MUST umożliwić przejście do zamówienia tylko z niepustego koszyka,
  w którym wszystkie pozycje są dostępne w wymaganej ilości.
- **FR-015**: System MUST zbierać od klienta: adres e-mail, imię i nazwisko, adres dostawy
  (ulica z numerem, kod pocztowy w formacie NN-NNN, miejscowość; kraj: Polska) i walidować
  je przed przejściem do płatności.
- **FR-016**: System MUST przed przejściem do płatności ponownie zweryfikować ceny i dostępność;
  przy rozbieżności MUST pokazać zaktualizowane podsumowanie do ponownego zatwierdzenia.
- **FR-017**: System MUST utworzyć zamówienie z unikalnym numerem, niezmienną kopią pozycji
  (nazwa, cena jednostkowa, ilość), danymi klienta i sumą, ze statusem „Oczekuje na płatność".
- **FR-018**: System MUST realizować płatność kartą na stronie zewnętrznego operatora płatności;
  dane karty nigdy nie są wprowadzane ani przechowywane w sklepie.
- **FR-019**: System MUST oznaczać zamówienie jako „Opłacone" wyłącznie na podstawie
  zweryfikowanego potwierdzenia od operatora płatności, a nie na podstawie powrotu klienta
  na stronę sklepu.
- **FR-020**: System MUST przetwarzać powtórzone potwierdzenia tej samej płatności tylko raz.
- **FR-021**: Po opłaceniu zamówienia system MUST zmniejszyć stan magazynowy o zakupione ilości
  i opróżnić koszyk klienta; przy niewystarczającym stanie MUST nadać status „Wymaga wyjaśnienia".
- **FR-022**: Przy płatności odrzuconej lub anulowanej system MUST zachować zawartość koszyka,
  pokazać klientowi komunikat i umożliwić ponowną próbę.
- **FR-023**: System MUST wyświetlić stronę potwierdzenia z numerem zamówienia, listą pozycji,
  sumą i aktualnym statusem płatności, dostępną wyłącznie dla klienta, który złożył zamówienie.
- **FR-024**: Wszystkie kwoty MUSZĄ być prezentowane w PLN z dokładnością do 1 grosza, a suma
  zamówienia MUSI być równa sumie wartości pozycji (koszt dostawy w PoC wynosi 0 zł).

### Key Entities *(include if feature involves data)*

- **Produkt**: oferowany towar — nazwa, opis, cena (PLN), zdjęcia, kategoria, stan magazynowy,
  flaga aktywności w katalogu.
- **Kategoria**: grupa produktów do nawigacji — nazwa; w PoC płaska lista (bez podkategorii).
- **Koszyk**: tymczasowy zbiór pozycji powiązany z przeglądarką klienta — pozycje, data ostatniej
  zmiany; nie wymaga konta.
- **Pozycja koszyka**: produkt i ilość; cena jest zawsze odczytywana z aktualnego katalogu.
- **Zamówienie**: utrwalony zakup — numer, dane klienta (e-mail, imię i nazwisko, adres dostawy),
  niezmienne pozycje zamówienia, suma, status („Oczekuje na płatność", „Opłacone", „Płatność
  nieudana", „Wymaga wyjaśnienia"), daty utworzenia i opłacenia.
- **Pozycja zamówienia**: kopia nazwy produktu, ceny jednostkowej i ilości z chwili zamówienia.
- **Płatność**: próba zapłaty za zamówienie u operatora — identyfikator u operatora, kwota,
  status, data potwierdzenia.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Nowy klient przechodzi od wejścia do sklepu do opłaconego zamówienia jednego
  znalezionego produktu w mniej niż 3 minuty.
- **SC-002**: 90% uczestników testu użyteczności (min. 5 osób) samodzielnie znajduje wskazany
  produkt, dodaje go do koszyka, zmienia ilość i opłaca zamówienie przy pierwszej próbie.
- **SC-003**: Lista produktów, wyniki wyszukiwania i koszyk pokazują się klientowi w czasie
  poniżej 1 sekundy dla katalogu co najmniej 500 produktów.
- **SC-004**: W 100% przypadków testowych kwota pobrana przez operatora płatności jest równa
  sumie zamówienia wyliczonej przez sklep.
- **SC-005**: 0 zamówień oznaczonych jako „Opłacone" bez potwierdzenia od operatora płatności
  (w tym przy powtórzonych i sfałszowanych potwierdzeniach).
- **SC-006**: Po nieudanej lub anulowanej płatności 100% koszyków zachowuje pełną zawartość.
- **SC-007**: Status zamówienia zmienia się na „Opłacone" w ciągu 30 sekund od potwierdzenia
  płatności przez operatora.
- **SC-008**: Wszystkie cztery ścieżki (przeglądanie, dodanie do koszyka, edycja koszyka,
  płatność) można zademonstrować end-to-end na środowisku lokalnym.

## Assumptions

- Zakupy odbywają się jako gość — konta klientów, logowanie i historia zamówień są poza zakresem.
- Katalog (produkty, kategorie, zdjęcia, stany) jest wypełniony przykładowymi danymi;
  panel administracyjny do zarządzania katalogiem jest poza zakresem.
- Operatorem płatności jest Stripe działający wyłącznie w trybie testowym; jedyną metodą
  płatności w PoC jest karta (inne metody, np. BLIK, jako możliwe rozszerzenie).
- Jedna waluta (PLN), wysyłka tylko na terenie Polski, koszt dostawy 0 zł, bez kodów rabatowych.
- Stan magazynowy nie jest rezerwowany na czas płatności; ryzyko sprzedaży ostatniej sztuki
  dwóm klientom jest obsługiwane statusem „Wymaga wyjaśnienia" (zwroty i refundacje poza zakresem).
- Ceny są cenami brutto; faktury, paragony i obsługa podatków są poza zakresem.
- Potwierdzenie zamówienia e-mailem jest poza zakresem — klient widzi potwierdzenie na stronie.
- Przekazywanie opłaconych zamówień na tablicę kanban (Trello) jest osobną funkcją; ta
  specyfikacja zapewnia jedynie zdarzenie „zamówienie opłacone", z którego ta funkcja skorzysta.
- Interfejs jest w języku polskim i działa na przeglądarkach desktopowych i mobilnych.
- Wzorcem zachowań (koszyk, licznik, komunikaty o dostępności) są popularne marketplace'y
  (Allegro, eBay) w zakresie opisanym powyżej.
