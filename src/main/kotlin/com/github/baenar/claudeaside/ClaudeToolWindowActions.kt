package com.github.baenar.claudeaside

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.wm.ToolWindow

/** Title-bar action: start a brand-new Claude session (`claude`). */
class NewClaudeSessionAction(private val toolWindow: ToolWindow) :
    AnAction("New Session", "Start a new Claude session", AllIcons.General.Add) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        project.service<ClaudeTerminalService>().openTab(toolWindow, "claude")
    }
}

/** Title-bar action: close the current tab and open a fresh resumed one (`claude -c`). */
class RestartClaudeTabAction(private val toolWindow: ToolWindow) :
    AnAction("Restart Tab", "Close this tab and start Claude again resuming the last session", AllIcons.Actions.Restart) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val cm = toolWindow.contentManager
        cm.selectedContent?.let { cm.removeContent(it, true) }
        project.service<ClaudeTerminalService>().openTab(toolWindow, "claude -c")
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = toolWindow.contentManager.contentCount > 0
    }

    override fun getActionUpdateThread() = ActionUpdateThread.EDT
}

/** Title-bar action: close the current tab, terminating its terminal (pty). */
class RemoveClaudeTabAction(private val toolWindow: ToolWindow) :
    AnAction("Close Tab", "Close this tab and terminate its terminal", AllIcons.Actions.Close) {

    override fun actionPerformed(e: AnActionEvent) {
        val cm = toolWindow.contentManager
        // removeContent(_, true) disposes the content, whose disposer kills the pty.
        cm.selectedContent?.let { cm.removeContent(it, true) }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = toolWindow.contentManager.contentCount > 0
    }

    override fun getActionUpdateThread() = ActionUpdateThread.EDT
}
