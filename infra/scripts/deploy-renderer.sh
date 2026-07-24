#!/bin/bash
set -e

source "$HOME/repo/market-monitor-backend/infra/scripts/env.sh"

COMPOSE_FILE="$REPO_DIR/infra/renderer-docker-compose.yml"

echo "=== [renderer] Logging in to GHCR ==="
echo "$CR_PAT" | docker login ghcr.io -u "$GHCR_USER" --password-stdin

echo "=== [renderer] Pulling latest image ==="
docker pull "ghcr.io/$GHCR_USER/market-monitor-renderer:latest"

echo "=== [renderer] Restarting market-monitor-renderer ==="
docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" up -d

echo "=== [renderer] Done ==="
