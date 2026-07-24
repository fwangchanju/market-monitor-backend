#!/bin/bash
set -e

COMPOSE_FILE="$HOME/repo/market-monitor-backend/infra/docker-compose.yml"
ENV_FILE="$HOME/env/market-monitor.env"

echo "=== [db] Starting market-monitor-postgres ==="
docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" up -d market-monitor-postgres

echo "=== [db] Done ==="
