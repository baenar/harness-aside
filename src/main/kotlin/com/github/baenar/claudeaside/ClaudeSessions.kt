package com.github.baenar.claudeaside

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.name

/**
 * Pure helpers for locating Claude Code's per-project session storage.
 *
 * Claude keeps one JSONL file per session under
 * `~/.claude/projects/<encoded-project-path>/<session-id>.jsonl`, where the project path
 * is encoded by replacing every non-alphanumeric character with `-` (so `/`, `.` and `_`
 * all become `-`). Verified: `D:\claude-aside` → `D--claude-aside`.
 */
object ClaudeSessions {

    /** Encodes an absolute project path the way Claude names its `projects/` subdirectory. */
    fun encodeProjectPath(path: String): String = path.replace(Regex("[^A-Za-z0-9]"), "-")

    /** The directory Claude stores this project's sessions in, under [userHome]. */
    fun sessionsDir(userHome: String, projectPath: String): Path =
        Paths.get(userHome, ".claude", "projects", encodeProjectPath(projectPath))

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
