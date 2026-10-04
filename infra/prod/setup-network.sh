#!/bin/bash
# marketry-network를 서브넷 고정으로 만든다. 처음 한 번 손으로 실행한다.
# 서브넷은 nginx.conf의 set_real_ip_from과 같아야 한다.
set -euo pipefail

export PATH="$HOME/Optional/bin:$HOME/Optional/lima/bin:/usr/bin:/bin:/usr/sbin:/sbin"
export DOCKER_HOST="unix:///Users/deploy/.colima/default/docker.sock"

NETWORK="marketry-network"
SUBNET="172.30.0.0/24"

if docker network inspect "$NETWORK" >/dev/null 2>&1; then
  current=$(docker network inspect --format '{{range .IPAM.Config}}{{.Subnet}} {{end}}' "$NETWORK")
  current=${current% }
  if [ "$current" = "$SUBNET" ]; then
    echo "$NETWORK 이(가) 이미 있고 서브넷($SUBNET)이 같습니다."
    exit 0
  fi
  echo "오류: $NETWORK 의 서브넷이 '$current' 입니다. 기대값은 $SUBNET 입니다." >&2
  exit 1
fi

docker network create --driver bridge --subnet "$SUBNET" "$NETWORK"
echo "$NETWORK 을(를) $SUBNET 로 만들었습니다."
