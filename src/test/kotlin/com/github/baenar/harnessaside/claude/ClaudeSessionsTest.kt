package com.github.baenar.harnessaside.claude

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `a project without a sessions directory has nothing to continue`() {
        assertFalse(ClaudeSessions.hasSessions("/no/such/home", "/no/such/proj"))
    }

    @Test
    fun `only a jsonl file counts as a session`() {
        val home = Files.createTempDirectory("aside-home")
        val projectPath = "/tmp/proj"
        val dir = ClaudeSessions.sessionsDir(home.toString(), projectPath)
        // Non-session entries that must be ignored (the dir may hold e.g. memory/).
        Files.createDirectories(dir.resolve("memory"))
        Files.writeString(dir.resolve("notes.txt"), "ignore me")
        assertFalse(ClaudeSessions.hasSessions(home.toString(), projectPath))

        Files.writeString(dir.resolve("some-session.jsonl"), "{}")
        assertTrue(ClaudeSessions.hasSessions(home.toString(), projectPath))
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
