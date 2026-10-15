package ai.jarvis

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import java.util.concurrent.TimeUnit

class ControlService : AccessibilityService() {
    companion object { @Volatile var current: ControlService? = null; private set }
    override fun onServiceConnected() { current = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onDestroy() { current = null; super.onDestroy() }
}

enum class RootAction(val label: String, val command: String) {
    CHECK("ROOT 확인", "id"), LOCK("화면 끄기", "input keyevent 223"), REBOOT("기기 재부팅", "reboot")
}

object RootController {
    // Only enum constants reach su. AI output and user text never become shell commands.
    fun run(action: RootAction): String {
        val process = ProcessBuilder("su", "-c", action.command).redirectErrorStream(true).start()
        val output = StringBuilder()
        val reader = Thread {
            process.inputStream.bufferedReader().use { input ->
                val buffer = CharArray(256)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    synchronized(output) { if (output.length < 4096) output.append(buffer, 0, minOf(count, 4096 - output.length)) }
                }
            }
        }.apply { isDaemon = true; start() }
        if (!process.waitFor(20, TimeUnit.SECONDS)) { process.destroyForcibly(); error("ROOT 승인 시간 초과. 다시 시도하세요.") }
        reader.join(1000)
        val result = synchronized(output) { output.toString() }
        check(process.exitValue() == 0) { "ROOT 승인 거부 또는 명령 실패" }
        if (action == RootAction.CHECK) check(Regex("uid=0(?:\\D|$)").containsMatchIn(result)) { "ROOT 권한이 없습니다." }
        return if (action == RootAction.CHECK) "ROOT 권한 확인됨" else "명령 전달 완료 · 기기에서 결과를 확인하세요."
    }
}
