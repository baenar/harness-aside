package com.github.baenar.harnessaside.antigravity

import com.google.gson.JsonParser

/**
 * Maps an Antigravity hook payload to a status state, so a hook command only has to forward its stdin.
 * Native Windows `cmd.exe` has no `grep`, so the POSIX hooks' payload matching can't be ported there.
 */
object AntigravityHookEvents {

    /** Tools that need the user (or write files), so `PreToolUse` reports WAITING instead of a plain tool run. */
    val WAITING_TOOLS = listOf(
        "run_command", "ask_question", "ask_permission",
        "write_to_file", "replace_file_content", "multi_replace_file_content",
    )

    /** The state for [event] given its stdin JSON, or null for an event we don't handle. */
    fun state(event: String, stdin: String): String? = when (event) {
        "PreToolUse" -> if (toolName(stdin) in WAITING_TOOLS) "waiting" else "tool"
        "PostToolUse" -> if (hasError(stdin)) "idle" else "thinking"
        else -> null
    }

    private fun toolName(stdin: String): String? = runCatching {
        JsonParser.parseString(stdin).asJsonObject.getAsJsonObject("toolCall").get("name").asString
    }.getOrNull()

    /** `error` is optional and empty on success; only a non-empty string counts. */
    private fun hasError(stdin: String): Boolean = runCatching {
        JsonParser.parseString(stdin).asJsonObject.get("error").asString.isNotEmpty()
    }.getOrDefault(false)
}
