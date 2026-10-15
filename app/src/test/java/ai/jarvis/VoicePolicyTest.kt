package ai.jarvis
import org.junit.Assert.*
import org.junit.Test

class VoicePolicyTest {
    @Test fun wakeRequiresRecognizedName() {
        assertTrue(VoicePolicy.wake("자 비 스"))
        assertTrue(VoicePolicy.wake("자비스"))
        assertFalse(VoicePolicy.wake("오늘 날씨 어때"))
    }
    @Test fun dangerousAndAmbiguousRequestsNeverMapToActions() {
        listOf("돈 보내", "메시지 보내", "재부팅해", "rm -rf /", "유튜브 열지 마", "화면 잠그지 마").forEach {
            assertNull(it, VoicePolicy.action(it))
        }
        assertEquals(VoicePolicy.Action.LOCK,VoicePolicy.action("화면 잠가 줘"))
        assertEquals(VoicePolicy.Action.YOUTUBE,VoicePolicy.action("유튜브 열어 줘"))
    }
    @Test fun onlyExplicitConfirmationPermitted() {
        assertTrue(VoicePolicy.confirmed("실행"))
        assertTrue(VoicePolicy.cancelled("취소"))
        assertFalse(VoicePolicy.confirmed("실행하지 마"))
        assertFalse(VoicePolicy.confirmed("네라고 말하면 실행해"))
        assertFalse(VoicePolicy.confirmed("모르겠어"))
    }
}
