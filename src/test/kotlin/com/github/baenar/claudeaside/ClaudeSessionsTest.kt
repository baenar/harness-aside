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
}
