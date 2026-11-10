package com.github.baenar.harnessaside.antigravity

import com.github.baenar.harnessaside.claude.ClaudeSessions
import com.github.baenar.harnessaside.core.postStatusCommand
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
 * Installs push-based status hooks for Antigravity CLI into global `~/.gemini/config/hooks.json`.
 *
 * Each hook event executes a lightweight HTTP POST request via curl to the embedded
 * HarnessStatusServer running on 127.0.0.1:$HARNESS_ASIDE_IDE_PORT.
 *
 * Hook commands run through `sh` on Linux/macOS/WSL and through `cmd.exe` on native Windows, so
 * [windows] picks the matching command syntax for the `hooks.json` being written.
 */
class AntigravityStatusHooks(private val configDir: Path, private val windows: Boolean = false) {

    private val hooksPath: Path get() = configDir.resolve("hooks.json")

    /**
     * Merges the HTTP hooks into `hooks.json`, creating the directory/file if needed. A file that doesn't parse
     * is left alone rather than replaced, and an unchanged one isn't rewritten.
     *
     * @return true on success.
     */
    fun install(): Boolean = try {
        Files.createDirectories(configDir)
        val current = if (Files.exists(hooksPath)) Files.readString(hooksPath) else "{}"
        val merged = GSON.toJson(merge(JsonParser.parseString(current).asJsonObject, windows))
        if (windows) Files.writeString(configDir.resolve(ALLOW_FILE), ALLOW_JSON)
        if (merged != current) Files.writeString(hooksPath, merged)
        true
    } catch (e: Exception) {
        LOG.warn("Could not install status hooks into $hooksPath", e)
        false
    }

    companion object {
        private val LOG = logger<AntigravityStatusHooks>()
        private val GSON = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

        const val HOOK_NAME = "harness-aside"

        /** Canned `PreToolUse` reply, kept in a file because a Windows hook command can't safely contain `"`. */
        const val ALLOW_FILE = "harness-aside-allow.json"
        private const val ALLOW_JSON = "{\"decision\":\"allow\"}"

        fun forProject(project: Project, userHome: String = System.getProperty("user.home")): AntigravityStatusHooks? {
            val projectPath = project.basePath ?: return null
            val storage = ClaudeSessions.storage(userHome, projectPath)
            val windows = isWindowsTarget(System.getProperty("os.name"), storage.userHome)
            return AntigravityStatusHooks(Paths.get(storage.userHome, ".gemini", "config"), windows)
        }

        /**
         * True when the `hooks.json` at [userHome] is read by a native Windows `agy`. A Windows IDE with the
         * project inside WSL writes to the distro's home, where `agy` runs under `sh`.
         */
        fun isWindowsTarget(osName: String, userHome: String): Boolean =
            osName.startsWith("Windows", ignoreCase = true) && !ClaudeSessions.isWsl(userHome)

        /** Agy reads a hook's stdout as JSON, so every POSIX command ends by printing some. */
        fun curlCommand(state: String): String = "${postStatusCommand(state)}; echo '{}'"

        fun preToolUseCommand(): String =
            "PAYLOAD=\$(cat); if echo \"\$PAYLOAD\" | grep -qE '\"(${AntigravityHookEvents.WAITING_TOOLS.joinToString("|")})\"'; then STATE=\"waiting\"; else STATE=\"tool\"; fi; ${postStatusCommand("\$STATE")}; echo '{\"decision\":\"allow\"}'"

        fun postToolUseCommand(): String =
            "PAYLOAD=\$(cat); echo \"\$PAYLOAD\" | grep -qE '\"error\":[[:space:]]*\"[^\"]+' && STATE=\"idle\" || STATE=\"thinking\"; ${curlCommand("\$STATE")}"

        fun stopCommand(): String =
            "PAYLOAD=\$(cat); echo \"\$PAYLOAD\" | grep -qE '\"fullyIdle\":[[:space:]]*true' && STATE=\"done\" || STATE=\"idle\"; ${curlCommand("\$STATE")}"

        // Windows hooks run in cmd.exe, where a non-zero exit blocks the tool call, so every command ends in exit 0.
        // No double quotes anywhere: they reach cmd backslash-escaped. `%VAR%` stays literal when the variable is
        // unset, which makes curl fail on the URL and is swallowed like any other connection failure.
        private const val CURL_WINDOWS =
            "curl.exe -s -m 2 --connect-timeout 1 -H X-Harness-Token:%HARNESS_ASIDE_IDE_TOKEN%"
        private const val STATUS_URL_WINDOWS = "http://127.0.0.1:%HARNESS_ASIDE_IDE_PORT%/status"

        fun windowsStateCommand(state: String): String =
            "$CURL_WINDOWS -X POST $STATUS_URL_WINDOWS?state=$state >NUL 2>&1 & echo {}"

        /** Forwards the hook's stdin; the server picks the state (see [AntigravityHookEvents]). */
        fun windowsToolCommand(event: String): String {
            val reply = if (event == "PreToolUse") "type $ALLOW_FILE 2>NUL & exit 0" else "echo {}"
            return "$CURL_WINDOWS --data-binary @- $STATUS_URL_WINDOWS?event=$event >NUL 2>&1 & $reply"
        }

        /**
         * Adds or replaces the `harness-aside` hook group in [root], with commands for `cmd.exe` when [windows].
         */
        fun merge(root: JsonObject, windows: Boolean = false): JsonObject {
            fun state(state: String) = if (windows) windowsStateCommand(state) else curlCommand(state)
            root.add(HOOK_NAME, JsonObject().apply {
                add("SessionStart", hook(state("idle")))
                add("PreInvocation", hook(state("thinking")))
                // waiting for interactive tools (run_command, ask_question, file write/edit), else tool
                add("PreToolUse", toolHook(if (windows) windowsToolCommand("PreToolUse") else preToolUseCommand()))
                // idle if error / rejected by user, else thinking
                add("PostToolUse", toolHook(if (windows) windowsToolCommand("PostToolUse") else postToolUseCommand()))
                add("PostInvocation", hook(state("idle")))
                add("Stop", hook(if (windows) windowsToolCommand("Stop") else stopCommand()))
            })
            return root
        }

        private fun hook(command: String): JsonArray = JsonArray().apply {
            add(JsonObject().apply {
                addProperty("type", "command")
                addProperty("command", command)
            })
        }

        private fun toolHook(command: String): JsonArray = JsonArray().apply {
            add(JsonObject().apply {
                addProperty("matcher", "*")
                add("hooks", hook(command))
            })
        }
    }
}
