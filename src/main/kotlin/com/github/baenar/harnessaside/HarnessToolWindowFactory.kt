package com.github.baenar.harnessaside

import com.github.baenar.harnessaside.claude.ClaudeStatusHooks
import com.github.baenar.harnessaside.claude.ClaudeStatusService
import com.github.baenar.harnessaside.claude.DiagnoseStatusAction
import com.github.baenar.harnessaside.claude.EnableStatusHooksAction
import com.github.baenar.harnessaside.claude.NewClaudeSessionAction
import com.github.baenar.harnessaside.claude.RemoveClaudeTabAction
import com.github.baenar.harnessaside.claude.RestartClaudeTabAction
import com.github.baenar.harnessaside.core.HarnessRegistry
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory

/**
 * Opens a terminal in the right-docked tool window and starts the default AI harness.
 *
 * Harness-specific logic is modularized into respective harness packages (e.g. `claude`).
 */
class HarnessToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val harness = HarnessRegistry.default

        toolWindow.setTitleActions(
            listOf(
                NewClaudeSessionAction(toolWindow),
                RestartClaudeTabAction(toolWindow),
                RemoveClaudeTabAction(toolWindow),
            )
        )
        toolWindow.setAdditionalGearActions(
            DefaultActionGroup(EnableStatusHooksAction(project), DiagnoseStatusAction(project))
        )

        harness.openSession(project, toolWindow)
        offerStatusHooks(project)
    }

    /** On first open, offers (with consent) to install the hooks that drive the status icon. */
    private fun offerStatusHooks(project: Project) {
        val hooks = ClaudeStatusHooks.forProject(project) ?: return
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
