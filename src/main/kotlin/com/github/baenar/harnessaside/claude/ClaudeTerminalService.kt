package com.github.baenar.harnessaside.claude

import com.github.baenar.harnessaside.core.TOOL_WINDOW_ID
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
        val env = project.service<com.github.baenar.harnessaside.core.HarnessTerminalService>().harnessEnv()
        val options = ShellStartupOptions.Builder()
            .workingDirectory(project.basePath)
            .envVariables(env)
            .build()

        val widget = LocalTerminalDirectRunner.createTerminalRunner(project)
            .startShellTerminalWidget(toolWindow.disposable, options, true)
        widget.sendCommandToExecute(command)

        val cm = toolWindow.contentManager
        com.github.baenar.harnessaside.ui.HarnessToolWindowUi.isSwitching = true
        try {
            cm.removeAllContents(true)
            val tabTitle = title ?: "Claude Code"
            val content = ContentFactory.getInstance().createContent(widget.component, tabTitle, false)
            content.isCloseable = true
            content.putUserData(WIDGET_KEY, widget)
            content.putUserData(com.github.baenar.harnessaside.core.HarnessTerminalService.WIDGET_KEY, widget)
            content.putUserData(com.github.baenar.harnessaside.core.HARNESS_ID_KEY, "claude")
            content.putUserData(SESSION_KEY, sessionId)

            val status = project.service<ClaudeStatusService>()
            // Scoped to this tab: the key watcher must go when the tab does.
            val tabScope = Disposer.newDisposable("Claude Aside tab")
            sessionId?.let { status.watchInterruptKeys(widget.component, it, tabScope) }
            project.service<com.github.baenar.harnessaside.core.HarnessTerminalService>().forwardEscapeKey(widget, tabScope)
            // Closing the tab disposes the widget, terminating the pty.
            content.setDisposer {
                Disposer.dispose(tabScope)
                Disposer.dispose(widget)
            }

            cm.addContent(content)
            cm.setSelectedContent(content, true)
        } finally {
            com.github.baenar.harnessaside.ui.HarnessToolWindowUi.isSwitching = false
        }
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
     * session it picked, so the tab could not record (or later resume) its session.
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
        val WIDGET_KEY: Key<TerminalWidget> = Key.create<TerminalWidget>("claude.aside.terminalWidget")
        val SESSION_KEY: Key<String> = Key.create<String>("claude.aside.sessionId")

        fun hasTerminal(content: Content?): Boolean = content?.getUserData(WIDGET_KEY) != null

        /** The Claude session running in [content], when the plugin launched it with a known id. */
        fun sessionIdOf(content: Content?): String? = content?.getUserData(SESSION_KEY)
    }
}
