package com.github.baenar.harnessaside.mistral

import com.github.baenar.harnessaside.claude.ClaudeSessions
import com.github.baenar.harnessaside.core.isWindowsTarget
import com.github.baenar.harnessaside.core.postStatusCommand
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Installs push-based status hooks for Mistral Vibe into the user's `~/.vibe/hooks.toml`.
 *
 * Our entries sit in one block between [BEGIN] and [END], replaced on every install; the rest of the file is the
 * user's and stays as it is, so no TOML parser is needed. Every command reads the event's JSON from stdin (Vibe can
 * write a big one, such as a tool's whole output) and prints nothing: Vibe takes non-empty stdout for a JSON reply
 * and warns when it isn't one.
 *
 * Vibe runs hook commands with `sh` on Linux/macOS/WSL, and on native Windows with Git Bash, or PowerShell where
 * there is none. [windows] picks a command those two read the same way.
 */
class MistralStatusHooks(private val vibeDir: Path, private val windows: Boolean = false) {

    private val hooksPath: Path get() = vibeDir.resolve("hooks.toml")

    /** Writes our block into `hooks.toml`, creating it if needed; an unchanged file isn't rewritten. */
    fun install(): Boolean = try {
        Files.createDirectories(vibeDir)
        val current = if (Files.exists(hooksPath)) Files.readString(hooksPath) else ""
        val merged = merge(current, windows)
        if (merged != current) Files.writeString(hooksPath, merged)
        true
    } catch (e: Exception) {
        LOG.warn("Could not install status hooks into $hooksPath", e)
        false
    }

    companion object {
        private val LOG = logger<MistralStatusHooks>()

        const val BEGIN = "# >>> harness-aside: IDE status hooks, rewritten by the plugin on every launch >>>"
        const val END = "# <<< harness-aside <<<"

        // ponytail: ignores VIBE_HOME, which moves Vibe's config elsewhere; honour it if someone sets it.
        fun forProject(project: Project, userHome: String = System.getProperty("user.home")): MistralStatusHooks? {
            val projectPath = project.basePath ?: return null
            val storage = ClaudeSessions.storage(userHome, projectPath)
            val windows = isWindowsTarget(System.getProperty("os.name"), storage.userHome)
            return MistralStatusHooks(Paths.get(storage.userHome, ".vibe"), windows)
        }

        /** One `[[hooks]]` entry: Vibe's hook [type], what it must [match] (a notification's type), and our [state]. */
        data class Hook(val type: String, val state: String, val match: String? = null) {
            val name get() = "harness-aside-$type" + (match?.let { "-$it" } ?: "")
        }

        /**
         * `pre_tool` fires before the approval prompt, so a tool shows as running until `approval_requested`
         * follows it. `turn_completed` doesn't fire when the user interrupts the turn, which keeps that silent.
         */
        val HOOKS = listOf(
            Hook("session_start", "idle"),
            Hook("pre_agent", "thinking"),
            Hook("pre_tool", "tool"),
            Hook("post_tool", "thinking"),
            Hook("notification", "waiting", "approval_requested"),
            Hook("notification", "waiting", "question_asked"),
            Hook("notification", "done", "turn_completed"),
            Hook("notification", "idle", "turn_failed"),
            Hook("session_end", "idle"),
        )

        fun command(state: String, windows: Boolean): String =
            if (windows) windowsCommand(state) else "cat >/dev/null; ${postStatusCommand(state)}"

        /**
         * Valid Git Bash and PowerShell alike: `curl.exe` takes the port and token from the environment itself
         * (`--variable`, curl 8.3+), sends stdin as the body, which also drains it, writes the reply to `NUL`, and
         * `exit 0` hides any failure. Outside our terminal the port defaults to 0 and the request just fails.
         */
        fun windowsCommand(state: String): String =
            "curl.exe -s -m 2 --connect-timeout 1 -o NUL --variable %HARNESS_ASIDE_IDE_PORT=0 " +
                "--variable %HARNESS_ASIDE_IDE_TOKEN= --data-binary \"@-\" --expand-url " +
                "\"http://127.0.0.1:{{HARNESS_ASIDE_IDE_PORT}}/status?state=$state&token={{HARNESS_ASIDE_IDE_TOKEN}}\"; exit 0"

        /** [current] with our block, of commands for [windows], replacing any earlier one and moved to the end. */
        fun merge(current: String, windows: Boolean = false): String {
            val start = current.indexOf(BEGIN)
            val end = if (start < 0) -1 else current.indexOf(END, start)
            val user = (if (end < 0) current else current.removeRange(start, end + END.length)).trimEnd()
            val block = HOOKS.joinToString("\n", "$BEGIN\n", "$END\n") { hook ->
                buildString {
                    appendLine("[[hooks]]")
                    appendLine("name = ${literal(hook.name)}")
                    appendLine("type = ${literal(hook.type)}")
                    hook.match?.let { appendLine("match = ${literal(it)}") }
                    appendLine("command = ${literal(command(hook.state, windows))}")
                }
            }
            return if (user.isEmpty()) block else "$user\n\n$block"
        }

        /** A TOML literal string: no escapes, so the commands go in exactly as written. */
        private fun literal(s: String): String {
            require('\'' !in s && '\n' !in s) { "can't be a TOML literal string: $s" }
            return "'$s'"
        }
    }
}
