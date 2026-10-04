#!/bin/bash
# 운영에서 만든 개발용 스냅샷으로 로컬 개발 DB를 통째로 다시 만든다(drop, create, restore).
# 개발 계정에서 손으로 실행하거나 05:00 LaunchAgent(launchd/kr.co.marketry.dev-snapshot-restore.plist)가 실행한다.
# 로컬 DB에 쌓아 둔 데이터와 웹에서 바꾼 설정은 모두 사라진다. 브랜치에만 있는 마이그레이션은 다음 bootRun 때 Flyway가 적용한다.
set -euo pipefail

SNAPSHOT="${SNAPSHOT_FILE:-/Users/Shared/marketry-dev-snapshot/latest.dump}"
DB_NAME="market_monitor_db"
DB_USER="market_monitor"

cd "$(dirname "$0")"

if [ ! -r "$SNAPSHOT" ]; then
  echo "스냅샷이 없습니다: $SNAPSHOT"
  echo "운영 쪽 백업 작업이 아직 스냅샷을 만들지 않았거나 읽기 권한이 없습니다."
  exit 0
fi

# 비밀번호는 컨테이너 안의 로컬 소켓 접속으로 처리하므로 .env를 읽지 않는다
db() {
  docker compose exec -T db "$@"
}

echo "로컬 DB 컨테이너 확인"
docker compose up -d --wait db

echo "$DB_NAME 다시 만들기"
db psql -U "$DB_USER" -d postgres -v ON_ERROR_STOP=1 -q \
  -c "DROP DATABASE IF EXISTS $DB_NAME WITH (FORCE)" \
  -c "CREATE DATABASE $DB_NAME OWNER $DB_USER"

echo "스냅샷 복원: $SNAPSHOT"
db pg_restore -U "$DB_USER" -d "$DB_NAME" --no-owner --no-privileges --exit-on-error <"$SNAPSHOT"

echo "완료"
