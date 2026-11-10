package com.github.baenar.harnessaside.ui

import com.github.baenar.harnessaside.core.HarnessStatus
import com.github.baenar.harnessaside.core.HarnessStatusManager
import com.github.baenar.harnessaside.core.HarnessTerminalService
import com.github.baenar.harnessaside.core.TOOL_WINDOW_ID
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import javax.swing.JComponent

/** Keeps the single-tab invariant: the tool window shows either the [HarnessLauncherPanel] or one terminal. */
object HarnessToolWindowUi {

    /**
     * Replaces what the tool window shows with [component]. Its header reads as one line, a bold tool window
     * name followed by [title], without a tab's background or close button: there is only ever one, so it
     * must not look like a tab bar. The factory hides the platform's own name label, which would sit apart.
     *
     * The new content goes in before the old one leaves. The count never reaches zero, so the listener that
     * brings the launcher back when a session ends stays quiet.
     */
    fun show(toolWindow: ToolWindow, component: JComponent, title: String, configure: Content.() -> Unit = {}) {
        val content = ContentFactory.getInstance().createContent(component, "<html><b>$TOOL_WINDOW_ID</b> $title</html>", false).apply {
            toolwindowTitle = "$TOOL_WINDOW_ID $title" // anything reading the title elsewhere gets no markup
            isCloseable = false
            putUserData(Content.SIMPLIFIED_TAB_RENDERING_KEY, true)
            configure()
        }
        val cm = toolWindow.contentManager
        val previous = cm.contents
        cm.addContent(content)
        cm.setSelectedContent(content, true)
        previous.forEach { cm.removeContent(it, true) }
    }

    fun showLauncher(project: Project, toolWindow: ToolWindow) {
        show(toolWindow, HarnessLauncherPanel(project, toolWindow), "Launcher")
        project.service<HarnessStatusManager>().onStatusChanged(HarnessStatus.IDLE)
    }

    /** True while a terminal session is showing in the tool window. */
    fun hasActiveTerminal(toolWindow: ToolWindow): Boolean =
        toolWindow.contentManager.selectedContent?.getUserData(HarnessTerminalService.WIDGET_KEY) != null
}
