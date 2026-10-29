package com.github.baenar.harnessaside.core

import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.Disposable
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
import java.awt.Component
import java.awt.KeyboardFocusManager
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.MenuSelectionManager
import javax.swing.SwingUtilities

/**
 * Project-level service managing terminal execution and tabs in the tool window.
 *
 * Harness-agnostic: creates tabs, forwards commands to the shell, and provides text input.
 */
@Service(Service.Level.PROJECT)
class HarnessTerminalService(private val project: Project) {

    /**
     * Opens a new terminal tab running [command].
     */
    fun openTerminalTab(
        toolWindow: ToolWindow,
        command: String,
        title: String,
        env: Map<String, String> = emptyMap(),
        userData: Map<Key<*>, Any?> = emptyMap(),
        onDispose: (() -> Unit)? = null,
    ): Content {
        val mergedEnv = harnessEnv() + env
        val options = ShellStartupOptions.Builder()
            .workingDirectory(project.basePath)
            .envVariables(mergedEnv)
            .build()

        val widget = LocalTerminalDirectRunner.createTerminalRunner(project)
            .startShellTerminalWidget(toolWindow.disposable, options, true)
        widget.sendCommandToExecute(command)

        val cm = toolWindow.contentManager
        com.github.baenar.harnessaside.ui.HarnessToolWindowUi.isSwitching = true
        try {
            cm.removeAllContents(true)
            val content = ContentFactory.getInstance().createContent(widget.component, title, false)
            content.isCloseable = true
            content.putUserData(WIDGET_KEY, widget)
            content.putUserData(com.github.baenar.harnessaside.claude.ClaudeTerminalService.WIDGET_KEY, widget)
            userData.forEach { (key, value) ->
                @Suppress("UNCHECKED_CAST")
                content.putUserData(key as Key<Any>, value)
            }

            val statusManager = project.service<HarnessStatusManager>()
            val tabScope = Disposer.newDisposable("Harness tab")
            statusManager.watchInterruptKeys(widget.component, tabScope)
            forwardEscapeKey(widget, tabScope)

            content.setDisposer {
                Disposer.dispose(tabScope)
                onDispose?.invoke()
                Disposer.dispose(widget)
            }

            cm.addContent(content)
            cm.setSelectedContent(content, true)
            return content
        } finally {
            com.github.baenar.harnessaside.ui.HarnessToolWindowUi.isSwitching = false
        }
    }

    /** The terminal in the currently selected tab, or null when there is none. */
    fun activeWidget(toolWindow: ToolWindow): TerminalWidget? =
        toolWindow.contentManager.selectedContent?.getUserData(WIDGET_KEY)

    /**
     * Types [text] into the active terminal without submitting it (no trailing newline),
     * then focuses the terminal.
     */
    fun sendText(toolWindow: ToolWindow, text: String) {
        val widget = activeWidget(toolWindow) ?: return
        widget.ttyConnectorAccessor.executeWithTtyConnector { connector ->
            try {
                connector.write(text)
            } catch (_: Exception) {
                // pty already closed
            }
        }
        widget.requestFocus()
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
    fun harnessEnv(inheritedWslEnv: String? = System.getenv("WSLENV")): Map<String, String> {
        val server = HarnessStatusServer.getInstance(project)
        val port = server.ensureStarted()
        val token = server.token

        val inherited = inheritedWslEnv?.removeSuffix(":").orEmpty()
        val forwarded = "FORCE_HYPERLINK/u:HARNESS_ASIDE_IDE_PORT/u:HARNESS_ASIDE_IDE_TOKEN/u:HARNESS_IDE_PORT/u:HARNESS_IDE_TOKEN/u" +
            ":CLAUDE_CODE_SSE_PORT/u:ENABLE_IDE_INTEGRATION/u"
        val wslEnv = if (inherited.isEmpty()) forwarded else "$inherited:$forwarded"

        return mapOf(
            "FORCE_HYPERLINK" to "0",
            "HARNESS_ASIDE_IDE_PORT" to port.toString(),
            "HARNESS_ASIDE_IDE_TOKEN" to token,
            "HARNESS_IDE_PORT" to port.toString(),
            "HARNESS_IDE_TOKEN" to token,
            "WSLENV" to wslEnv,
        )
    }

    /**
     * Intercepts unadorned Escape key events when keyboard focus is inside [widget],
     * forwarding '\u001b' directly to the terminal PTY and consuming the event so the IDE
     * does not switch focus away from the tool window to the editor.
     */
    fun forwardEscapeKey(widget: TerminalWidget, parentDisposable: Disposable) {
        val dispatcher = IdeEventQueue.EventDispatcher { e ->
            if (project.isDisposed) return@EventDispatcher false

            val isEscape = when (e) {
                is KeyEvent -> when (e.id) {
                    KeyEvent.KEY_PRESSED, KeyEvent.KEY_RELEASED -> e.keyCode == KeyEvent.VK_ESCAPE
                    KeyEvent.KEY_TYPED -> e.keyChar == '\u001b'
                    else -> false
                }
                else -> false
            }
            if (!isEscape) return@EventDispatcher false

            val keyEvent = e as KeyEvent
            val modifiersMask = InputEvent.CTRL_DOWN_MASK or
                InputEvent.ALT_DOWN_MASK or
                InputEvent.SHIFT_DOWN_MASK or
                InputEvent.META_DOWN_MASK or
                InputEvent.ALT_GRAPH_DOWN_MASK
            if ((keyEvent.modifiersEx and modifiersMask) != 0) {
                return@EventDispatcher false
            }

            // Do not intercept if an active lookup (completion) or Swing menu is visible
            if (LookupManager.getInstance(project).activeLookup != null) return@EventDispatcher false
            if (MenuSelectionManager.defaultManager().selectedPath.isNotEmpty()) return@EventDispatcher false

            val source = (keyEvent as? java.awt.event.ComponentEvent)?.component ?: (keyEvent.source as? Component)
            val focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner

            val inWidget = isDescendingFrom(source, widget.component) || isDescendingFrom(focusOwner, widget.component)
            if (!inWidget) return@EventDispatcher false

            if (keyEvent.id == KeyEvent.KEY_PRESSED) {
                widget.ttyConnectorAccessor.executeWithTtyConnector { connector ->
                    try {
                        connector.write("\u001b")
                    } catch (_: Exception) {
                        // PTY closed
                    }
                }
                val statusManager = project.service<HarnessStatusManager>()
                if (statusManager.currentStatus != HarnessStatus.IDLE) {
                    statusManager.onStatusChanged(HarnessStatus.IDLE)
                }
            }

            keyEvent.consume()
            true
        }

        IdeEventQueue.getInstance().addDispatcher(dispatcher, parentDisposable)
    }

    private fun isDescendingFrom(child: Component?, parent: Component): Boolean {
        if (child == null) return false
        return child == parent || SwingUtilities.isDescendingFrom(child, parent)
    }

    companion object {
        val WIDGET_KEY = Key.create<TerminalWidget>("harness.aside.terminalWidget")
    }
}
