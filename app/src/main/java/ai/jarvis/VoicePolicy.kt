package ai.jarvis

/** No arbitrary shell strings, app names or model-generated action arguments are executed. */
object VoicePolicy {
    enum class Action { HOME, BACK, RECENTS, LOCK, VOLUME_UP, VOLUME_DOWN, YOUTUBE, CAMERA, CALENDAR, WIFI, STOP }
    fun normalize(text: String) = text.lowercase().replace(Regex("[^가-힣a-z0-9]"), "")
    fun wake(text: String) = listOf("자비스", "자비쓰", "자비서", "jarvis").any { normalize(text).contains(it) }
    fun confirmed(text: String) = normalize(text) in setOf("네", "예", "응", "실행", "실행해", "실행해줘", "확인", "잠가", "잠가줘")
    fun cancelled(text: String) = normalize(text) in setOf("아니", "아니요", "취소", "취소해", "하지마", "그만", "됐어")
    fun action(text: String): Action? {
        val t=normalize(text).removePrefix("자비스")
        return when {
            t in setOf("대기종료", "상시대기종료", "자비스종료", "종료해", "종료해줘", "듣기종료") -> Action.STOP
            t in setOf("홈", "홈으로", "홈으로가", "홈화면으로가", "홈화면보여줘", "홈으로가줘") -> Action.HOME
            t in setOf("뒤로", "뒤로가", "뒤로가줘", "이전화면으로가") -> Action.BACK
            t in setOf("최근앱", "최근앱열어", "최근앱보여줘", "최근앱열어줘") -> Action.RECENTS
            t in setOf("화면잠가", "화면잠가줘", "화면잠금", "휴대폰잠가줘", "화면꺼", "화면꺼줘") -> Action.LOCK
            t in setOf("볼륨올려", "볼륨올려줘", "소리키워", "소리키워줘") -> Action.VOLUME_UP
            t in setOf("볼륨내려", "볼륨내려줘", "소리줄여", "소리줄여줘") -> Action.VOLUME_DOWN
            t in setOf("유튜브열어", "유튜브열어줘", "유튜브켜", "유튜브켜줘") -> Action.YOUTUBE
            t in setOf("카메라열어", "카메라열어줘", "카메라켜", "카메라켜줘") -> Action.CAMERA
            t in setOf("캘린더열어", "캘린더열어줘", "일정앱열어줘", "달력열어줘") -> Action.CALENDAR
            t in setOf("와이파이설정열어줘", "와이파이설정열어", "와이파이설정") -> Action.WIFI
            else -> null
        }
    }
}
