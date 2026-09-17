#!/bin/bash
set -e

source "$HOME/repo/market-monitor-backend/infra/scripts/env.sh"

MAX_RESTART_COUNT=3
TIMEOUT=600
POLL_INTERVAL=5
elapsed=0

echo "=== [renderer-health-check] market-monitor-renderer 정상 기동 확인 시작 ==="

docker logs -f market-monitor-renderer &
LOGS_PID=$!
trap 'kill "$LOGS_PID" 2>/dev/null' EXIT

while [ "$elapsed" -lt "$TIMEOUT" ]; do
  restart_count=$(docker inspect --format='{{.RestartCount}}' market-monitor-renderer)
  if [ "$restart_count" -ge "$MAX_RESTART_COUNT" ]; then
    echo "=== [renderer-health-check] 재시작 ${restart_count}회, 크래시 루프로 판단하여 실패 처리 ==="
    exit 1
  fi

  if curl -sf http://127.0.0.1:3000/health > /dev/null; then
    echo "=== [renderer-health-check] 헬스체크 통과 ==="
    exit 0
  fi

  sleep "$POLL_INTERVAL"
  elapsed=$((elapsed + POLL_INTERVAL))
done

echo "=== [renderer-health-check] 타임아웃(${TIMEOUT}s) 내에 정상 기동 확인 실패 ==="
exit 1
