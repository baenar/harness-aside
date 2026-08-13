package com.github.baenar.claudeaside

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Installs the Claude Code hooks that report status into `.claude/aside-status/<session-id>`.
 *
 * Every hook runs the same generated POSIX script ([SCRIPT_FILE]) with the status word as
 * its only argument. The script reads the `session_id` Claude passes on the hook's stdin,
 * so each Claude tab gets its own state file and open tabs no longer share one indicator.
 * On a native Windows shell (no `sh`) the hook is a no-op and status stays on the default
 * icon — the feature degrades gracefully, as planned.
 */
class ClaudeStatusHooks(private val project: Project) {

    private fun settingsPath(): Path? {
        val base = project.basePath ?: return null
        return Paths.get(base, CLAUDE_DIR, "settings.json")
    }

    /** True when our status hooks are already present in the project settings. */
    fun isInstalled(): Boolean {
        val path = settingsPath() ?: return false
        if (!Files.exists(path)) return false
        return try {
            JsonParser.parseString(Files.readString(path)).toString().contains(SCRIPT_FILE)
        } catch (e: Exception) {
            LOG.warn("Could not read $path", e)
            false
        }
    }

    /**
     * Writes the reporter script and merges the hooks into `.claude/settings.json`,
     * preserving unrelated settings and replacing any entries from an earlier plugin
     * version.
     *
     * @return true on success.
     */
    fun install(): Boolean {
        val settings = settingsPath() ?: return false
        return try {
            val claudeDir = settings.parent
            Files.createDirectories(claudeDir)
            migrateStatusFile(claudeDir)
            Files.writeString(claudeDir.resolve(SCRIPT_FILE), SCRIPT)

            val root = if (Files.exists(settings)) {
                JsonParser.parseString(Files.readString(settings)).asJsonObject
            } else {
                JsonObject()
            }
            val hooks = root.getAsJsonObject("hooks") ?: JsonObject().also { root.add("hooks", it) }
            for (event in hooks.keySet().toList()) {
                dropOurEntries(hooks, event)
            }
            for ((event, word) in EVENT_TO_WORD) {
                addHook(hooks, event, "sh \"\$CLAUDE_PROJECT_DIR/$CLAUDE_DIR/$SCRIPT_FILE\" $word")
            }
            val json = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
            Files.writeString(settings, json.toJson(root))
            true
        } catch (e: Exception) {
            LOG.warn("Could not install Claude Aside hooks into $settings", e)
            false
        }
    }

    /** Pre-3.1 installs wrote a single status *file* where the per-session directory now goes. */
    private fun migrateStatusFile(claudeDir: Path) {
        val status = claudeDir.resolve(STATUS_DIR)
        if (Files.isRegularFile(status)) Files.delete(status)
    }

    /** Removes hook entries this plugin installed, so reinstalling upgrades instead of duplicating. */
    private fun dropOurEntries(hooks: JsonObject, event: String) {
        val list = hooks.getAsJsonArray(event) ?: return
        val kept = JsonArray()
        list.filterNot { it.toString().contains(MARKER) }.forEach { kept.add(it) }
        if (kept.isEmpty) hooks.remove(event) else hooks.add(event, kept)
    }

    /** Appends one `{ "hooks": [ { "type": "command", "command": … } ] }` entry. */
    private fun addHook(hooks: JsonObject, event: String, command: String) {
        val list = hooks.getAsJsonArray(event) ?: JsonArray().also { hooks.add(event, it) }
        val commandObj = JsonObject().apply {
            addProperty("type", "command")
            addProperty("command", command)
        }
        list.add(JsonObject().apply { add("hooks", JsonArray().apply { add(commandObj) }) })
    }

    companion object {
        private val LOG = logger<ClaudeStatusHooks>()
        const val CLAUDE_DIR = ".claude"

        /** Directory holding one file per session, named after the session id. */
        const val STATUS_DIR = "aside-status"
        const val SCRIPT_FILE = "aside-status.sh"

        /** Substring identifying a hook entry as ours, across plugin versions. */
        private const val MARKER = STATUS_DIR

        /** Word the script writes for `end` — the session is over, drop its state file. */
        private const val END = "end"

        /**
         * Claude hook event → status word.
         *
         * `PreToolUse`/`PostToolUse` are what make approvals recover: without them the
         * indicator stayed on `waiting` from the approval prompt until the whole turn ended.
         */
        private val EVENT_TO_WORD = linkedMapOf(
            "SessionStart" to ClaudeStatus.IDLE.word,
            "UserPromptSubmit" to ClaudeStatus.THINKING.word,
            "PreToolUse" to ClaudeStatus.THINKING.word,
            "PostToolUse" to ClaudeStatus.THINKING.word,
            "Notification" to ClaudeStatus.WAITING.word,
            "PermissionRequest" to ClaudeStatus.WAITING.word,
            "Stop" to ClaudeStatus.IDLE.word,
            "SessionEnd" to END,
        )

        /**
         * Reporter script. Kept dependency-free on purpose: `sed` rather than `jq`, since jq
         * is not installed by default on macOS or Git-bash. `session_id` is a plain UUID in
         * Claude's hook payload, so a non-greedy field match is enough.
         *
         * LF-terminated regardless of host OS — it is read by `sh`, not by Windows.
         */
        internal val SCRIPT = listOf(
            "#!/bin/sh",
            "# Generated by the Claude Aside IntelliJ plugin. Reports Claude's state to the IDE,",
            "# one file per session, so each tool-window tab shows its own indicator.",
            "# Safe to delete: without it the plugin just falls back to no status icon.",
            "dir=\"\$CLAUDE_PROJECT_DIR/$CLAUDE_DIR/$STATUS_DIR\"",
            "mkdir -p \"\$dir\" 2>/dev/null",
            "sid=\$(sed -n 's/.*\"session_id\"[[:space:]]*:[[:space:]]*\"\\([0-9a-fA-F-]*\\)\".*/\\1/p' | head -n 1)",
            "[ -n \"\$sid\" ] || sid=unknown",
            "if [ \"\$1\" = \"$END\" ]; then",
            "  rm -f \"\$dir/\$sid\"",
            "else",
            "  printf %s \"\$1\" > \"\$dir/\$sid\"",
            "fi",
            "exit 0",
            "",
        ).joinToString("\n")
    }
}
