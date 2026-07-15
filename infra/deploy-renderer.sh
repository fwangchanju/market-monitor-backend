#!/bin/bash
set -e

COMPOSE_FILE="$(dirname "$0")/docker-compose.yml"
ENV_FILE="$HOME/env/market-monitor-backend.env"

echo "=== [renderer] Logging in to GHCR ==="
echo "$CR_PAT" | docker login ghcr.io -u fwangchanju --password-stdin

echo "=== [renderer] Pulling latest image ==="
docker pull ghcr.io/fwangchanju/market-monitor-renderer:latest

echo "=== [renderer] Restarting market-monitor-renderer ==="
docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" up -d --no-deps market-monitor-renderer

echo "=== [renderer] Done ==="
