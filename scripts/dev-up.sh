#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./common.sh
. "$SCRIPT_DIR/common.sh"

require_cmd docker

print_step "Starting PostgreSQL and RabbitMQ for the data worker"
(
  cd "$REPO_ROOT"
  docker compose up -d db rabbitmq
)

echo
echo "Data infrastructure is up. Follow data/README.md to start the worker."
