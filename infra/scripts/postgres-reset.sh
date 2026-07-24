#!/bin/bash
set -e

source "$HOME/repo/market-monitor-backend/infra/scripts/env.sh"

COMPOSE_FILE="$REPO_DIR/infra/docker-compose.yml"

echo "이 스크립트는 DB 데이터를 전부 삭제하고 처음부터 다시 만듭니다."
read -p "계속하려면 'yes'를 입력하세요: " confirm
if [ "$confirm" != "yes" ]; then
  echo "취소됨"
  exit 1
fi

echo "=== [db] 전체 중지 (market-monitor-postgres, market-monitor 포함) ==="
docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" down

echo "=== [db] 데이터 디렉토리 삭제 ($DB_DATA_DIR) ==="
rm -rf "$DB_DATA_DIR"

echo "=== [db] 재기동 ==="
docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" up -d

echo "=== [db] Done ==="
