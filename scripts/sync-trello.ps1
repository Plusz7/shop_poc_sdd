# Mirrors specs/<feature>/tasks.md onto the Trello board (see docs/kanban-trello.md).
# Open tasks go to "Backlog", completed ones to "Done". Idempotent: cards are matched by task ID
# (T###), existing cards are never duplicated, and a card is only moved when its task is completed.
# Usage (from the repository root):
#   ./scripts/sync-trello.ps1                     # dry run: prints what would be synced
#   ./scripts/sync-trello.ps1 -Apply              # writes to the board
#   ./scripts/sync-trello.ps1 -Feature 002-x -Apply
param(
    [string]$Feature = '001-shop-browse-cart-checkout',
    [switch]$Apply
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$tasksPath = Join-Path $root "specs/$Feature/tasks.md"
if (-not (Test-Path $tasksPath)) { Write-Error "Missing $tasksPath" }

function Get-Setting($name) {
    $value = [Environment]::GetEnvironmentVariable($name, 'Process')
    if (-not $value) { $value = [Environment]::GetEnvironmentVariable($name, 'User') }
    if (-not $value) { Write-Error "Missing environment variable $name - see docs/kanban-trello.md" }
    $value
}

# Parse tasks.md: "- [X] T012 [P] [US2] Description"
$storyPriority = @{}
$phase = ''
$tasks = foreach ($line in Get-Content $tasksPath -Encoding UTF8) {
    if ($line -match '^## (Phase .+)$') {
        $phase = $Matches[1]
        if ($phase -match 'User Story (\d+) .*\(Priority: (P\d)\)') { $storyPriority["US$($Matches[1])"] = $Matches[2] }
        continue
    }
    if ($line -match '^- \[([ xX])\] (T\d{3})((?: \[[^\]]+\])*) (.+)$') {
        # capture before the next -match overwrites $Matches
        $done = $Matches[1] -ne ' '; $id = $Matches[2]; $tags = $Matches[3]; $text = $Matches[4]
        $story = if ($tags -match '\[(US\d+)\]') { $Matches[1] } else { $null }
        $short = ($text -split ' in `|; |: |\(', 2)[0].Trim().TrimEnd(',', '.', ' ', [char]0x2014)
        if ($short.Length -gt 90) { $short = $short.Substring(0, 87).TrimEnd() + [char]0x2026 }
        [pscustomobject]@{
            Id    = $id
            Done  = $done
            Story = $story
            Name  = if ($story) { "$id [$story] $short" } else { "$id $short" }
            Desc  = "$text`n`n---`nPhase: $phase`nSource: specs/$Feature/tasks.md ($id)"
        }
    }
}
"Parsed $($tasks.Count) tasks: $(@($tasks | Where-Object Done).Count) done, $(@($tasks | Where-Object { -not $_.Done }).Count) open"

if (-not $Apply) {
    $tasks | ForEach-Object { '{0,-8} {1}' -f $(if ($_.Done) { 'Done' } else { 'Backlog' }), $_.Name }
    'Dry run - pass -Apply to write to the board.'
    return
}

$key = Get-Setting 'TRELLO_API_KEY'
$token = Get-Setting 'TRELLO_TOKEN'
$headers = @{ Authorization = "OAuth oauth_consumer_key=`"$key`", oauth_token=`"$token`"" }
$api = 'https://api.trello.com/1'

function Invoke-Trello($method, $path, $body) {
    Start-Sleep -Milliseconds 120   # stay well below the 100 requests / 10 s token limit
    $params = @{ Method = $method; Uri = "$api$path"; Headers = $headers }
    if ($body) {
        $params.ContentType = 'application/json; charset=utf-8'
        $params.Body = [Text.Encoding]::UTF8.GetBytes(($body | ConvertTo-Json -Depth 5))
    }
    Invoke-RestMethod @params
}

# The labels endpoint needs the full board id, not the short link from the URL
$board = (Invoke-Trello GET "/boards/$(Get-Setting 'TRELLO_BOARD_ID')?fields=id").id

$lists = Invoke-Trello GET "/boards/$board/lists?fields=name"
$doneList = ($lists | Where-Object name -eq 'Done').id
$backlogList = ($lists | Where-Object name -eq 'Backlog').id
if (-not $doneList -or -not $backlogList) { Write-Error 'The board needs the lists "Backlog" and "Done".' }

$labels = Invoke-Trello GET "/boards/$board/labels?fields=name&limit=100"
$labels = @($labels)
$labelColors = @{ P1 = 'red'; P2 = 'orange'; P3 = 'yellow'; US1 = 'green'; US2 = 'lime'; US3 = 'sky'; US4 = 'purple'; US5 = 'pink' }
function Get-LabelId($name) {
    $label = $script:labels | Where-Object name -eq $name | Select-Object -First 1
    if (-not $label) {
        $color = if ($labelColors.ContainsKey($name)) { $labelColors[$name] } else { 'blue' }
        $label = Invoke-Trello POST '/labels' @{ name = $name; color = $color; idBoard = $board }
        $script:labels += $label
    }
    $label.id
}

# Assign first: Windows PowerShell 5.1 emits a JSON array as one object, and only a variable enumerates it
$cards = Invoke-Trello GET "/boards/$board/cards?fields=name,idList"
$existing = @{}
foreach ($card in $cards) {
    if ($card.name -match '^(T\d{3})\b') { $existing[$Matches[1]] = $card }
}

$created = 0; $moved = 0; $unchanged = 0
foreach ($task in $tasks) {
    if ($existing.ContainsKey($task.Id)) {
        $card = $existing[$task.Id]
        if ($task.Done -and $card.idList -ne $doneList) {
            Invoke-Trello PUT "/cards/$($card.id)" @{ idList = $doneList; pos = 'bottom' } | Out-Null
            $moved++
        } else { $unchanged++ }
        continue
    }
    $labelIds = @(Get-LabelId $Feature)
    if ($task.Story) {
        $labelIds += Get-LabelId $task.Story
        if ($storyPriority.ContainsKey($task.Story)) { $labelIds += Get-LabelId $storyPriority[$task.Story] }
    }
    Invoke-Trello POST '/cards' @{
        idList   = $(if ($task.Done) { $doneList } else { $backlogList })
        name     = $task.Name
        desc     = $task.Desc
        idLabels = $labelIds -join ','
        pos      = 'bottom'
    } | Out-Null
    $created++
}
"Created: $created, moved to Done: $moved, unchanged: $unchanged"
