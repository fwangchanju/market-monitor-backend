#!/bin/bash
set -e

NGINX_COMPOSE_FILE="$HOME/infra/nginx/docker-compose.yml"

echo "=== [nginx] Logging in to GHCR ==="
echo "$CR_PAT" | docker login ghcr.io -u fwangchanju --password-stdin

echo "=== [nginx] Pulling latest image ==="
docker pull ghcr.io/fwangchanju/market-monitor-nginx:latest

echo "=== [nginx] Restarting nginx ==="
docker compose -f "$NGINX_COMPOSE_FILE" down
docker compose -f "$NGINX_COMPOSE_FILE" up -d

echo "=== [nginx] Done ==="
