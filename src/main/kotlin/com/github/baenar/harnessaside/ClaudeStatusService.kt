package com.github.baenar.harnessaside

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
import java.util.concurrent.ConcurrentHashMap
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
 *    [ClaudeStatusHooks] installs, and polled here. These carry the normal lifecycle:
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

    /** Background poll of the hook state files; see [startStatusPolling]. */
    private var statusPoll: ScheduledFuture<*>? = null

    /**
     * What the previous scan saw in each state file: its mtime and its contents.
     *
     * The poll must only report *changes*, which is what `WatchService` used to give for
     * free. Re-applying an unchanged file is not harmless: Claude fires no hook on Ctrl+C,
     * so after an interrupt the file still reads `thinking` while the button is correctly
     * idle, and re-applying it would put the animation straight back.
     */
    @Volatile
    private var seenStatusFiles: Map<String, Pair<Long, String>> = emptyMap()

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
     * The status pipeline spans a shell script, a polled directory, a transcript and a key
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
        prepareStatusDir(base)
        scanStatusFiles(base)
        startStatusPolling(base)
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
        seenTranscripts.remove(sessionId)
        refreshIcon()
    }

    // ---------------------------------------------------------------- hook state files

    private fun statusDir(base: String): Path =
        Paths.get(base, ClaudeStatusHooks.CLAUDE_DIR, ClaudeStatusHooks.STATUS_DIR)

    /** Makes sure the directory the hooks write into exists before anything reads it. */
    private fun prepareStatusDir(base: String) {
        val dir = statusDir(base)
        try {
            // Pre-3.1 installs left a plain file where the status directory now lives.
            if (Files.isRegularFile(dir)) Files.delete(dir)
            Files.createDirectories(dir)
        } catch (e: Exception) {
            LOG.warn("Claude Aside: cannot prepare $dir", e)
        }
    }

    /**
     * Polls the state files instead of watching them.
     *
     * NIO's `WatchService` was the obvious choice and is the wrong one here. On Windows it
     * is backed by `ReadDirectoryChangesW`, which reports nothing for a project opened over
     * `\\wsl$\...` - that share is the distro's 9P filesystem, not a local volume.
     * The watch registered without error and then stayed silent forever, so the hooks
     * wrote `thinking` while the button went on showing `idle`.
     *
     * Polling is a listing and a short read twice a second, over a directory holding one
     * file per open tab. Cheap enough everywhere, and it is one code path rather than one
     * per filesystem.
     */
    private fun startStatusPolling(base: String) {
        statusPoll = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(
            {
                // With no tab there is nothing the icon could show, so skip the listing
                // rather than repeat it for the life of the project: a scan measures
                // ~0.2ms locally but ~6ms over `\\wsl$`.
                if (!project.isDisposed && state.openTabCount() > 0) runCatching { scanStatusFiles(base) }
            },
            STATUS_POLL_MS, STATUS_POLL_MS, TimeUnit.MILLISECONDS,
        )
    }

    /** Applies every state file, and clears the sessions whose file has gone. */
    private fun scanStatusFiles(base: String) {
        val dir = statusDir(base)
        val names = try {
            if (!Files.isDirectory(dir)) emptySet()
            else Files.list(dir).use { stream -> stream.map { it.fileName.toString() }.toList().toSet() }
        } catch (e: Exception) {
            LOG.warn("Claude Aside: cannot read $dir", e)
            return
        }

        val current = HashMap<String, Pair<Long, String>>(names.size)
        for (name in names) {
            // mtime as well as contents: a new turn rewrites the same word `thinking`, and
            // that rewrite has to count as a change even though nothing in the file did.
            val file = dir.resolve(name)
            val stamp = try {
                Files.getLastModifiedTime(file).toMillis() to Files.readString(file)
            } catch (_: Exception) {
                continue // mid-write, or gone since the listing; the next poll settles it
            }
            current[name] = stamp
            if (seenStatusFiles[name] != stamp) onStatusFileChanged(base, name)
        }

        // A file disappears when the session ends; the same handler clears it.
        (seenStatusFiles.keys - current.keys).forEach { onStatusFileChanged(base, it) }
        seenStatusFiles = current
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

    /**
     * Per session: the transcript's size and mtime when it was last read, and the interrupt
     * uuid found then.
     *
     * A transcript that has not moved cannot hold a new interrupt, and the longest stretch
     * of animation — Claude sitting on an approval prompt — is exactly when it does not
     * move. Two stat calls are much cheaper than the 64KB tail read they replace, which
     * measures ~14ms over `\\wsl$`.
     */
    private val seenTranscripts = ConcurrentHashMap<String, Pair<Pair<Long, Long>, String?>>()

    /** The newest interrupt record in [sessionId]'s transcript, or null. */
    private fun latestInterrupt(sessionId: String): String? {
        val base = project.basePath ?: return null
        val path = ClaudeSessions.transcriptPath(System.getProperty("user.home"), base, sessionId)
        val stamp = try {
            Files.size(path) to Files.getLastModifiedTime(path).toMillis()
        } catch (_: Exception) {
            return null // transcript missing or being rotated
        }
        seenTranscripts[sessionId]?.let { (seen, uuid) -> if (seen == stamp) return uuid }

        val uuid = readTail(path)?.let { ClaudeSessions.lastInterruptUuid(it) }
        seenTranscripts[sessionId] = stamp to uuid
        return uuid
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
        statusPoll?.cancel(false)
        statusPoll = null
    }

    companion object {
        private val LOG = logger<ClaudeStatusService>()
        const val NOTIFICATION_GROUP = "Harness Aside"

        /** Enough to hold the last transcript records; they are single lines of JSON. */
        private const val TAIL_BYTES = 64L * 1024

        /** How often the hook state files are re-read. Fast enough to feel immediate. */
        private const val STATUS_POLL_MS = 500L

        /**
         * 10 fps: smooth enough for a 13px glyph, and every frame is EDT work — `setIcon`
         * repaints the stripe button and refreshes the activate action's presentation —
         * running for as long as Claude is busy.
         */
        private const val FRAME_MS = 100

        /** How often to look for an interrupt while Claude is busy. */
        private const val POLL_MS = 700L
    }
}
