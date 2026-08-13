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

    /** The last line of [text] that isn't blank, or null when there is none. */
    fun lastNonBlankLine(text: String): String? =
        text.lineSequence().lastOrNull { it.isNotBlank() }

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

    private const val INTERRUPT_TEXT = "[Request interrupted by user"
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
