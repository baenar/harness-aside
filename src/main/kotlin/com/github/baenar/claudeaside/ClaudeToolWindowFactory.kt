package com.github.baenar.claudeaside

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import org.jetbrains.plugins.terminal.LocalTerminalDirectRunner
import org.jetbrains.plugins.terminal.ShellStartupOptions

/**
 * Opens a terminal in the right-docked tool window and starts Claude Code in it.
 *
 * `claude -c` resumes the last session for this directory and silently starts a fresh
 * one when there is none, so no session lookup is needed here.
 */
class ClaudeToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val options = ShellStartupOptions.Builder()
            .workingDirectory(project.basePath)
            .build()

        // The command is injected into the shell rather than passed as shellCommand, so the
        // tab stays usable after Claude exits.
        val widget = LocalTerminalDirectRunner.createTerminalRunner(project)
            .startShellTerminalWidget(toolWindow.disposable, options, true)
        widget.sendCommandToExecute("claude -c")

        val content = ContentFactory.getInstance().createContent(widget.component, null, false)
        toolWindow.contentManager.addContent(content)
    }
}
