#!/usr/bin/env bash
set -euo pipefail

HOST="${HOST:-http://localhost:8080}"
FROM="${1:-2026-01-01}"
TO="${2:-2026-01-31}"
LIMIT="${3:-200}"
CURSOR="${4:-}"

URL="$HOST/api/v1/news"

if [[ -n "$CURSOR" ]]; then
  curl -sS -G "$URL" \
    --data-urlencode "from=$FROM" \
    --data-urlencode "to=$TO" \
    --data-urlencode "limit=$LIMIT" \
    --data-urlencode "cursor=$CURSOR"
else
  curl -sS -G "$URL" \
    --data-urlencode "from=$FROM" \
    --data-urlencode "to=$TO" \
    --data-urlencode "limit=$LIMIT"
fi

