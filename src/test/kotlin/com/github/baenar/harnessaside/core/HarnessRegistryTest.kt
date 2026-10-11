package com.github.baenar.harnessaside.core

import com.github.baenar.harnessaside.claude.ClaudeHarness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HarnessRegistryTest {

    @Test
    fun `default harness is Claude`() {
        assertEquals("claude", HarnessRegistry.default.id)
        assertEquals("Claude Code", HarnessRegistry.default.displayName)
    }

    @Test
    fun `finds registered harnesses`() {
        val claude = HarnessRegistry.find("claude")
        assertNotNull(claude)
        assertEquals(ClaudeHarness, claude)
    }

    @Test
    fun `registered list contains default harnesses`() {
        val ids = HarnessRegistry.registered.map { it.id }
        assertTrue(ids.contains("claude"))
    }
}
