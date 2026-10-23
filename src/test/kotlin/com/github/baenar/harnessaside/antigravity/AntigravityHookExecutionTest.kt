package com.github.baenar.harnessaside.antigravity

import com.github.baenar.harnessaside.core.HarnessStatus
import com.github.baenar.harnessaside.core.HarnessStatusServer
import com.github.baenar.harnessaside.core.HookShell
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.StringReader
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Installs the hooks this machine would get, then executes every generated command the way `agy` does
 * (see [HookShell]) against a live [HarnessStatusServer]. No command string is written here, so a change
 * to how commands are generated is tested as-is on every OS in the CI matrix.
 *
 * The contract comes from what `agy` was seen to do: a non-zero exit blocks the tool call, and stdout that
 * isn't strict JSON fails the call ("failed to unmarshal result from hook ... via protojson").
 */
class AntigravityHookExecutionTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val server = HarnessStatusServer()
    private val received = CopyOnWriteArrayList<HarnessStatus>()
    private lateinit var configDir: Path

    private data class Case(val event: String, val stdin: String, val expected: HarnessStatus)

    private val cases = listOf(
        Case("SessionStart", payload(), HarnessStatus.IDLE),
        Case("PreInvocation", payload(), HarnessStatus.THINKING),
        Case("PreToolUse", preTool("run_command"), HarnessStatus.WAITING),
        Case("PreToolUse", preTool("view_file"), HarnessStatus.EXECUTING_TOOL),
        Case("PostToolUse", postTool(""), HarnessStatus.THINKING),
        Case("PostToolUse", postTool("user rejected the tool call"), HarnessStatus.IDLE),
        Case("PostInvocation", payload(), HarnessStatus.IDLE),
        Case("Stop", payload(""","executionNum":1,"terminationReason":"done","error":"","fullyIdle":true"""), HarnessStatus.IDLE),
    )

    @Before
    fun setUp() {
        server.statusListener = { received.add(it) }
        server.start()
        configDir = tmp.newFolder("config").toPath()
        val windows = AntigravityStatusHooks.isWindowsTarget(System.getProperty("os.name"), System.getProperty("user.home"))
        assertTrue(AntigravityStatusHooks(configDir, windows).install())

        val curl = HookShell.run(if (HookShell.isWindows) "curl.exe --version" else "curl --version", configDir)
        assertEquals("curl must be on PATH: ${curl.stderr}", 0, curl.exitCode)
    }

    @After
    fun tearDown() {
        server.dispose()
    }

    @Test
    fun `every hook reports its state and gives agy a valid reply`() {
        for (case in cases) {
            received.clear()
            val result = HookShell.run(command(configDir, case.event), configDir, ideEnv(), case.stdin)
            assertEquals("${case.event} ${case.stdin}", emptyList<String>(), violations(case.event, result))
            assertEquals("${case.event} ${case.stdin}", listOf(case.expected), received)
        }
    }

    @Test
    fun `hooks stay harmless outside the IDE terminal`() {
        assertHarmless("variables unset", ideEnv().mapValues { null })
    }

    @Test
    fun `hooks stay harmless when the IDE is gone`() {
        val stopped = HarnessStatusServer().apply { start() }
        val deadPort = stopped.port.toString()
        stopped.dispose()
        assertHarmless("server stopped", ideEnv() + mapOf("HARNESS_ASIDE_IDE_PORT" to deadPort))
    }

    @Test
    fun `hooks stay harmless with a stale token`() {
        assertHarmless("wrong token", ideEnv() + mapOf("HARNESS_ASIDE_IDE_TOKEN" to "stale"))
    }

    /** Hook cwd was only observed in `agy -p`; elsewhere the Windows allow-file reply may be empty, but must not block. */
    @Test
    fun `hooks never fail from another working directory`() {
        val elsewhere = tmp.newFolder("elsewhere").toPath()
        for (case in cases) {
            val result = HookShell.run(command(configDir, case.event), elsewhere, ideEnv(), case.stdin)
            assertEquals("${case.event}: ${result.stderr}", 0, result.exitCode)
        }
    }

    /** The bug seen on Windows: quotes in a command reach cmd as `\"`, so this exact probe hook broke every tool call. */
    @Test
    fun `a hook printing quoted JSON is caught`() {
        val result = HookShell.run("echo {\"decision\":\"allow\"}", configDir)
        val expected = if (HookShell.isWindows) "{\\\"decision\\\":\\\"allow\\\"}" else "{decision:allow}"
        assertEquals("runner must mangle quotes the way the real shell does", expected, result.stdout.trim())
        assertTrue(violations("PreToolUse", result).isNotEmpty())
    }

    /** Guards against a checker that passes anything: the other platform's commands must fail here. */
    @Test
    fun `the other platform's hooks are rejected on this machine`() {
        val otherDir = tmp.newFolder("other").toPath()
        assertTrue(AntigravityStatusHooks(otherDir, windows = !HookShell.isWindows).install())
        val result = HookShell.run(command(otherDir, "PreToolUse"), otherDir, ideEnv(), preTool("view_file"))
        assertTrue("$result", violations("PreToolUse", result).isNotEmpty())
    }

    private fun assertHarmless(scenario: String, env: Map<String, String?>) {
        for (case in cases) {
            received.clear()
            val result = HookShell.run(command(configDir, case.event), configDir, env, case.stdin)
            assertEquals("$scenario, ${case.event}", emptyList<String>(), violations(case.event, result))
            assertEquals("$scenario, ${case.event}", emptyList<HarnessStatus>(), received)
        }
    }

    private fun ideEnv(): Map<String, String?> = mapOf(
        "HARNESS_ASIDE_IDE_PORT" to server.port.toString(),
        "HARNESS_ASIDE_IDE_TOKEN" to server.token,
    )

    private fun violations(event: String, result: HookShell.Result): List<String> = buildList {
        if (result.exitCode != 0) add("exit ${result.exitCode} blocks the tool call: ${result.stderr}")
        val reply = strictJsonObject(result.stdout)
        if (reply == null) add("stdout is not a JSON object: '${result.stdout}'")
        else if (event == "PreToolUse" && reply.get("decision")?.asString != "allow") add("PreToolUse must allow: '${result.stdout}'")
    }

    companion object {
        private val GSON = Gson()

        /** Strict like agy's protojson; Gson's JsonParser is lenient and would accept `{decision:allow}`. */
        private fun strictJsonObject(text: String): JsonObject? = runCatching {
            val reader = JsonReader(StringReader(text))
            GSON.getAdapter(JsonObject::class.java).read(reader).also { check(reader.peek() == JsonToken.END_DOCUMENT) }
        }.getOrNull()

        private fun command(dir: Path, event: String): String {
            val entry = JsonParser.parseString(Files.readString(dir.resolve("hooks.json"))).asJsonObject
                .getAsJsonObject(AntigravityStatusHooks.HOOK_NAME).getAsJsonArray(event).single().asJsonObject
            val handler = entry.getAsJsonArray("hooks")?.single()?.asJsonObject ?: entry
            return handler.get("command").asString
        }

        /** Payload shapes from https://antigravity.google/docs/hooks?tab=cli; never captured from a live run. */
        private fun payload(extra: String = "") =
            """{"conversationId":"c1","workspacePaths":["/work"],"transcriptPath":"/t.jsonl","modelName":"m"$extra}"""

        private fun preTool(tool: String) = payload(""","toolCall":{"name":"$tool","args":{"path":"a.txt"}},"stepIdx":3""")

        private fun postTool(error: String) = payload(""","toolCall":{"name":"view_file","args":{}},"stepIdx":3,"error":"$error"""")
    }
}
