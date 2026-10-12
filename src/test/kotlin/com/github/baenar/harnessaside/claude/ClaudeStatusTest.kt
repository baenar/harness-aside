package com.github.baenar.harnessaside.claude

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Parsing the state files the hook script writes.
 *
 * The words are a contract between [ClaudeStatusHooks]'s generated shell script and this
 * enum; the last test pins both ends of it together.
 */
class ClaudeStatusTest {

    @Test
    fun `reads the words the hooks write`() {
        assertEquals(ClaudeStatus.IDLE, ClaudeStatus.fromWord("idle"))
        assertEquals(ClaudeStatus.THINKING, ClaudeStatus.fromWord("thinking"))
        assertEquals(ClaudeStatus.WAITING, ClaudeStatus.fromWord("waiting"))
    }

    @Test
    fun `tolerates the whitespace a shell redirect can leave behind`() {
        assertEquals(ClaudeStatus.THINKING, ClaudeStatus.fromWord("thinking\n"))
        assertEquals(ClaudeStatus.IDLE, ClaudeStatus.fromWord("  idle  "))
    }

    @Test
    fun `unknown content is no status rather than a wrong one`() {
        assertNull(ClaudeStatus.fromWord(""))
        assertNull(ClaudeStatus.fromWord("end"))
        assertNull("half-written file", ClaudeStatus.fromWord("think"))
        assertNull("case matters; the script writes lower case", ClaudeStatus.fromWord("IDLE"))
    }

    @Test
    fun `every word the hooks can write is either a status or the end marker`() {
        for ((event, word) in ClaudeStatusHooks.EVENT_TO_WORD) {
            val known = ClaudeStatus.fromWord(word) != null || word == "end"
            assertEquals("hook $event writes '$word', which nothing understands", true, known)
        }
    }
}
