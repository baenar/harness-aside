package com.github.baenar.claudeaside

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchService

/**
 * Watches `.claude/aside-status` and reflects Claude's state as the tool-window icon
 * (and the icon on every open tab): a coloured circle for thinking / waiting / idle,
 * falling back to the plain Claude glyph when no status is known.
 *
 * Status is project-level for now — one indicator for the whole tool window. Mapping a
 * state to an individual tab needs tab→session-id resolution (see [ClaudeSessions]) and
 * is left for a follow-up.
 */
@Service(Service.Level.PROJECT)
class ClaudeStatusService(private val project: Project) : Disposable {

    @Volatile
    private var watchService: WatchService? = null
    private var started = false
    private var current: ClaudeStatus? = null

    /** Starts watching the status file (idempotent). */
    @Synchronized
    fun ensureStarted() {
        if (started) return
        started = true
        val base = project.basePath ?: return
        val claudeDir = Paths.get(base, ClaudeStatusHooks.CLAUDE_DIR)
        val statusFile = claudeDir.resolve(ClaudeStatusHooks.STATUS_FILE)
        try {
            Files.createDirectories(claudeDir)
            val ws = claudeDir.fileSystem.newWatchService()
            claudeDir.register(
                ws,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
            )
            watchService = ws
            setStripeIcon(ClaudeStatus.DEFAULT_ICON) // tall glyph canvas from the start, so no resize later
            refreshFrom(statusFile) // pick up any status already on disk
            ApplicationManager.getApplication().executeOnPooledThread { watchLoop(ws, statusFile) }
        } catch (e: Exception) {
            LOG.warn("Could not start Claude Aside status watcher", e)
        }
    }

    private fun watchLoop(ws: WatchService, statusFile: Path) {
        while (!project.isDisposed) {
            val key = try {
                ws.take()
            } catch (_: Exception) {
                return // watch service closed on dispose
            }
            val relevant = key.pollEvents().any { it.context()?.toString() == ClaudeStatusHooks.STATUS_FILE }
            if (relevant) refreshFrom(statusFile)
            if (!key.reset()) return
        }
    }

    private fun refreshFrom(statusFile: Path) {
        val status = try {
            if (Files.exists(statusFile)) ClaudeStatus.fromWord(Files.readString(statusFile)) else null
        } catch (_: Exception) {
            null // mid-write; the next event will settle it
        } ?: return
        applyStatus(status)
    }

    private fun applyStatus(status: ClaudeStatus) {
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            val previous = current
            current = status
            val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID) ?: return@invokeLater
            toolWindow.setIcon(status.icon)

            if (status == ClaudeStatus.WAITING && status != previous && !toolWindow.isVisible) {
                notifyWaiting()
            }
        }
    }

    private fun setStripeIcon(icon: javax.swing.Icon) {
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)?.setIcon(icon)
        }
    }

    private fun notifyWaiting() {
        val session = project.basePath?.let {
            ClaudeSessions.latestSessionId(System.getProperty("user.home"), it)
        }
        val where = session?.let { " (session ${it.take(8)})" } ?: ""
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP)
            .createNotification("Claude is waiting for you$where", NotificationType.INFORMATION)
            .notify(project)
    }

    override fun dispose() {
        watchService?.let { runCatching { it.close() } }
        watchService = null
    }

    companion object {
        private val LOG = logger<ClaudeStatusService>()
        const val NOTIFICATION_GROUP = "Claude Aside"
    }
}
