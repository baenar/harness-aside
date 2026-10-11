package com.github.baenar.harnessaside

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.event.InputEvent
import java.awt.event.KeyEvent

/** Which keystrokes count as "Claude, stop". */
class ClaudeInterruptKeysTest {

    @Test
    fun `ctrl-C interrupts`() {
        assertTrue(ClaudeInterruptKeys.isInterrupt(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK))
    }

    @Test
    fun `escape interrupts on its own`() {
        assertTrue(ClaudeInterruptKeys.isInterrupt(KeyEvent.VK_ESCAPE, 0))
        // Denying a permission prompt is Esc with whatever modifiers happen to be held.
        assertTrue(ClaudeInterruptKeys.isInterrupt(KeyEvent.VK_ESCAPE, InputEvent.SHIFT_DOWN_MASK))
    }

    @Test
    fun `plain C is typing, not interrupting`() {
        assertFalse(ClaudeInterruptKeys.isInterrupt(KeyEvent.VK_C, 0))
        assertFalse(ClaudeInterruptKeys.isInterrupt(KeyEvent.VK_C, InputEvent.SHIFT_DOWN_MASK))
    }

    @Test
    fun `other ctrl combinations are left alone`() {
        // Ctrl+V, Ctrl+D and friends must not blank the indicator.
        for (key in listOf(KeyEvent.VK_V, KeyEvent.VK_D, KeyEvent.VK_Z, KeyEvent.VK_ENTER)) {
            assertFalse(ClaudeInterruptKeys.isInterrupt(key, InputEvent.CTRL_DOWN_MASK))
        }
    }
}
