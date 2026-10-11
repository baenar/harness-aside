package com.github.baenar.harnessaside.claude

import com.github.baenar.harnessaside.core.Harness
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import javax.swing.Icon

/**
 * Harness adapter for Claude Code (`claude`).
 */
object ClaudeHarness : Harness {
    override val id: String = "claude"
    override val displayName: String = "Claude Code"
    override val executable: String = "claude"
    override val icon: Icon get() = ClaudeStatusIcons.BASE

    override fun openSession(
        project: Project,
        toolWindow: ToolWindow,
        continueSession: Boolean,
        sessionId: String?,
    ) {
        project.service<ClaudeStatusService>().ensureStarted()
        val terminalService = project.service<ClaudeTerminalService>()
        if (continueSession) {
            terminalService.openContinuedSession(toolWindow, sessionId)
        } else {
            terminalService.openNewSession(toolWindow)
        }
    }
}
