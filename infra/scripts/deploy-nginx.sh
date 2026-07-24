#!/bin/bash
set -e

source "$HOME/repo/market-monitor-backend/infra/scripts/env.sh"

NGINX_COMPOSE_FILE="$REPO_DIR/infra/nginx-docker-compose.yml"

echo "=== [nginx] Logging in to GHCR ==="
echo "$CR_PAT" | docker login ghcr.io -u "$GHCR_USER" --password-stdin

echo "=== [nginx] Pulling latest image ==="
docker pull "ghcr.io/$GHCR_USER/market-monitor-nginx:latest"

echo "=== [nginx] Restarting nginx ==="
docker compose -f "$NGINX_COMPOSE_FILE" down
docker compose -f "$NGINX_COMPOSE_FILE" up -d

echo "=== [nginx] Done ==="
