package ai.jarvis

import org.junit.Assert.*
import org.junit.Test

class RootPolicyTest {
    @Test fun noDestructiveOrUserSuppliedCommands() {
        assertEquals(setOf("id", "input keyevent 223", "reboot"), RootAction.entries.map { it.command }.toSet())
        RootAction.entries.forEach {
            assertFalse(it.command.contains(";"))
            assertFalse(it.command.contains("$"))
            assertFalse(it.command.contains("rm "))
        }
    }
    @Test fun rootDetectionRejectsNonRootAndPartialUidMatches() {
        val pattern=Regex("uid=0(?:\\D|$)")
        assertTrue(pattern.containsMatchIn("uid=0(root) gid=0(root)"))
        assertFalse(pattern.containsMatchIn("uid=2000(shell)"))
        assertFalse(pattern.containsMatchIn("uid=0123"))
    }
}
