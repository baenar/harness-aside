package com.github.baenar.harnessaside.claude

import com.github.baenar.harnessaside.core.Harness
import com.github.baenar.harnessaside.manul.ManulInk
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import java.awt.Color
import javax.swing.Icon

/** Harness adapter for Claude Code (`claude`). */
object ClaudeHarness : Harness {
    override val id = "claude"
    override val displayName = "Claude Code"
    override val executable = "claude"
    override val icon: Icon by lazy { IconLoader.getIcon("/icons/claude.svg", ClaudeHarness::class.java) }
    override val manulInk = ManulInk(Color(0xD97757)) // the logo's orange

    override fun launchCommand(project: Project, sessionId: String?): String {
        val projectPath = project.basePath ?: return executable
        return command(System.getProperty("user.home"), projectPath, sessionId)
    }

    /**
     * Without [sessionId], continues the project's latest session, or starts one when it has none. With it,
     * resumes that session, or starts it under that id: Claude only writes a session once it has a message.
     */
    internal fun command(userHome: String, projectPath: String, sessionId: String?): String = when {
        sessionId != null && ClaudeSessions.hasSession(userHome, projectPath, sessionId) -> "$executable -r $sessionId"
        sessionId != null -> "$executable --session-id $sessionId"
        ClaudeSessions.hasSessions(userHome, projectPath) -> "$executable -c"
        else -> executable
    }

    override fun installStatusHooks(project: Project) {
        ClaudeStatusHooks.forProject(project)?.install()
    }
}
