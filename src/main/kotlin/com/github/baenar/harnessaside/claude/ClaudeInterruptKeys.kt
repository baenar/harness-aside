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
}
