package com.github.baenar.harnessaside.core

/**
 * Unified status states for AI coding harnesses running in the tool window.
 */
enum class HarnessStatus(val id: String) {
    /** Harness is waiting for user input or idle. */
    IDLE("idle"),

    /** Agent model is generating a response or reasoning. */
    THINKING("thinking"),

    /** Agent is blocked waiting for user confirmation or permission. */
    WAITING("waiting"),

    /** Harness is executing a tool or shell command. */
    EXECUTING_TOOL("tool"),

    /** Harness encountered an error. */
    ERROR("error");

    companion object {
        fun fromId(id: String): HarnessStatus? =
            entries.firstOrNull { it.id.equals(id.trim(), ignoreCase = true) }
    }
}
