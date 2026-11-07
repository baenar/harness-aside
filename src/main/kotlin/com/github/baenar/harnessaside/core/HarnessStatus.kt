package com.github.baenar.harnessaside.core

/** What a harness running in the tool window is doing. */
enum class HarnessStatus(val id: String) {
    /** Waiting for the user's next prompt. */
    IDLE("idle"),

    /** Generating a response or reasoning. */
    THINKING("thinking"),

    /** Blocked on the user's confirmation or permission. */
    WAITING("waiting"),

    /** Running a tool or shell command. */
    EXECUTING_TOOL("tool");

    companion object {
        fun fromId(id: String?): HarnessStatus? = entries.firstOrNull { it.id == id }
    }
}
