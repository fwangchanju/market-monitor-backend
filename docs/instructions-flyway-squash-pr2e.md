# 지시서 — Flyway 마이그레이션을 V1 하나로 합친다 (구획 1, PR 2e)

이 파일 하나만 읽고 작업할 수 있게 썼다. V2(집계 테이블 삭제)·V3(custom_* 이름 변경)를 V1에 녹여 넣고
V2·V3 파일을 지운다. 다음 작업(가입·로그인)이 새 V2부터 시작하게 하려는 것이다.

---

## 1. 무엇을 하나

| 파일 | 할 일 |
|---|---|
| `src/main/resources/flyway/V1__create_schema.sql` | `docs/instructions-flyway-squash-pr2e.V1.sql` 내용으로 **통째로 교체** |
| `src/main/resources/flyway/V2__drop_category_aggregate.sql` | 삭제 |
| `src/main/resources/flyway/V3__rename_custom_tables.sql` | 삭제 |
| `docs/instructions-flyway-squash-pr2e.V1.sql` | 교체에 쓴 뒤 삭제 (마지막 커밋) |
| `docs/backlog.md` | 설계 쪽이 미리 고쳐둔 변경이 작업 트리에 있다. `docs:` 커밋으로 그대로 포함한다. 더 고치지 않는다 |

**새 V1은 설계 쪽이 만들고 검증까지 끝냈다. 한 글자도 고치지 않는다.**
검증: 빈 PostgreSQL 17에 (a) 옛 V1→V2→V3 를 차례로, (b) 새 V1만 적용한 뒤 `pg_dump -s` 두 결과가
동일했다(pg_dump가 매번 바꾸는 `\restrict` 토큰 두 줄 제외). 옛 체인의 결과는 2026-09-25 운영 DB 덤프와도
테이블·컬럼·제약·인덱스·시퀀스가 전부 일치한다.

---

## 2. 하지 않는 것

- 새 V1 내용 수정 (포맷·주석·순서 포함)
- Java 코드, 엔티티, 설정 파일(`application*.properties`) 수정 — 스키마는 바뀌지 않는다
- 운영 DB 접속, SQL 실행 — 4절은 사용자 몫이다
- `docs/` 에서 1절에 적힌 것 외의 수정

---

## 3. 완료·검증

- `cmp docs/instructions-flyway-squash-pr2e.V1.sql src/main/resources/flyway/V1__create_schema.sql` 가 같다
  (교체 직후, 원본 파일 지우기 전에 확인)
- `ls src/main/resources/flyway` 에 `V1__create_schema.sql` 하나만 있다
- `./gradlew spotlessApply build` 통과
- PR 설명 맨 위에 **"★ 배포 전 서버에서 flyway repair + 이력 정리 필요 (지시서 4절). 이걸 빼먹고 배포하면
  앱이 뜨지 않는다"** 를 적는다

---

## 4. ★ 운영 반영 — 사용자 몫 (구현자는 하지 않는다)

이미 V1·V2·V3가 적용된 운영 DB는 V1 체크섬이 달라지고 V2·V3 파일이 사라져서, 그대로 배포하면
`validate-on-migrate` 에서 **앱이 기동하지 않는다.** 순서를 지킨다.

```
1. PR 병합
2. 서버:  ~/scripts/flyway-cli.sh repair     ← main 을 pull 한 뒤 새 V1 기준으로 체크섬 갱신, V2·V3는 '삭제됨' 표시
3. 서버:  psql 에서 V2·V3 이력 행 삭제          ← 아래 SQL. 남겨두면 다음 V2 추가 때 번호가 겹친다
4. 서버:  ~/scripts/flyway-cli.sh info       ← V1 하나만 Success 인지 확인. 결과를 설계 쪽에 전달
5. 배포 (application)
```

3번 SQL:

```sql
DELETE FROM flyway_schema_history WHERE version IN ('2', '3');
SELECT installed_rank, version, description, type, checksum, success FROM flyway_schema_history ORDER BY 1;
```

기대 결과: 1행, `version = 1`, `success = t`.

로컬 개발 DB가 있다면 같은 이유로 기동하지 않는다. 로컬은 DB를 지우고 새로 만드는 편이 간단하다.

---

## 5. 브랜치·커밋

- 브랜치: `claude/migrate/squash-flyway-v1`
- 커밋: 첫 커밋에 이 지시서·`.V1.sql`·`backlog.md` 포함(`docs:`) → `migrate:` V1 교체 + V2·V3 삭제 →
  마지막 커밋에서 이 지시서와 `.V1.sql` 만 삭제
- push 하고 PR 까지 올린다
