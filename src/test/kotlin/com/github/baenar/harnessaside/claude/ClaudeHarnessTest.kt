package com.github.baenar.harnessaside.claude

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

/** How Claude starts: one session per project in single mode, one per tab in multi-session mode. */
class ClaudeHarnessTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val project = "/tmp/proj"
    private val id = "2b0a7c1e-4d88-4b3c-9d55-5f9d1a201111"

    @Test
    fun `the single session continues the latest one, if there is one`() {
        val home = tmp.newFolder().path
        assertEquals("claude", ClaudeHarness.command(home, project, null))

        session(home, "someone-elses")
        assertEquals("claude -c", ClaudeHarness.command(home, project, null))
    }

    @Test
    fun `a tab keeps its own conversation`() {
        val home = tmp.newFolder().path
        session(home, "a-later-one")
        // Claude writes nothing until the first message, so a new tab's session is started under its id...
        assertEquals("claude --session-id $id", ClaudeHarness.command(home, project, id))

        session(home, id)
        // ...and resumed by it, never by "latest", which would be another tab's.
        assertEquals("claude -r $id", ClaudeHarness.command(home, project, id))
    }

    private fun session(home: String, name: String) {
        val dir = Files.createDirectories(ClaudeSessions.sessionsDir(home, project))
        Files.writeString(dir.resolve("$name.jsonl"), "{}")
    }
}
