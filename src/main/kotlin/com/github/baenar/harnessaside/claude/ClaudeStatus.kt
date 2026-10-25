package com.github.baenar.harnessaside.claude

/**
 * The Manul frame sets: which animation [ClaudeStatusIcons.frameName] picks.
 */
enum class ClaudeStatus {
    /** Still icon. */
    IDLE,

    /** Working frames. */
    THINKING,

    /** Needs-help frames. */
    WAITING,
}
