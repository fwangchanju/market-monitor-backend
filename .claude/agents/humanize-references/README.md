# humanize 에이전트

한글 글에서 AI가 쓴 티가 나는 표현을 걷어내는 에이전트 3종과 그 룰북이다.
출처는 [epoko77-ai/im-not-ai](https://github.com/epoko77-ai/im-not-ai) (MIT).

## 왜 이 레포에 두나

이 프로젝트는 문서와 PR 설명을 사람이 아니라 모델이 쓴다. 그래서 코드는 리뷰로 걸러도
글은 걸러지지 않은 채 쌓인다. 실제로 `docs/decisions.md`는 세 줄에 한 번꼴로 볼드가
들어가 있었고, 문장 끝 쉼표 빈도는 일반적인 한국어 산문의 표준편차 3배였다.

플러그인으로 설치하면 각자 로컬에만 깔려서 원격 세션에는 오지 않는다. 원격 컨테이너는
매번 레포를 새로 클론하므로, 레포 안에 있어야 어느 세션에서든 쓸 수 있다.

판정 기준은 `docs/rules/writing.md`에 있다. 룰북은 일반적인 한국어 산문 기준이고,
그 문서는 이 프로젝트에서 지킬 것만 추린 것이다. 둘이 어긋나면 `writing.md`를 따른다.

## 쓰는 법

윤문이 필요하면 `humanize-monolith` 에이전트를 부르고 룰북 경로를 절대경로로 넘긴다.

```
quick_rules_path: <레포경로>/.claude/agents/humanize-references/quick-rules.md
```

진단만 따로 돌릴 때는 `humanize-diagnostician`에 `taxonomy_path`로
`diagnosis-rules.md`를 넘긴다. 경로를 안 주면 에이전트가 cwd 기준으로 찾다 실패한다.

원본 저장소의 나머지 구성요소는 가져오지 않았다.

- 에이전트 9종 중 3종만 가져왔다. 나머지는 룰북 자체를 유지보수하는 개발 도구이고,
  설명 매칭으로 엉뚱한 작업에 끼어들 수 있다. 원본 `install.sh`도 기본 설치에서 뺀다
- `scripts/`의 측정 스크립트는 뺐다. 문서가 마크다운이라 산문 전제의 점수가 잘 맞지 않는다
- `ai-tell-taxonomy.md`(931줄)는 뺐다. 진단에는 슬림 인덱스인 `diagnosis-rules.md`면 된다
