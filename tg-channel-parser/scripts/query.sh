#!/usr/bin/env bash
set -euo pipefail
FROM="${1:-2026-01-01}"
TO="${2:-2026-01-31}"
LIMIT="${3:-200}"
BASE_URL="${BASE_URL:-http://localhost:8080}"
curl -s -G "${BASE_URL}/api/v1/news" \
  --data-urlencode "from=${FROM}" \
  --data-urlencode "to=${TO}" \
  --data-urlencode "limit=${LIMIT}" | jq .

