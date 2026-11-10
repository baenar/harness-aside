package com.github.baenar.harnessaside.antigravity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AntigravityHookEventsTest {

    private fun pre(tool: String) = """{"conversationId":"c","toolCall":{"name":"$tool","args":{}},"stepIdx":1}"""

    @Test
    fun `interactive and writing tools wait`() {
        for (tool in AntigravityHookEvents.WAITING_TOOLS) {
            assertEquals(tool, "waiting", AntigravityHookEvents.state("PreToolUse", pre(tool)))
        }
    }

    @Test
    fun `other tools just run`() {
        assertEquals("tool", AntigravityHookEvents.state("PreToolUse", pre("view_file")))
    }

    @Test
    fun `a tool name inside the arguments does not count`() {
        val payload = """{"toolCall":{"name":"view_file","args":{"path":"run_command"}}}"""
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
