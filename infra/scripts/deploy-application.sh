#!/bin/bash
set -e

source "$HOME/repo/market-monitor-backend/infra/scripts/env.sh"

COMPOSE_FILE="$REPO_DIR/infra/docker-compose.yml"

echo "=== [application] Logging in to GHCR ==="
echo "$CR_PAT" | docker login ghcr.io -u "$GHCR_USER" --password-stdin

echo "=== [application] Pulling latest image ==="
docker pull "ghcr.io/$GHCR_USER/market-monitor:latest"

echo "=== [application] Restarting market-monitor ==="
docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" up -d --no-deps market-monitor

echo "=== [application] Done ==="
