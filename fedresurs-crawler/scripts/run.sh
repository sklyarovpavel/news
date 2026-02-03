#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_ROOT"

if ! command -v mvn >/dev/null 2>&1; then
  echo "mvn не найден. Установите Maven или используйте mvnw."
  exit 1
fi

ARGS=()
exec mvn spring-boot:run

