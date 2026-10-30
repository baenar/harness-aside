package com.github.baenar.harnessaside.claude

import java.awt.event.InputEvent
import java.awt.event.KeyEvent

/**
 * Which keystrokes mean "stop what you are doing" to Claude Code.
 *
 * This is the plugin's only *immediate* evidence that a turn was cut short. Claude fires
 * no hook when the user interrupts, and — measured against real transcripts — it does not
 * reliably write a transcript record either: interrupting a tool call leaves
 * `[Request interrupted by user for tool use]`, but interrupting plain thinking often
 * leaves nothing at all. The keystroke always happens, and it happens in a terminal this
 * plugin owns.
 *
 * Reading it is optimistic by design: if Claude turns out to still be working, its next
 * `PreToolUse`/`PostToolUse` hook puts the indicator back to thinking within moments.
 */
internal object ClaudeInterruptKeys {

    /** True for Ctrl+C and Esc, the two ways to interrupt Claude Code. */
    fun isInterrupt(keyCode: Int, modifiersEx: Int): Boolean = when (keyCode) {
        KeyEvent.VK_ESCAPE -> true
        KeyEvent.VK_C -> (modifiersEx and InputEvent.CTRL_DOWN_MASK) != 0
        else -> false
    }

    /**
     * How a key press answers Claude's permission dialog: `1. Yes`, `2. Yes, and don't ask again`,
     * `3. No` (Esc is an interrupt, see [isInterrupt]). True approves, false declines, null is
     * anything else. Only meaningful while a dialog is open; callers check that first.
     */
    fun permissionAnswer(keyCode: Int, keyChar: Char): Boolean? = when {
        keyCode == KeyEvent.VK_ENTER -> true // the highlighted option, "Yes" unless moved
        keyChar in "12yY" -> true
        keyChar in "3nN" -> false
        else -> null
    }
}
