package com.github.baenar.claudeaside

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.name

/**
 * Pure helpers for locating and reading Claude Code's per-project session storage.
 *
 * Claude keeps one JSONL file per session under
 * `~/.claude/projects/<encoded-project-path>/<session-id>.jsonl`, where the project path
 * is encoded by replacing every non-alphanumeric character with `-` (so `/`, `.` and `_`
 * all become `-`). Verified: `D:\claude-aside` → `D--claude-aside`.
 */
object ClaudeSessions {

    const val TRANSCRIPT_SUFFIX = ".jsonl"

    /** Encodes an absolute project path the way Claude names its `projects/` subdirectory. */
    fun encodeProjectPath(path: String): String = path.replace(Regex("[^A-Za-z0-9]"), "-")

    /** The directory Claude stores this project's sessions in, under [userHome]. */
    fun sessionsDir(userHome: String, projectPath: String): Path =
        Paths.get(userHome, ".claude", "projects", encodeProjectPath(projectPath))

    /** The JSONL transcript Claude appends to while [sessionId] runs. */
    fun transcriptPath(userHome: String, projectPath: String, sessionId: String): Path =
        sessionsDir(userHome, projectPath).resolve("$sessionId$TRANSCRIPT_SUFFIX")

    /**
     * The uuid of the newest interrupt record anywhere in [text], or null when it holds
     * none.
     *
     * Deliberately not "is the *last* line an interrupt": Claude appends bookkeeping
     * records (`system`, `last-prompt`, `total_tokens_reminder`, …) within milliseconds of
     * the interrupt, so the interrupt is almost never the final line. Verified against real
     * transcripts on this machine.
     */
    fun lastInterruptUuid(text: String): String? =
        text.lineSequence().mapNotNull { interruptUuid(it) }.lastOrNull()

    /**
     * The entry uuid of [line] when it is Claude's "the user interrupted me" transcript
     * record, else null.
     *
     * Claude Code writes no hook on Ctrl+C or on denying a permission prompt with Esc, but
     * it does append a synthetic user message — `[Request interrupted by user]` or
     * `[Request interrupted by user for tool use]` — to the transcript. That record is the
     * plugin's only reliable "the turn ended early" signal; the uuid lets the caller apply
     * each interrupt exactly once.
     */
    fun interruptUuid(line: String): String? {
        if (!line.contains(INTERRUPT_TEXT)) return null
        if (!USER_TYPE.containsMatchIn(line)) return null
        return ENTRY_UUID.find(line)?.groupValues?.get(1)
    }

    /**
     * The marker, anchored to the `text` field it appears in.
     *
     * The bare phrase is not enough: a transcript is full of records that merely *mention*
     * it — tool calls that grep for it, files being written that contain it. Those nest the
     * quotes one level deeper (`\"text\":\"[Request…`), so requiring the unescaped form
     * tells a real interrupt from a record talking about one. Measured on this project's
     * own transcript: the loose match found 27 "interrupts", 5 of which were real.
     */
    private const val INTERRUPT_TEXT = "\"text\":\"[Request interrupted by user"
    private val USER_TYPE = Regex("\"type\"\\s*:\\s*\"user\"")
    private val ENTRY_UUID = Regex("\"uuid\"\\s*:\\s*\"([^\"]+)\"")

    /**
     * The id (file name without extension) of the most recently modified session for the
     * project, or null when the directory has no `*.jsonl` files (or does not exist).
     */
    fun latestSessionId(userHome: String, projectPath: String): String? {
        val dir = sessionsDir(userHome, projectPath)
        if (!Files.isDirectory(dir)) return null
        return Files.list(dir).use { stream ->
            stream.filter { it.name.endsWith(".jsonl") }
                .max(compareBy { Files.getLastModifiedTime(it) })
                .map { it.name.removeSuffix(".jsonl") }
                .orElse(null)
        }
    }
}
