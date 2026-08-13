package com.github.baenar.claudeaside

import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/**
 * Runs the generated reporter script through a real `sh`.
 *
 * The script is the one place where a stray backslash breaks the status indicator
 * silently — the hook still exits 0 and Claude reports nothing — so it is worth executing
 * rather than string-matching. Skipped where `sh` is unavailable (a bare Windows CI).
 */
class ClaudeStatusHooksTest {

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

    private fun runScript(word: String, payload: String): String? {
        val sh = shell()
        assumeTrue("no POSIX sh on this machine", sh != null)

        val projectDir = Files.createTempDirectory("aside-project")
        val script = projectDir.resolve("aside-status.sh")
        Files.writeString(script, ClaudeStatusHooks.SCRIPT)

        val process = ProcessBuilder(sh, script.toString(), word)
            .apply { environment()["CLAUDE_PROJECT_DIR"] = projectDir.toString() }
            .redirectErrorStream(true)
            .start()
        process.outputStream.use { it.write(payload.toByteArray()) }
        assertEquals(true, process.waitFor(30, TimeUnit.SECONDS))
        assertEquals("hook script must always exit 0", 0, process.exitValue())

        val statusDir = projectDir.resolve(ClaudeStatusHooks.CLAUDE_DIR).resolve(ClaudeStatusHooks.STATUS_DIR)
        return Files.list(statusDir).use { stream ->
            stream.findFirst().map { "${it.fileName}=${Files.readString(it)}" }.orElse(null)
        }
    }

    @Test
    fun `writes the status word into a file named after the session`() {
        val payload = """{"session_id":"5f9d1a20-1111-4b3c-9d55-2b0a7c1e4d88",""" +
            """"transcript_path":"/home/x/.claude/projects/p/s.jsonl","cwd":"/home/x/p",""" +
            """"hook_event_name":"UserPromptSubmit"}"""
        assertEquals("5f9d1a20-1111-4b3c-9d55-2b0a7c1e4d88=thinking", runScript("thinking", payload))
    }

    @Test
    fun `falls back to a placeholder when the payload carries no session id`() {
        assertEquals("unknown=waiting", runScript("waiting", """{"hook_event_name":"Notification"}"""))
    }
}
