package com.github.baenar.harnessaside.core

import com.github.baenar.harnessaside.ui.HarnessToolWindowUi
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindow
import com.intellij.terminal.ui.TerminalWidget
import org.jetbrains.plugins.terminal.LocalTerminalDirectRunner
import org.jetbrains.plugins.terminal.ShellStartupOptions
import java.awt.Component
import java.awt.KeyboardFocusManager
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.MenuSelectionManager
import javax.swing.SwingUtilities

/**
 * Runs harnesses in terminals inside the tool window.
 *
 * Every session is a shell (working dir = project root) with the harness command typed into it, so the
 * terminal survives the harness exiting. Disposing the session kills the pty.
 */
@Service(Service.Level.PROJECT)
class HarnessTerminalService(private val project: Project) {

    /** Sessions between a launcher click and their terminal opening; a double click must not start two. */
    private val launching = HashSet<SessionStatus>()

    /**
     * Starts [harness] for [status], then gives its terminal and the session's disposable to [show] on the
     * EDT. The session lives no longer than [status]. [sessionId] pins a multi-session tab to its own conversation. Hooks and session lookup touch files
     * that can sit on a slow `\\wsl.localhost` share, so they run off the EDT, before the terminal opens,
     * because a harness reads its hooks when it starts.
     */
    fun launch(
        harness: Harness,
        sessionId: String?,
        status: SessionStatus,
        show: (TerminalWidget, session: Disposable) -> Unit,
    ) {
        if (!launching.add(status)) return
        val app = ApplicationManager.getApplication()
        app.executeOnPooledThread {
            harness.installStatusHooks(project)
            val command = harness.launchCommand(project, sessionId)
            val port = project.service<HarnessStatusServer>().ensureStarted()
            app.invokeLater({
                launching.remove(status)
                if (status.isDisposed) return@invokeLater // its tab was closed meanwhile
                val (widget, session) = openTerminal(harness, command, port, status)
                show(widget, session)
            }, project.disposed)
        }
    }

    private fun openTerminal(harness: Harness, command: String?, port: Int, status: SessionStatus): Pair<TerminalWidget, Disposable> {
        // Owns everything of this session: the pty, its key handling and its status token.
        val session = Disposer.newDisposable(status, "Harness session")
        val server = project.service<HarnessStatusServer>()
        val app = ApplicationManager.getApplication()
        val token = server.register { state -> app.invokeLater({ status.onStatusChanged(state) }, project.disposed) }
        Disposer.register(session) { server.unregister(token) }

        val options = ShellStartupOptions.Builder()
            .workingDirectory(project.basePath)
            .envVariables(harnessEnv(port, token))
            .build()
        val widget = LocalTerminalDirectRunner.createTerminalRunner(project)
            .startShellTerminalWidget(session, options, true)
        command?.let(widget::sendCommandToExecute)

        status.harnessName = harness.displayName
        status.watchKeys(widget.component, session)
        forwardEscapeKey(widget, session, status)
        return widget to session
    }

    /**
     * Types [text] into the active terminal without submitting it (no trailing newline),
     * then focuses the terminal.
     */
    fun sendText(toolWindow: ToolWindow, text: String) {
        val widget = HarnessToolWindowUi.activeWidget(toolWindow) ?: return
        widget.write(text)
        widget.requestFocus()
    }

    private fun TerminalWidget.write(text: String) {
        // runCatching: the pty may already be closed.
        ttyConnectorAccessor.executeWithTtyConnector { connector -> runCatching { connector.write(text) } }
    }

    /**
     * Environment variables for the harness shell, providing server connection details.
     *
     * `FORCE_HYPERLINK=0` stops Claude emitting OSC 8 hyperlinks. They deadlock the classic
     * terminal (jediterm holds the text-buffer lock while the IDE's hyperlink filters wait for
     * it), costing a ~6s freeze per link. WSL only passes names listed in `WSLENV` into the
     * distro, so ours are appended to whatever the user already forwards.
     *
     * `CLAUDE_CODE_SSE_PORT` / `ENABLE_IDE_INTEGRATION` aren't set here: the official Claude Code
     * plugin, when installed, adds them to every local terminal (its `LocalTerminalCustomizer`)
     * so `claude` connects to that plugin's IDE MCP server. They're only forwarded, so the
     * connection also works for `claude` inside WSL.
     */
    private fun harnessEnv(port: Int, token: String): Map<String, String> {
        val inherited = System.getenv("WSLENV")?.removeSuffix(":").orEmpty()
        // No `/u` flag: a Windows exe started from the distro (opencode.exe via PATH interop) needs them too.
        val forwarded = "FORCE_HYPERLINK:HARNESS_ASIDE_IDE_PORT:HARNESS_ASIDE_IDE_TOKEN:CLAUDE_CODE_SSE_PORT:ENABLE_IDE_INTEGRATION"

        return mapOf(
            "FORCE_HYPERLINK" to "0",
            "HARNESS_ASIDE_IDE_PORT" to port.toString(),
            "HARNESS_ASIDE_IDE_TOKEN" to token,
            "WSLENV" to if (inherited.isEmpty()) forwarded else "$inherited:$forwarded",
        )
    }

    /**
     * Sends a plain Escape inside [widget] straight to the pty and consumes it, so the IDE doesn't move
     * focus from the tool window to the editor. This sees every IDE event, so it filters cheaply first.
     */
    private fun forwardEscapeKey(widget: TerminalWidget, parentDisposable: Disposable, status: SessionStatus) {
        val dispatcher = IdeEventQueue.EventDispatcher { e ->
            if (e !is KeyEvent || !isPlainEscape(e)) return@EventDispatcher false

            val focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
            if (!isInside(e.component, widget) && !isInside(focusOwner, widget)) return@EventDispatcher false

            // An active lookup (completion) or Swing menu keeps its Escape.
            if (LookupManager.getInstance(project).activeLookup != null) return@EventDispatcher false
            if (MenuSelectionManager.defaultManager().selectedPath.isNotEmpty()) return@EventDispatcher false

            if (e.id == KeyEvent.KEY_PRESSED) {
                widget.write("\u001b")
                status.onInterrupt()
            }
            e.consume()
            true
        }
        IdeEventQueue.getInstance().addDispatcher(dispatcher, parentDisposable)
    }

    private fun isPlainEscape(e: KeyEvent): Boolean {
        val escape = if (e.id == KeyEvent.KEY_TYPED) e.keyChar == '\u001b' else e.keyCode == KeyEvent.VK_ESCAPE
        return escape && (e.modifiersEx and MODIFIERS) == 0
    }

    private fun isInside(child: Component?, widget: TerminalWidget): Boolean =
        child != null && SwingUtilities.isDescendingFrom(child, widget.component)

    companion object {
        val WIDGET_KEY = Key.create<TerminalWidget>("harness.aside.terminalWidget")

        private const val MODIFIERS = InputEvent.CTRL_DOWN_MASK or InputEvent.ALT_DOWN_MASK or
            InputEvent.SHIFT_DOWN_MASK or InputEvent.META_DOWN_MASK or InputEvent.ALT_GRAPH_DOWN_MASK
    }
}
