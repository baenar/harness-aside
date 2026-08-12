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
 * Installs the Claude Code hooks that report status into `.claude/aside-status`.
 *
 * Each hook is a trivial POSIX-shell write keyed off `$CLAUDE_PROJECT_DIR`, so it works
 * for local, WSL and Git-bash shells and touches the same physical file the plugin
 * watches. On a native Windows shell the command is a no-op and status simply stays on
 * the default icon — the feature degrades gracefully, as planned.
 */
class ClaudeStatusHooks(private val project: Project) {

    /** Relative path (from the project root) of the file the hooks write and the plugin watches. */
    val statusFileRelPath: String get() = "$CLAUDE_DIR/$STATUS_FILE"

    private fun settingsPath(): Path? {
        val base = project.basePath ?: return null
        return Paths.get(base, CLAUDE_DIR, "settings.json")
    }

    /** True when our status hooks are already present in the project settings. */
    fun isInstalled(): Boolean {
        val path = settingsPath() ?: return false
        if (!Files.exists(path)) return false
        return try {
            JsonParser.parseString(Files.readString(path)).toString().contains(STATUS_FILE)
        } catch (e: Exception) {
            LOG.warn("Could not read $path", e)
            false
        }
    }

    /**
     * Merges the status hooks into `.claude/settings.json`, preserving any existing content.
     * @return true on success.
     */
    fun install(): Boolean {
        val path = settingsPath() ?: return false
        return try {
            Files.createDirectories(path.parent)
            val root = if (Files.exists(path)) {
                JsonParser.parseString(Files.readString(path)).asJsonObject
            } else {
                JsonObject()
            }
            val hooks = root.getAsJsonObject("hooks") ?: JsonObject().also { root.add("hooks", it) }
            for ((event, word) in EVENT_TO_WORD) {
                addHook(hooks, event, command(word))
            }
            Files.writeString(path, GsonBuilder().setPrettyPrinting().create().toJson(root))
            true
        } catch (e: Exception) {
            LOG.warn("Could not install Claude Aside hooks into $path", e)
            false
        }
    }

    private fun command(word: String): String =
        "printf %s $word > \"\$CLAUDE_PROJECT_DIR/$CLAUDE_DIR/$STATUS_FILE\""

    /** Appends one `{ "hooks": [ { "type": "command", "command": … } ] }` entry, unless already present. */
    private fun addHook(hooks: JsonObject, event: String, command: String) {
        val list = hooks.getAsJsonArray(event) ?: JsonArray().also { hooks.add(event, it) }
        val alreadyThere = list.any { it.toString().contains(STATUS_FILE) }
        if (alreadyThere) return

        val commandObj = JsonObject().apply {
            addProperty("type", "command")
            addProperty("command", command)
        }
        val entry = JsonObject().apply {
            add("hooks", JsonArray().apply { add(commandObj) })
        }
        list.add(entry)
    }

    companion object {
        private val LOG = logger<ClaudeStatusHooks>()
        const val CLAUDE_DIR = ".claude"
        const val STATUS_FILE = "aside-status"

        /** Claude hook event → status word written by that event's hook. */
        private val EVENT_TO_WORD = linkedMapOf(
            "UserPromptSubmit" to ClaudeStatus.THINKING.word,
            "Notification" to ClaudeStatus.WAITING.word,
            "Stop" to ClaudeStatus.IDLE.word,
        )
    }
}
