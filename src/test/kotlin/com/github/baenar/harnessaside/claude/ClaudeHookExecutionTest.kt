package com.github.baenar.harnessaside.claude

import com.github.baenar.harnessaside.core.HarnessStatus
import com.github.baenar.harnessaside.core.HarnessStatusServer
import com.github.baenar.harnessaside.core.HookShell
import com.google.gson.JsonParser
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Installs the Claude hooks and fires them the way Claude Code does: picks the entries whose matcher
 * selects the event, runs each through `sh` with the event's JSON on stdin, against a live server.
 * The payloads use the field names of Claude Code 2.1.312's hook input schema.
 */
class ClaudeHookExecutionTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val server = HarnessStatusServer()
    private val received = CopyOnWriteArrayList<HarnessStatus>()
    private lateinit var dir: Path

    @Before
    fun setUp() {
        server.statusListener = { received.add(it) }
        server.ensureStarted()
        dir = tmp.newFolder(".claude").toPath()
        ClaudeStatusHooks(dir).install()
    }

    @After
    fun tearDown() {
        server.dispose()
    }

    @Test
    fun `a whole turn ends idle`() {
        val turn = listOf(
            "UserPromptSubmit" to """{"prompt":"hi"}""",
            "PreToolUse" to """{"tool_name":"Bash"}""",
            "PermissionRequest" to """{"tool_name":"Bash"}""",
            "PostToolUseFailure" to """{"tool_name":"Bash","error":"Exit code 1","is_interrupt":false}""",
            "PostToolUse" to """{"tool_name":"Read"}""",
            "Stop" to """{"stop_hook_active":false}""",
        )
        val states = turn.map { (event, payload) -> fire(event, payload) }
        assertEquals(
            listOf(
                HarnessStatus.THINKING, HarnessStatus.EXECUTING_TOOL, HarnessStatus.WAITING,
                HarnessStatus.THINKING, // a failed tool doesn't end the turn
                HarnessStatus.THINKING, HarnessStatus.IDLE,
            ),
            states,
        )
    }

    /** The reported bug: ~1 min after a turn the idle reminder made the icon "wait", and typing then made it "work". */
    @Test
    fun `the idle reminder reads as idle, never as a dialog`() {
        assertEquals(HarnessStatus.IDLE, fire("Notification", notification("idle_prompt")))
        assertEquals(HarnessStatus.WAITING, fire("Notification", notification("permission_prompt")))
    }

    @Test
    fun `other notifications leave the icon alone`() {
        for (type in listOf("agent_completed", "auth_success", "push_notification")) {
            assertEquals(type, null, fire("Notification", notification(type)))
        }
    }

    @Test
    fun `only an interrupted tool ends the turn`() {
        assertEquals(HarnessStatus.IDLE, fire("PostToolUseFailure", """{"tool_name":"Bash","error":"Interrupted","is_interrupt":true}"""))
        assertEquals(HarnessStatus.THINKING, fire("PostToolUseFailure", """{"tool_name":"Bash","error":"Exit code 2"}"""))
    }

    @Test
    fun `an auto-mode denial keeps the turn going`() {
        assertEquals(HarnessStatus.THINKING, fire("PermissionDenied", """{"tool_name":"Bash","reason":"classifier"}"""))
    }

    private fun notification(type: String) = """{"message":"m","notification_type":"$type"}"""

    /** Runs every installed entry Claude would select for [event]; returns the status the IDE ends up with. */
    private fun fire(event: String, payload: String): HarnessStatus? {
        received.clear()
        val input = payload.replaceFirst("{", """{"session_id":"s","hook_event_name":"$event",""")
        val query = JsonParser.parseString(input).asJsonObject.get(MATCH_FIELD[event] ?: "")?.asString
        val env = mapOf("HARNESS_ASIDE_IDE_PORT" to server.port.toString(), "HARNESS_ASIDE_IDE_TOKEN" to server.token)
        for (entry in JsonParser.parseString(Files.readString(dir.resolve("settings.json"))).asJsonObject
            .getAsJsonObject("hooks").getAsJsonArray(event) ?: return null) {
            val matcher = entry.asJsonObject.get("matcher")?.asString
            if (!selects(matcher, query)) continue
            for (hook in entry.asJsonObject.getAsJsonArray("hooks")) {
                val result = HookShell.run(hook.asJsonObject.get("command").asString, dir, env, input, posix = true)
                assertEquals("$event: ${result.stderr}", 0, result.exitCode)
            }
        }
        return received.lastOrNull()
    }

    private companion object {
        /** The input field each event's matcher is compared with (Claude Code's own table, for the events we use). */
        val MATCH_FIELD = mapOf(
            "PreToolUse" to "tool_name", "PostToolUse" to "tool_name", "PostToolUseFailure" to "tool_name",
            "PermissionRequest" to "tool_name", "PermissionDenied" to "tool_name", "Notification" to "notification_type",
        )

        /** Claude Code's rule for plain matchers: none or `*` selects all, `a|b` selects exactly a or b. */
        fun selects(matcher: String?, query: String?): Boolean {
            if (matcher.isNullOrEmpty() || matcher == "*" || query == null) return true
            require(Regex("[A-Za-z0-9_|]+").matches(matcher)) { "only plain matchers are modelled: $matcher" }
            return query in matcher.split('|')
        }
    }
}
