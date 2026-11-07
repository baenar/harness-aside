package com.github.baenar.harnessaside.claude

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
 * all become `-`). Verified: `D:\harness-aside` → `D--harness-aside`.
 */
object ClaudeSessions {

    /** Encodes an absolute project path the way Claude names its `projects/` subdirectory. */
    fun encodeProjectPath(path: String): String = path.replace(NON_ALPHANUMERIC, "-")

    private val NON_ALPHANUMERIC = Regex("[^A-Za-z0-9]")

    /** A `$HOME` and a project path as *Claude* sees them; the IDE's need not match. */
    data class Storage(val userHome: String, val projectPath: String)

    /**
     * Translates what the IDE sees into what Claude uses.
     *
     * A plain local project needs no translation. A project inside WSL does: the IDE calls
     * it `\\wsl$\Ubuntu\home\me\proj`, while Claude runs *inside* the distro, encodes
     * `/home/me/proj`, and keeps `~/.claude` in the distro's home rather than under
     * `C:\Users`. Translating one half without the other still finds nothing.
     *
     * The distro's home is derived from the project path, so a path outside `/home/<user>`
     * or `/root` falls back to the IDE's own values — no worse than the behaviour this had
     * everywhere before, and every caller already copes with finding nothing.
     */
    fun storage(ideUserHome: String, ideProjectPath: String): Storage {
        val path = ideProjectPath.replace('\\', '/')
        val prefix = wslPrefix(path) ?: return Storage(ideUserHome, ideProjectPath)

        val rest = path.substring(prefix.length)
        val distro = rest.substringBefore('/')
        if (distro.isEmpty()) return Storage(ideUserHome, ideProjectPath)

        val linuxPath = "/" + rest.substringAfter('/', "").trimEnd('/')
        val linuxHome = wslHome(linuxPath) ?: return Storage(ideUserHome, ideProjectPath)
        return Storage(prefix + distro + linuxHome, linuxPath)
    }

    /** The distro home holding [linuxPath], when the path alone reveals which one it is. */
    private fun wslHome(linuxPath: String): String? =
        if (linuxPath == "/root" || linuxPath.startsWith("/root/")) "/root"
        else WSL_HOME.matchEntire(linuxPath)?.groupValues?.get(1)

    /** True for a `\\wsl$\distro\...` or `\\wsl.localhost\distro\...` path. */
    fun isWsl(path: String): Boolean = wslPrefix(path.replace('\\', '/')) != null

    private fun wslPrefix(slashPath: String): String? =
        WSL_PREFIXES.firstOrNull { slashPath.startsWith(it, ignoreCase = true) }

    /** Both spellings Windows mounts a distro under; `wsl.localhost` is the newer one. */
    private val WSL_PREFIXES = listOf("//wsl\$/", "//wsl.localhost/")
    private val WSL_HOME = Regex("^(/home/[^/]+)(?:/.*)?$")

    /**
     * The directory Claude stores this project's sessions in.
     *
     * Both arguments are the IDE's view; [storage] turns them into Claude's first.
     */
    fun sessionsDir(userHome: String, projectPath: String): Path {
        val (home, path) = storage(userHome, projectPath)
        return Paths.get(home, ".claude", "projects", encodeProjectPath(path))
    }

    /** True when the project has a session to continue; a missing or unreadable directory means it hasn't. */
    fun hasSessions(userHome: String, projectPath: String): Boolean = runCatching {
        Files.list(sessionsDir(userHome, projectPath)).use { files -> files.anyMatch { it.name.endsWith(".jsonl") } }
    }.getOrDefault(false)
}
