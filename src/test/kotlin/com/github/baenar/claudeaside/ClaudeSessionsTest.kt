package com.github.baenar.claudeaside

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        // Verified against this machine: D:\claude-aside -> D--claude-aside
        assertEquals("D--claude-aside", ClaudeSessions.encodeProjectPath("D:\\claude-aside"))
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
    fun `lastNonBlankLine skips trailing newlines`() {
        assertEquals("second", ClaudeSessions.lastNonBlankLine("first\nsecond\n\n"))
        assertNull(ClaudeSessions.lastNonBlankLine("\n  \n"))
    }

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
}
