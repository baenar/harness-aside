package com.github.baenar.harnessaside

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/** Installing the hooks into `.claude/settings.json`, and the script they run. */
class ClaudeStatusHooksTest {

    private fun claudeDir(): Path = Files.createTempDirectory("aside-project").resolve(".claude")

    private fun settingsOf(dir: Path): JsonObject =
        JsonParser.parseString(Files.readString(dir.resolve("settings.json"))).asJsonObject

    private fun hookCommands(dir: Path, event: String): List<String> {
        val hooks = settingsOf(dir).getAsJsonObject("hooks").getAsJsonArray(event) ?: return emptyList()
        return hooks.flatMap { entry ->
            entry.asJsonObject.getAsJsonArray("hooks").map { it.asJsonObject.get("command").asString }
        }
    }

    // --- installing ------------------------------------------------------------------

    @Test
    fun `install writes the script and every hook event`() {
        val dir = claudeDir()
        assertFalse(ClaudeStatusHooks(dir).isInstalled())
        assertTrue(ClaudeStatusHooks(dir).install())

        assertTrue(Files.exists(dir.resolve(ClaudeStatusHooks.SCRIPT_FILE)))
        assertTrue(ClaudeStatusHooks(dir).isInstalled())

        val hooks = settingsOf(dir).getAsJsonObject("hooks")
        assertEquals(ClaudeStatusHooks.EVENT_TO_WORD.keys, hooks.keySet())
        assertEquals(listOf("sh \"\$CLAUDE_PROJECT_DIR/.claude/aside-status.sh\" thinking"), hookCommands(dir, "UserPromptSubmit"))
        assertEquals(listOf("sh \"\$CLAUDE_PROJECT_DIR/.claude/aside-status.sh\" waiting"), hookCommands(dir, "Notification"))
        assertEquals(listOf("sh \"\$CLAUDE_PROJECT_DIR/.claude/aside-status.sh\" end"), hookCommands(dir, "SessionEnd"))
    }

    @Test
    fun `install is repeatable without stacking up duplicates`() {
        val dir = claudeDir()
        ClaudeStatusHooks(dir).install()
        ClaudeStatusHooks(dir).install()
        ClaudeStatusHooks(dir).install()

        assertEquals(1, hookCommands(dir, "Stop").size)
    }

    @Test
    fun `install keeps settings and hooks that are not ours`() {
        val dir = claudeDir()
        Files.createDirectories(dir)
        Files.writeString(
            dir.resolve("settings.json"),
            """
            {
              "permissions": { "allow": ["Bash(git status)"] },
              "hooks": {
                "Stop": [ { "hooks": [ { "type": "command", "command": "notify-send done" } ] } ]
              }
            }
            """.trimIndent(),
        )

        assertTrue(ClaudeStatusHooks(dir).install())

        val root = settingsOf(dir)
        assertEquals(
            "unrelated settings must survive",
            "Bash(git status)",
            root.getAsJsonObject("permissions").getAsJsonArray("allow")[0].asString,
        )
        val stop = hookCommands(dir, "Stop")
        assertTrue("someone else's hook must survive", stop.contains("notify-send done"))
        assertTrue(stop.any { it.contains(ClaudeStatusHooks.SCRIPT_FILE) })
    }

    @Test
    fun `install replaces entries from an older plugin version`() {
        val dir = claudeDir()
        Files.createDirectories(dir)
        // Pre-3.1 format: one shared status file, written inline instead of via the script.
        Files.writeString(
            dir.resolve("settings.json"),
            """
            {
              "hooks": {
                "UserPromptSubmit": [ { "hooks": [ { "type": "command",
                  "command": "printf %s thinking > \"${'$'}CLAUDE_PROJECT_DIR/.claude/aside-status\"" } ] } ]
              }
            }
            """.trimIndent(),
        )

        assertTrue(ClaudeStatusHooks(dir).install())

        val commands = hookCommands(dir, "UserPromptSubmit")
        assertEquals("the old entry must be replaced, not joined", 1, commands.size)
        assertTrue(commands.single().contains(ClaudeStatusHooks.SCRIPT_FILE))
    }

    @Test
    fun `install clears the plain status file that older versions left behind`() {
        val dir = claudeDir()
        Files.createDirectories(dir)
        // The path is a directory now; a stale file there would block it.
        Files.writeString(dir.resolve(ClaudeStatusHooks.STATUS_DIR), "thinking")

        assertTrue(ClaudeStatusHooks(dir).install())
        assertFalse(Files.isRegularFile(dir.resolve(ClaudeStatusHooks.STATUS_DIR)))
    }

    @Test
    fun `isInstalled copes with a missing or unreadable settings file`() {
        val dir = claudeDir()
        assertFalse("nothing there at all", ClaudeStatusHooks(dir).isInstalled())

        Files.createDirectories(dir)
        Files.writeString(dir.resolve("settings.json"), "{ not json")
        assertFalse("must not throw on a broken file", ClaudeStatusHooks(dir).isInstalled())
    }

    @Test
    fun `merge leaves an unrelated document otherwise untouched`() {
        val root = JsonParser.parseString("""{ "model": "opus", "hooks": {} }""").asJsonObject
        ClaudeStatusHooks.merge(root)
        assertEquals("opus", root.get("model").asString)
    }

    // --- the reporter script ---------------------------------------------------------

    /**
     * `sh` on PATH, or Git-for-Windows' copy — the Gradle JVM on Windows does not inherit
     * a PATH containing it, though the shells Claude's hooks run in do.
     */
    private fun shell(): String? = sequenceOf(
        "sh",
        "C:\\Program Files\\Git\\bin\\sh.exe",
        "C:\\Program Files\\Git\\usr\\bin\\sh.exe",
    ).firstOrNull { candidate ->
        runCatching {
            ProcessBuilder(candidate, "-c", "exit 0").start().waitFor(30, TimeUnit.SECONDS)
        }.getOrDefault(false)
    }

    /**
     * Runs the generated reporter script through a real `sh` and returns the state files it
     * left behind as `name=contents`.
     *
     * The script is the one place where a stray backslash breaks the status indicator
     * silently — the hook still exits 0 and Claude reports nothing — so it is worth
     * executing rather than string-matching. Skipped where `sh` is unavailable.
     */
    private fun runScript(word: String, payload: String, projectDir: Path? = null): List<String> {
        val sh = shell()
        assumeTrue("no POSIX sh on this machine", sh != null)

        val root = projectDir ?: Files.createTempDirectory("aside-project")
        val script = root.resolve("aside-status.sh")
        Files.writeString(script, ClaudeStatusHooks.SCRIPT)

        val process = ProcessBuilder(sh, script.toString(), word)
            .apply { environment()["CLAUDE_PROJECT_DIR"] = root.toString() }
            .redirectErrorStream(true)
            .start()
        process.outputStream.use { it.write(payload.toByteArray()) }
        assertTrue("script must not hang", process.waitFor(30, TimeUnit.SECONDS))
        assertEquals("hook script must always exit 0", 0, process.exitValue())

        val statusDir = root.resolve(ClaudeStatusHooks.CLAUDE_DIR).resolve(ClaudeStatusHooks.STATUS_DIR)
        if (!Files.isDirectory(statusDir)) return emptyList()
        return Files.list(statusDir).use { stream ->
            stream.map { "${it.fileName}=${Files.readString(it)}" }.sorted().toList()
        }
    }

    private fun payload(sessionId: String, event: String = "UserPromptSubmit") =
        """{"session_id":"$sessionId","transcript_path":"/home/x/.claude/projects/p/s.jsonl",""" +
            """"cwd":"/home/x/p","hook_event_name":"$event"}"""

    @Test
    fun `script writes the status word into a file named after the session`() {
        assertEquals(
            listOf("5f9d1a20-1111-4b3c-9d55-2b0a7c1e4d88=thinking"),
            runScript("thinking", payload("5f9d1a20-1111-4b3c-9d55-2b0a7c1e4d88")),
        )
    }

    @Test
    fun `script keeps sessions apart`() {
        val root = Files.createTempDirectory("aside-project")
        runScript("thinking", payload("aaaaaaaa-0000-4000-8000-000000000001"), root)
        runScript("waiting", payload("bbbbbbbb-0000-4000-8000-000000000002"), root)

        // The second run reports the whole directory, so both sessions show up side by side.
        assertEquals(
            listOf(
                "aaaaaaaa-0000-4000-8000-000000000001=thinking",
                "bbbbbbbb-0000-4000-8000-000000000002=waiting",
            ),
            runScript("waiting", payload("bbbbbbbb-0000-4000-8000-000000000002"), root),
        )
    }

    @Test
    fun `script overwrites rather than appends`() {
        val root = Files.createTempDirectory("aside-project")
        val id = "cccccccc-0000-4000-8000-000000000003"
        runScript("thinking", payload(id), root)
        assertEquals(listOf("$id=waiting"), runScript("waiting", payload(id), root))
    }

    @Test
    fun `the end word removes the session's state file`() {
        val root = Files.createTempDirectory("aside-project")
        val id = "dddddddd-0000-4000-8000-000000000004"
        runScript("thinking", payload(id), root)
        assertEquals(emptyList<String>(), runScript("end", payload(id, "SessionEnd"), root))
    }

    @Test
    fun `script falls back to a placeholder when the payload carries no session id`() {
        assertEquals(listOf("unknown=waiting"), runScript("waiting", """{"hook_event_name":"Notification"}"""))
    }
}
