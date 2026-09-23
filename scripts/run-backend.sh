#!/usr/bin/env bash
# Runs the backend with the "local" profile, reading variables from .env explicitly
# (no spring-dotenv, research R-20). Usage (from the repository root): ./scripts/run-backend.sh
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
env_file="$root/.env"

if [[ ! -f "$env_file" ]]; then
  echo "Missing .env - copy .env.example to .env and fill it in." >&2
  exit 1
fi

while IFS= read -r line || [[ -n "$line" ]]; do
  line="${line%$'\r'}"
  [[ -z "${line// }" || "$line" =~ ^[[:space:]]*# ]] && continue
  name="${line%%=*}"
  value="${line#*=}"
  [[ -n "$value" ]] && export "$name=$value"
done < "$env_file"

exec "$root/backend/mvnw" -f "$root/backend/pom.xml" spring-boot:run -Dspring-boot.run.profiles=local
