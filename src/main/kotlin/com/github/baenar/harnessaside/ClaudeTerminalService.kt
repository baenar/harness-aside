package com.github.baenar.harnessaside

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindow
import com.intellij.terminal.ui.TerminalWidget
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import org.jetbrains.plugins.terminal.LocalTerminalDirectRunner
import org.jetbrains.plugins.terminal.ShellStartupOptions
import java.util.UUID

const val TOOL_WINDOW_ID = "Harness Aside"

/**
 * Owns the Claude terminal tabs inside the right-docked tool window.
 *
 * Every tab is a shell terminal (working dir = project root) into which a `claude`
 * command is injected via [TerminalWidget.sendCommandToExecute], so the tab survives
 * Claude exiting. Closing a tab disposes its widget, which kills the pty.
 *
 * Each tab is launched with a **known session id** — `--session-id <uuid>` for a new
 * session, `-r <id>` for a resumed one — instead of the older bare `claude -c`. That is
 * what lets [ClaudeStatusService] tell the tabs' states apart: `-c` picks a session
 * without telling anyone which one it picked.
 */
@Service(Service.Level.PROJECT)
class ClaudeTerminalService(private val project: Project) {

    /** Opens a tab running a brand-new session under a freshly minted id. */
    fun openNewSession(toolWindow: ToolWindow) {
        val launch = Launch.new()
        openTab(toolWindow, launch.command, launch.sessionId)
    }

    /**
     * Opens a tab resuming [sessionId] — or, when null, the project's most recent session.
     * Falls back to a new session when the project has none, matching what `claude -c` did
     * before, except that the id is known up front either way.
     */
    fun openContinuedSession(toolWindow: ToolWindow, sessionId: String? = null) {
        val resumed = sessionId ?: project.basePath?.let {
            ClaudeSessions.latestSessionId(System.getProperty("user.home"), it)
        }
        val launch = Launch.resumeOrNew(resumed)
        openTab(toolWindow, launch.command, launch.sessionId)
    }

    /**
     * Opens a new terminal tab and runs [command] in it.
     *
     * @param sessionId the Claude session [command] will run under, used for the tab's
     *   status indicator; null when it cannot be known.
     * @param title tab title; when null a sensible "Claude"/"Claude N" name is derived.
     */
    fun openTab(toolWindow: ToolWindow, command: String, sessionId: String? = null, title: String? = null) {
        val options = ShellStartupOptions.Builder()
            .workingDirectory(project.basePath)
            .envVariables(claudeEnv(System.getenv("WSLENV")))
            .build()

        val widget = LocalTerminalDirectRunner.createTerminalRunner(project)
            .startShellTerminalWidget(toolWindow.disposable, options, true)
        widget.sendCommandToExecute(command)

        val cm = toolWindow.contentManager
        val tabTitle = title ?: if (cm.contentCount == 0) "Claude" else "Claude ${cm.contentCount + 1}"
        val content = ContentFactory.getInstance().createContent(widget.component, tabTitle, false)
        content.isCloseable = true
        content.putUserData(WIDGET_KEY, widget)
        content.putUserData(SESSION_KEY, sessionId)

        val status = project.service<ClaudeStatusService>()
        // Scoped to this tab: the key watcher must go when the tab does.
        val tabScope = Disposer.newDisposable("Claude Aside tab")
        sessionId?.let {
            status.registerTab(it, content)
            status.watchInterruptKeys(widget.component, it, tabScope)
        }
        // Closing the tab disposes the widget, terminating the pty.
        content.setDisposer {
            sessionId?.let { status.unregisterTab(it) }
            Disposer.dispose(tabScope)
            Disposer.dispose(widget)
        }

        cm.addContent(content)
        cm.setSelectedContent(content, true)
    }

    /** The terminal in the currently selected tab, or null when there is none. */
    fun activeWidget(toolWindow: ToolWindow): TerminalWidget? =
        toolWindow.contentManager.selectedContent?.getUserData(WIDGET_KEY)

    /**
     * Types [text] into the active terminal without submitting it (no trailing newline),
     * so the user can add to it before pressing Enter, then focuses the terminal.
     */
    fun sendText(toolWindow: ToolWindow, text: String) {
        val widget = activeWidget(toolWindow) ?: return
        widget.ttyConnectorAccessor.executeWithTtyConnector { connector ->
            try {
                connector.write(text)
            } catch (_: Exception) {
                // pty already closed — nothing to type into
            }
        }
        widget.requestFocus()
    }

    /**
     * A shell command plus the session id it will run under.
     *
     * Bare `claude -c` is deliberately never used: it resumes without telling anyone which
     * session it picked, and [ClaudeStatusService] needs that id to match a tab to its
     * state file and transcript.
     */
    data class Launch(val command: String, val sessionId: String) {
        companion object {
            fun new(sessionId: String = UUID.randomUUID().toString()): Launch =
                Launch("claude --session-id $sessionId", sessionId)

            fun resumeOrNew(sessionId: String?): Launch =
                if (sessionId == null) new() else Launch("claude -r $sessionId", sessionId)
        }
    }

    companion object {
        private val WIDGET_KEY = Key.create<TerminalWidget>("claude.aside.terminalWidget")
        private val SESSION_KEY = Key.create<String>("claude.aside.sessionId")

        /** Name and forwarding flag of the variable that turns Claude's OSC 8 links off. */
        private const val FORWARDED = "FORCE_HYPERLINK/u"

        /**
         * Environment overrides for a tab's shell. The platform merges these over the
         * inherited environment, so this map is additions, not a replacement.
         *
         * `FORCE_HYPERLINK=0` stops Claude emitting OSC 8 hyperlinks. They deadlock the
         * classic terminal: jediterm parses the sequence holding the text-buffer lock and
         * then waits on the IDE's hyperlink filters, which want that same lock, while the
         * EDT waits behind both to paint. It unwinds only on jediterm's own timeout, so
         * every hyperlink cost a ~6s freeze.
         *
         * WSL is a second hop — the variable is set on `wsl.exe`, and only names listed in
         * [inheritedWslEnv] cross into the distro — so ours is appended to whatever the
         * user already had. The platform in turn appends its own names to this, rather
         * than overwriting it.
         */
        fun claudeEnv(inheritedWslEnv: String?): Map<String, String> {
            val inherited = inheritedWslEnv?.removeSuffix(":").orEmpty()
            return mapOf(
                "FORCE_HYPERLINK" to "0",
                "WSLENV" to if (inherited.isEmpty()) FORWARDED else "$inherited:$FORWARDED",
            )
        }

        /** The Claude session running in [content], when the plugin launched it with a known id. */
        fun sessionIdOf(content: Content?): String? = content?.getUserData(SESSION_KEY)
    }
}
