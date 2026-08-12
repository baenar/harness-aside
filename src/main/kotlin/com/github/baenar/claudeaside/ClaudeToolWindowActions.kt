package com.github.baenar.claudeaside

import com.intellij.icons.AllIcons
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
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

/**
 * Gear-menu action: installs (or reinstalls) the Claude Code hooks that drive the status
 * indicator. Available any time, so status can be enabled without relying on the one-time
 * startup notification.
 */
class EnableStatusHooksAction(private val project: Project) :
    AnAction("Enable Status Indicator (Install Hooks)", "Add status hooks to .claude/settings.json", AllIcons.General.Settings) {

    override fun actionPerformed(e: AnActionEvent) {
        val hooks = ClaudeStatusHooks(project)
        val ok = hooks.install()
        NotificationGroupManager.getInstance()
            .getNotificationGroup(ClaudeStatusService.NOTIFICATION_GROUP)
            .createNotification(
                if (ok) "Status hooks installed in .claude/settings.json — restart the Claude tab to activate."
                else "Could not install status hooks (see idea.log).",
                if (ok) NotificationType.INFORMATION else NotificationType.WARNING,
            )
            .notify(project)
    }

    override fun update(e: AnActionEvent) {
        val installed = ClaudeStatusHooks(project).isInstalled()
        e.presentation.text =
            if (installed) "Reinstall Status Indicator Hooks" else "Enable Status Indicator (Install Hooks)"
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
