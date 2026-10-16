package com.github.baenar.harnessaside.antigravity

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

class AntigravityStatusHooksTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `isInstalled detects configured hooks`() {
        val dir = tmp.newFolder(".agents").toPath()
        val hooks = AntigravityStatusHooks(dir)

        assertFalse(hooks.isInstalled())

        Files.writeString(dir.resolve("hooks.json"), """{"harness-aside": {}}""")
        assertTrue(hooks.isInstalled())
    }

    @Test
    fun `install creates hooks file and merges cleanly`() {
        val dir = tmp.root.toPath().resolve(".gemini").resolve("config")
        val hooks = AntigravityStatusHooks(dir)

        assertTrue(hooks.install())
        assertTrue(hooks.isInstalled())

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
    fun `curl commands contain port token and stdout json`() {
        val thinkingCmd = AntigravityStatusHooks.curlCommand("thinking", "{}")
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
}
