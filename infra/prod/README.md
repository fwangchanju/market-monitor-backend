# 맥미니 운영 구성

운영 컨테이너 구성과 배포 게이트·백업 스크립트의 원본이다. 맥미니 구성과 결정은 `docs/server-migration.md`에 있다.
아래는 `deploy` 계정에서 하는 작업이고, 표시한 곳만 `admin`이 한다. 오라클 서버용 파일(`infra/*.yml`, `infra/scripts/`)은 이관 당일까지 그대로 쓴다.

## 설치 순서

1. 네트워크를 만든다. 서브넷이 `nginx.conf`의 `set_real_ip_from`과 같아야 한다.

   ```sh
   cd ~/Projects/marketry/marketry-backend/infra/prod
   ./setup-network.sh
   ```

2. env 파일을 만든다. `env.template`의 키를 `~/env/marketry.env`로 복사해 값을 채우고 권한을 `600`으로 둔다.
   `R2_*`는 비워 두면 외부 업로드만 건너뛴다.

3. GHCR에 로그인한다(읽기 권한 토큰).

   ```sh
   docker login ghcr.io
   ```

4. 게이트와 백업 스크립트를 레포 밖에 설치한다. 스크립트를 고치면 다시 설치해야 반영된다.

   ```sh
   install -m 755 gate.sh ~/Optional/bin/marketry-gate
   install -m 755 backup.sh ~/Optional/bin/marketry-backup
   mkdir -p ~/backups/predeploy ~/backups/daily
   ```

5. `~/.ssh/authorized_keys`에 배포 키를 강제 명령과 함께 등록한다. 키는 GitHub Actions 전용으로 만든다.

   ```
   command="/Users/deploy/Optional/bin/marketry-gate",restrict ssh-ed25519 AAAA... ci-deploy
   ```

6. 백업 LaunchDaemon을 `admin`이 설치한다.

   ```sh
   sudo install -m 644 -o root -g wheel launchd/kr.co.marketry.backup.plist /Library/LaunchDaemons/
   sudo launchctl bootstrap system /Library/LaunchDaemons/kr.co.marketry.backup.plist
   ```

7. 개발용 스냅샷 폴더를 만든다. `deploy`가 쓰고 개발 계정은 읽기만 한다.

   ```sh
   mkdir -p /Users/Shared/marketry-dev-snapshot
   chmod 755 /Users/Shared/marketry-dev-snapshot
   ```

8. 첫 기동은 손으로 한다. 이미지 태그는 환경변수로 넘기고, 생략하면 `deployed`(앱)와 `latest`(나머지)를 쓴다.

   ```sh
   docker compose -f compose.yml --env-file ~/env/marketry.env up -d
   ```

   이후 배포는 GitHub Actions가 게이트를 거쳐서만 한다.

## 개발 계정

개발 DB는 `infra/local/restore-snapshot.sh`로 스냅샷에서 다시 만든다. 05:00 예약 작업은
`infra/local/launchd/kr.co.marketry.dev-snapshot-restore.plist`를 개발 계정의 `~/Library/LaunchAgents`에 복사해 쓴다.
