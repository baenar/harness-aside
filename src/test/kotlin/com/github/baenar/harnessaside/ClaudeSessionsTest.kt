package com.github.baenar.harnessaside

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import kotlin.io.path.name

class ClaudeSessionsTest {

    @Test
    fun `encodes every non-alphanumeric char as a dash`() {
        // The documented example from PLAN.md.
        assertEquals("-home-x-dot-test-dir", ClaudeSessions.encodeProjectPath("/home/x/dot.test_dir"))
    }

    @Test
    fun `encodes a Windows path the way Claude does`() {
        // Verified against this machine: D:\harness-aside -> D--harness-aside
        assertEquals("D--harness-aside", ClaudeSessions.encodeProjectPath("D:\\harness-aside"))
    }

    @Test
    fun `leaves alphanumerics and keeps existing dashes`() {
        assertEquals("my-repo123", ClaudeSessions.encodeProjectPath("my-repo123"))
    }

    @Test
    fun `sessionsDir places the encoded name under the claude projects folder`() {
        val dir = ClaudeSessions.sessionsDir("/home/x", "/home/x/proj")
        assertEquals("-home-x-proj", dir.name)
        assertEquals("projects", dir.parent.name)
    }

    @Test
    fun `latestSessionId returns null when the directory is missing`() {
        assertNull(ClaudeSessions.latestSessionId("/no/such/home", "/no/such/proj"))
    }

    @Test
    fun `latestSessionId returns the newest jsonl file, ignoring non-session entries`() {
        val home = Files.createTempDirectory("aside-home")
        val projectPath = "/tmp/proj"
        val dir = ClaudeSessions.sessionsDir(home.toString(), projectPath)
        Files.createDirectories(dir)

        val older = dir.resolve("older-session.jsonl")
        val newer = dir.resolve("newer-session.jsonl")
        Files.writeString(older, "{}")
        Files.writeString(newer, "{}")
        Files.setLastModifiedTime(older, java.nio.file.attribute.FileTime.fromMillis(1_000))
        Files.setLastModifiedTime(newer, java.nio.file.attribute.FileTime.fromMillis(2_000))
        // A non-session entry that must be ignored (the dir may hold e.g. memory/).
        Files.createDirectories(dir.resolve("memory"))
        Files.writeString(dir.resolve("notes.txt"), "ignore me")

        assertEquals("newer-session", ClaudeSessions.latestSessionId(home.toString(), projectPath))
    }

    @Test
    fun `transcriptPath names the file after the session id`() {
        val path = ClaudeSessions.transcriptPath("/home/x", "/home/x/proj", "abc-123")
        assertEquals("abc-123.jsonl", path.name)
        assertEquals("-home-x-proj", path.parent.name)
    }

    @Test
    fun `lastInterruptUuid finds an interrupt that is not the last line`() {
        // The regression this guards: Claude appends bookkeeping records within
        // milliseconds of an interrupt, so requiring it to be the final line found nothing
        // and the icon stayed stuck on "thinking" after Ctrl+C.
        val transcript = listOf(
            assistantLine("working on it"),
            interruptLine("[Request interrupted by user]", "int-1"),
            """{"type":"system","content":"post-interrupt bookkeeping","uuid":"sys-1"}""",
            """{"type":"last-prompt","uuid":"lp-1"}""",
            """{"type":"total_tokens_reminder","uuid":"tok-1"}""",
        ).joinToString("\n")

        assertEquals("int-1", ClaudeSessions.lastInterruptUuid(transcript))
    }

    @Test
    fun `lastInterruptUuid reports the newest of several interrupts`() {
        val transcript = listOf(
            interruptLine("[Request interrupted by user]", "old"),
            """{"type":"user","message":{"role":"user","content":"carry on"},"uuid":"prompt"}""",
            interruptLine("[Request interrupted by user for tool use]", "new"),
            """{"type":"system","uuid":"sys"}""",
        ).joinToString("\n")

        assertEquals("new", ClaudeSessions.lastInterruptUuid(transcript))
    }

    @Test
    fun `lastInterruptUuid finds nothing in an uninterrupted transcript`() {
        val transcript = listOf(
            """{"type":"user","message":{"role":"user","content":"build it"},"uuid":"a"}""",
            assistantLine("done"),
        ).joinToString("\n")

        assertNull(ClaudeSessions.lastInterruptUuid(transcript))
        assertNull(ClaudeSessions.lastInterruptUuid(""))
    }

    @Test
    fun `records that merely mention an interrupt are not interrupts`() {
        // Measured on this project's own transcript: matching the bare phrase found 27
        // "interrupts" where 5 were real. The rest were the assistant's own tool calls —
        // greps for the phrase, and test files containing it — where the marker sits inside
        // an escaped JSON string one level deeper.
        val toolCall = """{"type":"assistant","message":{"role":"assistant","content":[{"type":"tool_use",""" +
            """"name":"Bash","input":{"command":"grep 'Request interrupted by user' transcript.jsonl"}}]},""" +
            """"uuid":"tool-call"}"""
        assertNull(ClaudeSessions.interruptUuid(toolCall))

        val writingATestFile = """{"type":"assistant","message":{"content":[{"type":"tool_use","name":"Write",""" +
            """"input":{"content":"{\"type\":\"user\",\"text\":\"[Request interrupted by user]\"}"}}]},""" +
            """"uuid":"write-call"}"""
        assertNull("escaped quotes mean this record only talks about an interrupt", ClaudeSessions.interruptUuid(writingATestFile))
    }

    @Test
    fun `lastInterruptUuid ignores a truncated leading line`() {
        // Only the tail of a transcript is read, so the first line is usually cut in half.
        val transcript = "d by user]\"}]},\"uuid\":\"chopped\"}\n" + assistantLine("hello")
        assertNull(ClaudeSessions.lastInterruptUuid(transcript))
    }

    private fun interruptLine(text: String, uuid: String) =
        """{"parentUuid":"p","isSidechain":false,"type":"user",""" +
            """"message":{"role":"user","content":[{"type":"text","text":"$text"}]},""" +
            """"uuid":"$uuid","timestamp":"2026-08-14T00:00:00Z"}"""

    private fun assistantLine(text: String) =
        """{"type":"assistant","message":{"role":"assistant","content":[{"type":"text","text":"$text"}]},"uuid":"asst"}"""

    // The samples below are real transcript lines, trimmed: verified against
    // ~/.claude/projects/*/*.jsonl on this machine.

    @Test
    fun `interruptUuid reads the uuid of a plain Ctrl-C interrupt`() {
        val line = """{"parentUuid":"15479a1e","isSidechain":false,"type":"user",""" +
            """"message":{"role":"user","content":[{"type":"text","text":"[Request interrupted by user]"}]},""" +
            """"uuid":"b88c27c5-1299-42f1-bba8-70213a994223","timestamp":"2026-07-17T18:11:00Z"}"""
        assertEquals("b88c27c5-1299-42f1-bba8-70213a994223", ClaudeSessions.interruptUuid(line))
    }

    @Test
    fun `interruptUuid reads the uuid of a denied permission prompt`() {
        // Answering "no" to a tool approval is recorded as an interrupt, not as a Stop hook.
        val line = """{"parentUuid":"0699c839","isSidechain":false,"type":"user",""" +
            """"message":{"role":"user","content":[{"type":"text","text":"[Request interrupted by user for tool use]"}]},""" +
            """"uuid":"07ebd885-4d52-48f2-8502-a048090c4429","timestamp":"2026-07-17T18:12:00Z"}"""
        assertEquals("07ebd885-4d52-48f2-8502-a048090c4429", ClaudeSessions.interruptUuid(line))
    }

    @Test
    fun `interruptUuid ignores ordinary records`() {
        val prompt = """{"type":"user","message":{"role":"user","content":"build it"},"uuid":"aaa"}"""
        assertNull(ClaudeSessions.interruptUuid(prompt))

        // Claude quoting the marker back at us must not count as an interrupt.
        val assistant = """{"type":"assistant","message":{"role":"assistant","content":""" +
            """[{"type":"text","text":"you saw [Request interrupted by user] earlier"}]},"uuid":"bbb"}"""
        assertNull(ClaudeSessions.interruptUuid(assistant))
    }
    // ---------------------------------------------------------------- WSL projects

    @Test
    fun `a local project is passed through untouched`() {
        assertEquals(
            ClaudeSessions.Storage("/home/x", "/home/x/proj"),
            ClaudeSessions.storage("/home/x", "/home/x/proj"),
        )
        assertEquals(
            ClaudeSessions.Storage("C:\\Users\\me", "D:\\proj"),
            ClaudeSessions.storage("C:\\Users\\me", "D:\\proj"),
        )
    }

    @Test
    fun `a WSL project resolves to the distro home and the Linux path`() {
        // What IntelliJ reports for a project opened from a distro: a UNC path with
        // forward slashes. Claude, running inside the distro, sees neither half of it.
        assertEquals(
            ClaudeSessions.Storage("//wsl$/Ubuntu/home/me", "/home/me/proj"),
            ClaudeSessions.storage("C:\\Users\\me", "//wsl$/Ubuntu/home/me/proj"),
        )
    }

    @Test
    fun `the backslash spelling of the same UNC path resolves identically`() {
        assertEquals(
            ClaudeSessions.storage("C:\\Users\\me", "//wsl$/Ubuntu/home/me/proj"),
            ClaudeSessions.storage("C:\\Users\\me", "\\\\wsl\$\\Ubuntu\\home\\me\\proj"),
        )
    }

    @Test
    fun `wsl-localhost is recognised, case-insensitively, with a dotted distro name`() {
        assertEquals(
            ClaudeSessions.Storage("//wsl.localhost/Ubuntu-24.04/home/me", "/home/me/proj"),
            ClaudeSessions.storage("C:\\Users\\me", "//WSL.LocalHost/Ubuntu-24.04/home/me/proj"),
        )
    }

    @Test
    fun `a project at the distro home root resolves to that home`() {
        assertEquals(
            ClaudeSessions.Storage("//wsl$/Ubuntu/home/me", "/home/me"),
            ClaudeSessions.storage("C:\\Users\\me", "//wsl$/Ubuntu/home/me/"),
        )
    }

    @Test
    fun `a project under root resolves to the root home`() {
        assertEquals(
            ClaudeSessions.Storage("//wsl$/Ubuntu/root", "/root/proj"),
            ClaudeSessions.storage("C:\\Users\\me", "//wsl$/Ubuntu/root/proj"),
        )
    }

    @Test
    fun `a WSL path whose home cannot be derived falls back to the IDE's own values`() {
        // /opt says nothing about which user's ~/.claude to read; guessing would be worse
        // than the pre-existing behaviour of simply finding nothing.
        val ide = "//wsl$/Ubuntu/opt/proj"
        assertEquals(
            ClaudeSessions.Storage("C:\\Users\\me", ide),
            ClaudeSessions.storage("C:\\Users\\me", ide),
        )
    }

    @Test
    fun `a UNC prefix with no distro falls back too`() {
        val ide = "//wsl$/"
        assertEquals(
            ClaudeSessions.Storage("C:\\Users\\me", ide),
            ClaudeSessions.storage("C:\\Users\\me", ide),
        )
    }

    @Test
    fun `sessionsDir of a WSL project encodes the Linux path, not the UNC one`() {
        val dir = ClaudeSessions.sessionsDir("C:\\Users\\me", "//wsl$/Ubuntu/home/me/proj")
        assertEquals("-home-me-proj", dir.name)
        assertEquals("projects", dir.parent.name)
        // and it sits in the distro's home, not the Windows one
        assertTrue(dir.toString(), dir.toString().replace('\\', '/').contains("/Ubuntu/home/me/.claude"))
    }
}
