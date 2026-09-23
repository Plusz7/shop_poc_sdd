---
name: commit
description: Tworzy commity w shop_poc_sdd zgodnie z Conventional Commits 1.0.0 i zasadami częstotliwości (jeden atomowy commit na zadanie T### / jeden bounded context). Użyj, gdy trzeba zrobić commit, wymyślić nazwę lub wiadomość commita, zdecydować, czy już pora commitować, albo podzielić duże zmiany na kilka commitów. Triggers: "commit", "zacommituj", "nazwa commita", "wiadomość commita", "podziel commit", "conventional commits".
---

# Commity w shop_poc_sdd

Commituj tylko wtedy, gdy użytkownik o to poprosił albo wprost upoważnił cię do tego
(np. „commituj po każdym zadaniu”). Ten skill mówi, **jak** i **kiedy** — nie daje zgody.

## Przebieg

1. **Przejrzyj zmiany:** `git status --short` i `git diff --stat` (oraz `git diff --cached --stat`).
   Ustal, ile jest niezależnych zmian — każda to osobny commit.
2. **Sprawdź gałąź:** na `main` nie commitujemy — najpierw gałąź feature.
3. **Stage'uj jedną logiczną zmianę:** `git add <konkretne ścieżki>`. `git add -A` tylko wtedy,
   gdy wszystkie zmiany tworzą jedną całość.
4. **Upewnij się, że build jest zielony** dla dotkniętej części
   (`./backend/mvnw -f backend/pom.xml verify`, `npm --prefix frontend test`), jeśli ten kod już istnieje.
5. **Napisz wiadomość** według formatu poniżej i sprawdź ją z checklistą.
6. **Commit** — każde `-m` to osobny akapit:
   ```bash
   git commit -m "feat(cart): add cart aggregate with line limits" -m "The 99-item line limit comes from FR-008." -m "Refs: T037"
   ```
   Commit tworzony przez agenta kończy się stopką `Co-Authored-By` z bieżącego przypomnienia systemowego.
7. Jeśli zostały inne zmiany — wróć do kroku 3.

## Format wiadomości (Conventional Commits 1.0.0)

```
<typ>(<scope>)[!]: <opis>

<treść: DLACZEGO — co, widać w diffie>

Refs: T037, T031
BREAKING CHANGE: <co przestaje działać i jak migrować>
```

| typ | kiedy |
|---|---|
| `feat` | nowe zachowanie widoczne dla użytkownika / API |
| `fix` | naprawa błędu |
| `refactor` | zmiana struktury bez zmiany zachowania |
| `perf` | wydajność |
| `test` | tylko testy, bez kodu produkcyjnego |
| `docs` | dokumentacja, `specs/`, konstytucja |
| `build` | Maven/npm, zależności, compose, Dockerfile |
| `ci` | `.github/workflows` |
| `chore` | konfiguracja repo, MCP, skille, `.gitignore` |
| `style` | formatowanie bez zmian logiki |
| `revert` | cofnięcie commita |

**Scope** = bounded context lub obszar: `catalog`, `cart`, `payment`, `order`, `shared`,
`support`, `arch` (ArchUnit), `backend` (poza BC), `frontend`, `db` (migracje), `e2e`, `api`
(kontrakt OpenAPI), `deps`, `specs`, `constitution`, `skills`, `mcp`, `ci`, `docker`.
Jedna zmiana w kilku BC: `feat(cart,order): …` albo bez scope. Czysto dokumentacyjne
commity mogą być bez scope (`docs: …`), tak jak dotychczasowa historia.

### Checklista wiadomości

- [ ] Nagłówek ≤ 72 znaki.
- [ ] Opis po angielsku (jak dotychczasowa historia), w trybie rozkazującym: `add`, `fix`, `update` —
      nie `added`/`adds`/`dodano`. Nazwy domenowe też po angielsku (skill `english-only`).
- [ ] Opis zaczyna się małą literą (chyba że to akronim/nazwa klasy) i nie kończy się kropką.
- [ ] Opis mówi konkretnie, CO się zmienia — żadnych `wip`, `fixes`, `changes`, `misc`.
- [ ] Pusta linia między nagłówkiem a treścią.
- [ ] Treść wyjaśnia powód, jeśli nie jest oczywisty (linie ≤ 100 znaków).
- [ ] ID zadania z `specs/*/tasks.md` w stopce `Refs: T###`, nie w nagłówku.
- [ ] Breaking change (kontrakt REST/OpenAPI, schemat zdarzenia outbox, migracja bez zgodności
      wstecznej) → `!` po scope **i** stopka `BREAKING CHANGE: …`.

Przykłady:
```
feat(catalog): add product search by name and category
fix(payment): reject webhook with invalid Stripe signature
refactor(cart): extract CartMapper from CartService
test(order): cover outbox event on order placement
build(deps): bump spring-boot to 4.0.1
docs(specs): clarify cart quantity limits in spec
```

## Częstotliwość — kiedy commitować

- **Jeden commit = jedna logiczna zmiana = jedno zadanie T###** (lub ściśle powiązana grupa).
  W tym samym commicie odhacz zadanie `- [X]` w `tasks.md`.
- **Każdy commit się buduje i przechodzi testy.** Zadania „najpierw test” (czerwone testy)
  commituj razem z zadaniem, które je zazielenia: `Refs: T031, T037`. Czerwony commit psuje CI i `git bisect`.
- **Commituj od razu**, gdy: zadanie jest skończone i zielone; przechodzisz do innego BC lub zadania;
  zaczynasz ryzykowny refaktor (punkt powrotu); zmiana przekroczyła ~400 linii lub ~15 plików.
- **Dziel**, gdy zmiana ma > ~800 linii albo obejmuje kilka BC, które nie wynikają z jednego zadania.
  Kolejność zgodna z tasks.md: migracja → domena → port → JPA/adapter → Service → fasada/kontroler → frontend.
- **Nie mieszaj** refaktoru ani formatowania z nowym zachowaniem — osobny commit `refactor`/`style` przed `feat`/`fix`.
- **Nie zostawiaj gotowej pracy niezacommitowanej** na koniec zadania lub sesji — zaproponuj commit.
- **Poprawki przed PR** do wcześniejszego commita na gałęzi: `git commit --fixup <sha>`.

## Nigdy

- Nie commituj na `main`.
- Nie commituj sekretów: `.env`, kluczy `sk_…`/`whsec_…`, tokenów (konstytucja, zasada I).
  Sprawdź `git diff --cached` przed commitem.
- Nie commituj artefaktów builda: `target/`, `node_modules/`, `dist/`.
- Nie przepisuj opublikowanej historii (`rebase`, `--amend`, `push --force`) bez wyraźnej prośby.
- Nie pomijaj hooków (`--no-verify`).
