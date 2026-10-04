#!/bin/bash
# 배포 게이트. GitHub Actions가 ssh로 들어오면 deploy 계정의 authorized_keys 강제 명령(command=)이 이 스크립트를 실행한다.
# 레포 밖(~/Optional/bin/marketry-gate)에 설치해서 쓴다. 레포의 배포 절차가 바뀌어도 배포 직전 DB 덤프는 남게 하려는 구조다.
# 이 파일은 설치본의 원본이다. 고치면 다시 설치해야 반영된다.
#
# 입력은 SSH_ORIGINAL_COMMAND 하나뿐이다: deploy <target> <tag>
#   target: application | nginx | renderer | all
#   tag:    ^[A-Za-z0-9._-]{1,128}$
# 레지스트리 포인터(:deployed, :previous) 갱신과 원복은 워크플로 쪽 일이라 여기서 하지 않는다.
set -euo pipefail

export PATH="$HOME/Optional/bin:$HOME/Optional/lima/bin:/usr/bin:/bin:/usr/sbin:/sbin"
export DOCKER_HOST="unix:///Users/deploy/.colima/default/docker.sock"

REPO_DIR="$HOME/Projects/marketry/marketry-backend"
COMPOSE_FILE="$REPO_DIR/infra/macmini/compose.yml"
ENV_FILE="${MARKETRY_ENV_FILE:-$HOME/env/marketry.env}"
BACKUP_DIR="$HOME/backups/predeploy"
KEEP_DUMPS=5
LOCK_DIR="$HOME/.marketry-gate.lock"

HEALTH_TIMEOUT=600
HEALTH_INTERVAL=5
MAX_RESTART_COUNT=3

TARGET=""
TAG=""

log() {
  echo "=== [gate] $* ==="
}

reject() {
  echo "gate: 거부됨: $1" >&2
  exit 2
}

# SSH_ORIGINAL_COMMAND 문자열을 검증하고 TARGET, TAG에 담는다. 문자열을 셸로 평가하지 않는다.
# 줄바꿈 등이 섞인 입력도 전체 일치 정규식에서 걸러진다.
parse_command() {
  local input="${1-}"
  local re='^deploy (application|nginx|renderer|all) ([A-Za-z0-9._-]{1,128})$'

  if [[ ! "$input" =~ $re ]]; then
    reject "허용되지 않는 명령 형식입니다. 형식: deploy <application|nginx|renderer|all> <tag>"
  fi
  TARGET="${BASH_REMATCH[1]}"
  TAG="${BASH_REMATCH[2]}"
}

acquire_lock() {
  if mkdir "$LOCK_DIR" 2>/dev/null; then
    echo "$$" >"$LOCK_DIR/pid"
    trap 'rm -rf "$LOCK_DIR"' EXIT
    return
  fi

  # 이전 실행이 강제 종료되어 잠금만 남은 경우에만 인수한다
  local holder=""
  [ -r "$LOCK_DIR/pid" ] && holder=$(cat "$LOCK_DIR/pid")
  if [ -n "$holder" ] && kill -0 "$holder" 2>/dev/null; then
    echo "gate: 다른 배포가 진행 중입니다(pid $holder)." >&2
    exit 3
  fi
  log "남아 있던 잠금을 인수합니다"
  rm -rf "$LOCK_DIR"
  mkdir "$LOCK_DIR"
  echo "$$" >"$LOCK_DIR/pid"
  trap 'rm -rf "$LOCK_DIR"' EXIT
}

predeploy_dump() {
  mkdir -p "$BACKUP_DIR"
  chmod 700 "$BACKUP_DIR"

  local stamp final tmp
  stamp=$(date +%Y%m%d-%H%M%S)
  final="$BACKUP_DIR/predeploy-$stamp.dump"
  tmp="$final.tmp"

  log "배포 직전 DB 덤프: $final"
  if ! docker exec marketry-postgres pg_dump -U marketry -Fc marketry_db >"$tmp"; then
    rm -f "$tmp"
    echo "gate: DB 덤프에 실패해 배포를 중단합니다." >&2
    exit 1
  fi
  if [ ! -s "$tmp" ]; then
    rm -f "$tmp"
    echo "gate: DB 덤프가 비어 있어 배포를 중단합니다." >&2
    exit 1
  fi
  mv "$tmp" "$final"

  # 파일명에 시각이 들어 있어 글롭 정렬이 곧 오래된 순서다
  local dumps=("$BACKUP_DIR"/predeploy-*.dump)
  local count=${#dumps[@]}
  local i
  if [ "$count" -gt "$KEEP_DUMPS" ]; then
    for ((i = 0; i < count - KEEP_DUMPS; i++)); do
      rm -f "${dumps[$i]}"
    done
  fi
}

update_repo() {
  log "레포 갱신(origin/main)"
  /usr/bin/git -C "$REPO_DIR" fetch origin main
  /usr/bin/git -C "$REPO_DIR" reset --hard origin/main
}

compose() {
  docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" "$@"
}

deploy_services() {
  local services=()
  case "$TARGET" in
    application) services=(marketry-app) ;;
    nginx) services=(marketry-nginx) ;;
    renderer) services=(marketry-renderer) ;;
    all) services=(marketry-app marketry-nginx marketry-renderer) ;;
  esac

  case "$TARGET" in
    application | all) export APP_TAG="$TAG" ;;
  esac
  case "$TARGET" in
    nginx | all) export NGINX_TAG="$TAG" ;;
  esac
  case "$TARGET" in
    renderer | all) export RENDERER_TAG="$TAG" ;;
  esac

  log "이미지 받기: ${services[*]} (tag: $TAG)"
  compose pull "${services[@]}"

  log "기동: ${services[*]}"
  compose up -d --no-deps "${services[@]}"
}

# 컨테이너가 크래시 루프에 빠졌는지 본다. 빠졌으면 1을 반환한다
crash_looping() {
  local count
  count=$(docker inspect --format='{{.RestartCount}}' "$1")
  if [ "$count" -ge "$MAX_RESTART_COUNT" ]; then
    echo "gate: $1 재시작 ${count}회, 크래시 루프로 판단합니다." >&2
    return 0
  fi
  return 1
}

# 앱 헬스 응답 본문을 가져온다. 앱 이미지의 도구(curl, wget)에 기대지 않는다.
# nginx 컨테이너(busybox wget)가 떠 있으면 같은 네트워크에서 요청하고, 아니면(첫 기동 등)
# 앱 컨테이너 안에서 bash /dev/tcp로 직접 요청한다.
app_health_body() {
  if [ "$(docker inspect --format='{{.State.Running}}' marketry-nginx 2>/dev/null || true)" = "true" ]; then
    docker exec marketry-nginx wget -qO- -T 5 http://marketry-app:8081/actuator/health
  else
    docker exec marketry-app bash -c \
      'exec 3<>/dev/tcp/127.0.0.1/8081 && printf "GET /actuator/health HTTP/1.0\r\nHost: localhost\r\n\r\n" >&3 && cat <&3'
  fi
}

app_healthy() {
  local body
  body=$(app_health_body 2>/dev/null) || return 1
  [[ "$body" == *'"status":"UP"'* ]]
}

# 헬스 조건(함수 또는 명령)이 성공할 때까지 기다린다. 크래시 루프는 container 기준으로 확인한다. 실패하면 1로 끝낸다
wait_healthy() {
  local container="$1"
  shift
  local elapsed=0

  while [ "$elapsed" -lt "$HEALTH_TIMEOUT" ]; do
    if crash_looping "$container"; then
      exit 1
    fi
    if "$@" >/dev/null 2>&1; then
      log "$container 헬스체크 통과"
      return
    fi
    sleep "$HEALTH_INTERVAL"
    elapsed=$((elapsed + HEALTH_INTERVAL))
  done

  echo "gate: $container 이(가) ${HEALTH_TIMEOUT}초 안에 정상 기동하지 않았습니다." >&2
  docker logs --tail 50 "$container" >&2 || true
  exit 1
}

# nginx는 헬스 엔드포인트가 없어 잠시 지켜보며 크래시 루프만 확인한다
watch_container() {
  local container="$1"
  local elapsed=0

  while [ "$elapsed" -lt 15 ]; do
    if [ "$(docker inspect --format='{{.State.Running}}' "$container")" != "true" ] || crash_looping "$container"; then
      echo "gate: $container 이(가) 정상 실행 중이 아닙니다." >&2
      docker logs --tail 50 "$container" >&2 || true
      exit 1
    fi
    sleep 3
    elapsed=$((elapsed + 3))
  done
  log "$container 크래시 루프 미감지"
}

health_check() {
  case "$TARGET" in
    application | all)
      wait_healthy marketry-app app_healthy
      ;;
  esac
  case "$TARGET" in
    renderer | all)
      wait_healthy marketry-renderer docker exec marketry-renderer node -e "require('http').get('http://localhost:3000/health',r=>process.exit(r.statusCode===200?0:1)).on('error',()=>process.exit(1))"
      ;;
  esac
  case "$TARGET" in
    nginx | all)
      watch_container marketry-nginx
      ;;
  esac
}

main() {
  # 입력 검증만 시험하는 모드. 강제 명령으로는 인자를 넘길 수 없어서 로컬에서만 쓸 수 있다
  if [ "${1-}" = "--check" ]; then
    parse_command "${2-}"
    echo "ok target=$TARGET tag=$TAG"
    exit 0
  fi

  parse_command "${SSH_ORIGINAL_COMMAND-}"
  log "배포 요청: target=$TARGET tag=$TAG"

  acquire_lock
  predeploy_dump
  update_repo
  deploy_services
  health_check
  log "배포 완료"
}

main "$@"
