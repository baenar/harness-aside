package com.github.baenar.claudeaside

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory

/**
 * Opens a terminal in the right-docked tool window and starts Claude Code in it.
 *
 * `claude -c` resumes the last session for this directory and silently starts a fresh
 * one when there is none, so no session lookup is needed here. Tab management and the
 * launch itself live in [ClaudeTerminalService]; the status indicator in
 * [ClaudeStatusService].
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
        toolWindow.setAdditionalGearActions(DefaultActionGroup(EnableStatusHooksAction(project)))
        project.service<ClaudeTerminalService>().openTab(toolWindow, "claude -c")

        project.service<ClaudeStatusService>().ensureStarted()
        offerStatusHooks(project)
    }

    /** On first open, offers (with consent) to install the hooks that drive the status icon. */
    private fun offerStatusHooks(project: Project) {
        val hooks = ClaudeStatusHooks(project)
        if (hooks.isInstalled()) return

        NotificationGroupManager.getInstance()
            .getNotificationGroup(ClaudeStatusService.NOTIFICATION_GROUP)
            .createNotification(
                "Show Claude's status in the tool window?",
                "Claude Aside can add hooks to <code>.claude/settings.json</code> that light the " +
                    "tool-window icon while Claude is thinking, waiting for approval, or idle. " +
                    "Restart the Claude tab afterwards for it to take effect.",
                NotificationType.INFORMATION,
            )
            .addAction(NotificationAction.createSimple("Enable status indicator") {
                val ok = hooks.install()
                NotificationGroupManager.getInstance()
                    .getNotificationGroup(ClaudeStatusService.NOTIFICATION_GROUP)
                    .createNotification(
                        if (ok) "Status hooks installed — restart the Claude tab to activate."
                        else "Could not install status hooks (see idea.log).",
                        if (ok) NotificationType.INFORMATION else NotificationType.WARNING,
                    )
                    .notify(project)
            })
            .notify(project)
    }
}
