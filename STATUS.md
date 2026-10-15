# 검증 상태 — 2026-10-15

설치 테스트용 APK 생성 완료. 전체 실기기 기능 검증 완료를 뜻하지 않습니다.

- 소스 커밋: `a54e717bab5adbd605dbbd134ddd7e37cd12dd26`
- CI: https://github.com/rkdvudcjs90-glitch/jarvis-android/actions/runs/38690971006
- Kotlin 컴파일 / 단위 테스트 / Android lint: 통과
- APK v2 서명 검사: 통과 (debug 서명)
- Android 16 API 36 x86_64 에뮬레이터: 설치·앱 시작·홀로그램 뷰 배치·설정 진입·Keystore AES-GCM 암호화 저장 및 복원 검사 통과
- 에뮬레이터 기본 검사는 실제 마이크, TTS, API, 접근성, ROOT를 검증하지 않음
- SM-F731N Android 16 실기기: 미실행
- 실제 OpenAI API 대화 / 한국어 음성 인식·TTS / 접근성 동작 / ROOT 승인·실행 / 접기·펼치기·발열: 미검증
- 참고 영상: 미제공

## 설치 파일

https://github.com/rkdvudcjs90-glitch/jarvis-android/releases/download/preview-2/JARVIS-0.1.0.apk

- 크기: 2,457,556 bytes
- SHA-256: `9412facecdad09b1ec1b4be3b50f0a90d5d1452fc6fc7d8eb338017902947221`
- 미리보기 릴리스: https://github.com/rkdvudcjs90-glitch/jarvis-android/releases/tag/preview-2
- debug APK로 배포 서명 키는 포함하지 않음. 이후 CI APK와 서명이 다르면 제거 후 재설치가 필요할 수 있음. 제거 시 저장된 키와 이력이 삭제됨.

## 수정한 오류

최초 SDK 준비 실패: setup-android 기본 패키지 중 폐기된 `tools` 패키지를 찾지 못함. `platform-tools`만 설치하도록 수정하고 전체 빌드 재실행 성공.

작업 환경의 프록시 오류로 로컬 빌드는 불가능하여, 사용자 GitHub 연결 후 GitHub Actions에서 빌드와 에뮬레이터 검사를 수행함.
