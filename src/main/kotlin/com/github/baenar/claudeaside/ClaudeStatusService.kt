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
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.AppExecutorUtil
import java.awt.AWTEvent
import java.awt.Component
import java.awt.Toolkit
import java.awt.event.AWTEventListener
import java.awt.event.KeyEvent
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities
import javax.swing.Timer

/**
 * Tracks Claude's state and shows it on the tool-window button, animating the icon through
 * the three looks in [ClaudeStatusIcons].
 *
 * **Only the first tab drives the icon** — see [ClaudeStatusState], which holds those rules
 * and is unit-tested; this class is the IDE wiring around it.
 *
 * Two sources feed the state, because neither alone is complete:
 *
 * 1. **Hook state files** — `.claude/aside-status/<session-id>`, written by the hooks
 *    [ClaudeStatusHooks] installs, and watched here. These carry the normal lifecycle:
 *    idle → thinking → waiting → thinking → idle.
 * 2. **Transcript polling** — `~/.claude/projects/<encoded>/<session-id>.jsonl`. Claude
 *    fires no hook when a turn is cut short by Ctrl+C or by denying a permission prompt,
 *    which would strand the icon on `thinking`/`waiting` forever. It does append a
 *    `[Request interrupted by user]` record, and that is what gets the button back to idle.
 *    Polled rather than watched, and only while the icon is animating — that is exactly
 *    when an interrupt is possible, and it costs one tail read a second.
 */
@Service(Service.Level.PROJECT)
class ClaudeStatusService(private val project: Project) : Disposable {

    private val state = ClaudeStatusState()

    @Volatile
    private var statusWatch: WatchService? = null
    private var started = false

    /** EDT-only animation state. */
    private var animator: Timer? = null
    private var animated: ClaudeStatus? = null
    private var frame = 0

    /** Background poll of the primary session's transcript; runs only while animating. */
    private var interruptPoll: ScheduledFuture<*>? = null

    /** When an interrupt keystroke was last seen, for [describeState]. */
    @Volatile
    private var lastInterruptKey: Long = 0

    /**
     * A human-readable dump of everything the indicator depends on.
     *
     * The status pipeline spans a shell script, a file watcher, a transcript and a key
     * listener, and when it goes quiet none of that is visible. This is what the gear
     * menu's *Diagnose Status Indicator* shows.
     */
    fun describeState(): String {
        val base = project.basePath
        val primary = state.primarySession()
        val statusFile = base?.let { statusDir(it).resolve(primary ?: "-") }
        val onDisk = statusFile?.takeIf { Files.exists(it) }?.let { runCatching { Files.readString(it) }.getOrNull() }
        val keySeen = if (lastInterruptKey == 0L) "never" else "${(System.currentTimeMillis() - lastInterruptKey) / 1000}s ago"

        return buildString {
            appendLine("Tracked tabs: ${state.openTabCount()}")
            appendLine("First tab's session: ${primary ?: "none — no tab was launched with a known id"}")
            appendLine("Status the button shows: ${state.visibleStatus()?.word ?: "none (still glyph)"}")
            appendLine("Status file: ${onDisk ?: "not written yet"}")
            appendLine("Hooks installed: ${ClaudeStatusHooks.forProject(project)?.isInstalled() ?: false}")
            appendLine("Animating: ${animated?.word ?: "no"}")
            append("Interrupt key last seen: $keySeen")
        }
    }

    /** Starts watching the hook state files (idempotent). */
    @Synchronized
    fun ensureStarted() {
        if (started) return
        started = true
        val base = project.basePath ?: return
        watchStatusFiles(base)
        rescanStatusFiles(base)
        refreshIcon()
    }

    /** Associates a tool-window tab with the Claude session running in it. */
    fun registerTab(sessionId: String, content: Content) {
        state.openTab(sessionId, content.displayName)
        baselineInterrupts(sessionId)
        refreshIcon()
    }

    /**
     * Reports the tab as idle the moment the user presses an interrupt key inside
     * [component], for as long as [parent] lives.
     *
     * See [ClaudeInterruptKeys] for why the keystroke, rather than anything Claude writes,
     * is what this hangs on.
     */
    fun watchInterruptKeys(component: Component, sessionId: String, parent: Disposable) {
        // Listens on the AWT toolkit rather than through IdeEventQueue: the terminal is a
        // heavily customised component and this is the lowest level the keystroke can be
        // observed at, before anything in the IDE gets a chance to route or swallow it.
        val listener = AWTEventListener { event ->
            if (event is KeyEvent &&
                event.id == KeyEvent.KEY_PRESSED &&
                ClaudeInterruptKeys.isInterrupt(event.keyCode, event.modifiersEx) &&
                isOurs(component, event.source as? Component)
            ) {
                lastInterruptKey = System.currentTimeMillis()
                noteUserInterrupt(sessionId)
            }
        }
        Toolkit.getDefaultToolkit().addAWTEventListener(listener, AWTEvent.KEY_EVENT_MASK)
        Disposer.register(parent) { Toolkit.getDefaultToolkit().removeAWTEventListener(listener) }
    }

    /**
     * Whether a keystroke belongs to a Claude tab.
     *
     * Ancestry is the precise answer, but the terminal can host its editor outside the
     * tab's own component tree, so an active Claude tool window counts too — if it has the
     * IDE's focus, Ctrl+C was meant for Claude.
     */
    private fun isOurs(tabComponent: Component, source: Component?): Boolean {
        if (source != null && SwingUtilities.isDescendingFrom(source, tabComponent)) return true
        return ToolWindowManager.getInstance(project).activeToolWindowId == TOOL_WINDOW_ID
    }

    /** Treats [sessionId] as idle again after the user interrupted it. */
    private fun noteUserInterrupt(sessionId: String) {
        state.setStatus(sessionId, ClaudeStatus.IDLE)
        refreshIcon()
    }

    /** Forgets a closed tab; the next-oldest tab takes over the icon. */
    fun unregisterTab(sessionId: String) {
        state.closeTab(sessionId)
        refreshIcon()
    }

    // ---------------------------------------------------------------- hook state files

    private fun statusDir(base: String): Path =
        Paths.get(base, ClaudeStatusHooks.CLAUDE_DIR, ClaudeStatusHooks.STATUS_DIR)

    private fun watchStatusFiles(base: String) {
        val dir = statusDir(base)
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
            statusWatch = ws
            ApplicationManager.getApplication().executeOnPooledThread { watchLoop(ws, base) }
        } catch (e: Exception) {
            LOG.warn("Claude Aside: cannot watch $dir for status changes", e)
        }
    }

    private fun watchLoop(ws: WatchService, base: String) {
        while (!project.isDisposed) {
            val key = try {
                ws.take()
            } catch (_: Exception) {
                return // watch service closed on dispose
            }
            key.pollEvents()
                .mapNotNull { it.context()?.toString() }
                .distinct()
                .forEach { onStatusFileChanged(base, it) }
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

    private fun onStatusFileChanged(base: String, sessionId: String) {
        val file = statusDir(base).resolve(sessionId)
        val status = try {
            if (Files.exists(file)) ClaudeStatus.fromWord(Files.readString(file)) else null
        } catch (_: Exception) {
            return // mid-write; the next event will settle it
        }
        if (status == null) {
            state.clearStatus(sessionId)
        } else if (state.setStatus(sessionId, status)) {
            notifyWaitingIfHidden(sessionId)
        }
        refreshIcon()
    }

    // ------------------------------------------------------------------- interrupts

    /** The newest interrupt record in [sessionId]'s transcript, or null. */
    private fun latestInterrupt(sessionId: String): String? {
        val base = project.basePath ?: return null
        val path = ClaudeSessions.transcriptPath(System.getProperty("user.home"), base, sessionId)
        return readTail(path)?.let { ClaudeSessions.lastInterruptUuid(it) }
    }

    /**
     * Marks whatever the transcript already holds as accounted for, off the EDT.
     *
     * Done when a tab opens and again whenever Claude starts being busy, so that an old
     * record — including one the keyboard watcher has already acted on — can't reset the
     * icon during the *next* turn.
     */
    private fun baselineInterrupts(sessionId: String) {
        AppExecutorUtil.getAppExecutorService().execute {
            state.baselineInterrupt(sessionId, latestInterrupt(sessionId))
        }
    }

    /**
     * Resets a session to idle when a new interrupt record has appeared since the last look.
     */
    private fun readInterrupt(sessionId: String) {
        if (!state.observeInterrupt(sessionId, latestInterrupt(sessionId))) return
        state.setStatus(sessionId, ClaudeStatus.IDLE)
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

    /** Runs [readInterrupt] on a background thread while the icon animates, nothing when idle. */
    private fun setInterruptPolling(enabled: Boolean) {
        if (!enabled) {
            interruptPoll?.cancel(false)
            interruptPoll = null
            return
        }
        if (interruptPoll != null) return
        // Re-baseline first: only an interrupt that arrives during *this* busy stretch counts.
        state.primarySession()?.let { baselineInterrupts(it) }
        interruptPoll = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(
            {
                if (project.isDisposed) return@scheduleWithFixedDelay
                state.primarySession()?.let { runCatching { readInterrupt(it) } }
            },
            POLL_MS, POLL_MS, TimeUnit.MILLISECONDS,
        )
    }

    // ----------------------------------------------------------------------- painting

    /** Points the tool-window button at the first open tab's state, animating if needed. */
    private fun refreshIcon() {
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID) ?: return@invokeLater
            val status = state.visibleStatus()
            setInterruptPolling(ClaudeStatusIcons.isAnimated(status))
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
            val where = state.tabName(sessionId) ?: "session ${sessionId.take(8)}"
            NotificationGroupManager.getInstance()
                .getNotificationGroup(NOTIFICATION_GROUP)
                .createNotification("Claude is waiting for you in $where", NotificationType.INFORMATION)
                .notify(project)
        }
    }

    override fun dispose() {
        animator?.stop()
        animator = null
        interruptPoll?.cancel(false)
        interruptPoll = null
        statusWatch?.let { runCatching { it.close() } }
        statusWatch = null
    }

    companion object {
        private val LOG = logger<ClaudeStatusService>()
        const val NOTIFICATION_GROUP = "Claude Aside"

        /** Enough to hold the last transcript records; they are single lines of JSON. */
        private const val TAIL_BYTES = 64L * 1024

        /** ~14 fps: smooth enough for a 13px glyph, cheap enough to leave running. */
        private const val FRAME_MS = 70

        /** How often to look for an interrupt while Claude is busy. */
        private const val POLL_MS = 700L
    }
}
