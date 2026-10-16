package com.github.baenar.harnessaside.ui

import com.github.baenar.harnessaside.claude.ClaudeTerminalService
import com.github.baenar.harnessaside.core.HarnessStatus
import com.github.baenar.harnessaside.core.HarnessStatusManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.content.ContentFactory

/**
 * Utility managing the single-tab invariant and transitions between the [HarnessLauncherPanel]
 * and active harness terminal sessions.
 */
object HarnessToolWindowUi {

    val LAUNCHER_KEY: Key<Boolean> = Key.create<Boolean>("harness.aside.launcher")

    /** Flag indicating a planned content transition is in progress, preventing spurious listener callbacks. */
    @Volatile
    var isSwitching: Boolean = false

    /**
     * Replaces current tool window content with [HarnessLauncherPanel] and resets status indicator to [HarnessStatus.IDLE].
     */
    fun showLauncher(project: Project, toolWindow: ToolWindow) {
        if (project.isDisposed || isSwitching) return
        val cm = toolWindow.contentManager
        if (cm.contentCount == 1 && cm.selectedContent?.getUserData(LAUNCHER_KEY) == true) return

        isSwitching = true
        try {
            cm.removeAllContents(true)
            val launcherPanel = HarnessLauncherPanel(project, toolWindow)
            val content = ContentFactory.getInstance().createContent(launcherPanel, "Launcher", false)
            content.isCloseable = false
            content.putUserData(LAUNCHER_KEY, true)
            cm.addContent(content)
            cm.setSelectedContent(content, true)
        } finally {
            isSwitching = false
        }

        project.service<HarnessStatusManager>().onStatusChanged(HarnessStatus.IDLE)
    }

    /**
     * Returns true when an interactive terminal session is currently running in the tool window.
     */
    fun hasActiveTerminal(toolWindow: ToolWindow): Boolean {
        val selected = toolWindow.contentManager.selectedContent ?: return false
        return selected.getUserData(LAUNCHER_KEY) != true &&
            (ClaudeTerminalService.hasTerminal(selected) ||
                selected.getUserData(com.github.baenar.harnessaside.core.HarnessTerminalService.WIDGET_KEY) != null)
    }
}
