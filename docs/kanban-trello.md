# Kanban projektu w Trello (MCP dla Claude Code)

Tablica Trello służy do śledzenia zadań zespołu. Claude Code zarządza nią przez serwer MCP
[`@delorenj/mcp-server-trello`](https://github.com/delorenj/mcp-server-trello) skonfigurowany
w [`.mcp.json`](../.mcp.json). To narzędzie deweloperskie — nie jest częścią aplikacji sklepu.

## Konfiguracja (jednorazowo)

1. Utwórz tablicę Trello z listami: `Backlog`, `To Do`, `In Progress`, `Review`, `Done`.
2. Wygeneruj klucz API i token: <https://trello.com/power-ups/admin> → nowy Power-Up →
   „API key" → link „Token". Token daje dostęp do Twojego konta — traktuj go jak hasło.
3. Ustaw zmienne środowiskowe użytkownika (Claude Code nie czyta pliku `.env` — rozwija
   `${...}` w `.mcp.json` ze zmiennych procesu):

   ```powershell
   setx TRELLO_API_KEY "<klucz>"
   setx TRELLO_TOKEN "<token>"
   setx TRELLO_BOARD_ID "<id-tablicy>"
   ```

4. Uruchom ponownie aplikację Claude, zatwierdź serwer `trello` przy pierwszym użyciu
   i sprawdź połączenie poleceniem: „pokaż listy na tablicy Trello".

Wartości sekretów nigdy nie trafiają do repozytorium; `.mcp.json` zawiera tylko odwołania
do zmiennych. Przy wycieku tokenu unieważnij go w ustawieniach konta Trello i wygeneruj nowy.

## Konwencje pracy z tablicą

- **Karta = zadanie z `tasks.md`**. Tytuł: `T012 [US2] <opis>`; w opisie ścieżka do
  `specs/<feature>/tasks.md` i pliki, których dotyczy zadanie.
- **Etykieta = feature** (np. `001-shop-browse-cart-checkout`) oraz priorytet historii (`P1`).
- **Przepływ**: `Backlog` (po `/speckit-tasks`) → `To Do` (zaplanowane w bieżącej iteracji)
  → `In Progress` (w trakcie `/speckit-implement`) → `Review` (otwarty PR) → `Done` (zmergowane).
- Źródłem prawdy o zakresie jest `tasks.md`; Trello odzwierciedla status. Zmiany zakresu
  wprowadzaj w `tasks.md`, a potem synchronizuj tablicę.

## Aktualizacja serwera

Wersja serwera jest przypięta w `.mcp.json` (`@1.8.1`), żeby nowe wydanie paczki npm nie
uruchomiło się bez przeglądu. Podbijaj ją świadomie, po sprawdzeniu zmian w repozytorium paczki.
