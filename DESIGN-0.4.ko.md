# 확정 디자인 0.4 — 입체 입자 네트워크 구체

사용자가 선택한 후보는 검은 배경, 옅은 청백색, 가는 연결선과 작은 발광 입자, 외곽이 밝고 내부가 어두운 입체 구체입니다. 기존의 중앙 코어와 기계식 동심 링을 이 디자인으로 교체합니다.

## 구현

- OpenGL ES 2.0, 실제 입체 좌표를 가진 구체 노드 약 1,100개와 외곽 입자 100개.
- 가까운 노드를 연결하는 선, 투명한 앞뒤 레이어, 외곽 조명, 노드 주변의 약한 빛 번짐.
- 셰이더에서 입자 위치를 변형하고 전체 구체를 회전합니다. 정지 그림이나 GIF를 붙여 재생하지 않습니다.
- 기본 색은 청백색. 듣기·생각·음성 응답·오류 상태에 따라 색상과 움직임을 바꿉니다. 답변 중 진폭은 합성 애니메이션이며 실제 TTS 파형 분석은 아닙니다.
- 형상과 연결선은 렌더러 시작 시 한 번 생성하고, 매 프레임 전체 연결선을 재계산하지 않습니다.
- 음성·API·권한·제어 로직은 이번 디자인 변경 범위에서 수정하지 않습니다.

## 설치 및 검증

0.2 preview-4 및 0.3 preview-5와 같은 CI 테스트 서명 키로 빌드합니다. 기존 앱을 삭제하지 않고 업데이트할 수 있도록 구성합니다.

[설치 APK](https://github.com/rkdvudcjs90-glitch/jarvis-android/releases/download/preview-8/JARVIS-0.4.0.apk) · [실행 화면](https://github.com/rkdvudcjs90-glitch/jarvis-android/releases/download/preview-8/hologram.png) · [움직임 미리보기](https://github.com/rkdvudcjs90-glitch/jarvis-android/releases/download/preview-8/design-preview.mp4)

- GitHub Actions 실행 38769374590: 빌드, 단위 검사, lint, APK 서명 검증 통과.
- Android 16 에뮬레이터: 앱 실행, 실제 구체 표시, 시간에 따른 픽셀 변화, GL 오류 없음, 설정 펼치기, Keystore 암호화 왕복, 한국어 네이티브 모델 로드 및 무음 디코딩 통과.
- 미리보기는 에뮬레이터에서 캡처한 실제 화면 8장을 5fps로 인코딩한 자료이며 기기 성능 측정 영상이 아닙니다.
- APK SHA-256: `dc4a09472a715f941cad747f995fab16b2b7094f340b6405fd6b68bfa7e3b19a`.

Galaxy Z Flip5에서의 화면 품질·발열·프레임 속도와 장시간 화면 꺼짐 호출은 별도 실기기 검증이 필요합니다. 이 버전은 디자인 업데이트이며 전체 자동화 완성을 뜻하지 않습니다.

원본 GIF와 타인의 영상 파일은 APK나 공개 소스에 포함하지 않습니다. 참고한 시각 특징을 바탕으로 작성한 독자적 실시간 렌더러입니다.
