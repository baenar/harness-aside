package com.github.baenar.harnessaside.core

import com.github.baenar.harnessaside.claude.ClaudeInterruptKeys
import com.github.baenar.harnessaside.claude.ClaudeStatusIcons
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
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
 * Project service managing status presentation on the tool window button and user notifications.
 *
 * Driven by push events from [HarnessStatusServer] and optimistic keyboard interrupts.
 */
@Service(Service.Level.PROJECT)
class HarnessStatusManager(private val project: Project) : Disposable {

    @Volatile
    var currentStatus: HarnessStatus = HarnessStatus.IDLE
        private set

    @Volatile
    private var lastInterruptTime: Long = 0L

    private var animator: Timer? = null
    private var frame: Int = 0

    /** Ensures the status server and status subsystem are ready. */
    fun ensureStarted() {
        project.service<HarnessStatusServer>().ensureStarted()
    }

    /** Called on EDT when a new status event arrives. */
    fun onStatusChanged(status: HarnessStatus) {
        val previous = currentStatus
        currentStatus = status

        if (status == HarnessStatus.WAITING && previous != HarnessStatus.WAITING) {
            notifyWaitingIfHidden()
        }

        refreshIcon()
    }

    /**
     * Listens for interrupt keys (Ctrl+C, Esc) in [component] to optimistically
     * reset status to [HarnessStatus.IDLE] without waiting for hook completion.
     */
    fun watchInterruptKeys(component: Component, parent: Disposable) {
        val listener = AWTEventListener { event ->
            val source = (event as? java.awt.event.ComponentEvent)?.component ?: event.source as? Component
            if (isOurs(component, source)) {
                if (event is KeyEvent && event.id == KeyEvent.KEY_PRESSED) {
                    if (ClaudeInterruptKeys.isInterrupt(event.keyCode, event.modifiersEx)) {
                        lastInterruptTime = System.currentTimeMillis()
                        ApplicationManager.getApplication().invokeLater {
                            if (!project.isDisposed) {
                                onStatusChanged(HarnessStatus.IDLE)
                            }
                        }
                    } else if (currentStatus == HarnessStatus.WAITING && (event.keyCode == KeyEvent.VK_ENTER || event.keyCode == KeyEvent.VK_ESCAPE)) {
                        // User answered or dismissed the prompt via keyboard — immediately clear waiting indicator
                        ApplicationManager.getApplication().invokeLater {
                            if (!project.isDisposed && currentStatus == HarnessStatus.WAITING) {
                                onStatusChanged(HarnessStatus.IDLE)
                            }
                        }
                    }
                } else if (event is java.awt.event.MouseEvent && event.id == java.awt.event.MouseEvent.MOUSE_PRESSED && currentStatus == HarnessStatus.WAITING) {
                    // User clicked an option (e.g. 'No') or clicked into the prompt — immediately clear waiting indicator
                    ApplicationManager.getApplication().invokeLater {
                        if (!project.isDisposed && currentStatus == HarnessStatus.WAITING) {
                            onStatusChanged(HarnessStatus.IDLE)
                        }
                    }
                }
            }
        }
        Toolkit.getDefaultToolkit().addAWTEventListener(listener, AWTEvent.KEY_EVENT_MASK or AWTEvent.MOUSE_EVENT_MASK)
        Disposer.register(parent) { Toolkit.getDefaultToolkit().removeAWTEventListener(listener) }
    }

    private fun isOurs(tabComponent: Component, source: Component?): Boolean {
        if (source != null && SwingUtilities.isDescendingFrom(source, tabComponent)) return true
        return ToolWindowManager.getInstance(project).activeToolWindowId == TOOL_WINDOW_ID
    }

    private fun refreshIcon() {
        if (project.isDisposed) return
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID) ?: return

        animator?.stop()
        animator = null
        frame = 0

        val animated = ClaudeStatusIcons.isAnimatedHarness(currentStatus)
        if (!animated) {
            val icon = if (currentStatus == HarnessStatus.WAITING) {
                ClaudeStatusIcons.forHarnessStatus(HarnessStatus.WAITING, 0)
            } else {
                ClaudeStatusIcons.BASE
            }
            toolWindow.setIcon(icon)
            return
        }

        toolWindow.setIcon(ClaudeStatusIcons.forHarnessStatus(currentStatus, 0))
        animator = Timer(FRAME_MS) {
            if (project.isDisposed) {
                animator?.stop()
                return@Timer
            }
            frame++
            val tw = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID) ?: return@Timer
            try {
                tw.setIcon(ClaudeStatusIcons.forHarnessStatus(currentStatus, frame))
            } catch (e: Exception) {
                animator?.stop()
                animator = null
                LOG.warn("Status animation stopped after a paint failure", e)
                tw.setIcon(ClaudeStatusIcons.BASE)
            }
        }.apply {
            isRepeats = true
            start()
        }
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
            appendLine("Animating: ${animator != null}")
            appendLine("Interrupt key last seen: $keySeen")
        }
    }

    override fun dispose() {
        animator?.stop()
        animator = null
    }

    companion object {
        private val LOG = logger<HarnessStatusManager>()
        const val NOTIFICATION_GROUP = "Harness Aside"
        private const val FRAME_MS = 100

        fun getInstance(project: Project): HarnessStatusManager = project.service()
    }
}
