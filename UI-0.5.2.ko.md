# 0.5.2 — 상단 상태 글씨 제거

상단의 준비·듣기·응답·호출 대기 등 상태 안내를 구체 긴 누르기로 여는 조작 패널 안으로 옮겼습니다. 상태를 갱신해도 메인 화면 상단에 글씨가 다시 나타나지 않습니다. 서비스가 실행 중이면 알림에서도 상태를 확인할 수 있습니다. 구체 색과 움직임, 하단 최근 응답, 자동 음성 대기 로직은 유지합니다.

기존 앱을 삭제하지 않고 업데이트하세요. API 키와 대화 저장 형식은 변경하지 않았습니다.

[설치 APK](https://github.com/rkdvudcjs90-glitch/jarvis-android/releases/download/preview-12/JARVIS-0.5.2.apk) · [실행 화면](https://github.com/rkdvudcjs90-glitch/jarvis-android/releases/download/preview-12/hologram.png) · [움직임 미리보기](https://github.com/rkdvudcjs90-glitch/jarvis-android/releases/download/preview-12/design-preview.mp4)

Actions 38822439544에서 빌드·단위 검사·lint·서명 검증을 통과했습니다. 첫 에뮬레이터 실행에서는 화면·설정 캡처 후 종료/재실행 검사 중 Input dispatch timeout ANR이 발생했습니다. 원인은 확정하지 않았습니다. 같은 APK로 실패한 작업만 재실행한 결과 전체 검사를 통과했습니다.

재검사 통과 범위: 메인 상단 이름·상태 글씨 및 표시 버튼 없음, 실제 구체 표시와 움직임, GL 오류 없음, 구체 긴 누르기 설정, 권한 부여 후 마이크 foreground service 자동 시작, 대기 비활성화 선택의 재실행 유지, Keystore 암호화 왕복 및 한국어 네이티브 모델 로드·무음 디코딩. 실제 휴대폰의 장시간 음성 대기·호출 정확도는 이번 검사 범위가 아닙니다.

재실행 뒤 같은 이름의 과거 실패 아티팩트가 선택되어 자동 배포가 실패했습니다. 통과한 정확한 아티팩트 ID(12013099203)와 APK 아티팩트 ID(12014032020)를 지정한 별도 배포 실행 38824016165로 게시했습니다. 향후 워크플로도 이름 대신 업로드 작업의 아티팩트 ID 출력으로 연결했습니다.

APK SHA-256: `a54b35b8b62ebcf6aad61cae09462abd7f090ecb49a89f89f9dd949d9c7080d4`.

