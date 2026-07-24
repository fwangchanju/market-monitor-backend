#!/bin/bash
set -e

source "$HOME/repo/market-monitor-backend/infra/scripts/env.sh"

COMPOSE_FILE="$REPO_DIR/infra/docker-compose.yml"

echo "=== [db] Stopping market-monitor-postgres (데이터 유지) ==="
docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" stop market-monitor-postgres

echo "=== [db] Starting market-monitor-postgres ==="
docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" up -d market-monitor-postgres

echo "=== [db] Done ==="
