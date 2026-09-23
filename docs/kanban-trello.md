# Project Kanban in Trello (MCP for Claude Code)

The Trello board tracks the team's tasks. Claude Code manages it through the MCP server
[`@delorenj/mcp-server-trello`](https://github.com/delorenj/mcp-server-trello) configured
in [`.mcp.json`](../.mcp.json). This is a developer tool — it is not part of the shop application.

## Setup (one-time)

1. Create a Trello board with the lists: `Backlog`, `To Do`, `In Progress`, `Review`, `Done`.
2. Generate an API key and token: <https://trello.com/power-ups/admin> → new Power-Up →
   "API key" → "Token" link. The token grants access to your account — treat it like a password.
3. Set user environment variables (Claude Code does not read the `.env` file — it expands
   `${...}` in `.mcp.json` from the process environment):

   ```powershell
   setx TRELLO_API_KEY "<key>"
   setx TRELLO_TOKEN "<token>"
   setx TRELLO_BOARD_ID "<board-id>"
   ```

4. Restart the Claude app, approve the `trello` server on first use
   and verify the connection with the prompt: "show the lists on the Trello board".

Secret values never go into the repository; `.mcp.json` contains only references
to the variables. If the token leaks, revoke it in your Trello account settings and generate a new one.

## Board conventions

- **Card = task from `tasks.md`**. Title: `T012 [US2] <description>`; the description holds the path to
  `specs/<feature>/tasks.md` and the files the task touches.
- **Label = feature** (e.g. `001-shop-browse-cart-checkout`) plus the story priority (`P1`).
- **Flow**: `Backlog` (after `/speckit-tasks`) → `To Do` (planned for the current iteration)
  → `In Progress` (during `/speckit-implement`) → `Review` (PR open) → `Done` (merged).
- `tasks.md` is the source of truth for scope; Trello mirrors status. Make scope changes
  in `tasks.md` first, then sync the board.

## Updating the server

The server version is pinned in `.mcp.json` (`@1.8.1`) so that a new npm package release does not
run without review. Bump it deliberately, after checking the changes in the package repository.
