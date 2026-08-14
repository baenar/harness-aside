package com.github.baenar.claudeaside

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * The commands tabs are launched with.
 *
 * The point of these is the invariant behind the whole status feature: the plugin always
 * knows the session id up front. A regression to bare `claude -c` would compile and run
 * fine while silently breaking every indicator, so it is pinned here.
 */
class ClaudeLaunchTest {

    @Test
    fun `a new session is launched under an id the plugin chose`() {
        val launch = ClaudeTerminalService.Launch.new()

        assertEquals("claude --session-id ${launch.sessionId}", launch.command)
        // Claude requires a real UUID for --session-id.
        assertEquals(launch.sessionId, UUID.fromString(launch.sessionId).toString())
    }

    @Test
    fun `each new session gets its own id`() {
        assertNotEquals(ClaudeTerminalService.Launch.new().sessionId, ClaudeTerminalService.Launch.new().sessionId)
    }

    @Test
    fun `resuming targets the given session`() {
        val launch = ClaudeTerminalService.Launch.resumeOrNew("2b0a7c1e-4d88-4b3c-9d55-5f9d1a201111")

        assertEquals("claude -r 2b0a7c1e-4d88-4b3c-9d55-5f9d1a201111", launch.command)
        assertEquals("2b0a7c1e-4d88-4b3c-9d55-5f9d1a201111", launch.sessionId)
    }

    @Test
    fun `with nothing to resume it starts a fresh session instead`() {
        val launch = ClaudeTerminalService.Launch.resumeOrNew(null)

        assertTrue(launch.command.startsWith("claude --session-id "))
        assertEquals("claude --session-id ${launch.sessionId}", launch.command)
    }

    @Test
    fun `never falls back to a command that hides the session id`() {
        val commands = listOf(
            ClaudeTerminalService.Launch.new().command,
            ClaudeTerminalService.Launch.resumeOrNew(null).command,
            ClaudeTerminalService.Launch.resumeOrNew("abc").command,
        )
        for (command in commands) {
            assertTrue(
                "'$command' must name a session; bare -c resumes without saying which",
                command.contains("--session-id ") || command.contains("-r "),
            )
        }
    }
}
