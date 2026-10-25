# 휴대폰에서 공동 개발 시작하기

자비스 앱에 Claude API 키를 추가하는 작업이 아니다. 개발 도구 두 개가 같은 저장소에서 작업하도록 연결한다.
사용자 PC를 자비스 실행 서버로 둘 필요는 없다.

## 사용자가 한 번 해 줄 연결
1. https://claude.ai/code 에 로그인한다. 계정에서 Claude Code 사용이 가능한지 확인한다.
2. GitHub 연결 절차에서 `rkdvudcjs90-glitch/jarvis-android` 접근을 허용한다. 가능하면 필요한 저장소만 선택한다.
3. 프로젝트 저장소를 선택하고 준비 브랜치 `codex/claude-codex-setup`을 선택한다. main에는 아직 준비 문서가 없다.
4. 아래 지시를 첫 작업으로 보낸다. 브랜치 선택 기능이 없으면 같은 저장소의 클라우드 터미널에서 해당 브랜치를 checkout한 후 진행한다.
5. Claude가 보고서를 커밋/브랜치로 남기면 그 링크를 Codex에 전달한다. 아직 자동 인수인계 서비스는 구성하지 않았다.

## Claude Code에 보낼 첫 지시

```text
저장소 rkdvudcjs90-glitch/jarvis-android의 codex/claude-codex-setup 브랜치를 기준으로 시작해.
작업 전 브랜치와 커밋을 확인해. AGENTS.md, CLAUDE.md, docs/team/BASELINES.md,
docs/team/WORKFLOW.md, docs/team/TASK-001.md를 읽고 TASK-001만 수행해.
이번 작업은 읽기 중심 감사와 보고서 작성이야. APK 기능을 무작정 다시 만들지 마.
현재 설치 앱과 GitHub 코드의 식별자가 다르다는 점을 먼저 확인해.
유료 API 호출이나 개인 키 요청 없이 오류 경로를 분석하고 최소 수정안을 제안해.
결과는 docs/team/audits/TASK-001-claude.md에 남기고 작업 브랜치/커밋을 알려줘.
실행하지 않은 검사나 Codex 독립 검토를 완료로 표시하지 마.
```

## Codex에 인계할 때
- Claude 작업 브랜치/커밋 또는 PR 링크와 TASK-001 보고서 경로를 전달한다.
- Codex는 그 커밋을 확인하고 독립 검토한 후 첫 수정 작업의 범위를 정한다.
- 이 ChatGPT의 GitHub 연결은 확인됐지만 현재 로컬 CLI 인증은 별개다. 실패한 CLI 인증을 연결 완료로 간주하지 않는다.

## 현재 상태
- 공통 규칙·Claude 지시 파일·작업 인수인계 문서: 준비함.
- 이 준비 브랜치: 기존 main의 앱 기능을 보존함.
- Claude 로그인·GitHub 권한 승인: 사용자 계정에서 수행 필요.
- Claude 첫 감사 실행 / 두 도구의 실제 교차 검토: 아직 미실행.
- 저장소 공개 범위는 기존 public 그대로다. 개인 대화·키·음성·위치를 올리지 않는다.

공식 문서:
- https://code.claude.com/docs/en/claude-code-on-the-web
- https://code.claude.com/docs/en/memory
- https://developers.openai.com/codex/cloud
