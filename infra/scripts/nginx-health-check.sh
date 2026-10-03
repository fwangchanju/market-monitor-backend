#!/bin/bash
set -e

MAX_RESTART_COUNT=3
TIMEOUT=30
POLL_INTERVAL=3
elapsed=0

echo "=== [nginx-health-check] market-monitor-nginx, market-monitor-cloudflared 크래시 루프 확인 시작 ==="

docker logs -f market-monitor-nginx &
LOGS_PID=$!
trap 'kill "$LOGS_PID" 2>/dev/null' EXIT

while [ "$elapsed" -lt "$TIMEOUT" ]; do
  for container in market-monitor-nginx market-monitor-cloudflared; do
    restart_count=$(docker inspect --format='{{.RestartCount}}' "$container")
    if [ "$restart_count" -ge "$MAX_RESTART_COUNT" ]; then
      echo "=== [nginx-health-check] ${container} 재시작 ${restart_count}회, 크래시 루프로 판단하여 실패 처리 ==="
      exit 1
    fi
  done

  sleep "$POLL_INTERVAL"
  elapsed=$((elapsed + POLL_INTERVAL))
done

echo "=== [nginx-health-check] 크래시 루프 미감지, 정상으로 판단 ==="
exit 0
