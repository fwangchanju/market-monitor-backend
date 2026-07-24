#!/bin/bash
set -e

COMPOSE_FILE="$HOME/repo/market-monitor-backend/infra/docker-compose.yml"
ENV_FILE="$HOME/env/market-monitor.env"

echo "=== [application] Logging in to GHCR ==="
echo "$CR_PAT" | docker login ghcr.io -u fwangchanju --password-stdin

echo "=== [application] Pulling latest image ==="
docker pull ghcr.io/fwangchanju/market-monitor:latest

echo "=== [application] Restarting market-monitor ==="
docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" up -d --no-deps market-monitor

echo "=== [application] Done ==="
