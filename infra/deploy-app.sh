#!/bin/bash
set -e

COMPOSE_FILE="$(dirname "$0")/docker-compose.yml"
ENV_FILE="$HOME/env/market-monitor.env"

echo "=== [app] Logging in to GHCR ==="
echo "$CR_PAT" | docker login ghcr.io -u fwangchanju --password-stdin

echo "=== [app] Pulling latest image ==="
docker pull ghcr.io/fwangchanju/market-monitor:latest

echo "=== [app] Restarting market-monitor ==="
docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" up -d --no-deps market-monitor

echo "=== [app] Done ==="
