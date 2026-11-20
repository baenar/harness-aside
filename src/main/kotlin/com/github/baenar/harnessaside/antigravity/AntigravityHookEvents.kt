package com.github.baenar.harnessaside.antigravity

import com.google.gson.JsonParser

/**
 * Maps an Antigravity hook payload to a status state, so a hook command only has to forward its stdin.
 * Native Windows `cmd.exe` has no `grep`, so the POSIX hooks' payload matching can't be ported there.
 */
object AntigravityHookEvents {

    /**
     * Tools that wait for the user whatever the permission mode, so `PreToolUse` reports WAITING instead of a plain
     * tool run. A command or file edit only waits when agy asks to approve it, which no hook says (the payload has
     * just the tool call), so they count as tool runs: better no alert for an approval than one for every command.
     */
    val WAITING_TOOLS = listOf("ask_question", "ask_permission")

    /** The state for [event] given its stdin JSON, or null for an event we don't handle. */
    fun state(event: String, stdin: String): String? = when (event) {
        "PreToolUse" -> if (toolName(stdin) in WAITING_TOOLS) "waiting" else "tool"
        "PostToolUse" -> if (hasError(stdin)) "idle" else "thinking"
        "Stop" -> if (isFullyIdle(stdin)) "done" else "idle"
        else -> null
    }

    private fun toolName(stdin: String): String? = runCatching {
        JsonParser.parseString(stdin).asJsonObject.getAsJsonObject("toolCall").get("name").asString
    }.getOrNull()

    /** `Stop` ends one execution; only `fullyIdle` says nothing else is still running. */
    private fun isFullyIdle(stdin: String): Boolean = runCatching {
        JsonParser.parseString(stdin).asJsonObject.get("fullyIdle").asBoolean
    }.getOrDefault(false)

    /** `error` is optional and empty on success; only a non-empty string counts. */
    private fun hasError(stdin: String): Boolean = runCatching {
        JsonParser.parseString(stdin).asJsonObject.get("error").asString.isNotEmpty()
    }.getOrDefault(false)
}
