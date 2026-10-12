package com.github.baenar.harnessaside.core

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindow
import com.intellij.terminal.ui.TerminalWidget
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import org.jetbrains.plugins.terminal.LocalTerminalDirectRunner
import org.jetbrains.plugins.terminal.ShellStartupOptions

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
        val content = ContentFactory.getInstance().createContent(widget.component, title, false)
        content.isCloseable = true
        content.putUserData(WIDGET_KEY, widget)
        userData.forEach { (key, value) ->
            @Suppress("UNCHECKED_CAST")
            content.putUserData(key as Key<Any>, value)
        }

        content.setDisposer {
            onDispose?.invoke()
            Disposer.dispose(widget)
        }

        cm.addContent(content)
        cm.setSelectedContent(content, true)
        return content
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
     */
    fun harnessEnv(inheritedWslEnv: String? = System.getenv("WSLENV")): Map<String, String> {
        val server = HarnessStatusServer.getInstance(project)
        val port = server.ensureStarted()
        val token = server.token

        val inherited = inheritedWslEnv?.removeSuffix(":").orEmpty()
        val forwarded = "FORCE_HYPERLINK/u:HARNESS_IDE_PORT/u:HARNESS_IDE_TOKEN/u"
        val wslEnv = if (inherited.isEmpty()) forwarded else "$inherited:$forwarded"

        return mapOf(
            "FORCE_HYPERLINK" to "0",
            "HARNESS_IDE_PORT" to port.toString(),
            "HARNESS_IDE_TOKEN" to token,
            "WSLENV" to wslEnv,
        )
    }

    companion object {
        val WIDGET_KEY = Key.create<TerminalWidget>("harness.aside.terminalWidget")
    }
}
