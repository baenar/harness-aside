package com.github.baenar.harnessaside.core

import com.github.baenar.harnessaside.claude.ClaudeInterruptKeys
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowManager
import java.awt.AWTEvent
import java.awt.Component
import java.awt.Toolkit
import java.awt.event.AWTEventListener
import java.awt.event.KeyEvent
import javax.swing.SwingUtilities
import javax.swing.Timer

/**
 * Project service showing the harness status on the tool window button.
 *
 * Driven by push events from [HarnessStatusServer] and optimistic keyboard interrupts. Everything here
 * runs on the EDT, except reading [currentStatus]; the animation only runs while there is a status to show.
 */
@Service(Service.Level.PROJECT)
class HarnessStatusManager(private val project: Project) : Disposable {

    @Volatile
    var currentStatus: HarnessStatus = HarnessStatus.IDLE
        private set

    @Volatile
    private var lastInterruptTime: Long = 0L

    private var frame = 0
    private val animator = Timer(FRAME_MS) {
        frame++
        showIcon()
    }

    fun onStatusChanged(status: HarnessStatus) {
        if (status == HarnessStatus.WAITING && currentStatus != HarnessStatus.WAITING) notifyWaitingIfHidden()
        currentStatus = status
        // start() does nothing when already running, so thinking <-> tool doesn't restart the animation.
        if (StatusIcons.isAnimated(status)) animator.start() else animator.stop()
        showIcon()
    }

    /** The user cut the turn short, which no hook reports. */
    fun onInterrupt() {
        lastInterruptTime = System.currentTimeMillis()
        onStatusChanged(HarnessStatus.IDLE)
    }

    /**
     * Listens for keys in [component] that change the status before any hook can report it:
     * interrupts (Ctrl+C, Esc) and answers to a permission dialog.
     */
    fun watchInterruptKeys(component: Component, parent: Disposable) {
        val listener = AWTEventListener { event ->
            if (event !is KeyEvent || event.id != KeyEvent.KEY_PRESSED || !isOurs(component, event.component)) {
                return@AWTEventListener
            }
            if (ClaudeInterruptKeys.isInterrupt(event.keyCode, event.modifiersEx)) {
                onInterrupt()
            } else if (currentStatus == HarnessStatus.WAITING) {
                // WAITING only ever means an open dialog (see ClaudeStatusHooks.HOOKS), so a key
                // that answers it moves on at once: approve -> the tool runs, decline -> idle.
                when (ClaudeInterruptKeys.permissionAnswer(event.keyCode, event.keyChar)) {
                    true -> onStatusChanged(HarnessStatus.EXECUTING_TOOL)
                    false -> onStatusChanged(HarnessStatus.IDLE)
                    null -> {}
                }
            }
        }
        Toolkit.getDefaultToolkit().addAWTEventListener(listener, AWTEvent.KEY_EVENT_MASK)
        Disposer.register(parent) { Toolkit.getDefaultToolkit().removeAWTEventListener(listener) }
    }

    private fun isOurs(tabComponent: Component, source: Component): Boolean =
        SwingUtilities.isDescendingFrom(source, tabComponent) ||
            ToolWindowManager.getInstance(project).activeToolWindowId == TOOL_WINDOW_ID

    private fun showIcon() {
        ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)
            ?.setIcon(StatusIcons.forStatus(currentStatus, frame))
    }

    private fun notifyWaitingIfHidden() {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)
        if (toolWindow != null && toolWindow.isVisible) return

        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP)
            .createNotification("Agent is waiting for your input", NotificationType.INFORMATION)
            .notify(project)
    }

    fun describeState(): String {
        val server = project.service<HarnessStatusServer>()
        val keySeen = if (lastInterruptTime == 0L) "never" else "${(System.currentTimeMillis() - lastInterruptTime) / 1000}s ago"

        return buildString {
            appendLine("Server running: ${server.isRunning} (port ${server.port})")
            appendLine("Current status: ${currentStatus.id}")
            appendLine("Animating: ${animator.isRunning}")
            appendLine("Interrupt key last seen: $keySeen")
        }
    }

    override fun dispose() {
        animator.stop()
    }

    companion object {
        const val NOTIFICATION_GROUP = "Harness Aside"
        private const val FRAME_MS = 140
    }
}
