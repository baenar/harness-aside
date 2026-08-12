package com.github.baenar.claudeaside

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory

/**
 * Opens a terminal in the right-docked tool window and starts Claude Code in it.
 *
 * `claude -c` resumes the last session for this directory and silently starts a fresh
 * one when there is none, so no session lookup is needed here. Tab management and the
 * launch itself live in [ClaudeTerminalService].
 */
class ClaudeToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        toolWindow.setTitleActions(
            listOf(
                NewClaudeSessionAction(toolWindow),
                RestartClaudeTabAction(toolWindow),
                RemoveClaudeTabAction(toolWindow),
            )
        )
        project.service<ClaudeTerminalService>().openTab(toolWindow, "claude -c")
    }
}
