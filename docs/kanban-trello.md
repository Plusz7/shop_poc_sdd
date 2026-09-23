# Project Kanban in Trello (Claude connector)

The Trello board tracks the team's tasks. Claude manages it through the official **Trello
connector** (Atlassian, `https://mcp.trello.com/v1`), connected per Claude account. This is a
developer tool — it is not part of the shop application.

## Setup (one-time)

1. Create a Trello board with the lists: `Backlog`, `To Do`, `In Progress`, `Review`, `Done`.
2. In Claude, add the **Trello** connector (Settings → Connectors, or accept the connector
   suggestion in a session) and sign in with your Trello account (OAuth). Verify it with the
   prompt: "show the lists on the Trello board".

No key or token is stored in the repository or in environment variables for Claude; access is
revoked in the Trello account settings (connected apps). Each team member connects their own account.

The previous setup (the npm server `@delorenj/mcp-server-trello` in `.mcp.json` with
`TRELLO_*` environment variables) was removed: the server only saw variables present when the
Claude app started, which caused `401` errors after a token change.

## Board conventions

- **Card = task from `tasks.md`**. Title: `T012 [US2] <description>`; the description holds the path to
  `specs/<feature>/tasks.md` and the files the task touches.
- **Label = feature** (e.g. `001-shop-browse-cart-checkout`) plus the story priority (`P1`).
- **Flow**: `Backlog` (after `/speckit-tasks`) → `To Do` (planned for the current iteration)
  → `In Progress` (during `/speckit-implement`) → `Review` (PR open) → `Done` (merged).
- `tasks.md` is the source of truth for scope; Trello mirrors status. Make scope changes
  in `tasks.md` first, then sync the board.

## Syncing `tasks.md` to the board

[`scripts/sync-trello.ps1`](../scripts/sync-trello.ps1) mirrors a feature's `tasks.md` onto the board
through the Trello REST API. It does not use the connector; it needs an API key and token
(<https://trello.com/power-ups/admin> → new Power-Up → "API key" → "Token" link — treat the
token like a password) in user environment variables:

```powershell
setx TRELLO_API_KEY "<key>"
setx TRELLO_TOKEN "<token>"
setx TRELLO_BOARD_ID "<board-id>"
```

```powershell
./scripts/sync-trello.ps1                                   # dry run
./scripts/sync-trello.ps1 -Apply                            # default feature
./scripts/sync-trello.ps1 -Feature 002-<name> -Apply
```

New tasks become cards in `Backlog` (or `Done` if already checked off); cards whose task has been
checked off are moved to `Done`. Cards are matched by task ID, so re-running never creates
duplicates, and cards moved by hand to `To Do`, `In Progress` or `Review` stay where they are until
their task is completed.

Note that the script moves completed tasks straight to `Done`, skipping `Review`. While the PR of
a feature is open, move its cards with the connector instead (`Review`), and run the script with
`-Apply` after the merge.
