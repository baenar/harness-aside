package com.github.baenar.harnessaside.claude

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
        project.service<ClaudeTerminalService>().openNewSession(toolWindow)
    }
}

/** Title-bar action: close the current tab and reopen it resuming the same session. */
class RestartClaudeTabAction(private val toolWindow: ToolWindow) :
    AnAction("Restart Tab", "Close this tab and start Claude again resuming its session", AllIcons.Actions.Restart) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val cm = toolWindow.contentManager
        val closed = cm.selectedContent
        // Resume exactly what this tab was running, not merely the newest session in the
        // project — with several tabs open those are rarely the same conversation.
        val sessionId = ClaudeTerminalService.sessionIdOf(closed)
        closed?.let { cm.removeContent(it, true) }
        project.service<ClaudeTerminalService>().openContinuedSession(toolWindow, sessionId)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = toolWindow.contentManager.contentCount > 0
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
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

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}

/**
 * Gear-menu action: reports what the status indicator currently believes.
 *
 * The indicator depends on a shell script, a file watcher, a transcript and a key
 * listener, none of which are visible when one of them goes quiet. This turns "the icon is
 * wrong" into a specific broken link.
 */
class DiagnoseStatusAction(private val project: Project) :
    AnAction("Diagnose Status Indicator", "Show what the status indicator currently sees", AllIcons.General.Information) {

    override fun actionPerformed(e: AnActionEvent) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(ClaudeStatusService.NOTIFICATION_GROUP)
            .createNotification(
                "Claude Aside status",
                project.service<ClaudeStatusService>().describeState().replace("\n", "<br/>"),
                NotificationType.INFORMATION,
            )
            .notify(project)
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}

/**
 * Gear-menu action: installs (or reinstalls) the Claude Code hooks that drive the status
 * indicator. Available any time, so status can be enabled without relying on the one-time
 * startup notification.
 */
class EnableStatusHooksAction(private val project: Project) :
    AnAction("Enable Status Indicator (Install Hooks)", "Add status hooks to .claude/settings.json", AllIcons.General.Settings) {

    override fun actionPerformed(e: AnActionEvent) {
        val ok = ClaudeStatusHooks.forProject(project)?.install() ?: false
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
        val installed = ClaudeStatusHooks.forProject(project)?.isInstalled() ?: false
        e.presentation.text =
            if (installed) "Reinstall Status Indicator Hooks" else "Enable Status Indicator (Install Hooks)"
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
