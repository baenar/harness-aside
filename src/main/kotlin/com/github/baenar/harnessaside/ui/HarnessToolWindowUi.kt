package com.github.baenar.harnessaside.ui

import com.github.baenar.harnessaside.core.Harness
import com.github.baenar.harnessaside.core.HarnessStatus
import com.github.baenar.harnessaside.core.HarnessStatusManager
import com.github.baenar.harnessaside.core.HarnessTerminalService
import com.github.baenar.harnessaside.core.TOOL_WINDOW_ID
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.terminal.ui.TerminalWidget
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import javax.swing.JComponent

/**
 * What the tool window shows. It always holds exactly one content: in single-session mode the
 * [HarnessLauncherPanel] or one terminal, in multi-session mode the [MultiSessionPanel] with its tabs.
 */
object HarnessToolWindowUi {

    /** Shows the launcher or the tabs, depending on the project's session mode. */
    fun showMode(project: Project, toolWindow: ToolWindow) {
        val manager = project.service<HarnessStatusManager>()
        manager.single.onStatusChanged(HarnessStatus.IDLE)
        if (!HarnessStatusManager.isMultiSession(project)) {
            show(toolWindow, HarnessLauncherPanel { launchSingle(project, toolWindow, it) }, "Launcher")
            return
        }
        val panel = MultiSessionPanel(project, toolWindow)
        val content = show(toolWindow, panel, panel.title) { setDisposer(panel) }
        panel.onTitleChanged = { setTitle(content, it) }
    }

    private fun launchSingle(project: Project, toolWindow: ToolWindow, harness: Harness) {
        val status = project.service<HarnessStatusManager>().single
        project.service<HarnessTerminalService>().launch(harness, null, status) { widget, session ->
            show(toolWindow, widget.component, "running ${harness.displayName}") {
                putUserData(HarnessTerminalService.WIDGET_KEY, widget)
                setDisposer(session)
            }
        }
    }

    /**
     * Replaces what the tool window shows with [component]. Its header reads as one line, a bold tool window
     * name followed by [title], without a tab's background or close button: there is only ever one, so it
     * must not look like a tab bar. The factory hides the platform's own name label, which would sit apart.
     *
     * The new content goes in before the old one leaves. The count never reaches zero, so the listener that
     * brings the launcher back when a session ends stays quiet.
     */
    fun show(toolWindow: ToolWindow, component: JComponent, title: String, configure: Content.() -> Unit = {}): Content {
        val content = ContentFactory.getInstance().createContent(component, "", false).apply {
            setTitle(this, title)
            isCloseable = false
            putUserData(Content.SIMPLIFIED_TAB_RENDERING_KEY, true)
            configure()
        }
        val cm = toolWindow.contentManager
        val previous = cm.contents
        cm.addContent(content)
        cm.setSelectedContent(content, true)
        previous.forEach { cm.removeContent(it, true) }
        return content
    }

    private fun setTitle(content: Content, title: String) {
        content.displayName = "<html><b>$TOOL_WINDOW_ID</b> $title</html>"
        content.toolwindowTitle = "$TOOL_WINDOW_ID $title" // anything reading the title elsewhere gets no markup
    }

    /** The terminal the user is looking at: the single session, or the selected tab's. */
    fun activeWidget(toolWindow: ToolWindow): TerminalWidget? {
        val content = toolWindow.contentManager.selectedContent ?: return null
        return content.getUserData(HarnessTerminalService.WIDGET_KEY) ?: (content.component as? MultiSessionPanel)?.selectedWidget
    }

    /** True while switching modes would end a running session. */
    fun hasRunningSessions(toolWindow: ToolWindow): Boolean {
        val content = toolWindow.contentManager.selectedContent ?: return false
        return content.getUserData(HarnessTerminalService.WIDGET_KEY) != null ||
            (content.component as? MultiSessionPanel)?.hasRunningSessions == true
    }

    /** True while a single-session terminal is showing. */
    fun hasActiveTerminal(toolWindow: ToolWindow): Boolean =
        toolWindow.contentManager.selectedContent?.getUserData(HarnessTerminalService.WIDGET_KEY) != null
}
