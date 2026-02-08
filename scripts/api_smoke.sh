#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"

echo "[api-smoke] BASE_URL=$BASE_URL"

node scripts/api_smoke.mjs
