# 운영 — 배포 · 롤백 · 장애 대응

배포나 롤백이 필요하면 **코드부터 뒤지지 말고 이 문서를 먼저 본다.**

---

## 구성

서버 두 대에 컨테이너 세 종류가 돈다.

| 이미지 | 서버 | 내용 |
|---|---|---|
| `ghcr.io/fwangchanju/market-monitor` | 서버 1 | Spring 애플리케이션 |
| `ghcr.io/fwangchanju/market-monitor-nginx` | 서버 1 | nginx + **프론트 정적 자산이 구워져 있음** |
| `ghcr.io/fwangchanju/market-monitor-renderer` | 서버 2 | 스크린샷 렌더러 |

nginx 이미지는 프론트 레포가 만든 `market-monitor-assets:latest`를 `FROM`으로 가져다 굽는다
(`containers/nginx/Dockerfile`). **프론트 코드는 nginx 이미지 안에 박히므로, 프론트를 배포하려면
nginx 이미지를 다시 빌드해야 한다.**

> nginx가 프론트 레포가 아니라 여기 있는 이유는 `docs/decisions.md` 참고.

---

## 이미지 태그 체계

| 태그 | 언제 생기나 |
|---|---|
| `:sha-<커밋SHA>` | 브랜치 push 시 자동 빌드 (승격의 재료) |
| `:main` | main 병합 시, `:sha-<머지된 브랜치 tip>`을 **재태깅**(재빌드 없음) |
| `:deployed` | 배포 성공 시, **지금 서버에 떠 있는 이미지**를 가리키도록 워크플로가 갱신 |
| `:previous` | 배포 성공 시, `:deployed`가 가리키던 **직전 정상 이미지**로 갱신 |

`:main`은 "배포 후보", `:deployed`는 "실제로 떠 있는 것"이다. **둘이 다르면 병합해놓고 배포를
안 한 상태**라는 뜻이다.

`:deployed`/`:previous`는 사람이 손대지 않는다. 롤백이 이 두 개만 보고 동작한다.

### main 브랜치 보호 설정 (전제 조건)

승격 로직이 머지 커밋의 `HEAD^2`(= 병합된 브랜치의 tip)로 원본 이미지를 찾기 때문에, **main에 올라오는
커밋이 항상 머지 커밋이어야** 한다. GitHub 설정에서 아래가 유지돼야 이 구조가 성립한다.

- Rulesets: **Require a pull request before merging** (main 직접 push 금지)
- 저장소 Pull Requests 설정: **Allow merge commits만 켜고 Squash/Rebase merge는 끈다**

---

## 병합과 배포는 분리되어 있다

**main에 병합해도 자동으로 배포되지 않는다.** 병합 시점에는 `:main` 태그 승격만 일어난다.
실제 배포는 원하는 시점에 수동으로 실행한다.

배포하면 컨테이너가 교체되면서 서비스가 잠시 끊기므로, **장 시간을 피해서** 몰아서 하면 된다.

nginx·renderer도 마찬가지로 병합만으로는 안 나간다. **예외는 하나** — 프론트 레포가 자산을 발행하면
(`repository_dispatch`) nginx가 자동으로 다시 빌드·배포된다. 프론트 배포 경로가 그것뿐이라 남겨뒀다.

> 승격(재태깅)은 남겨둬야 한다. 이걸 빼면 `:main` 태그가 안 만들어지고, 수동 배포 시 이미지가 없어서
> **처음부터 다시 빌드**하게 된다. 빌드가 두 번 도는 셈이다.

---

## 배포하기

GitHub Actions → **Release** 워크플로 → `Run workflow`

1. **Use workflow from**: 항상 `main` — 다른 브랜치를 고를 이유가 없다
2. **target**: `application` / `nginx` / `renderer` / `all` / `rollback`
3. 실행

`application`은 `:main` 이미지를 그대로 배포한다. 워크플로는 이미지를 빌드하지 않는다 — 배포할 태그가
없으면 **명확한 메시지와 함께 실패**한다(조용히 엉뚱한 걸 배포하지 않는다).

배포 → 헬스체크가 성공하면 `:previous`/`:deployed` 포인터가 자동으로 갱신된다.
**헬스체크가 실패하면 워크플로가 `:deployed`를 다시 배포해 원상복구한 뒤 실패로 끝난다.** 즉
실패한 배포는 서버에 남지 않는다.

### 배포 전 확인

병합과 배포가 분리돼 있으므로 **"병합했는데 배포를 깜빡"** 해서 운영이 main보다 뒤처질 수 있다.
GHCR에서 `:main`과 `:deployed`가 같은 다이제스트인지 보면 바로 안다.

---

## 롤백하기

**Release 워크플로 → target에 `rollback` → 실행.** 그게 전부다. 무엇으로 되돌릴지 고르지 않는다 —
`:previous`가 직전 정상 이미지를 기억하고 있다.

성공하면 `:deployed`와 `:previous`가 맞바뀐다.

### 알아둘 것 네 가지

1. **첫 롤백은 못 쓴다.** `:previous`는 두 번째 프로덕션 배포부터 생긴다. 없으면 실패한다
2. **롤백은 토글이다.** 두 번 누르면 방금 되돌린 이미지로 다시 돌아간다. **두 단계 전으로 가는 수단은
   없다**
3. **롤백한 뒤에 `application`을 누르면 사고 낸 이미지가 다시 나간다.** 롤백은 `:main`을 바꾸지 않기
   때문이다. revert PR을 병합해 `:main`이 갱신된 뒤에 배포한다
4. **롤백은 애플리케이션 이미지만 되돌린다.** 서버는 배포할 때마다 `git reset --hard origin/main`으로
   스크립트를 가져오므로 `nginx.conf`·`docker-compose.yml`·배포 스크립트는 최신이 유지된다. 인프라
   변경을 되돌리려면 revert PR이 필요하다

**DB 마이그레이션이 끼어 있으면 이미지 롤백만으로는 부족하다.** `docs/rules/process.md`의
"DB 마이그레이션 규칙"(하위호환 추가만, 선-머지)을 지켜야 롤백이 안전하다. 이 규칙이 지켜지는 한
스키마는 항상 구버전 코드와 호환된다.

---

## Flyway `V1__create_schema.sql`을 수정했을 때

이 프로젝트는 새 버전 파일을 추가하지 않고 `V1`을 계속 고쳐쓰고 있다(이유는 `docs/decisions.md`).

`spring.flyway.validate-on-migrate`가 기본값 `true`이므로, **이미 `V1`이 적용된 운영 DB에서는
checksum 불일치로 앱이 기동하지 않는다.**

대응 순서:

1. 운영 DB의 `flyway_schema_history`에서 해당 버전 행의 checksum을 새 파일 기준으로 갱신하거나,
   그 행을 삭제하고 `baseline`을 다시 잡는다
2. 스키마 자체의 실제 변경분은 **별도로 DB에 반영**해야 한다. `V1` 파일을 고친다고 이미 만들어진
   테이블이 바뀌지는 않는다
3. 배포 후 `/actuator/health`로 기동을 확인한다

**릴리즈 브랜치 운용을 시작하면 이 방식을 끝내고 정식 버전 분리로 전환한다**(`docs/decisions.md`).

---

## 헬스체크

배포 직후 `infra/scripts/health-check.sh`가 `/actuator/health`를 폴링한다.

컨테이너가 `restart: always`라 크래시해도 즉시 재시작되어 "Exited" 상태를 잡기 어렵다. 그래서
`docker inspect`의 `RestartCount`가 3 이상이면 크래시 루프로 간주하고 타임아웃(10분) 전에 조기 실패
처리한다. 실패하면 `docker logs`를 출력하므로 **서버에 SSH로 들어가지 않고 Actions 로그에서 원인을
확인할 수 있다.**

nginx는 `infra/scripts/nginx-health-check.sh`로 RestartCount만 확인한다(타임아웃 30초). nginx는
config 문법 오류면 즉시 기동 실패하는 이진적 실패 모드라 짧아도 충분하다.

---

## 이미지 정리

- **서버 로컬**: 배포 스크립트가 매번 "실행 중인 것 + 최근 2개"만 남기고 삭제한다.
  서버 디스크가 넉넉하지 않아 타이트하게 잡았다
- **GHCR**: 최근 20개 버전만 유지한다

GHCR 정리는 **태그가 붙어 있어도 오래된 것부터 지운다.** 병합과 배포가 분리돼 있어서 배포 없이 병합만
쌓이면 `:previous`가 가리키던 버전이 먼저 사라질 수 있다 — 그러면 사고 시점에 롤백이 불가능해진다.
20이라는 수치는 그 여유분이다. **배포 없이 application PR을 20건 넘게 쌓지 않는다.**

---

## 프론트 배포

프론트 레포에서 Release 워크플로를 실행하면 `market-monitor-assets:latest`가 갱신되고,
`repository_dispatch`로 이 레포의 `nginx` job이 트리거되어 **nginx 이미지를 다시 빌드하고 배포**한다.
이건 자동으로 유지한다.

> nginx job은 태그 존재 여부로 재빌드를 건너뛰던 로직이 있었는데, 그 태그가 프론트 자산의 변경을
> 반영하지 못해서 **프론트를 배포해도 운영에 안 나가는 버그**가 있었다. 지금은 매번 재빌드한다.
> 자세한 경위는 `docs/decisions.md`.

---

## 장애 시 확인 순서

1. `/actuator/health` — 앱이 떠 있는가
2. GitHub Actions의 마지막 배포 로그 — 헬스체크가 실패했다면 `docker logs`가 찍혀 있다
3. **ESCALATION 로그** — `EscalateException`으로 분류된 장애가 여기 쌓인다
4. 텔레그램 개발자 채널(`DEVELOPER_CHAT_ID`) — 에스컬레이션 알림이 갔는지
5. 수집 실패라면 `CollectionScheduler` 로그 — 수집기별 시작/종료와 소요 시간이 찍힌다

알림이 안 왔다고 장애가 없는 건 아니다. 텔레그램 자체가 죽으면 알림 경로도 같이 죽는다.

---

## 서버 접근

배포 스크립트는 `infra/scripts/`에 있고, 서버에서 매 배포마다
`git fetch && git reset --hard origin/<브랜치>`를 수행한다.

**이건 앱 코드를 가져오는 게 아니다.** 앱 코드는 Docker 이미지 안의 jar가 100% 결정한다. 이 체크아웃은
오직 배포 스크립트와 `docker-compose.yml`을 최신으로 유지하기 위한 것이다.
