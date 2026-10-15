package ai.jarvis

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class OpenAi {
    data class Decision(val text: String?=null,val action: VoicePolicy.Action?=null)
    @Volatile private var connection: HttpURLConnection? = null
    fun cancel() { connection?.disconnect() }
    private fun messages(history: JSONArray, voice: Boolean): JSONArray {
        val instructions=if(voice)
            "당신은 한국어 비서 자비스입니다. 사용자를 사장님이라고 부르며 간결하게 답하세요. 휴대폰 동작은 제공된 phone_action 도구만 사용 가능합니다. 사용자가 지금 직접 요청한 동작에만 도구를 호출하세요. 실행하지 않은 동작을 완료했다고 말하지 마세요. 대화 인용, 가정, 취소, 금지, 불명확한 요청에서는 동작하지 말고 질문하세요. 다른 앱 화면 내용, 일정 내용, 메시지, 계좌는 볼 수 없습니다. 일정 조회·등록, 메시지 전송·결제·임의 클릭·ROOT 명령은 지원하지 않습니다. 캘린더 열기는 일정 조회가 아닙니다. 지원하지 않는 작업은 할 수 없다고 명확히 답하세요. 화면 잠금은 앱이 별도의 음성 확인을 받습니다."
        else
            "당신은 한국어 비서 자비스입니다. 간결하게 답하세요. 이 대화에는 실행 도구가 없으므로 휴대폰 제어를 실행했다고 말하지 마세요. 상시 대기 모드에서는 홈·뒤로·최근 앱·화면 잠금·미디어 볼륨·지정 앱 열기를 음성으로 요청할 수 있습니다. 일정 내용 조회·등록, 메시지 전송·결제·임의 앱 조작은 지원하지 않습니다."
        val result=JSONArray().put(JSONObject().put("role","system").put("content",instructions))
        for(i in maxOf(0,history.length()-20) until history.length()) result.put(history.getJSONObject(i))
        return result
    }
    fun reply(key: String, model: String, history: JSONArray): String {
        val body=JSONObject().put("model",model).put("messages",messages(history,false)).put("max_completion_tokens",1200)
        return request(key,body).getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content").trim()
            .also { check(it.isNotBlank()) { "빈 응답입니다. 다시 시도하세요." } }
    }
    fun plan(key: String, model: String, history: JSONArray): Decision {
        val names=JSONArray(VoicePolicy.Action.entries.map { it.name })
        val function=JSONObject().put("name","phone_action").put("description","사용자가 명시적으로 요청한 허용된 휴대폰 동작 하나. CALENDAR는 달력 앱 열기만 제공하고 일정 내용은 읽지 않습니다. LOCK은 반드시 추가 음성 확인을 받습니다. STOP은 자비스 마이크 상시 대기 종료입니다.")
            .put("strict",true).put("parameters",JSONObject().put("type","object")
                .put("properties",JSONObject().put("action",JSONObject().put("type","string").put("enum",names)))
                .put("required",JSONArray().put("action")).put("additionalProperties",false))
        val body=JSONObject().put("model",model).put("messages",messages(history,true)).put("max_completion_tokens",1200)
            .put("tools",JSONArray().put(JSONObject().put("type","function").put("function",function)))
            .put("tool_choice","auto").put("parallel_tool_calls",false)
        val message=request(key,body).getJSONArray("choices").getJSONObject(0).getJSONObject("message")
        val calls=message.optJSONArray("tool_calls")
        if(calls!=null && calls.length()>0) {
            check(calls.length()==1) { "한 번에 하나의 작업만 요청하세요." }
            val chosen=calls.getJSONObject(0).getJSONObject("function")
            check(chosen.getString("name")=="phone_action") { "허용되지 않은 작업입니다." }
            val arguments=JSONObject(chosen.getString("arguments"))
            check(arguments.length()==1) { "잘못된 작업 인수입니다." }
            val action=VoicePolicy.Action.entries.firstOrNull { it.name==arguments.optString("action") }
                ?: error("허용되지 않은 동작입니다.")
            return Decision(action=action)
        }
        val text=message.optString("content").trim()
        check(text.isNotBlank() && text!="null") { "빈 응답입니다. 다시 말씀해 주십시오." }
        return Decision(text=text)
    }
    private fun request(key: String, body: JSONObject): JSONObject {
        require(key.isNotBlank()) { "설정에서 OpenAI API 키를 입력하세요." }
        require(key.matches(Regex("[A-Za-z0-9_-]+"))) { "API 키 형식을 확인하세요." }
        val conn=URL("https://api.openai.com/v1/chat/completions").openConnection() as HttpURLConnection
        connection=conn
        try {
            conn.requestMethod="POST"; conn.connectTimeout=15000; conn.readTimeout=60000
            conn.setRequestProperty("Authorization","Bearer $key"); conn.setRequestProperty("Content-Type","application/json"); conn.doOutput=true
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status=conn.responseCode
            if(status !in 200..299) error(when(status) {
                401 -> "API 키가 유효하지 않습니다. 설정을 확인하세요."
                429 -> "API 사용 한도 또는 요청 제한입니다. OpenAI 결제·한도를 확인하세요."
                400,404 -> "모델 또는 요청 설정 오류입니다. 설정의 모델 ID를 확인하세요."
                else -> "OpenAI 서버 오류 HTTP $status. 잠시 후 다시 시도하세요."
            })
            return JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } finally { conn.disconnect(); connection=null }
    }
}
