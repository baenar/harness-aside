package com.github.baenar.harnessaside.antigravity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AntigravityHookEventsTest {

    private fun pre(tool: String) = """{"conversationId":"c","toolCall":{"name":"$tool","args":{}},"stepIdx":1}"""

    @Test
    fun `tools that ask the user wait`() {
        for (tool in AntigravityHookEvents.WAITING_TOOLS) {
            assertEquals(tool, "waiting", AntigravityHookEvents.state("PreToolUse", pre(tool)))
        }
    }

    @Test
    fun `other tools just run, commands and edits too`() {
        for (tool in listOf("view_file", "run_command", "write_to_file", "replace_file_content")) {
            assertEquals(tool, "tool", AntigravityHookEvents.state("PreToolUse", pre(tool)))
        }
    }

    @Test
    fun `a tool name inside the arguments does not count`() {
        val payload = """{"toolCall":{"name":"view_file","args":{"path":"ask_question"}}}"""
        assertEquals("tool", AntigravityHookEvents.state("PreToolUse", payload))
    }

    @Test
    fun `unparseable pre-tool payload falls back to tool`() {
        assertEquals("tool", AntigravityHookEvents.state("PreToolUse", "not json"))
        assertEquals("tool", AntigravityHookEvents.state("PreToolUse", ""))
        assertEquals("tool", AntigravityHookEvents.state("PreToolUse", """{"toolCall":"x"}"""))
    }

    @Test
    fun `post-tool error goes idle, success keeps thinking`() {
        assertEquals("idle", AntigravityHookEvents.state("PostToolUse", """{"toolCall":{"name":"x"},"error":"boom"}"""))
        assertEquals("thinking", AntigravityHookEvents.state("PostToolUse", """{"toolCall":{"name":"x"},"error":""}"""))
        assertEquals("thinking", AntigravityHookEvents.state("PostToolUse", """{"toolCall":{"name":"x"},"error":null}"""))
        assertEquals("thinking", AntigravityHookEvents.state("PostToolUse", """{"toolCall":{"name":"x"}}"""))
        assertEquals("thinking", AntigravityHookEvents.state("PostToolUse", "not json"))
    }

    @Test
    fun `unknown event is rejected`() {
        assertNull(AntigravityHookEvents.state("PostInvocation", "{}"))
    }
}
