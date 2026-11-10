package com.github.baenar.harnessaside.antigravity

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

class AntigravityStatusHooksTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `install creates hooks file and merges cleanly`() {
        val dir = tmp.root.toPath().resolve(".gemini").resolve("config")
        val hooks = AntigravityStatusHooks(dir)

        assertTrue(hooks.install())

        val content = Files.readString(dir.resolve("hooks.json"))
        val json = JsonParser.parseString(content).asJsonObject
        assertTrue(json.has("harness-aside"))

        val group = json.getAsJsonObject("harness-aside")
        assertTrue(group.has("SessionStart"))
        assertTrue(group.has("PreInvocation"))
        assertTrue(group.has("PreToolUse"))
        assertTrue(group.has("PostToolUse"))
        assertTrue(group.has("PostInvocation"))
        assertTrue(group.has("Stop"))
    }

    @Test
    fun `merge preserves unrelated existing hooks`() {
        val existing = JsonObject().apply {
            val custom = JsonObject().apply {
                addProperty("enabled", true)
            }
            add("my-custom-hook", custom)
        }

        val merged = AntigravityStatusHooks.merge(existing)
        assertTrue(merged.has("my-custom-hook"))
        assertTrue(merged.has("harness-aside"))
        assertEquals(true, merged.getAsJsonObject("my-custom-hook").get("enabled").asBoolean)
    }

    @Test
    fun `a hooks file that doesn't parse is left alone`() {
        val dir = tmp.newFolder("broken").toPath()
        val file = dir.resolve("hooks.json")
        Files.writeString(file, "{ the user's hooks, mid-edit")

        assertFalse(AntigravityStatusHooks(dir).install())
        assertEquals("{ the user's hooks, mid-edit", Files.readString(file))
    }

    @Test
    fun `curl commands contain port token and stdout json`() {
        val thinkingCmd = AntigravityStatusHooks.curlCommand("thinking")
        assertTrue(thinkingCmd.contains("HARNESS_ASIDE_IDE_PORT"))
        assertTrue(thinkingCmd.contains("HARNESS_ASIDE_IDE_TOKEN"))
        assertTrue(thinkingCmd.contains("state=thinking"))
        assertTrue(thinkingCmd.contains("[ -n \"\$HARNESS_ASIDE_IDE_PORT\" ]"))
        assertTrue(thinkingCmd.contains("echo '{}'"))

        val preToolCmd = AntigravityStatusHooks.preToolUseCommand()
        assertTrue(preToolCmd.contains("run_command"))
        assertTrue(preToolCmd.contains("waiting"))
        assertTrue(preToolCmd.contains("allow"))

        val postToolCmd = AntigravityStatusHooks.postToolUseCommand()
        assertTrue(postToolCmd.contains("error"))
        assertTrue(postToolCmd.contains("idle"))
        assertTrue(postToolCmd.contains("thinking"))
    }

    @Test
    fun `posix pre-tool command still matches every waiting tool`() {
        val cmd = AntigravityStatusHooks.preToolUseCommand()
        assertTrue(cmd.contains("(run_command|ask_question|ask_permission|write_to_file|replace_file_content|multi_replace_file_content)"))
    }

    @Test
    fun `windows variant installs cmd-safe commands and the allow file`() {
        val dir = tmp.root.toPath().resolve(".gemini").resolve("config")
        assertTrue(AntigravityStatusHooks(dir, windows = true).install())

        assertEquals("""{"decision":"allow"}""", Files.readString(dir.resolve(AntigravityStatusHooks.ALLOW_FILE)))

        val group = JsonParser.parseString(Files.readString(dir.resolve("hooks.json")))
            .asJsonObject.getAsJsonObject("harness-aside")
        val commands = commandsOf(group)
        assertEquals(6, commands.size)
        for (cmd in commands) {
            assertTrue(cmd, cmd.startsWith("curl.exe "))
            assertFalse("double quotes reach cmd backslash-escaped: $cmd", cmd.contains('"'))
            assertFalse("not a cmd construct: $cmd", cmd.contains("\$") || cmd.contains("[ -n") || cmd.contains("/dev/null"))
            assertTrue(cmd, cmd.contains("%HARNESS_ASIDE_IDE_PORT%") && cmd.contains("X-Harness-Token:%HARNESS_ASIDE_IDE_TOKEN%"))
            assertTrue("a non-zero exit blocks the tool call: $cmd", cmd.endsWith("echo {}") || cmd.endsWith("exit 0"))
        }
        assertTrue(commands.any { it.contains("--data-binary @-") && it.contains("?event=PreToolUse") && it.contains("type ${AntigravityStatusHooks.ALLOW_FILE}") })
        assertTrue(commands.any { it.contains("--data-binary @-") && it.contains("?event=PostToolUse") })
        assertTrue(commands.any { it.contains("-X POST") && it.contains("?state=thinking") })
    }

    @Test
    fun `reinstalling for windows replaces a posix group`() {
        val dir = tmp.root.toPath().resolve(".gemini").resolve("config")
        assertTrue(AntigravityStatusHooks(dir).install())
        assertTrue(AntigravityStatusHooks(dir, windows = true).install())

        val group = JsonParser.parseString(Files.readString(dir.resolve("hooks.json")))
            .asJsonObject.getAsJsonObject("harness-aside")
        assertTrue(commandsOf(group).all { it.startsWith("curl.exe ") })
    }

    @Test
    fun `posix install writes no allow file`() {
        val dir = tmp.root.toPath().resolve(".gemini").resolve("config")
        assertTrue(AntigravityStatusHooks(dir).install())
        assertFalse(Files.exists(dir.resolve(AntigravityStatusHooks.ALLOW_FILE)))
    }

    @Test
    fun `windows target is native windows only`() {
        assertTrue(AntigravityStatusHooks.isWindowsTarget("Windows 11", "C:\\Users\\me"))
        assertFalse("WSL home read by agy under sh", AntigravityStatusHooks.isWindowsTarget("Windows 11", "//wsl$/Debian/home/me"))
        assertFalse(AntigravityStatusHooks.isWindowsTarget("Windows 11", "\\\\wsl.localhost\\Debian\\home\\me"))
        assertFalse(AntigravityStatusHooks.isWindowsTarget("Linux", "/home/me"))
        assertFalse(AntigravityStatusHooks.isWindowsTarget("Mac OS X", "/Users/me"))
    }

    private fun commandsOf(group: JsonObject): List<String> = group.entrySet().flatMap { (_, entries) ->
        entries.asJsonArray.flatMap { entry ->
            val handler = entry.asJsonObject
            if (handler.has("hooks")) handler.getAsJsonArray("hooks").map { it.asJsonObject } else listOf(handler)
        }
    }.map { it.get("command").asString }
}
