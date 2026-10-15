# 0.5.1 — 상단 명칭 제거

- 메인 화면의 J.A.R.V.I.S. ACTIVE/STANDBY 이름 헤더를 제거했습니다. 상태 및 최근 응답 안내와 입자 구체는 유지합니다.
- 구체를 길게 누르면 설정·조작 패널을 열고 닫습니다. 접근성에도 긴 누르기 동작과 설명을 제공합니다.
- 음성 호출, 자동 시작, 12초 무응답 후 호출 대기로 복귀하는 로직은 변경하지 않았습니다.
- 기존 앱을 삭제하지 않고 업데이트하세요. API 키와 대화 저장 형식은 변경하지 않았습니다.

[설치 APK](https://github.com/rkdvudcjs90-glitch/jarvis-android/releases/download/preview-11/JARVIS-0.5.1.apk) · [실행 화면](https://github.com/rkdvudcjs90-glitch/jarvis-android/releases/download/preview-11/hologram.png)

Actions 38820982715에서 컴파일·단위 검사·lint·서명 검증 및 Android 16 에뮬레이터 검사를 통과했습니다. 이름 헤더 및 표시 버튼 없음, 실제 구체 표시와 움직임, GL 오류 없음, 구체 긴 누르기로 설정 패널 열기, 권한 부여 후 마이크 foreground service 자동 시작, 대기 비활성화 설정의 재실행 유지, Keystore 암호화 왕복 및 한국어 네이티브 모델 로드·무음 디코딩을 확인했습니다. 배포된 실행 캡처에서도 상단 명칭 제거를 확인했습니다.

APK SHA-256: `f8d7700c8300a593213bfbc98ea4f7e58453fa802bb1b93eee91f416af3ce82f`.

실제 호출 정확도·장시간 화면 꺼짐 대기·실제 음성 및 API 명령은 이번 에뮬레이터 검사 범위에 포함되지 않습니다.
