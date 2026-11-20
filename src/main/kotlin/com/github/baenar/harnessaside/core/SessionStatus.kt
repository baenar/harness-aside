package com.github.baenar.harnessaside.core

import com.github.baenar.harnessaside.claude.ClaudeInterruptKeys
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowManager
import java.awt.AWTEvent
import java.awt.Component
import java.awt.Toolkit
import java.awt.event.AWTEventListener
import java.awt.event.KeyEvent
import javax.swing.Icon
import javax.swing.SwingUtilities
import javax.swing.Timer

/**
 * One harness session's status: what it is doing, the alerts that raises, and the animation frame its icon
 * is on. Driven by the session's own hook events and by optimistic keyboard interrupts. Everything here runs
 * on the EDT, and the animation only runs while there is a status to show.
 *
 * [showIcon] puts the icon somewhere: the tool window button in single-session mode, a tab's manul otherwise.
 * [isSeen] says whether the user can see the session, which decides if needing input notifies with the
 * sound option off.
 */
class SessionStatus(
    private val project: Project,
    private val showIcon: (HarnessStatus, frame: Int) -> Unit,
    private val isSeen: () -> Boolean,
    private val notificationIcon: Icon? = null,
) : Disposable {

    /** The harness running here, named in notifications. */
    var harnessName = "Agent"

    var current: HarnessStatus = HarnessStatus.IDLE
        private set

    private var lastInterruptTime = 0L

    /** The user cut the turn short, so its end isn't news. Cleared when the harness shows it carried on. */
    private var interrupted = false

    /** A hook event or a starting terminal can still be on its way after the session is gone. */
    var isDisposed = false
        private set

    private var frame = 0
    private val animator = Timer(FRAME_MS) {
        frame++
        showIcon(current, frame)
    }

    fun onStatusChanged(status: HarnessStatus) {
        if (isDisposed) return
        if (status == HarnessStatus.WAITING && current != HarnessStatus.WAITING) onInputNeeded()
        if (status == HarnessStatus.DONE && !interrupted) onTurnDone()
        if (StatusIcons.isAnimated(status)) interrupted = false
        current = status
        // start() does nothing when already running, so thinking <-> tool doesn't restart the animation.
        if (StatusIcons.isAnimated(status)) animator.start() else animator.stop()
        showIcon(current, frame)
    }

    /** The user cut the turn short, which no hook reports. */
    fun onInterrupt() {
        lastInterruptTime = System.currentTimeMillis()
        interrupted = true
        onStatusChanged(HarnessStatus.IDLE)
    }

    /**
     * Listens for keys in [component] that change the status before any hook can report it:
     * interrupts (Ctrl+C, Esc) and answers to a permission dialog.
     */
    fun watchKeys(component: Component, parent: Disposable) {
        val listener = AWTEventListener { event ->
            if (event !is KeyEvent || event.id != KeyEvent.KEY_PRESSED || !isOurs(component, event.component)) {
                return@AWTEventListener
            }
            if (ClaudeInterruptKeys.isInterrupt(event.keyCode, event.modifiersEx)) {
                onInterrupt()
            } else if (current == HarnessStatus.WAITING) {
                // WAITING only ever means an open dialog (see ClaudeStatusHooks.HOOKS), so a key
                // that answers it moves on at once: approve -> the tool runs, decline -> idle.
                when (ClaudeInterruptKeys.permissionAnswer(event.keyCode, event.keyChar)) {
                    true -> onStatusChanged(HarnessStatus.EXECUTING_TOOL)
                    false -> onInterrupt()
                    null -> {}
                }
            }
        }
        Toolkit.getDefaultToolkit().addAWTEventListener(listener, AWTEvent.KEY_EVENT_MASK)
        Disposer.register(parent) { Toolkit.getDefaultToolkit().removeAWTEventListener(listener) }
    }

    /** Only a terminal on screen counts: the other tabs' terminals sit in the same active tool window. */
    private fun isOurs(terminal: Component, source: Component): Boolean =
        SwingUtilities.isDescendingFrom(source, terminal) ||
            (terminal.isShowing && ToolWindowManager.getInstance(project).activeToolWindowId == TOOL_WINDOW_ID)

    /** With the sound option: a sound and a notification, always. Without it: a notification only if unseen. */
    private fun onInputNeeded() {
        val alert = HarnessStatusManager.soundOnInput
        if (alert) playSound()
        if (alert || !isSeen()) notify("$harnessName is waiting for your input")
    }

    private fun onTurnDone() {
        if (!HarnessStatusManager.soundWhenDone) return
        playSound()
        notify("$harnessName has finished working")
    }

    private fun notify(message: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(HarnessStatusManager.NOTIFICATION_GROUP)
            .createNotification(message, NotificationType.INFORMATION)
            .apply { notificationIcon?.let { icon = it } }
            .notify(project)
    }

    fun describe(): String {
        val keySeen = if (lastInterruptTime == 0L) "never" else "${(System.currentTimeMillis() - lastInterruptTime) / 1000}s ago"
        return "$harnessName: ${current.id}, animating: ${animator.isRunning}, interrupt key last seen: $keySeen"
    }

    override fun dispose() {
        isDisposed = true
        animator.stop()
    }

    companion object {
        private const val FRAME_MS = 140

        /** Swappable because a headless test run has no sound to play. */
        internal var playSound: () -> Unit = { Toolkit.getDefaultToolkit().beep() }
    }
}
