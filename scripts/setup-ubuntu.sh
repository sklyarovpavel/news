#!/usr/bin/env bash

# Re-exec under bash if invoked via sh/dash (to support pipefail and bashisms)
if [ -z "${BASH_VERSION:-}" ]; then
  exec /usr/bin/env bash "$0" "$@"
fi

set -Eeuo pipefail

# Usage:
#   ./scripts/setup-ubuntu.sh [--up] [--up-all]
# Flags:
#   --up       Bring up minimal stack (db + app from news-app/docker-compose.yml)
#   --up-all   Bring up full stack from docker-compose.all.yml
#
# Notes:
# - After installation, you may need to re-login for docker group changes to apply.
# - Script targets Ubuntu; requires sudo privileges for package installation.

if [[ "${DEBUG:-}" == "1" ]]; then
  set -x
fi

info() { echo -e "\033[1;34m[INFO]\033[0m $*"; }
warn() { echo -e "\033[1;33m[WARN]\033[0m $*"; }
err()  { echo -e "\033[1;31m[ERROR]\033[0m $*"; }

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${REPO_DIR}"

UP_MINIMAL=0
UP_ALL=0
while [[ $# -gt 0 ]]; do
  case "$1" in
    --up) UP_MINIMAL=1; shift ;;
    --up-all) UP_ALL=1; shift ;;
    -h|--help)
      sed -n '1,40p' "$0" | sed -n '1,20p'
      exit 0
      ;;
    *)
      err "Unknown argument: $1"
      exit 1
      ;;
  esac
done

# Check Ubuntu
if [[ -r /etc/os-release ]]; then
  # shellcheck disable=SC1091
  . /etc/os-release
  if [[ "${ID:-}" != "ubuntu" ]]; then
    warn "Detected non-Ubuntu system: ${PRETTY_NAME:-unknown}. Proceeding anyway."
  fi
else
  warn "/etc/os-release not found; proceeding without OS check."
fi

SUDO="sudo"
if [[ "${EUID}" -eq 0 ]]; then
  SUDO=""
fi

info "Updating apt index and installing prerequisites..."
${SUDO} apt-get update -y
${SUDO} apt-get install -y \
  ca-certificates curl gnupg lsb-release software-properties-common apt-transport-https

if ! command -v docker >/dev/null 2>&1; then
  info "Installing Docker Engine and Compose plugin..."
  ${SUDO} install -m 0755 -d /etc/apt/keyrings
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg | ${SUDO} gpg --dearmor -o /etc/apt/keyrings/docker.gpg
  ${SUDO} chmod a+r /etc/apt/keyrings/docker.gpg
  echo \
    "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
    | ${SUDO} tee /etc/apt/sources.list.d/docker.list >/dev/null
  ${SUDO} apt-get update -y
  ${SUDO} apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
  ${SUDO} systemctl enable --now docker || true
else
  info "Docker already installed, skipping."
fi

if ! command -v java >/dev/null 2>&1; then
  info "Installing OpenJDK 17..."
  ${SUDO} apt-get install -y openjdk-17-jdk
else
  info "Java already installed, skipping."
fi

if ! command -v mvn >/dev/null 2>&1; then
  info "Installing Maven..."
  ${SUDO} apt-get install -y maven
else
  info "Maven already installed, skipping."
fi

if ! command -v git >/dev/null 2>&1; then
  info "Installing Git..."
  ${SUDO} apt-get install -y git
else
  info "Git already installed, skipping."
fi

# Add current user to docker group (if needed)
if ! id -nG "${USER}" | grep -q "\bdocker\b"; then
  info "Adding ${USER} to 'docker' group..."
  ${SUDO} usermod -aG docker "${USER}" || true
  warn "You need to re-login (or 'newgrp docker') for group changes to take effect."
fi

# Versions
info "Tool versions:"
set +e
docker --version || true
docker compose version || true
java -version || true
mvn -version || true
set -e

# Optionally start containers
if [[ ${UP_ALL} -eq 1 ]]; then
  info "Bringing up full stack (docker-compose.all.yml) with build..."
  docker compose -f "${REPO_DIR}/docker-compose.all.yml" up -d --build
elif [[ ${UP_MINIMAL} -eq 1 ]]; then
  info "Bringing up minimal stack (news-app/docker-compose.yml) with build..."
  docker compose -f "${REPO_DIR}/news-app/docker-compose.yml" up -d --build
else
  info "Installation complete. To start services, run one of:"
  echo "  ./scripts/setup-ubuntu.sh --up       # minimal stack (db + app)"
  echo "  ./scripts/setup-ubuntu.sh --up-all   # full stack (all services)"
fi

info "Done."

