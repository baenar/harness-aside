package com.github.baenar.harnessaside.claude

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.github.baenar.harnessaside.core.postStatusCommand
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Installs push-based status hooks for Claude Code into global `~/.claude/settings.json`.
 *
 * Each hook event executes a lightweight HTTP POST request via curl to the embedded
 * HarnessStatusServer running on 127.0.0.1:$HARNESS_ASIDE_IDE_PORT.
 *
 * This delivers zero-CPU push status notification with no file polling or transcript tail parsing.
 */
class ClaudeStatusHooks(private val claudeDir: Path) {

    private val settingsPath: Path get() = claudeDir.resolve("settings.json")

    /**
     * Merges the HTTP hooks into `settings.json`, preserving unrelated settings and replacing entries from
     * earlier plugin versions. An unchanged file isn't rewritten: Claude reloads it when it changes.
     *
     * @return true on success.
     */
    fun install(): Boolean = try {
        Files.createDirectories(claudeDir)
        val current = if (Files.exists(settingsPath)) Files.readString(settingsPath) else "{}"
        val merged = GSON.toJson(merge(JsonParser.parseString(current).asJsonObject))
        if (merged != current) Files.writeString(settingsPath, merged)
        true
    } catch (e: Exception) {
        LOG.warn("Could not install status hooks into $settingsPath", e)
        false
    }

    companion object {
        private val LOG = logger<ClaudeStatusHooks>()
        private val GSON = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

        const val CLAUDE_DIR = ".claude"

        /** What identifies our entries, including those of earlier plugin versions. */
        private val MARKERS = listOf("HARNESS_ASIDE_IDE_PORT", "HARNESS_IDE_PORT", "/status?state=", "aside-status")

        fun forProject(project: Project, userHome: String = System.getProperty("user.home")): ClaudeStatusHooks? {
            val projectPath = project.basePath ?: return null
            val storage = ClaudeSessions.storage(userHome, projectPath)
            return ClaudeStatusHooks(Paths.get(storage.userHome, CLAUDE_DIR))
        }

        /**
         * `PostToolUseFailure`: a failed tool call (say, Bash exiting non-zero) doesn't end the turn, so
         * Claude is still thinking. Only an interrupt does, and an interrupted turn fires no `Stop`.
         */
        fun toolFailureCommand(): String =
            "case \"\$(cat)\" in *'\"is_interrupt\":true'*|*'\"is_interrupt\": true'*) S=idle ;; *) S=thinking ;; esac; " +
                postStatusCommand("\$S")

        /** One entry in `settings.json`: [event], an optional [matcher] narrowing it, and its [command]. */
        data class Hook(val event: String, val command: String, val matcher: String? = null)

        /**
         * Everything we install. The icon must leave "working" only when the turn really ends, and must
         * not re-enter it afterwards: `Notification` also fires for things like the idle reminder.
         */
        val HOOKS = listOf(
            Hook("SessionStart", postStatusCommand("idle")),
            Hook("UserPromptSubmit", postStatusCommand("thinking")),
            Hook("PreToolUse", postStatusCommand("tool")),
            Hook("PostToolUse", postStatusCommand("thinking")),
            Hook("PostToolUseFailure", toolFailureCommand()),
            // Notification matchers compare `notification_type`. Only dialogs wait on the user; the
            // idle reminder (~1 min after a turn) means Claude is idle, which also heals a stuck icon.
            Hook("Notification", postStatusCommand("waiting"), "permission_prompt|elicitation_dialog|elicitation_url_dialog"),
            Hook("Notification", postStatusCommand("idle"), "idle_prompt"),
            Hook("PermissionRequest", postStatusCommand("waiting")),
            // Auto-mode denial: the model is told and carries on (it may even retry), the turn goes on.
            Hook("PermissionDenied", postStatusCommand("thinking")),
            Hook("Stop", postStatusCommand("idle")),
            Hook("StopFailure", postStatusCommand("idle")),
            Hook("SessionEnd", postStatusCommand("idle")),
        )

        /**
         * Adds our HTTP hooks to [root], dropping entries from earlier plugin versions so that
         * reinstalling upgrades instead of duplicating, and leaving everything else alone.
         */
        fun merge(root: JsonObject): JsonObject {
            val hooks = root.getAsJsonObject("hooks") ?: JsonObject().also { root.add("hooks", it) }
            for (event in hooks.keySet().toList()) {
                dropOurEntries(hooks, event)
            }
            for (hook in HOOKS) {
                addHook(hooks, hook)
            }
            return root
        }

        private fun dropOurEntries(hooks: JsonObject, event: String) {
            val list = hooks.getAsJsonArray(event) ?: return
            val kept = JsonArray()
            list.filterNot { entry ->
                val str = entry.toString()
                MARKERS.any { marker -> str.contains(marker) }
            }.forEach { kept.add(it) }
            if (kept.isEmpty) hooks.remove(event) else hooks.add(event, kept)
        }

        private fun addHook(hooks: JsonObject, hook: Hook) {
            val list = hooks.getAsJsonArray(hook.event) ?: JsonArray().also { hooks.add(hook.event, it) }
            val commandObj = JsonObject().apply {
                addProperty("type", "command")
                addProperty("command", hook.command)
            }
            list.add(JsonObject().apply {
                hook.matcher?.let { addProperty("matcher", it) }
                add("hooks", JsonArray().apply { add(commandObj) })
            })
        }
    }
}
