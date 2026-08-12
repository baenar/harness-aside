package com.github.baenar.claudeaside

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

/**
 * Claude's current state, as reported by the hooks installed by [ClaudeStatusHooks].
 * The hook for each event writes the matching [word] into `.claude/aside-status`.
 */
enum class ClaudeStatus(val word: String, private val iconPath: String) {
    /** Claude is working on the prompt (UserPromptSubmit). */
    THINKING("thinking", "/icons/status_thinking.svg"),

    /** Claude needs the user (Notification — e.g. awaiting tool approval). */
    WAITING("waiting", "/icons/status_waiting.svg"),

    /** Claude finished responding and is ready (Stop). */
    IDLE("idle", "/icons/status_idle.svg");

    val icon: Icon by lazy { IconLoader.getIcon(iconPath, ClaudeStatus::class.java) }

    companion object {
        /**
         * The Claude glyph with no status dot, on the same tall canvas as the status icons,
         * so switching to/from a status never resizes the glyph. Used when no status is
         * known / hooks aren't installed.
         */
        val DEFAULT_ICON: Icon by lazy { IconLoader.getIcon("/icons/status_none.svg", ClaudeStatus::class.java) }

        fun fromWord(word: String): ClaudeStatus? = entries.firstOrNull { it.word == word.trim() }
    }
}
