package com.github.baenar.harnessaside.claude

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test
    fun `permission dialog answers follow Claude's option order`() {
        // 1. Yes  2. Yes, and don't ask again  3. No
        assertEquals(true, ClaudeInterruptKeys.permissionAnswer(KeyEvent.VK_1, '1'))
        assertEquals("option 2 approves too", true, ClaudeInterruptKeys.permissionAnswer(KeyEvent.VK_2, '2'))
        assertEquals(false, ClaudeInterruptKeys.permissionAnswer(KeyEvent.VK_3, '3'))
        assertEquals(true, ClaudeInterruptKeys.permissionAnswer(KeyEvent.VK_ENTER, '\n'))
        assertNull("moving the highlight answers nothing", ClaudeInterruptKeys.permissionAnswer(KeyEvent.VK_DOWN, KeyEvent.CHAR_UNDEFINED))
        assertNull(ClaudeInterruptKeys.permissionAnswer(KeyEvent.VK_A, 'a'))
    }
}
