package com.github.baenar.harnessaside.claude

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

/** Installing the push-based HTTP hooks into `.claude/settings.json`. */
class ClaudeStatusHooksTest {

    private fun claudeDir(): Path = Files.createTempDirectory("aside-project").resolve(".claude")

    private fun settingsOf(dir: Path): JsonObject =
        JsonParser.parseString(Files.readString(dir.resolve("settings.json"))).asJsonObject

    private fun hookCommands(dir: Path, event: String): List<String> {
        val hooks = settingsOf(dir).getAsJsonObject("hooks")?.getAsJsonArray(event) ?: return emptyList()
        return hooks.flatMap { entry ->
            entry.asJsonObject.getAsJsonArray("hooks").map { it.asJsonObject.get("command").asString }
        }
    }

    @Test
    fun `install writes curl HTTP commands for every hook event`() {
        val dir = claudeDir()
        assertFalse(ClaudeStatusHooks(dir).isInstalled())
        assertTrue(ClaudeStatusHooks(dir).install())
        assertTrue(ClaudeStatusHooks(dir).isInstalled())

        val hooks = settingsOf(dir).getAsJsonObject("hooks")
        assertEquals(ClaudeStatusHooks.HOOKS.map { it.event }.toSet(), hooks.keySet())

        val submitCmd = hookCommands(dir, "UserPromptSubmit").single()
        assertTrue(submitCmd.contains("curl -s -m 2 --connect-timeout 1 -X POST"))
        assertTrue(submitCmd.contains("state=thinking"))
        assertTrue(submitCmd.contains("HARNESS_ASIDE_IDE_PORT"))
        assertTrue(submitCmd.contains("HARNESS_ASIDE_IDE_TOKEN"))
        assertTrue(submitCmd.contains("[ -n \"\$HARNESS_ASIDE_IDE_PORT\" ]"))

        val toolCmd = hookCommands(dir, "PreToolUse").single()
        assertTrue(toolCmd.contains("state=tool"))

        val stopCmd = hookCommands(dir, "Stop").single()
        assertTrue(stopCmd.contains("state=idle"))

        val deniedCmd = hookCommands(dir, "PermissionDenied").single()
        assertTrue("an auto-mode denial doesn't end the turn", deniedCmd.contains("state=thinking"))

        val stopFailureCmd = hookCommands(dir, "StopFailure").single()
        assertTrue(stopFailureCmd.contains("state=idle"))
    }

    @Test
    fun `install is repeatable without stacking up duplicates`() {
        val dir = claudeDir()
        ClaudeStatusHooks(dir).install()
        ClaudeStatusHooks(dir).install()
        ClaudeStatusHooks(dir).install()

        assertEquals(1, hookCommands(dir, "Stop").size)
        assertEquals(1, hookCommands(dir, "UserPromptSubmit").size)
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
        assertTrue(stop.any { it.contains("HARNESS_ASIDE_IDE_PORT") })
    }

    @Test
    fun `install replaces entries from an older plugin version`() {
        val dir = claudeDir()
        Files.createDirectories(dir)
        // Legacy file-based hook command
        Files.writeString(
            dir.resolve("settings.json"),
            """
            {
              "hooks": {
                "UserPromptSubmit": [ { "hooks": [ { "type": "command",
                  "command": "sh \"${'$'}CLAUDE_PROJECT_DIR/.claude/aside-status.sh\" thinking" } ] } ]
              }
            }
            """.trimIndent(),
        )

        assertTrue(ClaudeStatusHooks(dir).install())

        val commands = hookCommands(dir, "UserPromptSubmit")
        assertEquals("the old entry must be replaced, not joined", 1, commands.size)
        assertTrue(commands.single().contains("HARNESS_ASIDE_IDE_PORT"))
        assertFalse(commands.single().contains("aside-status.sh"))
    }

    @Test
    fun `notifications only wait for dialogs, and the idle reminder means idle`() {
        val dir = claudeDir()
        assertTrue(ClaudeStatusHooks(dir).install())

        val byMatcher = settingsOf(dir).getAsJsonObject("hooks").getAsJsonArray("Notification").associate { entry ->
            val obj = entry.asJsonObject
            obj.get("matcher")?.asString to obj.getAsJsonArray("hooks").single().asJsonObject.get("command").asString
        }
        assertFalse("an unmatched Notification hook fires for every notification type", byMatcher.containsKey(null))
        val waiting = byMatcher.filterValues { it.contains("state=waiting") }.keys.single()!!.split('|')
        assertTrue(waiting.contains("permission_prompt"))
        assertFalse("the idle reminder must never look like a dialog", waiting.contains("idle_prompt"))
        assertTrue(byMatcher.getValue("idle_prompt").contains("state=idle"))
    }

    @Test
    fun `reinstalling replaces an older unmatched Notification hook`() {
        val dir = claudeDir()
        Files.createDirectories(dir)
        val old = ClaudeStatusHooks.curlCommand("waiting").replace("\"", "\\\"")
        Files.writeString(dir.resolve("settings.json"), """{"hooks":{"Notification":[{"hooks":[{"type":"command","command":"$old"}]}]}}""")

        assertTrue(ClaudeStatusHooks(dir).install())

        val entries = settingsOf(dir).getAsJsonObject("hooks").getAsJsonArray("Notification")
        assertTrue(entries.all { it.asJsonObject.has("matcher") })
        assertEquals(ClaudeStatusHooks.HOOKS.count { it.event == "Notification" }, entries.size())
    }

    @Test
    fun `install cleans up legacy script and status files`() {
        val dir = claudeDir()
        Files.createDirectories(dir)
        val legacyScript = dir.resolve(ClaudeStatusHooks.SCRIPT_FILE)
        val legacyStatus = dir.resolve(ClaudeStatusHooks.STATUS_DIR)
        Files.writeString(legacyScript, "#!/bin/sh\nexit 1")
        Files.writeString(legacyStatus, "thinking")

        assertTrue(ClaudeStatusHooks(dir).install())
        val scriptContent = Files.readString(legacyScript)
        assertTrue("must neutralize legacy script to exit 0", scriptContent.contains("exit 0"))
        assertFalse(Files.isRegularFile(legacyStatus))
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
}
