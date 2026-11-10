package com.github.baenar.harnessaside.core

import com.github.baenar.harnessaside.antigravity.AntigravityHarness
import com.github.baenar.harnessaside.claude.ClaudeHarness
import com.github.baenar.harnessaside.opencode.OpenCodeHarness
import org.junit.Assert.assertEquals
import org.junit.Test

class HarnessRegistryTest {

    @Test
    fun `the launcher lists the plain terminal first, above Claude`() {
        assertEquals(
            listOf(TerminalHarness, ClaudeHarness, AntigravityHarness, OpenCodeHarness),
            HarnessRegistry.registered,
        )
    }

    @Test
    fun `only the plain terminal has no executable`() {
        assertEquals(listOf(null, "claude", "agy", "opencode"), HarnessRegistry.registered.map { it.executable })
    }
}
