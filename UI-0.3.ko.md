# 참고 디자인을 반영한 UI 0.3

- 실제 OpenGL ES 셰이더로 중앙 발광 코어, 회전 아크, 방사형 눈금, 여러 가닥의 외곽 파동을 렌더링합니다.
- 기존 3D 입자 구체는 배경층으로 유지합니다. 참고 사진을 화면에 붙인 정지 이미지가 아닙니다.
- 대기·듣기·생각·응답·오류 상태에 따라 색상과 움직임이 바뀝니다.
- 주 화면에는 큰 홀로그램과 최근 답변을 표시합니다. 입력과 상세 조작은 ‘설정·조작’을 눌러 펼칩니다.
- 상시 음성 서비스는 0.2와 같은 구조이며 실제 호출 인식률·장시간 대기·새 음성 명령 동작 검증은 별도로 필요합니다.

## 설치

0.2 preview-4와 같은 CI 캐시의 서명 키를 사용합니다. 기존 앱을 삭제하지 않고 새 APK를 열어 업데이트하세요. 업데이트에 실패하면 데이터를 보존하기 위해 삭제하지 말고 오류 화면을 확인하세요.

## 검증

[APK 다운로드](https://github.com/rkdvudcjs90-glitch/jarvis-android/releases/download/preview-5/JARVIS-0.3.0.apk) · [실제 에뮬레이터 화면](https://github.com/rkdvudcjs90-glitch/jarvis-android/releases/download/preview-5/hologram.png)

[빌드 실행](https://github.com/rkdvudcjs90-glitch/jarvis-android/actions/runs/38737914015)

- 소스 커밋: `de3f343bde5dff5d423c3aad002150fe973b4421`
- 컴파일·단위 테스트·lint·APK 서명 검사 통과.
- Android 16 에뮬레이터 앱 실행·네온 코어 픽셀 밝기 검사·레이아웃·패널 확장·설정 진입 검사 통과.
- 암호화 저장·한국어 네이티브 모델 로딩·무음 디코딩 검사 통과.
- APK 크기 135,309,663 bytes, SHA-256 `81cacab7896cf537642b300ef6fe48aaee27baae143add7042527ccb91f71a4f`.
- 빌드 로그에서 `jarvis-debug-signing-v2` 캐시 복원 확인. 0.2 preview-4와 동일한 테스트 서명 키 사용.

 Galaxy Z Flip5의 밝기·발열·프레임 속도와 참고 사진의 완전한 시각적 일치까지 검증한 것은 아닙니다.
