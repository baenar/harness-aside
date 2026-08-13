package com.github.baenar.claudeaside

/**
 * Claude's current state, as reported by the hooks installed by [ClaudeStatusHooks].
 * The hook for each event writes the matching [word] into `.claude/aside-status/<session-id>`.
 *
 * How each one looks on the tool-window button lives in [ClaudeStatusIcons].
 */
enum class ClaudeStatus(val word: String) {
    /** Claude finished and is waiting for the next prompt (Stop, SessionStart, interrupted turn). */
    IDLE("idle"),

    /** Claude is working on the prompt (UserPromptSubmit, Pre/PostToolUse). */
    THINKING("thinking"),

    /** Claude is blocked on the user (Notification / PermissionRequest — e.g. awaiting approval). */
    WAITING("waiting");

    companion object {
        fun fromWord(word: String): ClaudeStatus? = entries.firstOrNull { it.word == word.trim() }
    }
}
