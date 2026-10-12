package com.github.baenar.harnessaside.ui

import com.github.baenar.harnessaside.core.HarnessRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HarnessLauncherUiTest {

    @Test
    fun `harness registry provides supported harnesses for launcher`() {
        val registered = HarnessRegistry.registered
        assertFalse("Registered harnesses must not be empty", registered.isEmpty())
        val claude = registered.firstOrNull { it.id == "claude" }
        assertNotNull("Claude Code must be registered", claude)
        assertEquals("Claude Code", claude?.displayName)
        assertEquals("claude", claude?.executable)
        assertNotNull("Claude harness icon must not be null", claude?.icon)
        assertEquals(com.github.baenar.harnessaside.claude.ClaudeStatusIcons.CLAUDE, claude?.icon)
    }

    @Test
    fun `switching flag default is false`() {
        assertFalse(HarnessToolWindowUi.isSwitching)
    }

    @Test
    fun `launcher key is configured`() {
        assertNotNull(HarnessToolWindowUi.LAUNCHER_KEY)
    }
}
