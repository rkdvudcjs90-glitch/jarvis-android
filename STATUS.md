# 검증 상태

- 소스·Manifest·CI 생성: 완료
- XML / CI 구문 및 보안 구조 정적 검사: 별도 static-checks.json 참조
- Kotlin 컴파일: 실행 불가 (SDK, Gradle, Kotlin compiler 미설치)
- 자동 단위 테스트 / Android lint: 미실행
- APK / 서명 검증: 미실행, APK 없음
- GitHub Actions 원격 실행: 미실행, GitHub 연결 필요
- 에뮬레이터 / SM-F731N Android 16: 미실행

차단 원인: SDK 다운로드와 Maven 접근 모두 curl exit 7, `Failed to connect to proxy port 8080` 발생. 추가 네트워크 권한 도구 호출은 사용자 중단 상태이며 완료되지 않음. 프록시를 우회하지 않음.
