package com.github.baenar.harnessaside.claude

import com.github.baenar.harnessaside.core.Harness
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

/** Harness adapter for Claude Code (`claude`). */
object ClaudeHarness : Harness {
    override val displayName = "Claude Code"
    override val executable = "claude"
    override val icon: Icon by lazy { IconLoader.getIcon("/icons/claude.svg", ClaudeHarness::class.java) }

    /** Continues the project's latest session, or starts one when it has none. */
    override fun launchCommand(project: Project): String {
        val projectPath = project.basePath ?: return executable
        val resumable = ClaudeSessions.hasSessions(System.getProperty("user.home"), projectPath)
        return if (resumable) "$executable -c" else executable
    }

    override fun installStatusHooks(project: Project) {
        ClaudeStatusHooks.forProject(project)?.install()
    }
}
