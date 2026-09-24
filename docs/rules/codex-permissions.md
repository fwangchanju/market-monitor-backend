# Codex 권한 제어

## 목적과 현재 적용 범위

이 문서는 `.claude/settings.json`의 거부·승인 규칙을 Codex에서 어떻게 재현할지 정한다. 저장소의 `AGENTS.md`에는 에이전트의 행동 규칙을 적었다. `.codex/config.toml`과 `.codex/rules/market-monitor.rules`를 저장소에 추가했다. 이 프로젝트를 신뢰한 새 Codex 세션에서 로드된다. Codex CLI가 이 환경의 PATH에 없어 규칙 로딩과 `execpolicy check`를 실제 실행하지 못했다.

| Claude 규칙 | Codex에서 사용할 제어 | 범위 |
|---|---|---|
| PR 병합, `gh workflow run` 거부 | `AGENTS.md` + `.codex/rules/*.rules`의 `forbidden` | 규칙 파일은 샌드박스 밖 셸 실행에 적용. GitHub MCP·앱은 별도 도구 권한 또는 훅 필요 |
| `.env` 읽기·쓰기 거부 | `AGENTS.md` + 파일시스템 permission profile의 `deny` | 프로필은 로컬 샌드박스 명령에 적용. MCP·승인된 샌드박스 밖 실행은 별도 제어 필요 |
| commit·push·PR 생성·수정 전 승인 | `AGENTS.md` + `.rules`의 `prompt` + `approval_policy = "on-request"` | `.rules`의 프롬프트는 샌드박스 밖 명령에만 적용. MCP는 별도 승인 흐름 필요 |

`AGENTS.md`는 지시문이지 파일·도구 접근을 막는 보안 경계가 아니다. 특히 Claude의 `Read(infra/.env)`와 `mcp__github__merge_pull_request` 거부를 `AGENTS.md`만으로 동일하게 강제했다고 간주하면 안 된다.

## 프로젝트 Codex 설정

Codex 0.138.0 이상에서 permission profile을 사용할 수 있는 환경이라면, 저장소의 `.codex/config.toml`에 다음 설정이 적용된다. 활성 설정의 어느 계층에든 `sandbox_mode` 또는 `[sandbox_workspace_write]`가 있거나 `--sandbox`가 지정되면 이 프로필 대신 기존 샌드박스가 적용된다. 프로젝트 `.codex` 계층은 신뢰된 프로젝트에서만 로드된다.

```toml
approval_policy = "on-request"
default_permissions = "market-monitor"

[permissions.market-monitor]
description = "Workspace editing without environment-file access"
extends = ":workspace"

[permissions.market-monitor.filesystem]
glob_scan_max_depth = 6

[permissions.market-monitor.filesystem.":workspace_roots"]
"**/*.env" = "deny"
"**/*.env.*" = "deny"
```

위 패턴은 `infra/.env`를 포함한 환경 파일을 대상으로 한다. 실제 저장소 깊이와 Codex 버전에 맞춰 시작 시 규칙이 로드되는지 확인한다. 키를 이용한 테스트가 필요한 경우 Codex에게 파일 읽기 권한을 주는 대신 사용자가 실행 환경에 값을 주입한다.

## 셸 명령 규칙

신뢰된 프로젝트의 `.codex/rules/market-monitor.rules`에 아래 규칙을 추가했다. `.rules`는 인수 접두사를 비교하므로 `gh -R owner/repo pr merge` 같은 변형은 별도 규칙이나 훅으로 막아야 한다. `git commit`과 `push`는 샌드박스 밖 실행 요청에서 승인 대상으로 만든다.

```python
prefix_rule(pattern = ["gh", "pr", "merge"], decision = "forbidden", justification = "PR 병합은 사용자가 한다")
prefix_rule(pattern = ["gh", "workflow", "run"], decision = "forbidden", justification = "워크플로 수동 실행은 사용자가 한다")
prefix_rule(pattern = ["git", "commit"], decision = "prompt", justification = "커밋 전에 사용자 승인이 필요하다")
prefix_rule(pattern = ["git", "push"], decision = "prompt", justification = "푸시 전에 사용자 승인이 필요하다")
prefix_rule(pattern = ["gh", "pr", "create"], decision = "prompt", justification = "PR 생성 전에 사용자 승인이 필요하다")
prefix_rule(pattern = ["gh", "pr", "edit"], decision = "prompt", justification = "PR 수정 전에 사용자 승인이 필요하다")
```

설치 후 Codex를 다시 시작하고 `codex execpolicy check --rules .codex/rules/market-monitor.rules -- gh pr merge 123` 같은 명령으로 각 규칙을 검사한다. `forbidden`/`prompt` 결과와 `gh -R`처럼 규칙이 놓치는 변형을 모두 확인한다. `prompt`는 승인 요청을 만들 수 있는 실행 경로에 대한 규칙이지, 모든 도구 호출에서 자동으로 확인 창을 띄우는 전역 설정이 아니다.

## GitHub MCP·앱과 강제 경계

GitHub MCP·앱 도구는 셸 `.rules`와 로컬 파일 권한 프로필의 대상이 아니다. `AGENTS.md`를 행동 규칙으로 적용하고, 실제 차단이 필요하면 병합·워크플로 도구를 연결 설정에서 빼거나 관리형 `PreToolUse` 훅/도구 권한으로 차단한다. 프로젝트 훅은 신뢰 심사를 거치며 일부 도구 경로가 훅을 우회할 수 있으므로, 훅만으로 완전한 경계라고 가정하지 않는다. PR 생성·수정의 경우 도구 승인 정책에서 별도 승인 대상으로 설정한다.

이 환경의 Codex는 실행 주체가 제공한 샌드박스 권한도 받는다. 저장소 문서와 프로젝트 설정은 그 상위 권한을 대신하지 못한다. 특히 사용자가 따로 허용한 샌드박스 밖 실행이나 다른 커넥터까지 이 예시가 일괄 차단하지는 않는다.

## 근거

- [Codex AGENTS.md](https://learn.chatgpt.com/docs/agent-configuration/agents-md)
- [Codex Rules](https://learn.chatgpt.com/docs/agent-configuration/rules)
- [Codex Permissions](https://learn.chatgpt.com/docs/permissions)
- [Codex Hooks](https://learn.chatgpt.com/docs/hooks)
