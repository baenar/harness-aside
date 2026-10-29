package com.github.baenar.harnessaside

import com.github.baenar.harnessaside.claude.DiagnoseStatusAction
import com.github.baenar.harnessaside.claude.EnableStatusHooksAction
import com.github.baenar.harnessaside.claude.RemoveClaudeTabAction
import com.github.baenar.harnessaside.claude.RestartClaudeTabAction
import com.github.baenar.harnessaside.ui.HarnessToolWindowUi
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener

/**
 * Tool window factory for the dedicated right-docked AI harness tool window.
 *
 * Enforces a single-tab invariant: displays the HarnessLauncherPanel when no session is running,
 * and exactly one terminal tab when a harness session is active.
 *
 * [DumbAware], like every action here: nothing in the plugin reads indexes or PSI, so there is
 * no reason to lock the harness away while the IDE indexes.
 */
class HarnessToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        toolWindow.setTitleActions(
            listOf(
                RestartClaudeTabAction(toolWindow),
                RemoveClaudeTabAction(toolWindow),
            )
        )
        toolWindow.setAdditionalGearActions(
            DefaultActionGroup(EnableStatusHooksAction(project), DiagnoseStatusAction(project))
        )

        // Automatically return to the launcher whenever the active session tab is closed
        toolWindow.contentManager.addContentManagerListener(object : ContentManagerListener {
            override fun contentRemoved(event: ContentManagerEvent) {
                if (!HarnessToolWindowUi.isSwitching && toolWindow.contentManager.contentCount == 0 && !project.isDisposed) {
                    HarnessToolWindowUi.showLauncher(project, toolWindow)
                }
            }
        })

        // On open, always present the launcher view with supported harnesses
        HarnessToolWindowUi.showLauncher(project, toolWindow)
    }
}
