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
import java.util.concurrent.CompletableFuture
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
        server.ensureStarted()
    }

    @After
    fun tearDown() {
        server.dispose()
    }

    @Test
    fun `a turn with a permission prompt and a subagent ends idle`() {
        val output = run(
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
            output,
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

    /**
     * Each step is a hook name or an event JSON for the `event` hook. Node exits once every request is done;
     * returns what it printed, which names any request the plugin dropped.
     */
    private fun run(vararg steps: String, port: Int? = server.port): String {
        val home = tmp.newFolder().path
        OpenCodeStatusPlugin.install(home)
        val dir = tmp.root.toPath()
        // .mjs: older node won't load ESM from a .js
        Files.copy(OpenCodeStatusPlugin.pluginDir(home).resolve(OpenCodeStatusPlugin.FILE), dir.resolve("plugin.mjs"))
        // A file, not `node -e`: on Windows Java passes `"` inside arguments unescaped (see HookShell).
        val json = steps.joinToString(",", "[", "]") { JsonPrimitive(it).toString() }
        val driver = Files.writeString(dir.resolve("driver.mjs"), """
            import { HarnessAsideStatus } from "./plugin.mjs"
            const port = process.env.HARNESS_ASIDE_IDE_PORT
            if (port) await fetch(`http://127.0.0.1:${'$'}{port}/status`).then((r) => r.text()).catch((e) => console.log(`warm-up failed: ${'$'}{e}`))
            const send = globalThis.fetch
            globalThis.fetch = async (url, init) => {
              const start = performance.now()
              try { return await send(url, init) } catch (e) {
                console.log(`dropped ${'$'}{url} after ${'$'}{Math.round(performance.now() - start)} ms: ${'$'}{e.name} ${'$'}{e.cause?.code ?? e.message}`)
                throw e
              }
            }
            const hooks = await HarnessAsideStatus({})
            for (const step of $json)
              step.startsWith("{") ? await hooks.event?.({ event: JSON.parse(step) }) : await hooks[step]?.({}, {})
        """.trimIndent())
        val builder = ProcessBuilder("node", driver.toString()).redirectErrorStream(true)
        builder.environment().remove("HARNESS_ASIDE_IDE_PORT")
        if (port != null) {
            builder.environment()["HARNESS_ASIDE_IDE_PORT"] = port.toString()
            builder.environment()["HARNESS_ASIDE_IDE_TOKEN"] = server.token
        }
        val process = builder.start()
        val output = CompletableFuture.supplyAsync { process.inputStream.readAllBytes().toString(Charsets.UTF_8) }
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            throw AssertionError("node did not exit")
        }
        val printed = output.get(5, TimeUnit.SECONDS)
        assertEquals("node failed: $printed", 0, process.exitValue())
        return printed
    }
}
