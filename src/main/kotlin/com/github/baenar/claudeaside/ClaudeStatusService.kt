package com.github.baenar.claudeaside

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.Content
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchService
import java.util.concurrent.ConcurrentHashMap
import javax.swing.Timer

/**
 * Tracks Claude's state and shows it on the tool-window button, animating the icon through
 * the three looks in [ClaudeStatusIcons].
 *
 * **Only the first tab drives the icon.** There is one button for however many tabs are
 * open, so it follows the oldest open tab's session; the rest run untracked. (An earlier
 * attempt gave every tab its own icon, but tool-window tabs render icons only behind an
 * opt-in flag and the result was one indistinct dot.)
 *
 * Two sources feed the state, because neither alone is complete:
 *
 * 1. **Hook state files** — `.claude/aside-status/<session-id>`, written by the hooks
 *    [ClaudeStatusHooks] installs. These carry the normal lifecycle: idle → thinking →
 *    waiting → thinking → idle.
 * 2. **Transcript tail** — `~/.claude/projects/<encoded>/<session-id>.jsonl`. Claude fires
 *    no hook when a turn is cut short by Ctrl+C or by denying a permission prompt with Esc,
 *    which used to strand the icon on `thinking`/`waiting` forever. It does append a
 *    `[Request interrupted by user]` record, and that is what gets the button back to idle.
 */
@Service(Service.Level.PROJECT)
class ClaudeStatusService(private val project: Project) : Disposable {

    private val statuses = ConcurrentHashMap<String, ClaudeStatus>()

    /** Session ids in tab-creation order; the first still-open one owns the icon. */
    private val openSessions = LinkedHashSet<String>()

    /** Tab titles, for the "Claude is waiting for you" balloon. */
    private val tabNames = ConcurrentHashMap<String, String>()

    /** Last interrupt record already applied, per session — each one should reset the icon once. */
    private val appliedInterrupts = ConcurrentHashMap<String, String>()

    @Volatile
    private var watchers: List<WatchService> = emptyList()
    private var started = false

    /**
     * EDT-only animation state.
     *
     * Frames are pushed with [com.intellij.openapi.wm.ToolWindow.setIcon] on a timer rather
     * than handed to `com.intellij.ui.AnimatedIcon`: `setIcon` is known to repaint the
     * stripe button (that is how the status icon changed at all before this), while
     * `AnimatedIcon` drives repaints through the component it is painted on — unverified
     * here, and the class is `@ApiStatus.Experimental`. Each frame is a real icon file, for
     * the reason given in [ClaudeStatusIcons].
     */
    private var animator: Timer? = null
    private var animated: ClaudeStatus? = null
    private var frame = 0

    /** Starts watching the hook state files and session transcripts (idempotent). */
    @Synchronized
    fun ensureStarted() {
        if (started) return
        started = true
        val base = project.basePath ?: return

        watch(statusDir(base)) { name -> onStatusFileChanged(base, name) }
        watch(transcriptDir(base)) { name -> onTranscriptChanged(base, name) }
        rescanStatusFiles(base)
        refreshIcon()
    }

    /** Associates a tool-window tab with the Claude session running in it. */
    @Synchronized
    fun registerTab(sessionId: String, content: Content) {
        openSessions.add(sessionId)
        tabNames[sessionId] = content.displayName ?: sessionId
        refreshIcon()
    }

    /** Forgets a closed tab; the next-oldest tab takes over the icon. */
    @Synchronized
    fun unregisterTab(sessionId: String) {
        openSessions.remove(sessionId)
        tabNames.remove(sessionId)
        statuses.remove(sessionId)
        appliedInterrupts.remove(sessionId)
        refreshIcon()
    }

    /** The session whose state the tool-window button shows. */
    @Synchronized
    private fun primarySession(): String? = openSessions.firstOrNull()

    private fun statusDir(base: String): Path =
        Paths.get(base, ClaudeStatusHooks.CLAUDE_DIR, ClaudeStatusHooks.STATUS_DIR)

    private fun transcriptDir(base: String): Path =
        ClaudeSessions.sessionsDir(System.getProperty("user.home"), base)

    /**
     * Registers [dir] with a watch service and pumps its events into [onChange], keyed by
     * the changed file's name. Failures are logged and simply cost the status indicator.
     */
    private fun watch(dir: Path, onChange: (String) -> Unit) {
        try {
            // Pre-3.1 installs left a plain file where the status directory now lives.
            if (Files.isRegularFile(dir)) Files.delete(dir)
            Files.createDirectories(dir)
            val ws = dir.fileSystem.newWatchService()
            dir.register(
                ws,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE,
            )
            watchers = watchers + ws
            ApplicationManager.getApplication().executeOnPooledThread { watchLoop(ws, onChange) }
        } catch (e: Exception) {
            LOG.warn("Claude Aside: cannot watch $dir for status changes", e)
        }
    }

    private fun watchLoop(ws: WatchService, onChange: (String) -> Unit) {
        while (!project.isDisposed) {
            val key = try {
                ws.take()
            } catch (_: Exception) {
                return // watch service closed on dispose
            }
            key.pollEvents().mapNotNull { it.context()?.toString() }.distinct().forEach(onChange)
            if (!key.reset()) return
        }
    }

    /** Picks up state files written before the IDE started watching. */
    private fun rescanStatusFiles(base: String) {
        val dir = statusDir(base)
        if (!Files.isDirectory(dir)) return
        try {
            Files.list(dir).use { stream ->
                stream.forEach { onStatusFileChanged(base, it.fileName.toString()) }
            }
        } catch (e: Exception) {
            LOG.warn("Claude Aside: cannot read $dir", e)
        }
    }

    private fun onStatusFileChanged(base: String, fileName: String) {
        val file = statusDir(base).resolve(fileName)
        val status = try {
            if (Files.exists(file)) ClaudeStatus.fromWord(Files.readString(file)) else null
        } catch (_: Exception) {
            return // mid-write; the next event will settle it
        }
        if (status == null) statuses.remove(fileName) else setStatus(fileName, status)
        refreshIcon()
    }

    /**
     * Resets a session to idle when its transcript's newest record is an interrupt —
     * Claude's only trace of Ctrl+C or a denied permission prompt.
     */
    private fun onTranscriptChanged(base: String, fileName: String) {
        if (!fileName.endsWith(ClaudeSessions.TRANSCRIPT_SUFFIX)) return
        val sessionId = fileName.removeSuffix(ClaudeSessions.TRANSCRIPT_SUFFIX)
        if (!statuses.containsKey(sessionId) && sessionId != primarySession()) return

        val path = ClaudeSessions.transcriptPath(System.getProperty("user.home"), base, sessionId)
        val tail = readTail(path) ?: return
        val last = ClaudeSessions.lastNonBlankLine(tail) ?: return
        val uuid = ClaudeSessions.interruptUuid(last) ?: return
        // The same record stays last until Claude writes again — only act on it once.
        if (appliedInterrupts.put(sessionId, uuid) == uuid) return
        setStatus(sessionId, ClaudeStatus.IDLE)
        refreshIcon()
    }

    /** The trailing [TAIL_BYTES] of [path] as text; the leading partial line is harmless here. */
    private fun readTail(path: Path): String? = try {
        FileChannel.open(path, StandardOpenOption.READ).use { channel ->
            val size = channel.size()
            val from = maxOf(0L, size - TAIL_BYTES)
            val buffer = ByteBuffer.allocate((size - from).toInt())
            var at = from
            while (buffer.hasRemaining()) {
                val read = channel.read(buffer, at)
                if (read <= 0) break
                at += read
            }
            String(buffer.array(), 0, buffer.position(), StandardCharsets.UTF_8)
        }
    } catch (_: Exception) {
        null // transcript missing or being rotated
    }

    private fun setStatus(sessionId: String, status: ClaudeStatus) {
        val previous = statuses.put(sessionId, status)
        if (status == ClaudeStatus.WAITING && previous != ClaudeStatus.WAITING && sessionId == primarySession()) {
            notifyWaitingIfHidden(sessionId)
        }
    }

    /** Points the tool-window button at the first open tab's state, animating if needed. */
    private fun refreshIcon() {
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID) ?: return@invokeLater
            val status = primarySession()?.let { statuses[it] }
            if (status == animated && animator != null) return@invokeLater // already running this loop

            animator?.stop()
            animator = null
            animated = status
            frame = 0

            if (!ClaudeStatusIcons.isAnimated(status)) {
                toolWindow.setIcon(ClaudeStatusIcons.BASE)
                return@invokeLater
            }
            toolWindow.setIcon(ClaudeStatusIcons.forStatus(status, 0))
            animator = Timer(FRAME_MS) { tick(status) }.apply {
                isRepeats = true
                start()
            }
        }
    }

    /** One animation step. Re-resolves the tool window each time; it can be closed under us. */
    private fun tick(status: ClaudeStatus?) {
        if (project.isDisposed) {
            animator?.stop()
            return
        }
        frame++
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID) ?: return
        try {
            toolWindow.setIcon(ClaudeStatusIcons.forStatus(status, frame))
        } catch (e: Exception) {
            // A decorative animation must never keep throwing at the user: stop the loop,
            // fall back to the still glyph, and report once.
            animator?.stop()
            animator = null
            animated = null
            LOG.warn("Claude Aside: status animation stopped after a paint failure", e)
            runCatching { toolWindow.setIcon(ClaudeStatusIcons.BASE) }
        }
    }

    private fun notifyWaitingIfHidden(sessionId: String) {
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)
            if (toolWindow != null && toolWindow.isVisible) return@invokeLater
            val where = tabNames[sessionId] ?: "session ${sessionId.take(8)}"
            NotificationGroupManager.getInstance()
                .getNotificationGroup(NOTIFICATION_GROUP)
                .createNotification("Claude is waiting for you in $where", NotificationType.INFORMATION)
                .notify(project)
        }
    }

    override fun dispose() {
        animator?.stop()
        animator = null
        watchers.forEach { runCatching { it.close() } }
        watchers = emptyList()
    }

    companion object {
        private val LOG = logger<ClaudeStatusService>()
        const val NOTIFICATION_GROUP = "Claude Aside"

        /** Enough to hold the last transcript record; they are single lines of JSON. */
        private const val TAIL_BYTES = 64L * 1024

        /** ~14 fps: smooth enough for a 13px glyph, cheap enough to leave running. */
        private const val FRAME_MS = 70
    }
}
