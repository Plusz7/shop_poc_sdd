# Runs the backend with the "local" profile, reading variables from .env explicitly
# (no spring-dotenv, research R-20). Usage (from the repository root): ./scripts/run-backend.ps1
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $root '.env'

if (-not (Test-Path $envFile)) {
    Write-Error "Missing .env - copy .env.example to .env and fill it in."
}

foreach ($line in Get-Content $envFile) {
    $trimmed = $line.Trim()
    if ($trimmed -eq '' -or $trimmed.StartsWith('#')) { continue }
    $idx = $trimmed.IndexOf('=')
    if ($idx -lt 1) { continue }
    $name = $trimmed.Substring(0, $idx).Trim()
    $value = $trimmed.Substring($idx + 1).Trim()
    if ($value -ne '') {
        Set-Item -Path "Env:$name" -Value $value
    }
}

& (Join-Path $root 'backend/mvnw.cmd') -f (Join-Path $root 'backend/pom.xml') spring-boot:run '-Dspring-boot.run.profiles=local'
