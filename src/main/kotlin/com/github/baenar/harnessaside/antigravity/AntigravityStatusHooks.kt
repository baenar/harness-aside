package com.github.baenar.harnessaside.antigravity

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
 */
class AntigravityStatusHooks(private val configDir: Path) {

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
        Files.writeString(hooksPath, GSON.toJson(merge(root)))
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

        fun forProject(project: Project, userHome: String = System.getProperty("user.home")): AntigravityStatusHooks? {
            val projectPath = project.basePath ?: return null
            val storage = com.github.baenar.harnessaside.claude.ClaudeSessions.storage(userHome, projectPath)
            return AntigravityStatusHooks(Paths.get(storage.userHome, ".gemini", "config"))
        }

        fun curlCommand(state: String, stdoutJson: String = "{}"): String =
            "[ -n \"\$HARNESS_ASIDE_IDE_PORT\" ] && curl -s -m 2 --connect-timeout 1 -X POST \"http://127.0.0.1:\$HARNESS_ASIDE_IDE_PORT/status?state=$state&token=\$HARNESS_ASIDE_IDE_TOKEN\" >/dev/null 2>&1 || true; echo '$stdoutJson'"

        fun preToolUseCommand(): String =
            "PAYLOAD=\$(cat); if echo \"\$PAYLOAD\" | grep -qE '\"(run_command|ask_question|ask_permission|write_to_file|replace_file_content|multi_replace_file_content)\"'; then STATE=\"waiting\"; else STATE=\"tool\"; fi; [ -n \"\$HARNESS_ASIDE_IDE_PORT\" ] && curl -s -m 2 --connect-timeout 1 -X POST \"http://127.0.0.1:\$HARNESS_ASIDE_IDE_PORT/status?state=\$STATE&token=\$HARNESS_ASIDE_IDE_TOKEN\" >/dev/null 2>&1 || true; echo '{\"decision\":\"allow\"}'"

        fun postToolUseCommand(): String =
            "PAYLOAD=\$(cat); echo \"\$PAYLOAD\" | grep -qE '\"error\":[[:space:]]*\"[^\"]+' && STATE=\"idle\" || STATE=\"thinking\"; [ -n \"\$HARNESS_ASIDE_IDE_PORT\" ] && curl -s -m 2 --connect-timeout 1 -X POST \"http://127.0.0.1:\$HARNESS_ASIDE_IDE_PORT/status?state=\$STATE&token=\$HARNESS_ASIDE_IDE_TOKEN\" >/dev/null 2>&1 || true; echo '{}'"

        /**
         * Adds or replaces the `harness-aside` hook group in [root].
         */
        fun merge(root: JsonObject): JsonObject {
            val group = JsonObject()

            // SessionStart -> idle
            val sessionStart = JsonArray().apply {
                add(createCommandHook(curlCommand("idle", "{}")))
            }
            group.add("SessionStart", sessionStart)

            // PreInvocation -> thinking
            val preInvocation = JsonArray().apply {
                add(createCommandHook(curlCommand("thinking", "{}")))
            }
            group.add("PreInvocation", preInvocation)

            // PreToolUse -> waiting for interactive tools (run_command, ask_question, file write/edit), else tool
            val preToolUse = JsonArray().apply {
                val toolObj = JsonObject().apply {
                    addProperty("matcher", "*")
                    val hooks = JsonArray().apply {
                        add(createCommandHook(preToolUseCommand()))
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
                        add(createCommandHook(postToolUseCommand()))
                    }
                    add("hooks", hooks)
                }
                add(toolObj)
            }
            group.add("PostToolUse", postToolUse)

            // PostInvocation -> idle
            val postInvocation = JsonArray().apply {
                add(createCommandHook(curlCommand("idle", "{}")))
            }
            group.add("PostInvocation", postInvocation)

            // Stop -> idle
            val stop = JsonArray().apply {
                add(createCommandHook(curlCommand("idle", "{}")))
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
