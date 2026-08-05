#!/bin/bash
set -e

source "$HOME/repo/market-monitor-backend/infra/scripts/env.sh"

NGINX_COMPOSE_FILE="$REPO_DIR/infra/nginx-docker-compose.yml"

echo "=== [guest-access] Switching to guest nginx.conf ==="
cp "$REPO_DIR/infra/nginx-guest.conf" "$REPO_DIR/infra/nginx.conf"

echo "=== [guest-access] Restarting nginx ==="
docker compose -f "$NGINX_COMPOSE_FILE" restart

echo "=== [guest-access] Done. allowed-ips 외 전 구간이 개방되었습니다. ==="
echo "=== [guest-access] 원복하려면 'git -C $REPO_DIR checkout -- infra/nginx.conf' 후 nginx를 재시작하거나, 다음 정상 배포 때 자동 복구됩니다. ==="
