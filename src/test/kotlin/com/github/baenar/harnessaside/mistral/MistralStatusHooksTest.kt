package com.github.baenar.harnessaside.mistral

import com.github.baenar.harnessaside.core.HarnessStatus
import com.github.baenar.harnessaside.core.HarnessStatusServer
import com.github.baenar.harnessaside.core.HookShell
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The block in `~/.vibe/hooks.toml`, and its commands run the way Vibe 3.1.4 runs them: through `sh` (Git Bash on
 * Windows), with the event's JSON on stdin, against a live server.
 */
class MistralStatusHooksTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val server = HarnessStatusServer()
    private val received = CopyOnWriteArrayList<HarnessStatus>()
    private lateinit var token: String

    @Before
    fun setUp() {
        token = server.register { received.add(it) }
        server.ensureStarted()
    }

    @After
    fun tearDown() {
        server.dispose()
    }

    @Test
    fun `install keeps the user's hooks and replaces only its own block`() {
        val dir = tmp.newFolder(".vibe").toPath()
        val file = dir.resolve("hooks.toml")
        val users = "[[hooks]]\nname = \"mine\"\ntype = \"pre_tool\"\ncommand = \"echo hi\"\n"
        Files.writeString(file, users)

        assertTrue(MistralStatusHooks(dir).install())
        val installed = Files.readString(file)
        assertTrue(installed.startsWith(users))
        assertEquals(MistralStatusHooks.HOOKS.size, Regex("""(?m)^\[\[hooks]]$""").findAll(installed).count() - 1)

        assertTrue(MistralStatusHooks(dir).install())
        assertEquals("a second install changes nothing", installed, Files.readString(file))

        assertTrue(MistralStatusHooks(dir, windows = true).install())
        val windows = Files.readString(file)
        assertEquals(1, windows.split(MistralStatusHooks.BEGIN).size - 1)
        assertTrue(windows.contains("curl.exe"))
        assertFalse(windows.contains("cat >/dev/null"))
    }

    @Test
    fun `each event reports its state, reading a big payload and printing nothing`() {
        val expected = listOf(
            Triple("session_start", null, HarnessStatus.IDLE),
            Triple("pre_agent", null, HarnessStatus.THINKING),
            Triple("pre_tool", null, HarnessStatus.EXECUTING_TOOL),
            Triple("notification", "approval_requested", HarnessStatus.WAITING),
            Triple("post_tool", null, HarnessStatus.THINKING),
            Triple("notification", "question_asked", HarnessStatus.WAITING),
            Triple("notification", "turn_completed", HarnessStatus.DONE),
            Triple("notification", "turn_failed", HarnessStatus.IDLE),
            Triple("session_end", null, HarnessStatus.IDLE),
        )
        assertEquals("every hook is checked", MistralStatusHooks.HOOKS.size, expected.size)

        val env = mapOf("HARNESS_ASIDE_IDE_PORT" to server.port.toString(), "HARNESS_ASIDE_IDE_TOKEN" to token)
        for ((type, match, status) in expected) {
            received.clear()
            val result = run(type, match, env)
            assertEquals("$type $match exit, stderr: ${result.stderr}", 0, result.exitCode)
            assertEquals("$type $match stdout", "", result.stdout)
            assertEquals("$type $match", listOf(status), received)
        }
    }

    @Test
    fun `outside our terminal a hook does nothing and still succeeds`() {
        val result = run("post_tool", null, mapOf("HARNESS_ASIDE_IDE_PORT" to null, "HARNESS_ASIDE_IDE_TOKEN" to null))
        assertEquals(0, result.exitCode)
        assertEquals("", result.stdout)
        assertTrue(received.isEmpty())
    }

    private fun run(type: String, match: String?, env: Map<String, String?>): HookShell.Result {
        val hook = MistralStatusHooks.HOOKS.single { it.type == type && it.match == match }
        val payload = """{"session_id":"s","hook_event_name":"$type","tool_output_text":"${"x".repeat(200_000)}"}"""
        return HookShell.run(MistralStatusHooks.command(hook.state, HookShell.isWindows), tmp.root.toPath(), env, payload, posix = true)
    }
}
