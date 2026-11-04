package com.github.baenar.harnessaside.opencode

import com.github.baenar.harnessaside.core.HarnessStatus
import com.github.baenar.harnessaside.core.HarnessStatusServer
import com.google.gson.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * Installs the OpenCode status plugin and drives its hooks under `node` (OpenCode runs it under Bun; both
 * have `fetch`) against a live server. Event names and payloads follow `@opencode-ai/sdk` 1.18.21.
 */
class OpenCodeStatusPluginTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val server = HarnessStatusServer()
    private val received = CopyOnWriteArrayList<HarnessStatus>()

    @Before
    fun setUp() {
        server.statusListener = { received.add(it) }
        server.start()
    }

    @After
    fun tearDown() {
        server.dispose()
    }

    @Test
    fun `a turn with a permission prompt and a subagent ends idle`() {
        run(
            """{"type":"session.status","properties":{"sessionID":"main","status":{"type":"busy"}}}""",
            "tool.execute.before",
            """{"type":"permission.asked","properties":{"sessionID":"main"}}""",
            """{"type":"permission.replied","properties":{"sessionID":"main","response":"once"}}""",
            "tool.execute.after",
            """{"type":"session.status","properties":{"sessionID":"child","status":{"type":"busy"}}}""",
            """{"type":"session.status","properties":{"sessionID":"child","status":{"type":"idle"}}}""",
            """{"type":"session.status","properties":{"sessionID":"main","status":{"type":"idle"}}}""",
        )
        assertEquals(
            listOf(
                HarnessStatus.THINKING, HarnessStatus.EXECUTING_TOOL, HarnessStatus.WAITING, HarnessStatus.THINKING,
                HarnessStatus.THINKING, HarnessStatus.THINKING, // the subagent finishing is not the turn finishing
                HarnessStatus.IDLE,
            ),
            received,
        )
    }

    @Test
    fun `outside a Harness Aside terminal it sends nothing`() {
        run("tool.execute.before", port = null)
        assertEquals(emptyList<HarnessStatus>(), received)
    }

    /** Each step is a hook name or an event JSON for the `event` hook. Node exits once every request is done. */
    private fun run(vararg steps: String, port: Int? = server.port) {
        val home = tmp.newFolder().path
        OpenCodeStatusPlugin.install(home)
        val plugin = tmp.root.toPath().resolve("plugin.mjs") // .mjs: older node won't load ESM from a .js
        Files.copy(OpenCodeStatusPlugin.pluginDir(home).resolve(OpenCodeStatusPlugin.FILE), plugin)
        val script = """
            const { HarnessAsideStatus } = await import(require("url").pathToFileURL(process.argv[1]))
            const hooks = await HarnessAsideStatus({})
            for (const step of JSON.parse(process.argv[2]))
              step.startsWith("{") ? await hooks.event?.({ event: JSON.parse(step) }) : await hooks[step]?.({}, {})
        """.trimIndent()
        val json = steps.joinToString(",", "[", "]") { JsonPrimitive(it).toString() }
        val builder = ProcessBuilder("node", "--input-type=commonjs", "-e", "(async () => { $script })()", plugin.toString(), json)
            .redirectErrorStream(true)
        builder.environment().remove("HARNESS_ASIDE_IDE_PORT")
        if (port != null) {
            builder.environment()["HARNESS_ASIDE_IDE_PORT"] = port.toString()
            builder.environment()["HARNESS_ASIDE_IDE_TOKEN"] = server.token
        }
        val process = builder.start()
        val output = process.inputStream.readAllBytes().toString(Charsets.UTF_8)
        if (!process.waitFor(10, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            throw AssertionError("node did not exit: $output")
        }
        assertEquals("node failed: $output", 0, process.exitValue())
    }
}
