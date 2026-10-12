package com.github.baenar.harnessaside.claude

import com.github.baenar.harnessaside.core.Harness
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
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
    override val icon: Icon get() = ClaudeStatusIcons.CLAUDE

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
        setupStatusHooks(project)
    }

    override fun setupStatusHooks(project: Project) {
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

