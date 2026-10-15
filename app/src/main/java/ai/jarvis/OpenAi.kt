package ai.jarvis

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class OpenAi {
    @Volatile private var connection: HttpURLConnection? = null
    fun cancel() { connection?.disconnect() }
    fun reply(key: String, model: String, history: JSONArray): String {
        require(key.isNotBlank()) { "설정에서 OpenAI API 키를 입력하세요." }
        val messages = JSONArray().put(JSONObject().put("role", "system").put("content",
            "당신은 한국어 비서 자비스입니다. 간결하고 자연스러운 한국어로 답하세요. 앱은 별도 로컬 음성 명령으로 홈, 뒤로, 최근 앱, 화면 잠금, 미디어 볼륨, 유튜브·카메라·캘린더·와이파이 설정 열기만 지원합니다. 당신에게 전달된 요청에는 실행 도구가 없으므로 실제로 실행했다고 말하지 마세요. 지원하는 명령 문구를 짧게 안내하세요. 캘린더 내용 조회·등록, 메시지 전송, 결제, 임의 앱 자동 조작은 지원하지 않습니다. 실행하지 않은 동작을 실행했다고 말하지 마세요."))
        for (i in maxOf(0, history.length() - 20) until history.length()) messages.put(history.getJSONObject(i))
        val body = JSONObject().put("model", model).put("messages", messages).put("max_completion_tokens", 1200)
        val conn = URL("https://api.openai.com/v1/chat/completions").openConnection() as HttpURLConnection
        connection = conn
        try {
            conn.requestMethod = "POST"; conn.connectTimeout = 15000; conn.readTimeout = 60000
            conn.setRequestProperty("Authorization", "Bearer $key")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = conn.responseCode
            if (status !in 200..299) error(when(status) {
                401 -> "API 키가 유효하지 않습니다. 설정을 확인하세요."
                429 -> "API 사용 한도 또는 요청 제한입니다. OpenAI 결제·한도를 확인하세요."
                400, 404 -> "모델 또는 요청 설정 오류입니다. 설정의 모델 ID를 확인하세요."
                else -> "OpenAI 서버 오류 HTTP $status. 잠시 후 다시 시도하세요."
            })
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            return JSONObject(text).getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content").trim()
                .also { check(it.isNotEmpty()) { "빈 응답입니다. 다시 시도하세요." } }
        } finally { conn.disconnect(); connection = null }
    }
}
