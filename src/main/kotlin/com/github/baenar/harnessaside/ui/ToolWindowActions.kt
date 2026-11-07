package com.github.baenar.harnessaside.ui

import com.github.baenar.harnessaside.core.HarnessStatusManager
import com.intellij.icons.AllIcons
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow

/** Title-bar action: ends the session, which kills its terminal (pty), and returns to the launcher. */
class EndSessionAction(private val toolWindow: ToolWindow) :
    AnAction("Back to Launcher", "End this session and return to the harness launcher", AllIcons.Actions.Restart), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val cm = toolWindow.contentManager
        // removeContent(_, true) disposes the content, whose disposer kills the pty.
        cm.selectedContent?.let { cm.removeContent(it, true) }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = HarnessToolWindowUi.hasActiveTerminal(toolWindow)
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}

/**
 * Gear-menu action: reports what the status indicator currently believes, which turns
 * "the icon is wrong" into a specific broken link.
 */
class DiagnoseStatusAction(private val project: Project) :
    AnAction("Diagnose Status Indicator", "Show what the status indicator currently sees", AllIcons.General.Information), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(HarnessStatusManager.NOTIFICATION_GROUP)
            .createNotification(
                "Harness Aside status",
                project.service<HarnessStatusManager>().describeState().replace("\n", "<br/>"),
                NotificationType.INFORMATION,
            )
            .notify(project)
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
