# J.A.R.V.I.S. ROOT ADVANCED

현재 상태: 설치 테스트용 APK 생성 완료. Kotlin 컴파일·단위 테스트·lint·서명 검사 및 Android 16 에뮬레이터 기본 검사 통과. Galaxy Z Flip5 실기기와 실제 API·음성·접근성·ROOT 기능은 미검증.

[APK 바로 다운로드](https://github.com/rkdvudcjs90-glitch/jarvis-android/releases/download/preview-2/JARVIS-0.1.0.apk) · [검사 실행 결과](https://github.com/rkdvudcjs90-glitch/jarvis-android/actions/runs/38690971006)

## 설계 및 기능

- Kotlin 네이티브, OpenGL ES 2.0의 실제 3D 입자 구체 2,600개. 대기/듣기/생각/응답/오류에 따른 색과 회전·진폭 변화.
- Android SpeechRecognizer ko-KR, 설치된 한국어 TTS 엔진. 인식 입력 크기에 따른 진폭 변화. 응답 중 진폭은 합성 애니메이션이며 TTS PCM 분석이 아닙니다.
- OpenAI Chat Completions HTTPS 요청, 최근 20개 메시지 맥락. 모델 기본값 gpt-4.1-mini, 앱 설정에서 변경 가능.
- API 키 및 대화 이력은 Android Keystore AES-GCM으로 암호화. 백업 비활성화. API 키·대화를 로그로 기록하지 않음.
- API 키가 APK에 포함되지 않으며 사용자가 앱에서 입력. 앱이 키를 사용하는 동안 메모리에는 존재하므로 ROOT 기기나 감염 기기에서 완전한 비밀 보장은 불가능.
- 필요 시 마이크·알림 권한 요청, 앱 권한·접근성·TTS 설정으로 연결. 알림은 권한 승인 후 테스트 버튼을 다시 누르면 확인 가능.
- 화면 내용을 수집하지 않는 선택 접근성 서비스. 사용자 버튼으로만 홈·뒤로·최근 앱·화면 잠금 수행.
- 설정 화면을 통한 볼륨·Wi-Fi 조정. Android 제한을 우회하여 Wi-Fi를 직접 켜지 않음.
- 선택 ROOT: 명시적 활성화 + su 권한 확인 + 매 실행 확인. 고정 명령 id / input keyevent 223 / reboot만 허용. AI 응답과 입력을 셸에 전달하지 않음. 앱은 루팅을 수행하지 않음.

## 스마트폰 설치·연동 (APK 빌드 성공 후)

1. 제공 APK를 휴대폰에 다운로드하여 열고, 해당 브라우저/파일 앱의 ‘출처를 알 수 없는 앱 설치’를 허용합니다. 설치 후 다시 꺼도 됩니다. Samsung 자동 차단기나 조직 정책이 설치를 제한할 수 있습니다.
2. 앱의 설정·권한에서 본인의 OpenAI API 키를 입력합니다. OpenAI API 결제는 ChatGPT 구독과 별개입니다. 키를 채팅으로 보내지 마세요.
3. 음성 버튼을 누르고 마이크 권한을 허용합니다. 한국어 TTS가 없으면 앱의 TTS 설정에서 설치합니다.
4. 휴대폰 제어가 필요한 경우에만 설명을 읽고 접근성을 켭니다. 별도 설치 APK는 Android 앱 정보에서 ‘제한된 설정 허용’이 필요할 수 있습니다.
5. ROOT 사용자는 선택 모듈을 켜고 ROOT 확인을 누른 후 ROOT 관리자에서 승인합니다. ROOT가 없어도 대화·음성·일반 제어는 사용 가능합니다.

잠금 화면 상시 대기, 백그라운드 상시 청취, 호출어 ‘자비스’, 전화 발신, 메시지 전송, 앱 화면 자동 클릭, 알림 내용 수집은 구현 범위에 포함되지 않습니다. 홈·뒤로 동작은 열린 대화창부터 닫을 수 있습니다.

## 빌드

JDK 17, Gradle 8.13, Android SDK platform 36 / build-tools 36.0.0 필요.

```sh
gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

GitHub Actions `.github/workflows/android.yml`은 main push 또는 수동 실행에서 빌드·단위 테스트·lint·서명 검사를 수행합니다. 성공 시 `JARVIS-installable-debug-apk` 아티팩트에 APK와 SHA-256 파일을 업로드합니다. 스마트폰 브라우저에서 GitHub 로그인 후 Actions 실행 결과의 아티팩트를 다운로드하고 ZIP에서 APK를 꺼내 설치할 수 있습니다.

현재 CI는 설치 테스트용 debug APK입니다. 프로덕션 서명 키는 포함하지 않습니다. CI 실행마다 debug 서명 키가 달라질 수 있어 후속 APK 업데이트 시 제거 후 재설치가 필요할 수 있으며, 제거 시 키와 이력이 삭제됩니다. 배포용 지속 서명과 Play 스토어 배포는 별도 구성 필요.

## Z Flip5 / Android 16 실기기 체크리스트 (전부 미검증)

- 설치·실행, 화면 가장자리/키보드 여백, 접기·펼치기와 회전. 커버 디스플레이 전용 지원은 미구현.
- UI의 3D 입자 깊이와 다섯 상태 색상, 장시간 발열·프레임 속도.
- 마이크 승인/거부/영구 거부, 한국어 발화·무음·네트워크 단절.
- TTS 한국어 데이터 설치/미설치, 음성 중지, 홈 이동 시 녹음·TTS 중단.
- 유효/무효 API 키, API 결제 한도, 모델 오류, 대화 맥락, 앱 재시작 후 암호화 이력 복원.
- 접근성 거부·승인·해제, 홈/뒤로/최근 앱/화면 잠금.
- 알림 승인·거부 및 테스트.
- su 없는 기기, ROOT 거부/시간초과/승인. ROOT OFF에서 고급 버튼 숨김, 확인 취소 시 실행되지 않음.
- 재부팅은 작업을 저장하고 기기 소유자가 직접 최종 확인 후 테스트.

참고 영상이 제공되지 않아 영상과의 시각적 유사성은 검증할 수 없습니다. 실제 음성 인식·TTS 품질은 기기 서비스에 의존합니다. 기기 제어는 AI 자동 실행이 아닌 사용자 버튼 조작입니다.
