#!/bin/bash
set -e

source "$HOME/repo/market-monitor-backend/infra/scripts/env.sh"

MAX_RESTART_COUNT=3
TIMEOUT=600
POLL_INTERVAL=5
elapsed=0

echo "=== [health-check] market-monitor 정상 기동 확인 시작 ==="

while [ "$elapsed" -lt "$TIMEOUT" ]; do
  restart_count=$(docker inspect --format='{{.RestartCount}}' market-monitor)
  if [ "$restart_count" -ge "$MAX_RESTART_COUNT" ]; then
    echo "=== [health-check] 재시작 ${restart_count}회, 크래시 루프로 판단하여 실패 처리 ==="
    docker logs market-monitor
    exit 1
  fi

  if curl -sf http://127.0.0.1:8081/actuator/health > /dev/null; then
    echo "=== [health-check] 헬스체크 통과 ==="
    exit 0
  fi

  sleep "$POLL_INTERVAL"
  elapsed=$((elapsed + POLL_INTERVAL))
done

echo "=== [health-check] 타임아웃(${TIMEOUT}s) 내에 정상 기동 확인 실패 ==="
docker logs market-monitor
exit 1
