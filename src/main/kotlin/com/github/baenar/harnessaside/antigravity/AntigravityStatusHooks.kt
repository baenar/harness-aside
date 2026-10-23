package com.github.baenar.harnessaside.antigravity

import com.github.baenar.harnessaside.claude.ClaudeSessions
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

    /** True when our status hooks are already present in hooks.json. */
    fun isInstalled(): Boolean {
        if (!Files.exists(hooksPath)) return false
        return try {
            val content = Files.readString(hooksPath)
            content.contains("HARNESS_ASIDE_IDE_PORT") || content.contains(HOOK_NAME)
        } catch (e: Exception) {
            LOG.warn("Could not read $hooksPath", e)
            false
        }
    }

    /**
     * Merges the HTTP hooks into `hooks.json`, creating the directory/file if needed.
     *
     * @return true on success.
     */
    fun install(): Boolean = try {
        Files.createDirectories(configDir)
        val root = if (Files.exists(hooksPath)) {
            try {
                JsonParser.parseString(Files.readString(hooksPath)).asJsonObject
            } catch (_: Exception) {
                JsonObject()
            }
        } else {
            JsonObject()
        }
        if (windows) Files.writeString(configDir.resolve(ALLOW_FILE), ALLOW_JSON)
        Files.writeString(hooksPath, GSON.toJson(merge(root, windows)))
        true
    } catch (e: Exception) {
        LOG.warn("Could not install status hooks into $hooksPath", e)
        false
    }

    companion object {
        private val LOG = logger<AntigravityStatusHooks>()
        private val GSON = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

        const val AGENTS_DIR = ".agents"
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

        fun curlCommand(state: String, stdoutJson: String = "{}"): String =
            "[ -n \"\$HARNESS_ASIDE_IDE_PORT\" ] && curl -s -m 2 --connect-timeout 1 -X POST \"http://127.0.0.1:\$HARNESS_ASIDE_IDE_PORT/status?state=$state&token=\$HARNESS_ASIDE_IDE_TOKEN\" >/dev/null 2>&1 || true; echo '$stdoutJson'"

        fun preToolUseCommand(): String =
            "PAYLOAD=\$(cat); if echo \"\$PAYLOAD\" | grep -qE '\"(${AntigravityHookEvents.WAITING_TOOLS.joinToString("|")})\"'; then STATE=\"waiting\"; else STATE=\"tool\"; fi; [ -n \"\$HARNESS_ASIDE_IDE_PORT\" ] && curl -s -m 2 --connect-timeout 1 -X POST \"http://127.0.0.1:\$HARNESS_ASIDE_IDE_PORT/status?state=\$STATE&token=\$HARNESS_ASIDE_IDE_TOKEN\" >/dev/null 2>&1 || true; echo '{\"decision\":\"allow\"}'"

        fun postToolUseCommand(): String =
            "PAYLOAD=\$(cat); echo \"\$PAYLOAD\" | grep -qE '\"error\":[[:space:]]*\"[^\"]+' && STATE=\"idle\" || STATE=\"thinking\"; [ -n \"\$HARNESS_ASIDE_IDE_PORT\" ] && curl -s -m 2 --connect-timeout 1 -X POST \"http://127.0.0.1:\$HARNESS_ASIDE_IDE_PORT/status?state=\$STATE&token=\$HARNESS_ASIDE_IDE_TOKEN\" >/dev/null 2>&1 || true; echo '{}'"

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
            fun state(state: String) = if (windows) windowsStateCommand(state) else curlCommand(state, "{}")
            val preTool = if (windows) windowsToolCommand("PreToolUse") else preToolUseCommand()
            val postTool = if (windows) windowsToolCommand("PostToolUse") else postToolUseCommand()
            val group = JsonObject()

            // SessionStart -> idle
            val sessionStart = JsonArray().apply {
                add(createCommandHook(state("idle")))
            }
            group.add("SessionStart", sessionStart)

            // PreInvocation -> thinking
            val preInvocation = JsonArray().apply {
                add(createCommandHook(state("thinking")))
            }
            group.add("PreInvocation", preInvocation)

            // PreToolUse -> waiting for interactive tools (run_command, ask_question, file write/edit), else tool
            val preToolUse = JsonArray().apply {
                val toolObj = JsonObject().apply {
                    addProperty("matcher", "*")
                    val hooks = JsonArray().apply {
                        add(createCommandHook(preTool))
                    }
                    add("hooks", hooks)
                }
                add(toolObj)
            }
            group.add("PreToolUse", preToolUse)

            // PostToolUse -> idle if error / rejected by user, else thinking
            val postToolUse = JsonArray().apply {
                val toolObj = JsonObject().apply {
                    addProperty("matcher", "*")
                    val hooks = JsonArray().apply {
                        add(createCommandHook(postTool))
                    }
                    add("hooks", hooks)
                }
                add(toolObj)
            }
            group.add("PostToolUse", postToolUse)

            // PostInvocation -> idle
            val postInvocation = JsonArray().apply {
                add(createCommandHook(state("idle")))
            }
            group.add("PostInvocation", postInvocation)

            // Stop -> idle
            val stop = JsonArray().apply {
                add(createCommandHook(state("idle")))
            }
            group.add("Stop", stop)

            root.add(HOOK_NAME, group)
            return root
        }

        private fun createCommandHook(command: String): JsonObject =
            JsonObject().apply {
                addProperty("type", "command")
                addProperty("command", command)
            }
    }
}
